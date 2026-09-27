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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public class WebServer {
   private static final int CHUNK_SIZE = 131072;
   private static Map<String, String> _contentTypeMap = new HashMap<>();
   private int _port = 8080;
   private String _commandTag = "cmd";
   private String _htmlDirectory = "html";
   private Map<String, WebServer.WebHandler> _handlerMap = new Hashtable<>();
   private FileLocator _locator;
   // Set from EagleBoardScheduler.main when -bind is given; null keeps the
   // original Jetty behavior of accepting connections on every interface.
   private static String _bindHost = null;

   public static void setBindHost(String bindHost) {
      _bindHost = bindHost;
   }

   public static String getBindHost() {
      return _bindHost;
   }

   // Told after every POST a handler answers, so the pages listening on
   // /events re-read at once instead of polling (see ChangeFeed).
   private ChangeFeed _changeFeed = null;

   public void setChangeFeed(ChangeFeed changeFeed) {
      this._changeFeed = changeFeed;
   }

   public WebServer() {
      this("html");
   }

   public WebServer(String htmlDirectory) {
      this._htmlDirectory = htmlDirectory;
      this._locator = new FileLocator("/shkc/core/;" + htmlDirectory + ";/;.");
   }

   public void setPort(int port) {
      this._port = port;
   }

   public int getPort() {
      return this._port;
   }

   public void addHandler(String path, WebServer.WebHandler handler) {
      this._handlerMap.put(path, handler);
   }

   public void start() throws Exception {
      // Jetty 12 (ee11/jakarta) equivalent of the original Jetty 8 setup.
      // The Jetty 8 version also stacked a filesystem ResourceHandler in
      // front of the dispatcher, but LocalDefaultHandler's sendResponseFile
      // already checks the filesystem before the classpath, so all serving
      // behavior is preserved by the single dispatcher servlet.
      Server server;
      if (_bindHost == null) {
         server = new Server(this.getPort());
      } else {
         // Same server, but the public connector is pinned to one local address
         // so the socket never appears on virtual/VPN adapters.
         server = new Server();
         ServerConnector venueConnector = new ServerConnector(server);
         venueConnector.setHost(_bindHost);
         venueConnector.setPort(this.getPort());
         server.addConnector(venueConnector);

         // Pinning one address also drops loopback, which would break
         // http://127.0.0.1:<port>/scheduler on the admin machine itself. Add a
         // second connector for it: loopback is not reachable off-box, so this
         // costs nothing in exposure.
         if (!_bindHost.startsWith("127.")) {
            ServerConnector loopbackConnector = new ServerConnector(server);
            loopbackConnector.setHost("127.0.0.1");
            loopbackConnector.setPort(this.getPort());
            server.addConnector(loopbackConnector);
         }
      }

      ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      contextHandler.setContextPath("/");
      contextHandler.addServlet(new ServletHolder(new WebServer.LocalDefaultHandler()), "/*");
      server.setHandler(contextHandler);
      server.start();
      server.join();
   }

   public boolean sendResponseFile(String dir, String fileName, HttpServletResponse response) {
      EagleBoardScheduler.verbose("looking for: dir=" + dir + ", fname=" + fileName);
      if (fileName == null || fileName.length() == 0 || fileName.equals("/")) {
         fileName = "index.html";
      }

      File file = new File(dir, fileName);
      if (file.exists()) {
         try {
            FileInputStream fileStream = new FileInputStream(file);
            return this.sendResponseFile(fileName, fileStream, response);
         } catch (Exception openFailure) {
            response.setStatus(500);
            return true;
         }
      } else {
         try {
            EagleBoardScheduler.verbose("[1] not in filesystem, checking classpath dir=" + dir + "/" + fileName);
            String classpathPath = dir + "/" + fileName;
            if (dir.endsWith("/") || fileName.startsWith("/")) {
               classpathPath = dir + fileName;
            }

            InputStream dirStream = this._locator.getInputStream(classpathPath);
            return this.sendResponseFile(fileName, dirStream, response);
         } catch (IOException dirMiss) {
            try {
               EagleBoardScheduler.verbose("[2] not in filesystem, checking classpath dir=" + fileName);
               InputStream rootStream = this._locator.getInputStream(fileName);
               return this.sendResponseFile(fileName, rootStream, response);
            } catch (IOException rootMiss) {
               return false;
            }
         }
      }
   }

   public boolean sendResponseFile(String fileName, InputStream in, HttpServletResponse response) {
      EagleBoardScheduler.verbose("sendResponseFile:" + fileName);

      try {
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
         EagleBoardScheduler.verbose("size:" + body.length);
         String extension = "txt";
         int dotPos = fileName.lastIndexOf(46);
         if (dotPos > 0 && dotPos < fileName.length() - 1) {
            extension = fileName.substring(dotPos + 1).toLowerCase();
         }

         String contentType = getContentType(extension);
         response.setContentType(contentType);
         EagleBoardScheduler.verbose("content-type:" + contentType);
         response.getOutputStream().write(body);
         response.setStatus(200);
      } catch (Exception failure) {
         response.setStatus(500);
      }

      return true;
   }

   public String getCommandTag() {
      return this._commandTag;
   }

   public static String getContentType(String extension) {
      String contentType = _contentTypeMap.get(extension);
      return contentType == null ? "text/plain" : contentType;
   }


   static {
      _contentTypeMap.put("html", "text/html");
      _contentTypeMap.put("htm", "text/html");
      _contentTypeMap.put("css", "text/css");
      _contentTypeMap.put("csv", "text/csv");
      _contentTypeMap.put("js", "text/javascript");
      _contentTypeMap.put("rtf", "text/rtf");
      _contentTypeMap.put("xml", "text/xml");
      _contentTypeMap.put("png", "image/png");
      _contentTypeMap.put("gif", "image/gif");
      _contentTypeMap.put("jpg", "image/jpeg");
      _contentTypeMap.put("pdf", "application/pdf");
      _contentTypeMap.put("zip", "application/zip");
      _contentTypeMap.put("gzip", "application/gzip");
      _contentTypeMap.put("json", "application/json");
      _contentTypeMap.put("svg", "image/svg+xml");
   }

   public class LocalDefaultHandler extends HttpServlet {
      File _baseDir = new File(".");

      @Override
      protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         String target = request.getRequestURI();
         EagleBoardScheduler.verbose("LocalDefaultHandler: target=" + target);
         EagleBoardScheduler.verbose("context-path=" + request.getContextPath());
         WebServer.WebHandler handler = null;
         String command = null;

         try {
            command = ((String[])request.getParameterMap().get(WebServer.this.getCommandTag()))[0];
            handler = WebServer.this._handlerMap.get(target + "." + command);
         } catch (Exception noCommand) {
         }

         if (handler == null) {
            try {
               handler = WebServer.this._handlerMap.get(target);
            } catch (Exception noHandler) {
            }
         }

         // Safety net: contain any failure of a single request handler here.
         // The app's SLF4J backend is a no-op, so without this an exception
         // (or an Error such as OutOfMemoryError from a large response) is a
         // silent 500 with no diagnostics, and could disrupt later requests.
         // Catching Throwable logs a full stack trace and returns a clean 500
         // for THIS request only, so one bad request can never wedge the
         // server for subsequent page loads.
         try {
            if (handler != null || command == null || !WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, command + ".html", response)) {
               if (handler != null) {
                  EagleBoardScheduler.verbose("FOUND: " + target + "." + command);
                  handler.handle(target, request, response);
                  // The check-in pages' /api/ lookups are POSTs that change nothing.
                  if (WebServer.this._changeFeed != null && "POST".equalsIgnoreCase(request.getMethod())
                        && !target.startsWith("/api/")) {
                     WebServer.this._changeFeed.changed();
                  }
               } else if (!WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, target, response)) {
                  if (WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, target + ".html", response)) {
                     return;
                  }

                  EagleBoardScheduler.verbose("NOT FOUND: " + target);
                  response.sendError(404);
               }
            }
         } catch (Throwable failure) {
            System.out.println("ERROR handling request " + target + ": " + failure);
            failure.printStackTrace(System.out);
            if (!response.isCommitted()) {
               response.reset();
               response.setStatus(500);
               response.setContentType("text/plain");
               response.getOutputStream().write(("ERROR: " + failure).getBytes());
            }
         }
      }
   }

   public interface WebHandler {
      void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
   }
}
