package shkc.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a named resource, wherever it lives: a URL, a file on disk, or a file
 * packaged inside the jar (the classpath).
 *
 * <p>A relative name is tried against each folder of a search path in turn, such
 * as {@code "/shkc/core/;WEBROOT;/;."}, and the first folder that has it wins.
 * Names may contain {@code ${...}} variables, which {@link NameUtil} expands.
 */
public class FileLocator {
   private static final Logger LOG = Logger.getLogger(FileLocator.class.getName());

   /** Search-path folders may be separated by a semicolon, a comma or the platform's path separator. */
   private static final String SEARCH_PATH_SEPARATORS = ";," + File.pathSeparator;

   private final NameUtil nameUtil = new NameUtil();

   /** The search path with its variables expanded, kept for error messages. */
   private String searchPath = null;

   /** Each folder of the search path, ending in a separator so a file name can be appended. */
   private List<String> searchPathFolders = null;

   /** @param searchPath folders to look in for a relative name, separated by {@code ;} or {@code ,} */
   public FileLocator(String searchPath) {
      this.setSearchPath(searchPath);
   }

   /** Replaces the folders that are searched for a relative name. */
   public void setSearchPath(String searchPath) {
      searchPath = this.nameUtil.resolveVars(searchPath);
      this.searchPathFolders = this.parseSearchPath(searchPath);
      this.searchPath = searchPath;
      LOG.fine("setSearchPath: '" + searchPath + "'");
   }

   /**
    * Opens {@code location} exactly as written, trying it as a URL, then as a
    * file, then as a classpath resource.
    */
   private InputStream localGetInputStream(String location) throws IOException {
      if (LOG.isLoggable(Level.FINER)) {
         LOG.finer("localGetInputStream: file=" + location);
      }

      InputStream in = openAsUrl(location);

      if (in == null) {
         try {
            in = new FileInputStream(location);
         } catch (IOException notAFile) {
            // Not a file either: fall through to the classpath.
         }
      }

      if (in == null) {
         LOG.fine("getResourceAsStream: " + location);
         in = FileLocator.class.getResourceAsStream(location);
      }

      if (in == null) {
         throw new IOException("invalid location: " + location);
      }
      return in;
   }

   /** Opens {@code location} if it is a complete URL, or returns null when it is not one (or cannot be read). */
   // new URL(String) is deprecated, but URI.create(...).toURL() throws an unchecked
   // error for names with spaces or backslashes, and here those must mean only "not a URL".
   @SuppressWarnings("deprecation")
   private static InputStream openAsUrl(String location) {
      try {
         return new URL(location).openStream();
      } catch (IOException notAUrl) {
         return null;
      }
   }

   /**
    * Opens a resource by name.
    *
    * @param location a URL, an absolute file path, or a name relative to the search path
    * @return a stream the caller must close
    * @throws IOException if the name is null or nothing is found
    */
   public InputStream getInputStream(String location) throws IOException {
      if (location == null) {
         throw new IOException("null filename");
      }

      if (LOG.isLoggable(Level.FINER)) {
         LOG.finer("getInputStream: file=" + location);
      }

      location = this.nameUtil.resolveVars(location);
      if (LOG.isLoggable(Level.FINER)) {
         LOG.finer("  -- vars resolved: " + location);
      }

      if (this.searchPathFolders.isEmpty()) {
         LOG.finer("-- no search path");
         return this.localGetInputStream(location);
      }

      String absoluteKind = describeAbsoluteLocation(location);
      if (absoluteKind != null) {
         LOG.finer("-- " + absoluteKind);
         return this.localGetInputStream(location);
      }

      LOG.finer("-- relative path, checking search path");

      for (String folder : this.searchPathFolders) {
         String candidate = folder + location;
         if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("   path-elem: " + folder);
         }

         try {
            return this.localGetInputStream(candidate);
         } catch (Exception unreadable) {
            // Not in this folder: try the next one.
         }
      }

      throw new IOException("no such resource ' " + location + "' in search path '" + this.searchPath + "'");
   }

   /**
    * Says what kind of absolute name {@code location} is (for the debug log),
    * or returns null when it is relative and so belongs on the search path.
    */
   private static String describeAbsoluteLocation(String location) {
      if (location.startsWith("/")) {
         return "absolute file path";
      }
      if (location.startsWith("\\")) {
         return "absolute DOS file path";
      }
      if (location.length() > 2 && location.charAt(1) == ':') {
         return "absolute DOS drive file path";
      }
      if (location.startsWith("http:") || location.startsWith("ftp:") || location.startsWith("file:")
            || location.startsWith("jar:") || location.startsWith("https:")) {
         return "absolute url path";
      }
      return null;
   }

   /** Splits a search path into folders, each ending in a separator. */
   private List<String> parseSearchPath(String searchPath) {
      List<String> folders = new ArrayList<>();
      StringTokenizer tokens = new StringTokenizer(searchPath, SEARCH_PATH_SEPARATORS, false);

      while (tokens.hasMoreTokens()) {
         String folder = tokens.nextToken().trim();
         if (folder.length() == 0) {
            folder = ".";
         }

         // The file name is appended directly, so the folder must end in a
         // separator. A folder written with forward slashes (a classpath
         // folder, or an absolute one) gets '/'; a plain relative or
         // backslash-style folder gets the platform's separator.
         if (!folder.endsWith("/") && !folder.endsWith(File.separator)) {
            boolean usesForwardSlashes = folder.startsWith("/") || (folder.indexOf('/') >= 0 && folder.indexOf('\\') < 0);
            folder = folder + (usesForwardSlashes ? "/" : File.separator);
         }

         folders.add(folder);
      }

      return folders;
   }
}
