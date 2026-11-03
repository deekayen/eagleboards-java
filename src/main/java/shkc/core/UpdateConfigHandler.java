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
         if (existingConfig != null) {
            Log.verbose("UPDATING EXISTING CONFIG RECORD: " + existingConfig);
            existingConfig.updateFrom(config, ConfigRecord.COLUMNS);
         } else {
            Log.verbose("NEW CONFIG RECORD: " + config);
            this.data.getConfig().add(config, false);
         }

         this.data.getConfig().store();
         response.setStatus(200);
      }
   }
}
