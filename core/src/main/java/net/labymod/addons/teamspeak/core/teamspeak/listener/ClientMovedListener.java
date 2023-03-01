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

import net.labymod.addons.teamspeak.api.models.User;
import net.labymod.addons.teamspeak.api.util.Request;
import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultChannel;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultServer;

public class ClientMovedListener extends DefaultListener {

  public ClientMovedListener() {
    super("notifyclientmoved");
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

    Integer clientId = this.get(args, "clid", Integer.class);
    Integer channelId = this.get(args, "ctid", Integer.class);
    if (clientId == null || channelId == null) {
      return;
    }

    if (clientId == teamSpeakAPI.getClientId()) {
      DefaultChannel selectedChannel = selectedServer.getSelectedChannel();
      if (selectedChannel != null && selectedChannel.getId() != channelId) {
        selectedChannel.getUsers().clear();
      }

      DefaultChannel channel = selectedServer.getChannel(channelId);
      if (channel == null) {
        channel = selectedServer.addChannel(channelId);
      }

      DefaultChannel finalChannel = channel;

      teamSpeakAPI.request(Request.firstParamStartsWith(
          "channelconnectinfo",
          "path=",
          channelConnectInfoAnswer -> {
            String path = this.get(channelConnectInfoAnswer, "path", String.class);
            String[] splitPath = path.split("\\\\/");
            String name = splitPath[splitPath.length - 1];
            finalChannel.setName(name);
            selectedServer.setSelectedChannel(finalChannel);
            teamSpeakAPI.controller().refreshUsers(finalChannel);
          }
      ));

      return;
    }

    DefaultChannel selectedChannel = selectedServer.getSelectedChannel();
    if (selectedChannel == null) {
      return;
    }

    boolean sameChannel = channelId == selectedChannel.getId();
    User user = selectedChannel.getUser(clientId);
    if (user == null && sameChannel) {
      teamSpeakAPI.controller().refreshUsers(selectedChannel);
      return;
    }

    if (user != null && !sameChannel) {
      selectedChannel.getUsers().remove(user);
    }
  }
}
