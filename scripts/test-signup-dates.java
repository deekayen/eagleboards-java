// ------------------------------------------------------------------------
// test-signup-dates.java — regression tests for finding tonight's sign-up.
//
// The SignUpGenius import uses the active sign-up whose dates cover today.
// The API's dates carry a time ("2026-09-22 18:30:00"), and comparing
// today's "2026-09-22" against the whole string put today before the start,
// so a sign-up was missed on its first day: January's board night, for a
// sign-up that runs the year. The Mac version found and fixed it first.
//
// coversDay is pure, so this needs no network, no key and no test framework.
// Run it against the built jar (Java's single-file source launch):
//
//   java -cp target/eagleboardscheduler-<version>.jar scripts/test-signup-dates.java
//
// Runs on all four CI platforms (see .github/workflows/build.yml).
// ------------------------------------------------------------------------

import shkc.core.SignUpGeniusPlugin;

public class TestSignUpDates {
   private static int failures = 0;

   public static void main(String[] args) {
      String oneNightStart = "2026-09-22 18:30:00";
      String oneNightEnd = "2026-09-22 21:00:00";
      check("first day of a one-night sign-up", true, SignUpGeniusPlugin.coversDay("2026-09-22", oneNightStart, oneNightEnd));
      check("day before it", false, SignUpGeniusPlugin.coversDay("2026-09-21", oneNightStart, oneNightEnd));
      check("day after it", false, SignUpGeniusPlugin.coversDay("2026-09-23", oneNightStart, oneNightEnd));

      String yearStart = "2027-01-26 18:30:00";
      String yearEnd = "2027-12-14 21:00:00";
      check("first night of a year-long sign-up", true, SignUpGeniusPlugin.coversDay("2027-01-26", yearStart, yearEnd));
      check("a night in the middle of it", true, SignUpGeniusPlugin.coversDay("2027-06-22", yearStart, yearEnd));
      check("its last night", true, SignUpGeniusPlugin.coversDay("2027-12-14", yearStart, yearEnd));
      check("the year after it", false, SignUpGeniusPlugin.coversDay("2028-01-25", yearStart, yearEnd));

      check("dates with no time", true, SignUpGeniusPlugin.coversDay("2026-09-22", "2026-09-22", "2026-09-22"));

      if (failures > 0) {
         System.out.println(failures + " sign-up date check(s) failed");
         System.exit(1);
      }
      System.out.println("PASS: sign-up date range checks");
   }

   private static void check(String name, boolean expected, boolean actual) {
      if (expected != actual) {
         failures++;
         System.out.println("FAIL: " + name + ": expected " + expected + ", got " + actual);
      }
   }
}
