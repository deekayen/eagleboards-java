package monfox.log;

import java.util.Hashtable;

public class Logger {
   private static String _applicationName = "unknown";
   private static boolean _apiDefault = true;
   private static Hashtable _apiMap = new Hashtable();
   private static boolean _groupDefault = true;
   private static Hashtable _groupMap = new Hashtable();
   private static NameUtil _nameUtil = new NameUtil();
   private static Logger.Provider _provider = null;
   private static Logger.Provider _defaultProvider = new Logger.DefaultProvider();
   private static Hashtable _providerMap = null;

   public static void enableAll() {
      if (_provider == null) {
         _defaultProvider.enableAll();
      } else {
         _provider.enableAll();
      }
   }

   public static void disableAll() {
      if (_provider == null) {
         _defaultProvider.disableAll();
      } else {
         _provider.disableAll();
      }
   }

   public static void setProvider(Logger.Provider var0) {
      _provider = var0;
   }

   public static void setProvider(String var0, Logger.Provider var1) {
      if (_providerMap == null) {
         _providerMap = new Hashtable();
      }

      _providerMap.put(var0, var1);
   }

   public static Logger.Provider getProvider() {
      return _provider;
   }

   public static Logger.Provider getProvider(String var0) {
      return _providerMap == null ? null : (Logger.Provider)_providerMap.get(var0);
   }

   public static void disableAPIs(String[] var0) {
      _apiDefault = true;
      if (var0 != null) {
         for (int var1 = 0; var1 < var0.length; var1++) {
            _apiMap.put(var0[var1].toUpperCase(), new Boolean(true));
         }
      }
   }

   public static void enableAPIs(String[] var0) {
      _apiDefault = false;
      if (var0 != null) {
         for (int var1 = 0; var1 < var0.length; var1++) {
            _apiMap.put(var0[var1].toUpperCase(), new Boolean(true));
         }
      }
   }

   public static void disableGroups(String[] var0) {
      _groupDefault = true;
      if (var0 != null) {
         for (int var1 = 0; var1 < var0.length; var1++) {
            _groupMap.put(var0[var1].toUpperCase(), new Boolean(false));
         }
      }
   }

   public static void enableGroups(String[] var0) {
      _groupDefault = false;
      if (var0 != null) {
         for (int var1 = 0; var1 < var0.length; var1++) {
            _groupMap.put(var0[var1].toUpperCase(), new Boolean(true));
         }
      }
   }

   public static void setApplicationName(String var0) {
      _applicationName = var0;
      if (var0 != null) {
         _nameUtil.setProperty("appl", getApplicationName());
         _nameUtil.setProperty("applname", getApplicationName());
         _nameUtil.setProperty("appname", getApplicationName());
      }
   }

   public static String getApplicationName() {
      return _applicationName;
   }

   public static void setVariable(String var0, String var1) {
      _nameUtil.setProperty(var0, var1);
   }

   public static String resolveVariables(String var0) {
      return _nameUtil.resolveVars(var0);
   }

   public static Logger getInstance(String var0, String var1, String var2) {
      if (_provider == null && _providerMap == null) {
         return _defaultProvider.getInstance(var2);
      }

      if (var0 != null) {
         Boolean var3 = (Boolean)_apiMap.get(var0.toUpperCase());
         if (var3 == null && !_apiDefault || var3 != null && !var3) {
            return _defaultProvider.getInstance(var2);
         }
      } else if (!_apiDefault) {
         return _defaultProvider.getInstance(var2);
      }

      if (var1 != null) {
         Boolean var5 = (Boolean)_groupMap.get(var1.toUpperCase());
         if (var5 == null && !_groupDefault || var5 != null && !var5) {
            return _defaultProvider.getInstance(var2);
         }
      } else if (!_groupDefault) {
         return _defaultProvider.getInstance(var2);
      }

      Logger.Provider var6 = null;
      if (_providerMap != null && var0 != null) {
         var6 = (Logger.Provider)_providerMap.get(var0);
      }

      if (var6 == null) {
         var6 = _provider;
      }

      if (var6 == null) {
         return _defaultProvider.getInstance(var2);
      }

      Logger var4 = var6.getInstance(var2);
      var4.setApi(var0);
      var4.setGroup(var1);
      return var4;
   }

   public static Logger getInstance(String var0) {
      return getInstance(null, null, var0);
   }

   public String getApi() {
      return null;
   }

   public void setApi(String var1) {
   }

   public String getGroup() {
      return null;
   }

   public void setGroup(String var1) {
   }

   public void init(String var1) {
   }

   public boolean isErrorEnabled() {
      return false;
   }

   public void error(Object var1) {
   }

   public void error(Object var1, Throwable var2) {
   }

   public boolean isWarnEnabled() {
      return false;
   }

   public void warn(Object var1) {
   }

   public void warn(Object var1, Throwable var2) {
   }

   public boolean isInfoEnabled() {
      return false;
   }

   public void info(Object var1) {
   }

   public void info(Object var1, Throwable var2) {
   }

   public boolean isConfigEnabled() {
      return this.isInfoEnabled();
   }

   public void config(Object var1) {
      this.info(var1);
   }

   public void config(Object var1, Throwable var2) {
      this.info(var1, var2);
   }

   public boolean isCommsEnabled() {
      return this.isDebugEnabled();
   }

   public void comms(Object var1) {
      this.debug(var1);
   }

   public void comms(Object var1, Throwable var2) {
      this.debug(var1, var2);
   }

   public boolean isDebugEnabled() {
      return false;
   }

   public void debug(Object var1) {
   }

   public void debug(Object var1, Throwable var2) {
   }

   public boolean isDetailedEnabled() {
      return this.isDebugEnabled();
   }

   public void detailed(Object var1) {
      this.debug(var1);
   }

   public void detailed(Object var1, Throwable var2) {
      this.debug(var1, var2);
   }

   public boolean isEnabled() {
      return false;
   }

   public void setLevel(int var1) {
   }

   public static Logger.Provider startLogging(String var0, int var1) {
      SimpleLogger.Provider var2 = new SimpleLogger.Provider(var0);
      var2.setLevel(var1);
      setProvider(var2);
      return var2;
   }

   static {
      _nameUtil.setProperty("appl", "unknown");
      _nameUtil.setProperty("applname", "unknown");
      _nameUtil.setProperty("appname", "unknown");
   }

   public static class DefaultProvider implements Logger.Provider {
      private boolean _enabled = true;
      private Logger _instance = new Logger();

      @Override
      public Logger getInstance(String var1) {
         return this._instance;
      }

      @Override
      public void enableAll() {
         this._enabled = true;
      }

      @Override
      public void disableAll() {
         this._enabled = false;
      }
   }

   public interface Provider {
      Logger getInstance(String var1);

      void enableAll();

      void disableAll();
   }
}
