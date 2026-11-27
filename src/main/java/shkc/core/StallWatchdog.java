package shkc.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Notices when the program is stuck while it is stuck, which the event log
 * cannot otherwise do: a request's access line is written when it ends, so a
 * request that never ends writes nothing, and the heartbeat only wakes every
 * five minutes.
 *
 * <p>Once a second this looks at the requests being served. One that has run
 * longer than {@link #STALL_MILLIS} is reported once, with the stack of the
 * thread serving it, which shows whether it is waiting for the data lock
 * (BLOCKED, and the request holding the lock is reported too, being older),
 * inside a handler, or in a file write. It also reports a garbage collection
 * that took most of a second.
 *
 * <p>It runs on its own daemon thread and never takes the data lock, so it
 * cannot be stuck behind the thing it is watching.
 */
final class StallWatchdog {
   /** How often the requests are looked at. */
   static final long TICK_MILLIS = 1000L;

   /** A request running longer than this is reported. The event page waits on it long before this. */
   static final long STALL_MILLIS = 10_000L;

   /** Collections that took longer than this in one tick are reported. */
   static final long GC_PAUSE_MILLIS = 800L;

   /** How many frames of the stalled thread's stack are written. */
   private static final int STACK_FRAMES = 25;

   private final WebServer server;
   private long lastCollectionMillis = totalCollectionMillis();

   StallWatchdog(WebServer server) {
      this.server = server;
   }

   /** Starts watching on a daemon thread, which never keeps the program alive. */
   void start() {
      Thread watchdog = new Thread(this::run, "watchdog");
      watchdog.setDaemon(true);
      watchdog.start();
   }

   private void run() {
      while (true) {
         try {
            Thread.sleep(TICK_MILLIS);
         } catch (InterruptedException stopping) {
            return;
         }
         try {
            checkOnce(System.nanoTime());
         } catch (RuntimeException failure) {
            // Watching must never be what takes the program down.
            Log.warn("the watchdog could not check the requests", failure);
         }
      }
   }

   /** One look, as of {@code nowNanos}. Not private so the checks can be run without waiting. */
   void checkOnce(long nowNanos) {
      for (WebServer.InFlight request : this.server.requestsInFlight()) {
         long runningMillis = (nowNanos - request.startedNanos()) / 1_000_000L;
         if (runningMillis >= STALL_MILLIS && request.markReported()) {
            Log.warn(describe(request, runningMillis), stackOf(request.thread()));
         }
      }
      long collected = totalCollectionMillis();
      long pause = collected - this.lastCollectionMillis;
      this.lastCollectionMillis = collected;
      if (pause >= GC_PAUSE_MILLIS) {
         Runtime runtime = Runtime.getRuntime();
         Log.warn("garbage collection took " + pause + " ms in the last second (heap "
            + (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L) + "/" + runtime.maxMemory() / (1024L * 1024L)
            + " MB), so requests in that second waited");
      }
   }

   /** The line that says which request is stuck and what its thread is doing. */
   static String describe(WebServer.InFlight request, long runningMillis) {
      Thread thread = request.thread();
      return "stalled: request " + request.id() + " " + request.method() + " " + request.target() + " has run for "
         + runningMillis / 1000L + " s and not finished; its thread " + thread.getName() + " is " + thread.getState()
         + (thread.getState() == Thread.State.BLOCKED ? " (waiting for a lock another request holds)" : "")
         + ". Its stack follows";
   }

   /** The thread's current stack, as an exception so the log writes it under the line. */
   private static Throwable stackOf(Thread thread) {
      StackTraceElement[] frames = thread.getStackTrace();
      Throwable trace = new Throwable("stack of " + thread.getName());
      trace.setStackTrace(frames.length > STACK_FRAMES ? java.util.Arrays.copyOf(frames, STACK_FRAMES) : frames);
      return trace;
   }

   private static long totalCollectionMillis() {
      long total = 0L;
      List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
      for (GarbageCollectorMXBean collector : collectors) {
         long time = collector.getCollectionTime();
         if (time > 0L) {
            total += time;
         }
      }
      return total;
   }
}
