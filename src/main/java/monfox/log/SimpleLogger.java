package monfox.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

public class SimpleLogger extends Logger {
   static String HBAR = "-----------------------------------------------------------------------------";
   public static final int LEVEL_OFF = 8;
   public static final int LEVEL_ERROR = 7;
   public static final int LEVEL_WARN = 6;
   public static final int LEVEL_INFO = 5;
   public static final int LEVEL_CONFIG = 4;
   public static final int LEVEL_COMMS = 3;
   public static final int LEVEL_DEBUG = 2;
   public static final int LEVEL_DETAILED = 1;
   public static final int LEVEL_ALL = 0;
   public static final int LEVEL_UNSPECIFIED = -1;
   private int _level = -1;
   private String _api = null;
   private String _group = null;
   private String _category = null;
   private SimpleLogger.Provider _provider;

   SimpleLogger(String var1, SimpleLogger.Provider var2) {
      this._category = var1;
      this._provider = var2;
      this._level = -1;
   }

   @Override
   public void setLevel(int var1) {
      this._level = var1;
   }

   public void setLevel(SimpleLogger.LEVEL var1) {
      this._level = var1.ordinal();
   }

   public int getLevel() {
      return this._level;
   }

   @Override
   public boolean isErrorEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isErrorEnabled() : this._provider.isActive() && this._level <= 7;
   }

   @Override
   public boolean isWarnEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isWarnEnabled() : this._provider.isActive() && this._level <= 6;
   }

   @Override
   public boolean isInfoEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isInfoEnabled() : this._provider.isActive() && this._level <= 5;
   }

   @Override
   public boolean isConfigEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isConfigEnabled() : this._provider.isActive() && this._level <= 4;
   }

   @Override
   public boolean isCommsEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isCommsEnabled() : this._provider.isActive() && this._level <= 3;
   }

   @Override
   public boolean isDebugEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isDebugEnabled() : this._provider.isActive() && this._level <= 2;
   }

   @Override
   public boolean isDetailedEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isDetailedEnabled() : this._provider.isActive() && this._level <= 1;
   }

   @Override
   public boolean isEnabled() {
      return this._level == -1 ? this._provider.isActive() && this._provider.isEnabled() : this._provider.isActive() && this._level != 8;
   }

   @Override
   public void info(Object var1) {
      if (this.isInfoEnabled()) {
         this._provider.println(this.genString("INFO ", var1, null));
      }
   }

   @Override
   public void info(Object var1, Throwable var2) {
      if (this.isInfoEnabled()) {
         this._provider.println(this.genString("INFO ", var1, var2));
      }
   }

   @Override
   public void debug(Object var1) {
      if (this.isDebugEnabled()) {
         this._provider.println(this.genString("DEBUG", var1, null));
      }
   }

   @Override
   public void debug(Object var1, Throwable var2) {
      if (this.isDebugEnabled()) {
         this._provider.println(this.genString("DEBUG", var1, var2));
      }
   }

   @Override
   public void warn(Object var1) {
      if (this.isWarnEnabled()) {
         this._provider.println(this.genString("WARN ", var1, null));
      }
   }

   @Override
   public void warn(Object var1, Throwable var2) {
      if (this.isWarnEnabled()) {
         this._provider.println(this.genString("WARN ", var1, var2));
      }
   }

   @Override
   public void error(Object var1) {
      if (this.isErrorEnabled()) {
         this._provider.println(this.genString("ERROR", var1, null));
      }
   }

   @Override
   public void error(Object var1, Throwable var2) {
      if (this.isErrorEnabled()) {
         this._provider.println(this.genString("ERROR", var1, var2));
      }
   }

   @Override
   public void config(Object var1) {
      if (this.isConfigEnabled()) {
         this._provider.println(this.genString("CONFIG", var1, null));
      }
   }

   @Override
   public void config(Object var1, Throwable var2) {
      if (this.isConfigEnabled()) {
         this._provider.println(this.genString("CONFIG", var1, var2));
      }
   }

   @Override
   public void comms(Object var1) {
      if (this.isCommsEnabled()) {
         this._provider.println(this.genString("COMMS", var1, null));
      }
   }

   @Override
   public void comms(Object var1, Throwable var2) {
      if (this.isCommsEnabled()) {
         this._provider.println(this.genString("COMMS", var1, var2));
      }
   }

   @Override
   public void detailed(Object var1) {
      if (this.isDetailedEnabled()) {
         this._provider.println(this.genString("DETAILED", var1, null));
      }
   }

   @Override
   public void detailed(Object var1, Throwable var2) {
      if (this.isDetailedEnabled()) {
         this._provider.println(this.genString("DETAILED", var1, var2));
      }
   }

   @Override
   public void setGroup(String var1) {
      this._group = var1;
   }

   @Override
   public void setApi(String var1) {
      this._api = var1;
   }

   @Override
   public String getGroup() {
      return this._group;
   }

   @Override
   public String getApi() {
      return this._api;
   }

   private String genString(String var1, Object var2, Throwable var3) {
      StringBuffer var4 = new StringBuffer();
      if (this.getApi() != null && this._provider.isApiLogged()) {
         var4.append("[").append(this.getApi()).append("]");
      }

      if (this.getGroup() != null && this._provider.isGroupLogged()) {
         var4.append("[").append(this.getGroup()).append("]");
      }

      if (this._provider.isCategoryLogged()) {
         var4.append("[").append(this._category).append("]");
      }

      if (this._provider.isTypeLogged()) {
         var4.append("[").append(var1).append("]");
      }

      if (this._provider.isDateLogged()) {
         if (this._provider.getDateLoggingFormat() != null) {
            var4.append("[").append(this._provider.getDateLoggingFormat().format(new Date())).append("]");
         } else {
            var4.append("[").append(new Date()).append("]");
         }
      }

      if (this._provider.isAppTimeLogged()) {
         var4.append("[").append(this._provider.getAppTime()).append("]");
      }

      var4.append(": ");
      if (var2 == null) {
         var4.append("null");
      } else {
         var4.append(var2.toString());
      }

      if (var3 != null) {
         var4.append(" (EXCEPTION:").append(var3).append(")");
         StringWriter var5 = new StringWriter();
         PrintWriter var6 = new PrintWriter(var5);
         var3.printStackTrace(var6);
         var4.append(var5.getBuffer());
      }

      return var4.toString();
   }

   public enum LEVEL {
      ALL,
      DETAILED,
      DEBUG,
      COMMS,
      CONFIG,
      INFO,
      WARN,
      ERROR,
      OFF;
   }

   public enum OPTION {
      LOG_API,
      LOG_CATEGORY,
      LOG_GROUP,
      LOG_MEMORY,
      LOG_TYPE,
      LOG_APPTIME,
      ROLLOVER_1M,
      ROLLOVER_10M,
      ROLLOVER_100M,
      AUTO_RECREATE,
      DEFAULT;
   }

   public static final class Provider implements Logger.Provider {
      private static final String SPACES = "                                                                     ";
      private Socket _opensock = null;
      private SimpleLogger.Provider.LogServer _logServer = null;
      private Thread _memThread;
      private boolean _logMemory = false;
      private Object lock = new Object();
      private PrintStream outstream = System.err;
      private boolean _isAutoRecreate = false;
      private String _filename = null;
      private long _fileSize = 0L;
      private long _maxFileSize = -1L;
      private boolean _isCategoryLogged = true;
      private boolean _isApiLogged = true;
      private boolean _isGroupLogged = true;
      private boolean _isTypeLogged = true;
      private boolean _isDateLogged = false;
      private boolean _isAppTimeLogged = false;
      private DateFormat _dateLoggingFormat = null;
      private Hashtable _logMap = new Hashtable();
      private int _level = 2;
      private NameUtil _nameUtil = new NameUtil();
      private long _appStartTime = System.currentTimeMillis();

      public Provider(String var1) {
         this(var1, SimpleLogger.LEVEL.DEBUG);
      }

      public Provider(String var1, SimpleLogger.LEVEL var2, SimpleLogger.OPTION... var3) {
         this();
         if (var1 == null || var1.trim().equals("")) {
            this.outstream = null;
         } else if ("System.out".equals(var1)) {
            this.outstream = System.out;
         } else if ("System.err".equals(var1)) {
            this.outstream = System.err;
         } else {
            String var4 = Logger.resolveVariables(var1);

            try {
               FileOutputStream var5 = new FileOutputStream(var4);
               this.outstream = new PrintStream(var5);
               this._filename = var4;
               this.println(SimpleLogger.HBAR);
               this.println("-- [" + Logger.getApplicationName() + "]: " + new Date());

               try {
                  this.println("-- [Java System Properties] -----------------------------------------------");
                  Properties var6 = System.getProperties();

                  for (String var8 : var6.stringPropertyNames()) {
                     String var9 = var6.getProperty(var8);
                     int var10 = 30 - var8.length();
                     if (var10 < 0) {
                        var10 = 0;
                     }

                     this.println("   " + var8 + "                                                                     ".substring(0, var10) + " : " + var9);
                  }

                  this.println("---------------------------------------------------------------------------");
               } catch (Throwable var11) {
                  this.println(var11.toString());
               }

               this.println(SimpleLogger.HBAR);
            } catch (IOException var12) {
               System.err.println("\n\n  logger error: cannot open outfile: " + var1);
            }

            this.setLevel(var2.ordinal());
            if (var3 != null && var3.length > 0) {
               this.setOptions(var3);
            } else {
               this.setOptions(SimpleLogger.OPTION.DEFAULT);
            }
         }
      }

      public Provider(String var1, int var2) {
         this(var1);
         if (var2 > 0) {
            try {
               this.startListening(var2);
            } catch (Exception var4) {
               System.err.println("logger error: cannot open socket");
            }
         }
      }

      public Provider(PrintStream var1) {
         this();
         this.outstream = var1;
      }

      private Provider() {
         this._nameUtil.setProperty("appl", Logger.getApplicationName());
         this._nameUtil.setProperty("applname", Logger.getApplicationName());
         this._nameUtil.setProperty("appname", Logger.getApplicationName());
      }

      @Override
      public void enableAll() {
         this._level = 0;
      }

      @Override
      public void disableAll() {
         this._level = 8;
      }

      public void setLevel(int var1) {
         this._level = var1;
      }

      public void setLevel(SimpleLogger.LEVEL var1) {
         this._level = var1.ordinal();
      }

      public boolean isErrorEnabled() {
         return this._level <= 7;
      }

      public boolean isWarnEnabled() {
         return this._level <= 6;
      }

      public boolean isInfoEnabled() {
         return this._level <= 5;
      }

      public boolean isConfigEnabled() {
         return this._level <= 4;
      }

      public boolean isCommsEnabled() {
         return this._level <= 3;
      }

      public boolean isDebugEnabled() {
         return this._level <= 2;
      }

      public boolean isDetailedEnabled() {
         return this._level <= 1;
      }

      public boolean isEnabled() {
         return this._level != 8;
      }

      public void setCategoryLogged(boolean var1) {
         this._isCategoryLogged = var1;
      }

      public void setApiLogged(boolean var1) {
         this._isApiLogged = var1;
      }

      public void setGroupLogged(boolean var1) {
         this._isGroupLogged = var1;
      }

      public void setTypeLogged(boolean var1) {
         this._isTypeLogged = var1;
      }

      public void setDateLogged(boolean var1) {
         this._isDateLogged = var1;
      }

      public void setDateLoggingFormat(DateFormat var1) {
         this._dateLoggingFormat = var1;
      }

      public void setAppTimeLogged(boolean var1) {
         this._isAppTimeLogged = var1;
      }

      public void setAutoRecreate(boolean var1) {
         this._isAutoRecreate = var1;
      }

      public boolean isAutoRecreate() {
         return this._isAutoRecreate;
      }

      public boolean isCategoryLogged() {
         return this._isCategoryLogged;
      }

      public boolean isApiLogged() {
         return this._isApiLogged;
      }

      public boolean isGroupLogged() {
         return this._isGroupLogged;
      }

      public boolean isTypeLogged() {
         return this._isTypeLogged;
      }

      public boolean isDateLogged() {
         return this._isDateLogged;
      }

      public DateFormat getDateLoggingFormat() {
         return this._dateLoggingFormat;
      }

      public boolean isAppTimeLogged() {
         return this._isAppTimeLogged;
      }

      @Override
      public synchronized Logger getInstance(String var1) {
         Logger var2 = (Logger)this._logMap.get(var1);
         if (var2 == null) {
            var2 = new SimpleLogger(var1, this);
            this._logMap.put(var1, var2);
         }

         return var2;
      }

      public double getAppTime() {
         long var1 = System.currentTimeMillis();
         long var3 = var1 - this._appStartTime;
         return var3 / 1000.0;
      }

      public int getLevel() {
         return this._level;
      }

      public void setRolloverSize(long var1) {
         if (var1 < 1000L) {
            var1 = 1000L;
         }

         this._maxFileSize = var1;
      }

      public long getRolloverSize() {
         return this._maxFileSize;
      }

      public void startListening(int var1) throws IOException {
         SimpleLogger.Provider.LogServer var2 = this._logServer;
         if (var2 != null) {
            var2.shutdown();
         }

         this._logServer = new SimpleLogger.Provider.LogServer(var1);
      }

      public void stopListening() {
         SimpleLogger.Provider.LogServer var1 = this._logServer;
         if (var1 != null) {
            var1.shutdown();
            this._logServer = null;
         }
      }

      private void printSock(String var1, boolean var2) {
         Socket var3 = this._opensock;
         if (var3 != null) {
            synchronized (var3) {
               try {
                  var3.getOutputStream().write(var1.getBytes());
                  if (var2) {
                     var3.getOutputStream().write("\n".getBytes());
                  }
               } catch (Exception var9) {
                  try {
                     var3.close();
                  } catch (Exception var8) {
                  }

                  this._opensock = null;
               }
            }
         }
      }

      public void print(String var1) {
         if (this.outstream != null) {
            synchronized (this.lock) {
               this.checkRollover(var1);
               this.outstream.print(var1);
            }
         }

         this.printSock(var1, false);
      }

      public void println(String var1) {
         if (this.outstream != null) {
            synchronized (this.lock) {
               this.checkRollover(var1);
               this.outstream.println(var1);
            }
         }

         this.printSock(var1, true);
      }

      private synchronized void checkRollover(String var1) {
         if ((this._maxFileSize > 0L || this._isAutoRecreate) && this._filename != null) {
            if (this._isAutoRecreate) {
               File var9 = new File(this._filename);
               if (!var9.exists()) {
                  try {
                     FileOutputStream var11 = new FileOutputStream(this._filename);
                     this.outstream = new PrintStream(var11);
                     this._fileSize = 0L;
                     StringBuffer var4 = new StringBuffer();
                     var4.append(SimpleLogger.HBAR);
                     var4.append("\n-- [").append(Logger.getApplicationName()).append("]: ").append(new Date()).append("\n");
                     var4.append(SimpleLogger.HBAR);
                     this.outstream.println(var4.toString());
                     this._fileSize = var4.toString().length();
                  } catch (Exception var5) {
                     System.err.println("logging error: cannot create log file '" + this._filename + "'. (ex: " + var5 + ")");
                  }
               }
            } else {
               this._fileSize = this._fileSize + var1.length();
               if (this._fileSize > this._maxFileSize) {
                  try {
                     this.outstream.close();
                     File var2 = new File(this._filename);
                     File var3 = new File(this._filename + ".last");
                     if (var3.exists()) {
                        var3.delete();
                     }

                     var2.renameTo(var3);
                  } catch (Exception var7) {
                     System.err.println("logging error: cannot create rollover file '" + this._filename + ".last'. (ex: " + var7 + ")");
                  }

                  try {
                     FileOutputStream var8 = new FileOutputStream(this._filename);
                     this.outstream = new PrintStream(var8);
                     this._fileSize = 0L;
                     StringBuffer var10 = new StringBuffer();
                     var10.append(SimpleLogger.HBAR);
                     var10.append("\n-- [").append(Logger.getApplicationName()).append("]: ").append(new Date()).append("\n");
                     var10.append(SimpleLogger.HBAR);
                     this.outstream.println(var10.toString());
                     this._fileSize = var10.toString().length();
                  } catch (Exception var6) {
                     System.err.println("logging error: cannot create log file '" + this._filename + "'. (ex: " + var6 + ")");
                  }
               }
            }
         }
      }

      public boolean isActive() {
         return this.outstream != null || this._opensock != null;
      }

      public void startLoggingMemory(final int var1) {
         Thread var2 = this._memThread;
         if (var2 != null) {
            var2.interrupt();
         }

         this._logMemory = true;
         var2 = new Thread() {
            @Override
            public void run() {
               Runtime var1x = Runtime.getRuntime();

               try {
                  long var2x = 0L;

                  while (Provider.this._logMemory) {
                     var1x.gc();
                     long var4 = var1x.totalMemory();
                     long var6 = var1x.freeMemory();
                     long var8 = var4 - var6;
                     if (var8 > var2x) {
                        var2x = var8;
                     }

                     Provider.this.println(
                        "[MEMORY]: " + var4 / 1024L + "K total, " + var8 / 1024L + "K used, " + var6 / 1024L + "K free, " + var2x / 1024L + " K peak"
                     );
                     Thread.sleep(var1);
                  }
               } catch (Exception var10) {
               }
            }
         };
         var2.setDaemon(true);
         var2.start();
      }

      public void stopLoggingMemory() {
         Thread var1 = this._memThread;
         this._logMemory = false;
         if (var1 != null) {
            var1.interrupt();
         }
      }

      public void setOptions(SimpleLogger.OPTION... var1) {
         for (SimpleLogger.OPTION var5 : var1) {
            switch (var5) {
               case LOG_API:
                  this.setApiLogged(true);
                  break;
               case LOG_CATEGORY:
                  this.setCategoryLogged(true);
                  break;
               case LOG_GROUP:
                  this.setGroupLogged(true);
                  break;
               case LOG_MEMORY:
                  this.startLoggingMemory(10000);
                  break;
               case LOG_APPTIME:
                  this.setAppTimeLogged(true);
                  break;
               case LOG_TYPE:
                  this.setTypeLogged(true);
                  break;
               case ROLLOVER_1M:
                  this.setRolloverSize(1000000L);
                  break;
               case ROLLOVER_10M:
                  this.setRolloverSize(10000000L);
                  break;
               case ROLLOVER_100M:
                  this.setRolloverSize(100000000L);
                  break;
               case AUTO_RECREATE:
                  this.setAutoRecreate(true);
                  break;
               case DEFAULT:
                  this.setApiLogged(true);
                  this.setGroupLogged(true);
                  this.setAppTimeLogged(true);
                  this.setTypeLogged(true);
                  this.setCategoryLogged(true);
                  this.setRolloverSize(100000000L);
            }
         }
      }

      class LogServer {
         private Thread _thread = null;
         private ServerSocket _server = null;
         private boolean _isActive = true;

         LogServer(int var2) throws IOException {
            this._server = new ServerSocket(var2);
            this._thread = new Thread() {
               @Override
               public void run() {
                  try {
                     while (LogServer.this._isActive) {
                        Socket var1x = LogServer.this._server.accept();
                        Provider.this.printSock("\n\n   LOGGER: attempted connect from: " + var1x + "\n\n", false);
                        Socket var2x = Provider.this._opensock;
                        if (var2x != null) {
                           try {
                              var1x.getOutputStream().write("\n\n   TOO MANY CONNECTIONS \n\n".getBytes());
                              var1x.close();
                           } catch (Exception var4) {
                           }
                        } else {
                           Provider.this._opensock = var1x;
                           SimpleLogger.Provider.SocketReader var3 = Provider.this.new SocketReader(var1x);
                           var3.setDaemon(true);
                           var3.start();
                        }
                     }
                  } catch (Exception var5) {
                  }
               }
            };
            this._thread.setDaemon(true);
            this._thread.start();
         }

         public void shutdown() {
            Socket var1 = Provider.this._opensock;
            if (var1 != null) {
               try {
                  var1.close();
               } catch (Exception var4) {
               }
            }

            Provider.this._opensock = null;
            this._isActive = false;
            this._thread.interrupt();

            try {
               this._server.close();
            } catch (Exception var3) {
            }
         }
      }

      class SocketReader extends Thread {
         Socket _s;

         SocketReader(Socket var2) {
            this._s = var2;
         }

         @Override
         public void run() {
            try {
               StringBuffer var1 = new StringBuffer();

               while (true) {
                  int var2 = this._s.getInputStream().read();
                  if (var2 < 0) {
                     throw new IOException("stream closed");
                  }

                  if (var2 >= 32 && var2 <= 126) {
                     var1.append((char)var2);
                     if (var1.length() > 50) {
                        var1 = new StringBuffer();
                     }
                  } else {
                     StringTokenizer var3 = new StringTokenizer(var1.toString(), " =:,\n\t\r", false);
                     int var4 = var3.countTokens();
                     if (var4 > 0) {
                        String var5 = var3.nextToken();
                        if (var5.equalsIgnoreCase("detailed") || var5.equalsIgnoreCase("1")) {
                           Provider.this.println("LOGGER: changing level => detailed");
                           Provider.this.setLevel(1);
                        } else if (var5.equalsIgnoreCase("debug") || var5.equalsIgnoreCase("2")) {
                           Provider.this.println("LOGGER: changing level => debug");
                           Provider.this.setLevel(2);
                        } else if (var5.equalsIgnoreCase("comms") || var5.equalsIgnoreCase("3")) {
                           Provider.this.println("LOGGER: changing level => comms");
                           Provider.this.setLevel(3);
                        } else if (var5.equalsIgnoreCase("config") || var5.equalsIgnoreCase("4")) {
                           Provider.this.println("LOGGER: changing level => config");
                           Provider.this.setLevel(4);
                        } else if (var5.equalsIgnoreCase("info") || var5.equalsIgnoreCase("5")) {
                           Provider.this.println("LOGGER: changing level => info");
                           Provider.this.setLevel(5);
                        } else if (var5.equalsIgnoreCase("warn") || var5.equalsIgnoreCase("6")) {
                           Provider.this.println("LOGGER: changing level => warn");
                           Provider.this.setLevel(6);
                        } else if (var5.equalsIgnoreCase("error") || var5.equalsIgnoreCase("7")) {
                           Provider.this.println("LOGGER: changing level => error");
                           Provider.this.setLevel(7);
                        } else if (var5.equalsIgnoreCase("all") || var5.equalsIgnoreCase("0")) {
                           Provider.this.println("LOGGER: changing level => all");
                           Provider.this.setLevel(0);
                        } else if (var5.equalsIgnoreCase("off") || var5.equalsIgnoreCase("10")) {
                           Provider.this.println("LOGGER: changing level => off");
                           Provider.this.setLevel(8);
                        } else if (var5.equalsIgnoreCase("apptime")) {
                           Provider.this.println("LOGGER: accepted apptime");
                           Provider.this.setAppTimeLogged(true);
                        } else if (var5.equalsIgnoreCase("noapptime")) {
                           Provider.this.println("LOGGER: accepted noapptime");
                           Provider.this.setAppTimeLogged(false);
                        } else if (var5.equalsIgnoreCase("date")) {
                           Provider.this.println("LOGGER: accepted date");
                           Provider.this.setDateLogged(true);
                        } else if (var5.equalsIgnoreCase("nodate")) {
                           Provider.this.println("LOGGER: accepted nodate");
                           Provider.this.setDateLogged(false);
                        } else if (var5.equalsIgnoreCase("openlog")) {
                           if (var4 > 1) {
                              String var6 = var3.nextToken();

                              try {
                                 var6 = Provider.this._nameUtil.resolveVars(var6);
                                 File var7 = new File(var6);
                                 if (!var7.exists()) {
                                    FileOutputStream var8 = new FileOutputStream(var6);
                                    PrintStream var9 = new PrintStream(var8);
                                    if (Provider.this.outstream != null && Provider.this.outstream != System.out && Provider.this.outstream != System.err) {
                                       Provider.this.outstream.close();
                                    }

                                    Provider.this.outstream = var9;
                                 } else {
                                    Provider.this.println("LOGGER: error in newfile:  file exists");
                                 }
                              } catch (IOException var10) {
                                 Provider.this.println("LOGGER: newfile error: " + var10);
                              }
                           }
                        } else if (var5.equalsIgnoreCase("closelog")) {
                           if (Provider.this.outstream != null && Provider.this.outstream != System.out && Provider.this.outstream != System.err) {
                              Provider.this.outstream.close();
                           }

                           Provider.this.outstream = null;
                        }
                     }

                     var1 = new StringBuffer();
                  }
               }
            } catch (Exception var11) {
            }
         }
      }
   }
}
