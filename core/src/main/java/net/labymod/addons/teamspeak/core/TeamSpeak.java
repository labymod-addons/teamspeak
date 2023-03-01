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

package net.labymod.addons.teamspeak.core;

import java.io.IOException;
import javax.inject.Singleton;
import net.labymod.addons.teamspeak.core.generated.DefaultReferenceStorage;
import net.labymod.addons.teamspeak.core.hud.TeamSpeakHudWidget;
import net.labymod.addons.teamspeak.core.listener.ConfigurationSaveListener;
import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.reference.annotation.Referenceable;

@AddonMain
@Singleton
@Referenceable
public class TeamSpeak extends LabyAddon<TeamSpeakConfiguration> {

  @Override
  protected void enable() {
    this.registerSettingCategory();

    DefaultTeamSpeakAPI teamSpeakAPI = (DefaultTeamSpeakAPI) this.references().teamSpeakAPI();
    this.registerListener(new ConfigurationSaveListener(this, teamSpeakAPI));

    if (this.configuration().enabled().get()) {
      new Thread(() -> {
        try {
          teamSpeakAPI.initialize();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }).start();
    }

    this.labyAPI().hudWidgetRegistry().register(new TeamSpeakHudWidget(this, teamSpeakAPI));
  }

  @Override
  protected Class<TeamSpeakConfiguration> configurationClass() {
    return TeamSpeakConfiguration.class;
  }

  public DefaultReferenceStorage references() {
    return this.getReferenceStorageAccessor();
  }
}
