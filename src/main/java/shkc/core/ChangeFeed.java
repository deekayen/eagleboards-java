package shkc.core;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@code /events}: tells the operator's pages the moment the data changes, so
 * they never poll (SPEC.md D-15). A server-sent event stream: each message is
 * just a change number, and the page re-reads what it shows when one arrives.
 *
 * <p>WebServer calls {@link #changed()} after every POST a handler answers.
 * Every change to the data arrives as one (sign-in, seating, an Admin edit, a
 * Settings save) and no read does, so a page's own re-read can never set off
 * another round. A refused POST sends a message too; the re-read it causes
 * finds nothing new, which is cheaper than working out which POSTs changed
 * something.
 *
 * <p>Each open page holds one request thread while it listens. That is a
 * handful of threads at an event (the operator's screen, perhaps an Admin
 * tab), well inside Jetty's pool.
 */
public class ChangeFeed implements WebServer.WebHandler {
   // Sent when nothing has changed for this long, so a page whose connection
   // died without a word is noticed and dropped, and nothing in between
   // closes the stream as idle.
   private static final long HEARTBEAT_MILLIS = 15000L;
   // How long a browser waits before reconnecting after the stream drops.
   private static final int RETRY_MILLIS = 2000;

   /** Counts the changes so far; each listening page remembers the last number it was sent. */
   private long changeNumber = 0L;

   /** Records that the data changed and wakes every page that is waiting to hear of it. */
   public synchronized void changed() {
      this.changeNumber++;
      this.notifyAll();
   }

   // The change number once it differs from lastSeen, or lastSeen itself if
   // the heartbeat interval passes first.
   private synchronized long waitForChange(long lastSeen) throws InterruptedException {
      long deadline = System.currentTimeMillis() + HEARTBEAT_MILLIS;
      while (this.changeNumber == lastSeen) {
         long remaining = deadline - System.currentTimeMillis();
         if (remaining <= 0L) {
            break;
         }
         this.wait(remaining);
      }
      return this.changeNumber;
   }

   private synchronized long current() {
      return this.changeNumber;
   }

   /** Holds the request open and streams a message for each change, until the page goes away. */
   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("text/event-stream");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Cache-Control", "no-cache");
      OutputStream out = response.getOutputStream();

      // The first message says where the count stands, so a page that
      // reconnects after a drop re-reads straight away and misses nothing.
      long lastSent = this.current();
      try {
         send(out, response, "retry: " + RETRY_MILLIS + "\ndata: " + lastSent + "\n\n");
         while (true) {
            long now = this.waitForChange(lastSent);
            if (now == lastSent) {
               // A line starting with a colon is an SSE comment: browsers ignore it.
               send(out, response, ": still here\n\n");
            } else {
               lastSent = now;
               send(out, response, "data: " + now + "\n\n");
            }
         }
      } catch (IOException gone) {
         // The page closed or lost its connection: the normal way this ends.
      } catch (InterruptedException stopping) {
         Thread.currentThread().interrupt();
      }
   }

   /** Writes one event-stream message and pushes it to the browser at once. */
   private static void send(OutputStream out, HttpServletResponse response, String text) throws IOException {
      out.write(text.getBytes(StandardCharsets.UTF_8));
      out.flush();
      response.flushBuffer();
   }
}
