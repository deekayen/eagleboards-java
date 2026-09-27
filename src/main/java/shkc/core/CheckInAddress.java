package shkc.core;

import io.nayuki.qrcodegen.QrCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

// Where the tablets at the door reach the check-in page, for the Event page's
// footer and its QR code window. The operator's own browser usually reaches
// the scheduler as localhost, which is no use to a tablet, so the page asks
// the server for the addresses it is reachable at on the venue network --
// the same list the startup window and console print.
//
//   /checkin-address          {"urls": ["http://192.168.1.23:8080/", ...]}
//   /checkin-qr?url=<one>     that address as an SVG QR code
//
// The QR code is drawn only for an address in the list, so this cannot be
// used to make codes for anything else.
public class CheckInAddress {
   private CheckInAddress() {
   }

   // Every IPv4 address other than loopback, or only the -bind one when set.
   static List<String> urls(int port) {
      List<String> urls = new ArrayList<>();
      String bindHost = WebServer.getBindHost();
      if (bindHost != null && !bindHost.startsWith("127.")) {
         urls.add("http://" + bindHost + ":" + port + "/");
         return urls;
      }
      try {
         Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
         while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
               continue;
            }
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
               InetAddress address = addresses.nextElement();
               if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                  urls.add("http://" + address.getHostAddress() + ":" + port + "/");
               }
            }
         }
      } catch (IOException noInterfaces) {
         // No network at all: an empty list, which the page explains.
      }
      return urls;
   }

   public static class AddressHandler implements WebServer.WebHandler {
      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         StringBuilder json = new StringBuilder("{\"urls\": [");
         List<String> urls = urls(request.getLocalPort());
         for (int i = 0; i < urls.size(); i++) {
            json.append(i == 0 ? "" : ", ").append('"').append(urls.get(i)).append('"');
         }
         json.append("]}");
         byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
         response.setStatus(200);
         response.setContentType("application/json");
         response.setHeader("Cache-Control", "no-cache");
         response.setContentLength(body.length);
         response.getOutputStream().write(body);
      }
   }

   public static class QrHandler implements WebServer.WebHandler {
      @Override
      public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
         String url = request.getParameter("url");
         if (url == null || !urls(request.getLocalPort()).contains(url)) {
            response.sendError(404);
            return;
         }
         byte[] body = toSvg(QrCode.encodeText(url, QrCode.Ecc.MEDIUM), 4).getBytes(StandardCharsets.UTF_8);
         response.setStatus(200);
         response.setContentType("image/svg+xml");
         response.setHeader("Cache-Control", "no-cache");
         response.setContentLength(body.length);
         response.getOutputStream().write(body);
      }
   }

   // Black modules on white with a quiet zone of `border` modules, whatever
   // the page's theme: a camera needs the contrast the right way round.
   static String toSvg(QrCode qr, int border) {
      int size = qr.size + border * 2;
      StringBuilder path = new StringBuilder();
      for (int y = 0; y < qr.size; y++) {
         for (int x = 0; x < qr.size; x++) {
            if (qr.getModule(x, y)) {
               path.append('M').append(x + border).append(',').append(y + border).append("h1v1h-1z");
            }
         }
      }
      return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
         + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code for the check-in page\">"
         + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
         + "<path d=\"" + path + "\" fill=\"#000000\"/></svg>";
   }
}
