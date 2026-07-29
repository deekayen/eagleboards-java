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

   public WebServer() {
      this("html");
   }

   public WebServer(String var1) {
      this._htmlDirectory = var1;
      this._locator = new FileLocator("/shkc/core/;" + var1 + ";/;.");
   }

   public void setPort(int var1) {
      this._port = var1;
   }

   public int getPort() {
      return this._port;
   }

   public void addHandler(String var1, WebServer.WebHandler var2) {
      this._handlerMap.put(var1, var2);
   }

   public void addHandler(String var1, String var2, WebServer.WebHandler var3) {
      if (var2 == null) {
         this._handlerMap.put(var1, var3);
      } else {
         this._handlerMap.put(var1 + "." + var2, var3);
      }
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

   public boolean sendResponseFile(String var1, String var2, HttpServletResponse var3) {
      EagleBoardScheduler.verbose("looking for: dir=" + var1 + ", fname=" + var2);
      if (var2 == null || var2.length() == 0 || var2.equals("/")) {
         var2 = "index.html";
      }

      File var4 = new File(var1, var2);
      if (var4.exists()) {
         try {
            FileInputStream var11 = new FileInputStream(var4);
            return this.sendResponseFile(var2, var11, var3);
         } catch (Exception var8) {
            var3.setStatus(500);
            return true;
         }
      } else {
         try {
            EagleBoardScheduler.verbose("[1] not in filesystem, checking classpath dir=" + var1 + "/" + var2);
            String var10 = var1 + "/" + var2;
            if (var1.endsWith("/") || var2.startsWith("/")) {
               var10 = var1 + var2;
            }

            InputStream var6 = this._locator.getInputStream(var10);
            return this.sendResponseFile(var2, var6, var3);
         } catch (IOException var9) {
            try {
               EagleBoardScheduler.verbose("[2] not in filesystem, checking classpath dir=" + var2);
               InputStream var5 = this._locator.getInputStream(var2);
               return this.sendResponseFile(var2, var5, var3);
            } catch (IOException var7) {
               return false;
            }
         }
      }
   }

   public boolean sendResponseFile(String var1, InputStream var2, HttpServletResponse var3) {
      EagleBoardScheduler.verbose("sendResponseFile:" + var1);

      try {
         // Read the resource fully, then write it. Uses a growable stream
         // instead of reallocating+copying the whole buffer on every chunk
         // (the original was O(n^2) in memory churn, a needless GC/OOM risk).
         ByteArrayOutputStream var12 = new ByteArrayOutputStream(131072);
         byte[] var6 = new byte[131072];
         int var7;

         while ((var7 = var2.read(var6)) >= 0) {
            var12.write(var6, 0, var7);
         }

         byte[] var5 = var12.toByteArray();
         var3.setContentLength(var5.length);
         EagleBoardScheduler.verbose("size:" + var5.length);
         String var13 = "txt";
         int var9 = var1.lastIndexOf(46);
         if (var9 > 0 && var9 < var1.length() - 1) {
            var13 = var1.substring(var9 + 1).toLowerCase();
         }

         String var10 = getContentType(var13);
         var3.setContentType(var10);
         EagleBoardScheduler.verbose("content-type:" + var10);
         var3.getOutputStream().write(var5);
         var3.setStatus(200);
      } catch (Exception var11) {
         var3.setStatus(500);
      }

      return true;
   }

   public String getCommandTag() {
      return this._commandTag;
   }

   public void setCommandTag(String var1) {
      this._commandTag = var1;
   }

   public static String getContentType(String var0) {
      String var1 = _contentTypeMap.get(var0);
      return var1 == null ? "text/plain" : var1;
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
   }

   public class LocalDefaultHandler extends HttpServlet {
      File _baseDir = new File(".");

      @Override
      protected void service(HttpServletRequest var3, HttpServletResponse var4) throws IOException, ServletException {
         String var1 = var3.getRequestURI();
         EagleBoardScheduler.verbose("LocalDefaultHandler: target=" + var1);
         EagleBoardScheduler.verbose("context-path=" + var3.getContextPath());
         WebServer.WebHandler var5 = null;
         String var6 = null;

         try {
            var6 = ((String[])var3.getParameterMap().get(WebServer.this.getCommandTag()))[0];
            var5 = WebServer.this._handlerMap.get(var1 + "." + var6);
         } catch (Exception var9) {
         }

         if (var5 == null) {
            try {
               var5 = WebServer.this._handlerMap.get(var1);
            } catch (Exception var8) {
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
            if (var5 != null || var6 == null || !WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, var6 + ".html", var4)) {
               if (var5 != null) {
                  EagleBoardScheduler.verbose("FOUND: " + var1 + "." + var6);
                  var5.handle(var1, var3, var4);
               } else if (!WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, var1, var4)) {
                  if (WebServer.this.sendResponseFile(WebServer.this._htmlDirectory, var1 + ".html", var4)) {
                     return;
                  }

                  EagleBoardScheduler.verbose("NOT FOUND: " + var1);
                  var4.sendError(404);
               }
            }
         } catch (Throwable var10) {
            System.out.println("ERROR handling request " + var1 + ": " + var10);
            var10.printStackTrace(System.out);
            if (!var4.isCommitted()) {
               var4.reset();
               var4.setStatus(500);
               var4.setContentType("text/plain");
               var4.getOutputStream().write(("ERROR: " + var10).getBytes());
            }
         }
      }
   }

   public interface WebHandler {
      void handle(String var1, HttpServletRequest var2, HttpServletResponse var3) throws IOException, ServletException;
   }
}
