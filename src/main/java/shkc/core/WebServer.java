package shkc.core;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * The embedded web server. It listens on one port and sends every request to
 * either a registered {@link WebHandler} (the app's endpoints) or, when no
 * handler matches, to a static file from the browser UI.
 *
 * <p>Handlers are registered with {@link #addHandler(String, WebHandler)}
 * under a URL path, optionally followed by {@code .command} to pick a handler
 * by the {@code cmd} request parameter. Static files are looked up on disk
 * first, then on the classpath (see {@link FileLocator}).
 */
public class WebServer {
   /** Size of the chunks a file is read in, 128 KiB. */
   private static final int CHUNK_SIZE = 131072;

   /** The request parameter whose value selects among handlers on the same path. */
   private static final String COMMAND_PARAMETER = "cmd";

   /** Addresses beginning with this are the machine talking to itself, not reachable from the network. */
   static final String LOOPBACK_PREFIX = "127.";

   private static final String LOOPBACK_ADDRESS = "127.0.0.1";

   /** Served when a file's extension is not in {@link #CONTENT_TYPES}. */
   private static final String DEFAULT_CONTENT_TYPE = "text/plain";

   /** Served when the request names no file, just the site root. */
   private static final String DEFAULT_PAGE = "index.html";

   /** Browser content type for each file extension the UI serves. */
   private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
      Map.entry("html", "text/html"),
      Map.entry("htm", "text/html"),
      Map.entry("css", "text/css"),
      Map.entry("csv", "text/csv"),
      Map.entry("js", "text/javascript"),
      Map.entry("rtf", "text/rtf"),
      Map.entry("xml", "text/xml"),
      Map.entry("png", "image/png"),
      Map.entry("gif", "image/gif"),
      Map.entry("jpg", "image/jpeg"),
      Map.entry("pdf", "application/pdf"),
      Map.entry("zip", "application/zip"),
      Map.entry("gzip", "application/gzip"),
      Map.entry("json", "application/json"),
      Map.entry("svg", "image/svg+xml"));

   // Set from EagleBoardScheduler.main when -bind is given; null keeps the
   // original Jetty behavior of accepting connections on every interface.
   private static String bindHost = null;

   /** The path of the change feed, whose requests stay open for as long as a page does. */
   private static final String EVENT_STREAM_PATH = "/events";

   /** A request slower than this is logged as a warning: an operator waiting on it noticed. */
   private static final long SLOW_REQUEST_MILLIS = 2000L;

   /** Stops the set of client addresses seen growing without bound on a hostile network. */
   private static final int MAX_CLIENTS_REMEMBERED = 500;

   /** Set on a request once it has reached {@link LocalDefaultHandler}, so the server's own request log can tell the ones that never did. */
   private static final String REACHED_ATTRIBUTE = "eagleboards.reached";

   /** Set by {@link ChangeFeed} on an event stream's request to say how the stream ended. */
   static final String STREAM_END_ATTRIBUTE = "eagleboards.streamEnd";

   private final AtomicLong requestCounter = new AtomicLong();
   private final Map<String, InFlight> requestsInFlight = new ConcurrentHashMap<>();
   private final AtomicInteger openStreams = new AtomicInteger();
   private final Map<String, Boolean> clientsSeen = new ConcurrentHashMap<>();

   private int port = 8080;
   private final String htmlDirectory;
   private final Map<String, WebHandler> handlersByPath = new ConcurrentHashMap<>();
   private final FileLocator locator;

   // Told after every POST a handler answers, so the pages listening on
   // /events re-read at once instead of polling (see ChangeFeed).
   private ChangeFeed changeFeed = null;

   /** Creates a server whose static files come from a directory named {@code html}. */
   public WebServer() {
      this("html");
   }

   /**
    * @param htmlDirectory where the static files are looked for, both as a
    *                      directory on disk and as a folder inside the jar
    */
   public WebServer(String htmlDirectory) {
      this.htmlDirectory = htmlDirectory;
      // Search order for a file inside the jar or beside it: the packaged
      // classes' own folder, then the html directory, then the root, then
      // the current directory.
      this.locator = new FileLocator("/shkc/core/;" + htmlDirectory + ";/;.");
   }

   /** Limits the server to one local address (see {@code -bind}); null listens on every interface. */
   public static void setBindHost(String bindHost) {
      WebServer.bindHost = bindHost;
   }

   /** The single address the server was limited to, or null when it listens on every interface. */
   public static String getBindHost() {
      return bindHost;
   }

   /** The requests being served now, except the long-lived event streams. For the stall watchdog. */
   List<InFlight> requestsInFlight() {
      return new ArrayList<>(this.requestsInFlight.values());
   }

   /** A request being served: what the stall watchdog needs to say which one is stuck and where. */
   static final class InFlight {
      private final String id;
      private final String method;
      private final String target;
      private final long startedNanos;
      private final Thread thread;
      private final AtomicBoolean reported = new AtomicBoolean();

      InFlight(String id, String method, String target, long startedNanos, Thread thread) {
         this.id = id;
         this.method = method;
         this.target = target;
         this.startedNanos = startedNanos;
         this.thread = thread;
      }

      String id() {
         return this.id;
      }

      String method() {
         return this.method;
      }

      String target() {
         return this.target;
      }

      long startedNanos() {
         return this.startedNanos;
      }

      Thread thread() {
         return this.thread;
      }

      /** True the first time only, so a stall is reported once. */
      boolean markReported() {
         return this.reported.compareAndSet(false, true);
      }
   }

   /** Sets who is told after each POST that a handler answered. */
   public void setChangeFeed(ChangeFeed changeFeed) {
      this.changeFeed = changeFeed;
   }

   public void setPort(int port) {
      this.port = port;
   }

   public int getPort() {
      return this.port;
   }

   /**
    * Registers {@code handler} for a URL path such as {@code "/youth-update"}.
    * A path of the form {@code "/page.command"} matches only when the request
    * also carries {@code cmd=command}.
    */
   public void addHandler(String path, WebHandler handler) {
      this.handlersByPath.put(path, handler);
   }

   /** Starts listening and blocks until the server stops. */
   public void start() throws Exception {
      // Jetty 12 (ee11/jakarta) equivalent of the original Jetty 8 setup.
      // The Jetty 8 version also stacked a filesystem ResourceHandler in
      // front of the dispatcher, but LocalDefaultHandler's sendResponseFile
      // already checks the filesystem before the classpath, so all serving
      // behavior is preserved by the single dispatcher servlet.
      Server server = this.createServer();

      ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      contextHandler.setContextPath("/");
      contextHandler.addServlet(new ServletHolder(new LocalDefaultHandler()), "/*");
      server.setHandler(contextHandler);
      server.setRequestLog(WebServer::logRequestNeverServed);
      server.start();
      // After start(): that is when the port is bound, so this line is only written if it is.
      Log.info("listening on port " + this.getPort() + (bindHost == null ? ", every interface" : ", bound to " + bindHost));
      server.join();
   }

   /**
    * Jetty's own log of a request, called when any request ends. A request that
    * never reached {@link LocalDefaultHandler} (a tablet that typed https:// at
    * this plain port, a malformed request line, one Jetty refused as too large) is
    * otherwise written nowhere, and "the iPad can't connect" would leave no trace.
    * Requests the servlet served already have their line, so they are skipped.
    */
   private static void logRequestNeverServed(Request request, Response response) {
      try {
         if (request.getAttribute(REACHED_ATTRIBUTE) != null) {
            return;
         }
         String address = Request.getRemoteAddr(request);
         String uri = String.valueOf(request.getHttpURI());
         Log.warn("request never reached the app: " + Log.clean(request.getMethod()) + " "
            + Log.clean(uri.length() > 200 ? uri.substring(0, 200) : uri) + " -> " + response.getStatus() + " from " + Log.clean(address)
            + " (a malformed request, https:// typed at this plain http port, or one the server refused)");
      } catch (RuntimeException loggingFailure) {
         // Logging a request must never fail it.
      }
   }

   /** Builds the Jetty server, listening on every interface or only on the {@code -bind} address. */
   private Server createServer() {
      if (bindHost == null) {
         return new Server(this.getPort());
      }

      // Same server, but the public connector is pinned to one local address
      // so the socket never appears on virtual/VPN adapters.
      Server server = new Server();
      ServerConnector venueConnector = new ServerConnector(server);
      venueConnector.setHost(bindHost);
      venueConnector.setPort(this.getPort());
      server.addConnector(venueConnector);

      // Pinning one address also drops loopback, which would break
      // http://127.0.0.1:<port>/scheduler on the admin machine itself. Add a
      // second connector for it: loopback is not reachable off-box, so this
      // costs nothing in exposure.
      if (!bindHost.startsWith(LOOPBACK_PREFIX)) {
         ServerConnector loopbackConnector = new ServerConnector(server);
         loopbackConnector.setHost(LOOPBACK_ADDRESS);
         loopbackConnector.setPort(this.getPort());
         server.addConnector(loopbackConnector);
      }

      return server;
   }

   /**
    * Sends a static file to the browser: from {@code dir} on disk if it is
    * there, otherwise from the classpath. A blank name or {@code "/"} means
    * the home page.
    *
    * @return false only when the file was found nowhere and nothing was sent,
    *         so the caller can try something else
    */
   public boolean sendResponseFile(String dir, String fileName, HttpServletResponse response) {
      Log.verbose("looking for: dir=" + dir + ", fname=" + fileName);
      if (fileName == null || fileName.length() == 0 || fileName.equals("/")) {
         fileName = DEFAULT_PAGE;
      }

      File file = new File(dir, fileName);
      if (file.exists()) {
         try {
            return this.sendResponseFile(fileName, new FileInputStream(file), response);
         } catch (Exception openFailure) {
            Log.warn("cannot open the file " + file.getPath() + " to serve it", openFailure);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return true;
         }
      }

      Log.verbose("[1] not in filesystem, checking classpath dir=" + dir + "/" + fileName);
      String classpathPath = dir + "/" + fileName;
      if (dir.endsWith("/") || fileName.startsWith("/")) {
         classpathPath = dir + fileName;
      }
      InputStream stream = this.openFromSearchPath(classpathPath);
      if (stream == null) {
         Log.verbose("[2] not in filesystem, checking classpath dir=" + fileName);
         stream = this.openFromSearchPath(fileName);
      }
      if (stream == null) {
         return false;
      }
      return this.sendResponseFile(fileName, stream, response);
   }

   /** Opens {@code location} through the search path, or returns null when it is not there. */
   private InputStream openFromSearchPath(String location) {
      try {
         return this.locator.getInputStream(location);
      } catch (IOException notFound) {
         return null;
      }
   }

   /**
    * Writes the contents of {@code in} as the response, with a content type
    * chosen from the extension of {@code fileName}, and closes {@code in}.
    * A failure reading or writing becomes a 500 response.
    *
    * @return always true, since a response (good or 500) was produced
    */
   public boolean sendResponseFile(String fileName, InputStream in, HttpServletResponse response) {
      Log.verbose("sendResponseFile:" + fileName);

      try (in) {
         // Read the resource fully, then write it. Uses a growable stream
         // instead of reallocating+copying the whole buffer on every chunk
         // (the original was O(n^2) in memory churn, a needless GC/OOM risk).
         ByteArrayOutputStream buffer = new ByteArrayOutputStream(CHUNK_SIZE);
         byte[] chunk = new byte[CHUNK_SIZE];
         int bytesRead;

         while ((bytesRead = in.read(chunk)) >= 0) {
            buffer.write(chunk, 0, bytesRead);
         }

         byte[] body = buffer.toByteArray();
         response.setContentLength(body.length);
         Log.verbose("size:" + body.length);

         String contentType = getContentType(extensionOf(fileName));
         response.setContentType(contentType);
         Log.verbose("content-type:" + contentType);
         try {
            response.getOutputStream().write(body);
         } catch (IOException clientGone) {
            // Only the browser or the connection can fail the write itself, and how it says so
            // differs by system ("Broken pipe", "Connection reset by peer", "An established
            // connection was aborted"), so this is decided by where the failure happened, not
            // by its wording. A tablet that leaves the page mid-download is not a fault.
            Log.verbose("the client went away while " + fileName + " was being sent: " + clientGone);
            return true;
         }
         response.setStatus(HttpServletResponse.SC_OK);
      } catch (Exception failure) {
         // Reading the file (the disk or the jar) failed: that is a fault.
         Log.verbose("could not send " + fileName + ": " + failure);
         Log.warn("could not send the file " + fileName, failure);
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }

      return true;
   }

   /** The lower-case text after the last dot of {@code fileName}, or {@code "txt"} when there is none. */
   private static String extensionOf(String fileName) {
      int dotPosition = fileName.lastIndexOf('.');
      if (dotPosition > 0 && dotPosition < fileName.length() - 1) {
         return fileName.substring(dotPosition + 1).toLowerCase();
      }
      return "txt";
   }

   /** The name of the request parameter that selects among handlers on the same path. */
   public String getCommandTag() {
      return COMMAND_PARAMETER;
   }

   /** The browser content type for a file extension such as {@code "css"}; plain text if unknown. */
   public static String getContentType(String extension) {
      return CONTENT_TYPES.getOrDefault(extension, DEFAULT_CONTENT_TYPE);
   }

   /** The one servlet behind the server: it routes every request to a handler or a static file. */
   public class LocalDefaultHandler extends HttpServlet {
      private static final long serialVersionUID = 1L;

      @Override
      protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         // Every line logged while this request runs carries its number, so what
         // one tablet did can be followed through the event log.
         String target = request.getRequestURI();
         String requestId = "r" + requestCounter.incrementAndGet();
         long startedNanos = System.nanoTime();
         // The address follows the number as one word, so a line can be tied to the tablet
         // that sent it even when it is an ordinary read, which has no access line.
         Log.setContext(requestId + "/" + Log.clean(request.getRemoteAddr()));
         request.setAttribute(REACHED_ATTRIBUTE, Boolean.TRUE);
         boolean isStream = EVENT_STREAM_PATH.equals(target);
         if (!isStream) {
            requestsInFlight.put(requestId, new InFlight(requestId, request.getMethod(), target, startedNanos, Thread.currentThread()));
         }
         try {
            noteClient(request);
            if (isStream) {
               Log.info("event stream opened by " + request.getRemoteAddr() + " (" + openStreams.incrementAndGet() + " open)");
            }
            dispatch(target, request, response);
         } finally {
            requestsInFlight.remove(requestId);
            long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
            if (isStream) {
               Object endedBecause = request.getAttribute(STREAM_END_ATTRIBUTE);
               Log.info("event stream closed after " + millis / 1000L + " s (" + openStreams.decrementAndGet() + " open)"
                  + (endedBecause == null ? "" : ": " + endedBecause));
            } else {
               logAccess(target, request, response, millis);
            }
            Log.clearContext();
         }
      }

      /** Logs the first request from each address with the browser it came from, for "it only fails on that tablet". */
      private void noteClient(HttpServletRequest request) {
         String address = request.getRemoteAddr();
         if (address != null && clientsSeen.size() < MAX_CLIENTS_REMEMBERED && clientsSeen.putIfAbsent(address, Boolean.TRUE) == null) {
            String agent = request.getHeader("User-Agent");
            Log.info("new client " + address + ", browser: " + (agent == null ? "(none sent)" : Log.clean(agent.length() > 200 ? agent.substring(0, 200) : agent)));
         }
      }

      /**
       * Writes the one line that says how a request ended. Every POST, and any
       * request that failed or ran slowly, is in the event log; an ordinary
       * page or data read is not, since the pages re-read after every change and
       * would bury the lines that matter (they are in the file with -debug).
       */
      private void logAccess(String target, HttpServletRequest request, HttpServletResponse response, long millis) {
         try {
            int status = response.getStatus();
            String method = request.getMethod();
            boolean isPost = "POST".equalsIgnoreCase(method);
            boolean isApi = target.startsWith("/api/");
            // The check-in pages' /api/ calls carry email addresses, so only the path is written.
            String line = method + " " + target + " -> " + status + " (" + millis + " ms) from " + request.getRemoteAddr()
               + (isApi ? "" : paramsSuffix(request));
            if (status >= 500) {
               Log.warn("failed: " + line);
            } else if (millis >= SLOW_REQUEST_MILLIS) {
               Log.warn("slow: " + line);
            } else if (isPost || status >= 400) {
               Log.info(line);
            } else if (Log.isDebugToFile()) {
               Log.verbose(line);
            }
         } catch (RuntimeException loggingFailure) {
            // Logging a request must never fail it.
         }
      }

      private String paramsSuffix(HttpServletRequest request) {
         String params = Log.safeParams(request);
         return params.isEmpty() ? "" : " [" + params + "]";
      }

      private void dispatch(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
         Log.verbose("LocalDefaultHandler: target=" + target);
         Log.verbose("context-path=" + request.getContextPath());
         WebHandler handler = null;
         String command = null;

         try {
            command = request.getParameterMap().get(getCommandTag())[0];
            handler = handlersByPath.get(target + "." + command);
         } catch (Exception noCommand) {
            // No usable cmd parameter (absent, empty, or a query that cannot be
            // read): fall through to the handler for the plain path.
         }

         if (handler == null) {
            handler = handlersByPath.get(target);
         }

         // Safety net: contain any failure of a single request handler here.
         // The app's SLF4J backend is a no-op, so without this an exception
         // (or an Error such as OutOfMemoryError from a large response) is a
         // silent 500 with no diagnostics, and could disrupt later requests.
         // Catching Throwable logs a full stack trace and returns a clean 500
         // for THIS request only, so one bad request can never wedge the
         // server for subsequent page loads.
         try {
            // With no handler, ?cmd=name can ask for the page name.html.
            if (handler == null && command != null && sendResponseFile(htmlDirectory, command + ".html", response)) {
               return;
            }

            if (handler != null) {
               Log.verbose("FOUND: " + target + "." + command);
               handler.handle(target, request, response);
               announceChangeAfterPost(target, request);
            } else if (!sendResponseFile(htmlDirectory, target, response)) {
               if (sendResponseFile(htmlDirectory, target + ".html", response)) {
                  return;
               }

               Log.verbose("NOT FOUND: " + target);
               response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
         } catch (Throwable failure) {
            Log.error("unhandled failure in " + request.getMethod() + " " + target + " from " + request.getRemoteAddr()
               + (target.startsWith("/api/") ? "" : paramsSuffix(request)), failure);
            if (!response.isCommitted()) {
               response.reset();
               response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
               response.setContentType("text/plain");
               response.getOutputStream().write(("ERROR: " + failure).getBytes());
            }
         }
      }

      /** Tells the change feed that data may have changed, after a POST handler has answered. */
      private void announceChangeAfterPost(String target, HttpServletRequest request) {
         // The check-in pages' /api/ lookups are POSTs that change nothing.
         if (changeFeed != null && "POST".equalsIgnoreCase(request.getMethod())
               && !target.startsWith("/api/")) {
            changeFeed.changed();
         }
      }
   }

   /** One endpoint of the app: answers the requests the server routes to it. */
   public interface WebHandler {
      /**
       * @param target the request path, e.g. {@code "/youth-update"}
       */
      void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
   }
}
