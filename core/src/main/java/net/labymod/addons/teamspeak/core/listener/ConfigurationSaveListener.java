/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.labymod.addons.teamspeak.core.listener;

import java.io.IOException;
import net.labymod.addons.teamspeak.core.TeamSpeak;
import net.labymod.addons.teamspeak.core.TeamSpeakConfiguration;
import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.labymod.config.ConfigurationSaveEvent;

public class ConfigurationSaveListener {

  private final DefaultTeamSpeakAPI teamSpeakAPI;
  private final TeamSpeak teamSpeak;

  private boolean currentEnabled;
  private boolean currentAutomaticAPIKeyResolve;
  private String currentAPIKey;

  public ConfigurationSaveListener(TeamSpeak teamSpeak, DefaultTeamSpeakAPI teamSpeakAPI) {
    this.teamSpeak = teamSpeak;
    this.teamSpeakAPI = teamSpeakAPI;

    TeamSpeakConfiguration configuration = teamSpeak.configuration();
    this.currentEnabled = configuration.enabled().get();
    this.currentAutomaticAPIKeyResolve = configuration.resolveAPIKey().get();
    this.currentAPIKey = configuration.apiKey().get();

  }

  @Subscribe
  public void onConfigurationSave(ConfigurationSaveEvent event) {
    TeamSpeakConfiguration configuration = this.teamSpeak.configuration();
    boolean currentEnabled = configuration.enabled().get();
    boolean currentAutomaticAPIKeyResolve = configuration.resolveAPIKey().get();
    String currentAPIKey = configuration.apiKey().get();

    boolean refreshTeamSpeakAPI = false;
    if (this.currentEnabled != currentEnabled) {
      this.currentEnabled = currentEnabled;
      refreshTeamSpeakAPI = true;
    }

    if (this.currentAutomaticAPIKeyResolve != currentAutomaticAPIKeyResolve) {
      this.currentAutomaticAPIKeyResolve = currentAutomaticAPIKeyResolve;
      refreshTeamSpeakAPI = true;
    }

    if (!this.currentAPIKey.equals(currentAPIKey)) {
      this.currentAPIKey = currentAPIKey;
      refreshTeamSpeakAPI = true;
    }

    if (refreshTeamSpeakAPI) {
      try {
        this.teamSpeakAPI.stop();
      } catch (IOException e) {
        e.printStackTrace();
      }

      if (currentEnabled) {
        new Thread(() -> {
          try {
            this.teamSpeakAPI.initialize();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }).start();
      }
    }
  }
}
