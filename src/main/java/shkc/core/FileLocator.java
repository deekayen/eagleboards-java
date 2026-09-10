package shkc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileLocator {
   private List _searchPathList = null;
   private String _searchPath = null;
   private NameUtil _nameUtil = new NameUtil();
   private Logger _log = Logger.getLogger(FileLocator.class.getName());

   public FileLocator(String searchPath) {
      this.setSearchPath(searchPath);
   }

   public void setSearchPath(String searchPath) {
      searchPath = this._nameUtil.resolveVars(searchPath);
      this._searchPathList = this.parseSearchPath(searchPath);
      this._searchPath = searchPath;
      this._log.fine("setSearchPath: '" + searchPath + "'");
   }

   private InputStream localGetInputStream(String location) throws IOException {
      this._log = Logger.getLogger(FileLocator.class.getName());
      if (this._log.isLoggable(Level.FINER)) {
         this._log.finer("localGetInputStream: file=" + location);
      }

      InputStream in = null;
      if (in == null) {
         try {
            URL url = new URL(location);
            in = url.openStream();
         } catch (IOException notAUrl) {
         }
      }

      if (in == null) {
         try {
            in = new FileInputStream(location);
         } catch (IOException notAFile) {
         }
      }

      if (in == null) {
         this._log.fine("getResourceAsStream: " + location);
         in = FileLocator.class.getResourceAsStream(location);
      }

      if (in == null) {
         throw new IOException("invalid location: " + location);
      } else {
         return in;
      }
   }

   public InputStream getInputStream(String location) throws IOException {
      this._log = Logger.getLogger(FileLocator.class.getName());
      if (location == null) {
         throw new IOException("null filename");
      }

      if (this._log.isLoggable(Level.FINER)) {
         this._log.finer("getInputStream: file=" + location);
      }

      location = this._nameUtil.resolveVars(location);
      if (this._log.isLoggable(Level.FINER)) {
         this._log.finer("  -- vars resolved: " + location);
      }

      if (this._searchPathList == null || this._searchPathList.size() == 0) {
         this._log.finer("-- no search path");
         return this.localGetInputStream(location);
      }

      if (location.startsWith("/")) {
         this._log.finer("-- absolute file path");
         return this.localGetInputStream(location);
      }

      if (location.startsWith("\\")) {
         this._log.finer("-- absolute DOS file path");
         return this.localGetInputStream(location);
      }

      if (location.length() > 2 && location.charAt(1) == ':') {
         this._log.finer("-- absolute DOS drive file path");
         return this.localGetInputStream(location);
      }

      if (!location.startsWith("http:") && !location.startsWith("ftp:") && !location.startsWith("file:") && !location.startsWith("jar:") && !location.startsWith("https:")) {
         this._log.finer("-- relative path, checking search path");

         for (Object pathElemObj : this._searchPathList) {
            String pathElem = (String)pathElemObj;
            String candidate = pathElem + location;
            if (this._log.isLoggable(Level.FINER)) {
               this._log.finer("   path-elem: " + pathElem);
            }

            try {
               InputStream in = this.localGetInputStream(candidate);
               if (in != null) {
                  return in;
               }
            } catch (Exception unreadable) {
            }
         }

         throw new IOException("no such resource ' " + location + "' in search path '" + this._searchPath + "'");
      } else {
         this._log.finer("-- absolute url path");
         return this.localGetInputStream(location);
      }
   }

   private List parseSearchPath(String searchPath) {
      this._log = Logger.getLogger(FileLocator.class.getName());
      if (searchPath == null) {
         return null;
      }

      Vector pathElems = new Vector();
      StringTokenizer tokens = new StringTokenizer(searchPath, ";," + File.pathSeparator, false);

      while (tokens.hasMoreTokens()) {
         String pathElem = tokens.nextToken().trim();
         if (pathElem.length() == 0) {
            pathElem = ".";
         }

         if (pathElem.length() != 0 && !pathElem.endsWith("/") && !pathElem.endsWith(File.separator)) {
            if (!pathElem.startsWith("/") && (pathElem.indexOf(47) < 0 || pathElem.indexOf(92) >= 0)) {
               pathElem = pathElem + File.separator;
            } else {
               pathElem = pathElem + '/';
            }
         }

         pathElems.add(pathElem);
      }

      return pathElems;
   }

}
