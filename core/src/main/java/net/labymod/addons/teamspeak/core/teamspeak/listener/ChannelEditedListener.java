package net.labymod.addons.teamspeak.core.teamspeak.listener;

import net.labymod.addons.teamspeak.api.util.ArgumentParser;
import net.labymod.addons.teamspeak.core.teamspeak.DefaultTeamSpeakAPI;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultChannel;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultServer;

public class ChannelEditedListener extends DefaultListener {

  public ChannelEditedListener() {
    super("notifychanneledited");
  }

  @Override
  public void execute(DefaultTeamSpeakAPI teamSpeakAPI, String[] args) {
    Integer schandlerId = this.get(args, "schandlerid", Integer.class);
    DefaultServer server = teamSpeakAPI.getSelectedServer();
    if (server == null || schandlerId == null || server.getId() != schandlerId) {
      return;
    }

    Integer channelId = this.get(args, "cid", Integer.class);
    if (channelId == null) {
      return;
    }

    DefaultChannel channel = server.getChannel(channelId);
    if (channel == null) {
      return;
    }

    String channelName = this.get(args, "channel_name", String.class);
    if (channelName != null) {
      channel.setName(ArgumentParser.unescape(channelName));
    }
  }
}