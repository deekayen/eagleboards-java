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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
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
      server.start();
      server.join();
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
         response.getOutputStream().write(body);
         response.setStatus(HttpServletResponse.SC_OK);
      } catch (Exception failure) {
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
         String target = request.getRequestURI();
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
            System.out.println("ERROR handling request " + target + ": " + failure);
            failure.printStackTrace(System.out);
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
