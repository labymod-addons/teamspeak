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

public class TalkStatusChangeListener extends DefaultListener {

  public TalkStatusChangeListener() {
    super("notifytalkstatuschange");
  }

  @Override
  public void execute(DefaultTeamSpeakAPI teamSpeakAPI, String[] args) {
    Integer serverId = this.get(args, "schandlerid", Integer.class);
    DefaultServer selectedServer = teamSpeakAPI.getSelectedServer();
    if (selectedServer == null || serverId == null || selectedServer.getId() != serverId) {
      return;
    }

    Integer clientId = this.get(args, "clid", Integer.class);
    DefaultChannel selectedChannel = selectedServer.getSelectedChannel();
    if (selectedChannel == null || clientId == null) {
      return;
    }

    DefaultUser user = selectedChannel.getUser(clientId);
    if (user == null) {
      return;
    }

    Integer status = this.get(args, "status", Integer.class);
    if (status == null) {
      return;
    }

    user.setTalking(status == 1);
  }
}
