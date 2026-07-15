package shkc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import monfox.log.Logger;

public class FileLocator {
   private List _searchPathList = null;
   private String _searchPath = null;
   private NameUtil _nameUtil = new NameUtil();
   private List _resolverList = new Vector();
   private Logger _log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");

   public FileLocator() {
   }

   public FileLocator(String var1) {
      this.setSearchPath(var1);
   }

   public void setVariable(String var1, String var2) {
      this._nameUtil.setVariable(var1, var2);
   }

   public File getFile(String var1) throws IOException {
      return this.getFile(var1, true);
   }

   public File getFile(String var1, boolean var2) throws IOException {
      this._log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");
      if (var1 == null) {
         throw new IOException("null filename");
      }

      if (this._log.isDetailedEnabled()) {
         this._log.detailed("getFile: file=" + var1 + ", must-exist=" + var2);
      }

      var1 = this._nameUtil.resolveVars(var1);
      if (this._log.isDetailedEnabled()) {
         this._log.detailed("  -- vars resolved: " + var1);
      }

      if (!var2) {
         return new File(var1);
      }

      File var3 = null;
      if (this._searchPathList == null || this._searchPathList.size() == 0) {
         this._log.detailed("-- no search path");
         var3 = new File(var1);
      } else if (var1.startsWith("/")) {
         this._log.detailed("-- absolute file path");
         var3 = new File(var1);
      } else if (var1.startsWith("\\")) {
         this._log.detailed("-- absolute DOS file path");
         var3 = new File(var1);
      } else if (var1.length() > 2 && var1.charAt(1) == ':') {
         this._log.detailed("-- absolute DOS drive file path");
         var3 = new File(var1);
      } else if (var1.startsWith("file:")) {
         URL var4 = new URL(var1);
         var3 = new File(var4.getFile());
      } else if (var1.startsWith("http:") || var1.startsWith("ftp:") || var1.startsWith("jar:")) {
         this._log.detailed("-- absolute url path: not valid");
         throw new IOException("location must be a filesystem path not a URL: " + var1);
      }

      if (var3 == null) {
         this._log.detailed("-- relative path, checking search path");

         for (Object var5Obj : this._searchPathList) {
            String var5 = (String)var5Obj;
            String var6 = var5 + var1;
            if (this._log.isDetailedEnabled()) {
               this._log.detailed("   path-elem: " + var5);
            }

            try {
               var3 = new File(var6);
               if (var3.exists()) {
                  return var3;
               }
            } catch (Exception var8) {
            }
         }

         throw new IOException("no such file ' " + var1 + "' in search path '" + this._searchPath + "'");
      } else {
         if (var3.exists()) {
            return var3;
         }

         this._log.error("no such file: " + var1);
         throw new IOException("no such file: " + var1);
      }
   }

   public File getDirectory(String var1) throws IOException {
      return this.getDirectory(var1, true);
   }

   public File getDirectory(String var1, boolean var2) throws IOException {
      this._log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");
      if (this._log.isDetailedEnabled()) {
         this._log.detailed("getDirectory: dirname=" + var1);
      }

      if (var1 == null) {
         throw new IOException("null dirname");
      } else {
         File var3 = this.getFile(var1, var2);
         if (!var2) {
            return var3;
         } else if (var3.isDirectory()) {
            return var3;
         } else {
            throw new IOException("not a directory ' " + var1 + "'");
         }
      }
   }

   public void setSearchPath(String var1) {
      var1 = this._nameUtil.resolveVars(var1);
      this._searchPathList = this.parseSearchPath(var1);
      this._searchPath = var1;
      this._log.debug("setSearchPath: '" + var1 + "'");
   }

   public void addSearchPath(String var1) {
      var1 = this._nameUtil.resolveVars(var1);
      List var2 = this.parseSearchPath(var1);
      if (this._searchPathList != null) {
         this._searchPathList.addAll(var2);
      } else {
         this._searchPathList = var2;
         this._searchPathList.add("." + File.separator);
      }

      this._searchPath = this._searchPath + File.pathSeparator + var1;
      this._log.debug("addSearchPath: adding='" + var1 + "'");
      this._log.debug("addSearchPath: result='" + this._searchPath + "'");
   }

   public List getSearchPathList() {
      return this._searchPathList;
   }

   private InputStream localGetInputStream(String var1) throws IOException {
      this._log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");
      if (this._log.isDetailedEnabled()) {
         this._log.detailed("localGetInputStream: file=" + var1);
      }

      InputStream var2 = null;
      if (var2 == null) {
         try {
            URL var3 = new URL(var1);
            var2 = var3.openStream();
         } catch (IOException var5) {
         }
      }

      if (var2 == null) {
         try {
            var2 = new FileInputStream(var1);
         } catch (IOException var4) {
         }
      }

      if (var2 == null) {
         this._log.debug("getResourceAsStream: " + var1);
         var2 = FileLocator.class.getResourceAsStream(var1);
      }

      if (var2 == null) {
         throw new IOException("invalid location: " + var1);
      } else {
         return var2;
      }
   }

   public InputStream getInputStream(String var1) throws IOException {
      this._log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");
      if (var1 == null) {
         throw new IOException("null filename");
      }

      if (this._log.isDetailedEnabled()) {
         this._log.detailed("getInputStream: file=" + var1);
      }

      var1 = this._nameUtil.resolveVars(var1);
      if (this._resolverList.size() > 0) {
         for (Object var3Obj : this._resolverList) {
            FileLocator.Resolver var3 = (FileLocator.Resolver)var3Obj;
            try {
               InputStream var4 = var3.getInputStream(var1);
               if (var4 != null) {
                  return var4;
               }
            } catch (Exception var7) {
               this._log.error("exception in FileLocator.Resolver.getInputStream(" + var1 + ")", var7);
            }
         }
      }

      if (this._log.isDetailedEnabled()) {
         this._log.detailed("  -- vars resolved: " + var1);
      }

      if (this._searchPathList == null || this._searchPathList.size() == 0) {
         this._log.detailed("-- no search path");
         return this.localGetInputStream(var1);
      }

      if (var1.startsWith("/")) {
         this._log.detailed("-- absolute file path");
         return this.localGetInputStream(var1);
      }

      if (var1.startsWith("\\")) {
         this._log.detailed("-- absolute DOS file path");
         return this.localGetInputStream(var1);
      }

      if (var1.length() > 2 && var1.charAt(1) == ':') {
         this._log.detailed("-- absolute DOS drive file path");
         return this.localGetInputStream(var1);
      }

      if (!var1.startsWith("http:") && !var1.startsWith("ftp:") && !var1.startsWith("file:") && !var1.startsWith("jar:") && !var1.startsWith("https:")) {
         this._log.detailed("-- relative path, checking search path");

         for (Object var10Obj : this._searchPathList) {
            String var10 = (String)var10Obj;
            String var11 = var10 + var1;
            if (this._log.isDetailedEnabled()) {
               this._log.detailed("   path-elem: " + var10);
            }

            try {
               InputStream var5 = this.localGetInputStream(var11);
               if (var5 != null) {
                  return var5;
               }
            } catch (Exception var6) {
            }
         }

         throw new IOException("no such resource ' " + var1 + "' in search path '" + this._searchPath + "'");
      } else {
         this._log.detailed("-- absolute url path");
         return this.localGetInputStream(var1);
      }
   }

   private List parseSearchPath(String var1) {
      this._log = Logger.getInstance("DOSI", "DOSI-UTIL", "FileLocator");
      if (var1 == null) {
         return null;
      }

      Vector var2 = new Vector();
      StringTokenizer var3 = new StringTokenizer(var1, ";," + File.pathSeparator, false);

      while (var3.hasMoreTokens()) {
         String var4 = var3.nextToken().trim();
         if (var4.length() == 0) {
            var4 = ".";
         }

         if (var4.length() != 0 && !var4.endsWith("/") && !var4.endsWith(File.separator)) {
            if (!var4.startsWith("/") && (var4.indexOf(47) < 0 || var4.indexOf(92) >= 0)) {
               var4 = var4 + File.separator;
            } else {
               var4 = var4 + '/';
            }
         }

         var2.add(var4);
      }

      return var2;
   }

   public void addResolver(FileLocator.Resolver var1) {
      this._resolverList.add(var1);
   }

   public void removeResolver(FileLocator.Resolver var1) {
      this._resolverList.remove(var1);
   }

   public interface Resolver {
      InputStream getInputStream(String var1);
   }
}
