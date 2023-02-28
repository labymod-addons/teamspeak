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

package net.labymod.addons.teamspeak.core.teamspeak.misc;

import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.teamspeak.api.util.ArgumentParser;
import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultChannel;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultServer;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultUser;

public class TeamSpeakController {

  private final DefaultTeamSpeakAPI teamSpeakAPI;
  private final List<DefaultServer> servers;
  private DefaultServer selectedServer;

  public TeamSpeakController(DefaultTeamSpeakAPI teamSpeakAPI) {
    this.teamSpeakAPI = teamSpeakAPI;
    this.servers = new ArrayList<>();
  }

  public DefaultServer getSelectedServer() {
    return this.selectedServer;
  }

  public void setSelectedServer(DefaultServer server) {
    if (server == null) {
      this.selectedServer = null;
      return;
    }

    if (!this.servers.contains(server)) {
      throw new IllegalArgumentException("Server is not in the list of servers!");
    }

    this.selectedServer = server;
    this.teamSpeakAPI.getOutputStream()
        .println("clientnotifyregister schandlerid=" + server.getId() + " event=any");
  }

  public List<DefaultServer> getServers() {
    return this.servers;
  }

  public DefaultServer getServer(int id) {
    for (DefaultServer server : this.servers) {
      if (server.getId() == id) {
        return server;
      }
    }

    return null;
  }

  public void refreshUsers(DefaultChannel channel) {
    this.refreshUsers(channel, null);
  }

  public void refreshUsers(DefaultChannel channel, Runnable runnable) {
    this.teamSpeakAPI.request("channelclientlist cid=" + channel.getId() + " -voice -away",
        response -> {
          String[] clients = response.split("\\|");
          for (String client : clients) {
            String[] clientArgs = client.split(" ");
            Integer clientId = this.get(clientArgs, "clid", Integer.class);
            if (clientId == null) {
              continue;
            }

            DefaultUser user = channel.getUser(clientId);
            if (user != null) {
              continue;
            }

            user = channel.addUser(clientId);
            String clientNickname = this.get(clientArgs, "client_nickname", String.class);
            if (clientNickname != null) {
              user.setNickname(clientNickname);
            }

            Integer clientType = this.get(clientArgs, "client_type", Integer.class);
            if (clientType != null) {
              user.setQuery(clientType == 1);
            }

            Integer clientFlagTalking = this.get(clientArgs, "client_flag_talking", Integer.class);
            if (clientFlagTalking != null) {
              user.setTalking(clientFlagTalking == 1);
            }

            Integer clientAway = this.get(clientArgs, "client_away", Integer.class);
            if (clientAway != null) {
              user.setAway(clientAway == 1);
              String clientAwayMessage = this.get(clientArgs, "client_away_message", String.class);
              if (clientAwayMessage != null) {
                user.setAwayMessage(clientAwayMessage);
              }
            }

            // hardware muted
            Integer clientInputHardware = this.get(clientArgs, "client_input_hardware",
                Integer.class);
            if (clientInputHardware != null) {
              user.setHardwareMuted(clientInputHardware == 0);
            }

            // hardware deafened
            Integer clientOutputHardware = this.get(clientArgs, "client_output_hardware",
                Integer.class);
            if (clientOutputHardware != null) {
              user.setHardwareDeafened(clientOutputHardware == 0);
            }

            Integer clientInputMuted = this.get(clientArgs, "client_input_muted", Integer.class);
            if (clientInputMuted != null) {
              user.setMuted(clientInputMuted == 1);
            }

            Integer clientOutputMuted = this.get(clientArgs, "client_output_muted", Integer.class);
            if (clientOutputMuted != null) {
              user.setDeafened(clientOutputMuted == 1);
            }

            Integer clientTalkPower = this.get(clientArgs, "client_talk_power", Integer.class);
            if (clientTalkPower != null) {
              user.setTalkPower(clientTalkPower);
            }
          }

          channel.getUsers().sort(((o1, o2) -> {
            int firstTalkPower = o1.getTalkPower();
            int secondTalkPower = o2.getTalkPower();
            if (firstTalkPower == secondTalkPower) {
              return this.compareNickName(o1.getNickname(), o2.getNickname());
            }

            return Integer.compare(secondTalkPower, firstTalkPower);
          }));

          channel.getUsers().sort(((o1, o2) -> {
            boolean firstQuery = o1.isQuery();
            boolean secondQuery = o2.isQuery();
            if (firstQuery && secondQuery) {
              return this.compareNickName(o1.getNickname(), o2.getNickname());
            }

            return Boolean.compare(secondQuery, firstQuery);
          }));

          if (runnable != null) {
            runnable.run();
          }
        });
  }

  public void refreshCurrentServer(int schandlerId) {
    this.teamSpeakAPI.request("use " + schandlerId, response -> {
      this.teamSpeakAPI.request("whoami", 0, whoami -> {
        // ignored
      });

      this.refreshCurrentServer0(schandlerId);
    });
  }

  public void refreshCurrentServer0(int schandlerId) {
    DefaultServer server = this.teamSpeakAPI.getServer(schandlerId);
    if (server == null) {
      return;
    }

    server.getChannels().clear();
    this.teamSpeakAPI.controller().setSelectedServer(server);
    this.teamSpeakAPI.request("channellist", channelListAnswer -> {
      String[] channels = channelListAnswer.split("\\|");
      for (String rawChannel : channels) {
        String[] s = rawChannel.split(" ");
        Integer channelId = this.get(s, "cid", Integer.class);
        String channelName = this.get(s, "channel_name", String.class);
        if (channelId != null) {
          DefaultChannel channel = server.addChannel(channelId);
          if (channelName != null) {
            channel.setName(channelName);
          }
        }
      }

      this.teamSpeakAPI.request("channelconnectinfo", channelConnectInfoAnswer -> {
        String[] s = channelConnectInfoAnswer.split(" ");
        String name = this.get(s, "path", String.class);
        if (name != null) {
          for (DefaultChannel channel : server.getDefaultChannels()) {
            if (name.equals(channel.getName())) {
              server.setSelectedChannel(channel);
              this.refreshUsers(channel);
              break;
            }
          }
        }
      });
    });
  }

  private <T> T get(String[] arguments, String identifier, Class<T> clazz) {
    return ArgumentParser.parse(arguments, identifier, clazz);
  }

  private int compareNickName(String firstNickname, String secondNickname) {
    if (firstNickname == null || secondNickname == null) {
      return 0;
    }

    return firstNickname.toLowerCase().compareTo(secondNickname.toLowerCase());
  }
}
