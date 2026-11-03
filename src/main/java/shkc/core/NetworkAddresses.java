package shkc.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/**
 * Tells the operator which web addresses the scheduler can be reached at.
 *
 * <p>At startup it lists the IPv4 address of every network interface on the
 * console, and (with {@code -w}) in a small popup window, so the operator can
 * type one into a tablet. With {@code -bind <ip-prefix>} it also limits the
 * server to the one interface whose address starts with that prefix, such as
 * {@code 192.168.} for the venue wifi, so virtual adapters do not each pop
 * their own dialog.
 */
final class NetworkAddresses {
   /** Loopback addresses start with this; they are reachable only from this computer. */
   private static final String LOOPBACK_PREFIX = "127";

   private NetworkAddresses() {
   }

   /**
    * Prints the usable addresses, shows the popup when asked, and applies
    * {@code -bind}.
    *
    * <p>When {@code bindPrefix} is absent, nothing is restricted: the server
    * listens on every interface and every address is listed.
    *
    * @param port        the port the web server will listen on
    * @param bindPrefix  the {@code -bind} value, or null
    * @param showPopup   whether to open the popup window for each interface
    * @throws SocketException if the computer's network interfaces cannot be read
    */
   static void announce(int port, String bindPrefix, boolean showPopup) throws SocketException {
      System.out.println("\n\nAvailable Network Interfaces");
      String boundAddress = null;

      for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
         StringBuilder urls = new StringBuilder();
         int addressCount = 0;

         for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
            if (isUsable(address, bindPrefix)) {
               urls.append("\n                 http://").append(address.getHostAddress()).append(":").append(port);
               if (boundAddress == null) {
                  boundAddress = address.getHostAddress();
               }

               addressCount++;
            }
         }

         if (addressCount > 0) {
            if (showPopup) {
               PopupDialog popup = new PopupDialog("        Connect to the following URLs\n" + urls + "\n");
               popup.setVisible(true);
            }

            System.out.println(urls);
         }
      }

      if (bindPrefix != null) {
         restrictServerTo(bindPrefix, boundAddress);
      }
   }

   /** An IPv4 address that is not loopback and, when {@code -bind} was given, starts with the prefix. */
   private static boolean isUsable(InetAddress address, String bindPrefix) {
      String text = address.getHostAddress();
      return address instanceof Inet4Address
         && !text.startsWith(LOOPBACK_PREFIX)
         && (bindPrefix == null || text.startsWith(bindPrefix));
   }

   private static void restrictServerTo(String bindPrefix, String boundAddress) {
      if (boundAddress == null) {
         // Don't strand the event if the wifi is down at startup: warn loudly
         // and fall back to the original listen-everywhere behavior.
         System.out.println("\n   WARNING: no active interface matches -bind " + bindPrefix + "; listening on all interfaces.\n");
      } else {
         WebServer.setBindHost(boundAddress);
         System.out.println("\n   binding to " + boundAddress + " and 127.0.0.1 (-bind " + bindPrefix + ")\n");
      }
   }
}
