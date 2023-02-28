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

package net.labymod.addons.teamspeak.core.teamspeak.listener;

import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultChannel;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultServer;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultUser;

public class ClientEnterViewListener extends DefaultListener {

  public ClientEnterViewListener() {
    super("notifycliententerview");
  }

  @Override
  public void execute(DefaultTeamSpeakAPI teamSpeakAPI, String[] args) {
    Integer schandlerId = this.get(args, "schandlerid", Integer.class);
    if (schandlerId == null) {
      return;
    }

    DefaultServer selectedServer = teamSpeakAPI.getSelectedServer();
    if (selectedServer == null || selectedServer.getId() != schandlerId) {
      return;
    }

    DefaultChannel selectedChannel = selectedServer.getSelectedChannel();
    Integer channelId = this.get(args, "ctid", Integer.class);
    if (channelId == null || selectedChannel == null || selectedChannel.getId() != channelId) {
      return;
    }

    Integer clientId = this.get(args, "clid", Integer.class);
    DefaultUser user = selectedChannel.getUser(clientId);
    if (user != null) {
      return;
    }

    teamSpeakAPI.controller().refreshUsers(selectedChannel);
  }
}
