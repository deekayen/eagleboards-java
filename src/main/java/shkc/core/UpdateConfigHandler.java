package shkc.core;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /update-config}: saves the settings the Settings page posts.
 *
 * <p>Reads one request parameter per setting (any column in
 * {@link ConfigRecord#COLUMNS}). The settings are a single record: it is
 * updated if it exists and added if it does not, then the config file is
 * written. Answers with status 200 and no body.
 */
public class UpdateConfigHandler implements WebServer.WebHandler {
   private final EventData data;

   public UpdateConfigHandler(EventData data) {
      this.data = data;
   }

   @Override
   public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      synchronized (this.data.getLock()) {
         Log.verbose(request);

         ConfigRecord config = new ConfigRecord(request.getParameterMap());
         ConfigRecord existingConfig = this.data.getConfig().get(config.getID());
         String summary;
         if (existingConfig != null) {
            Log.verbose("UPDATING EXISTING CONFIG RECORD: " + existingConfig);
            summary = this.changedSettings(existingConfig, config, request);
            existingConfig.updateFrom(config, ConfigRecord.COLUMNS);
         } else {
            Log.verbose("NEW CONFIG RECORD: " + config);
            int alreadyThere = this.data.getConfig().getRecords().size();
            summary = "no config record with id=" + Log.safeValue("ID", config.getID()) + " exists (the file holds " + alreadyThere
               + "), so one was added with " + this.allSettings(config);
            if (alreadyThere > 0) {
               Log.warn("update-config: " + summary + "; the settings file now has more than one record");
            }
            this.data.getConfig().add(config, false);
         }

         this.data.getConfig().store();
         Log.info("update-config: " + summary + " -> saved");
         ConfigRecord saved = this.data.getConfig().get(config.getID());
         if (saved != null) {
            for (String problem : saved.implausibleSettings()) {
               Log.warn("update-config: the saved settings are odd: " + problem);
            }
         }
         response.setStatus(200);
      }
   }

   /**
    * The settings this save changes, as {@code Name old -> new}. A setting the
    * request did not send is given its default by {@link ConfigRecord}, which
    * overwrites the saved value, so that is said too.
    */
   private String changedSettings(ConfigRecord existing, ConfigRecord posted, HttpServletRequest request) {
      StringBuilder changes = new StringBuilder();
      for (String column : ConfigRecord.COLUMNS) {
         String newValue = posted.getValue(column);
         String oldValue = existing.getValue(column);
         // updateFrom skips an empty value, so an empty one changes nothing.
         if (newValue.isEmpty() || newValue.equals(oldValue)) {
            continue;
         }
         if (changes.length() > 0) {
            changes.append(", ");
         }
         changes.append(column).append(' ').append(shown(column, oldValue)).append(" -> ").append(shown(column, newValue));
         if (request.getParameter(column) == null) {
            changes.append(" (not in the request: the default replaced the saved value)");
         }
      }
      return changes.length() == 0 ? "id=" + Log.safeValue("ID", existing.getID()) + " no setting changed"
         : "id=" + Log.safeValue("ID", existing.getID()) + " changed " + changes;
   }

   private String allSettings(ConfigRecord config) {
      StringBuilder settings = new StringBuilder();
      for (String column : ConfigRecord.COLUMNS) {
         if (settings.length() > 0) {
            settings.append(' ');
         }
         settings.append(column).append('=').append(shown(column, config.getValue(column)));
      }
      return settings.toString();
   }

   /** A setting as it may be logged: the settings are names and numbers, and {@link Log#safeValue} keeps it that way. */
   private static String shown(String column, String value) {
      String safe = Log.safeValue(column, value);
      return safe.isEmpty() ? "<empty>" : safe;
   }
}
