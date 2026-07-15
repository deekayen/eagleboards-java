package monfox.log;

public class Version {
   private static String _version = "$Id: Version.java,v 1.1 2015/02/03 17:33:03 sking Exp $";

   public static void main(String[] var0) {
      System.out.println(new Version().toString());
   }

   public static String getBuildDate() {
      return "04-14-2013 20:39:04";
   }

   public static String getBuildRelease() {
      return "3_0_2j5";
   }

   public static String getBuildPlatform() {
      return "Linux mail.monfox.com 2.6.31.12-174.2.3.fc12.i686.PAE #1 SMP Mon Jan 18 20:06:44 UTC 2010 i686 i686 i386 GNU/Linux";
   }

   public static String getBuildJDKVersion() {
      return "JAVAC 1.6: /opt/jdk1.6/bin/java -version";
   }

   @Override
   public String toString() {
      return "Release   : "
         + getBuildRelease()
         + "\n"
         + "Build Date: "
         + getBuildDate()
         + "\n"
         + "Build JDK : "
         + getBuildJDKVersion()
         + "\n"
         + "Platform  : "
         + getBuildPlatform()
         + "\n";
   }
}
