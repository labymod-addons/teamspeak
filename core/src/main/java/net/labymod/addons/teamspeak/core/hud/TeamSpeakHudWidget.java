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

package net.labymod.addons.teamspeak.core.hud;

import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.teamspeak.api.TeamSpeakAPI;
import net.labymod.addons.teamspeak.api.models.Channel;
import net.labymod.addons.teamspeak.api.models.Server;
import net.labymod.addons.teamspeak.api.models.User;
import net.labymod.addons.teamspeak.core.TeamSpeak;
import net.labymod.addons.teamspeak.core.hud.TeamSpeakHudWidget.TeamSpeakHudWidgetConfig;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultChannel;
import net.labymod.addons.teamspeak.core.util.TeamSpeakUserIcon;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextDecoration;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.SimpleHudWidget;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.render.font.ComponentRenderer;
import net.labymod.api.client.render.font.RenderableComponent;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TeamSpeakHudWidget extends SimpleHudWidget<TeamSpeakHudWidgetConfig> {

  private static final Channel DUMMY_CHANNEL = new DummyChannel();
  private static final Component NOT_CONNECTED = Component.translatable(
      "teamspeak.hudWidget.teamSpeak.notConnected"
  ).color(NamedTextColor.RED);
  private static final Component NO_SELECTED_SERVER = Component.translatable(
      "teamspeak.hudWidget.teamSpeak.noSelectedServer"
  ).color(NamedTextColor.RED);
  private static final Component NO_SELECTED_CHANNEL = Component.translatable(
      "teamspeak.hudWidget.teamSpeak.noSelectedChannel"
  ).color(NamedTextColor.RED);
  private static final Component INVALID_KEY = Component.translatable(
      "teamspeak.hudWidget.teamSpeak.invalidKey"
  ).color(NamedTextColor.RED);
  private static final Component INVALID_KEY_MANUAL = Component.translatable(
      "teamspeak.hudWidget.teamSpeak.invalidKeyManual"
  ).color(NamedTextColor.RED);

  private final TeamSpeak teamSpeak;
  private final TeamSpeakAPI teamSpeakAPI;

  public TeamSpeakHudWidget(TeamSpeak teamSpeak, TeamSpeakAPI teamSpeakAPI) {
    super("teamSpeak", TeamSpeakHudWidgetConfig.class);
    this.teamSpeak = teamSpeak;
    this.teamSpeakAPI = teamSpeakAPI;
  }

  @Override
  public void render(
      @Nullable Stack stack,
      MutableMouse mutableMouse,
      float partialTicks,
      boolean isEditorContext,
      HudSize size
  ) {
    // Reset (Updated in renderChannel)
    size.setWidth(0);
    size.setHeight(0);

    if (isEditorContext) {
      this.renderChannel(DUMMY_CHANNEL, stack, size);
      return;
    }

    if (!this.teamSpeakAPI.isConnected()) {
      this.renderErrorComponent(NOT_CONNECTED, stack, size);
      return;
    }

    if (this.teamSpeakAPI.hasInvalidKey()) {
      Component component;
      if (this.teamSpeak.configuration().resolveAPIKey().get()) {
        component = INVALID_KEY;
      } else {
        component = INVALID_KEY_MANUAL;
      }

      this.renderErrorComponent(component, stack, size);
      return;
    }

    Server selectedServer = this.teamSpeakAPI.getSelectedServer();
    if (selectedServer == null) {
      this.renderErrorComponent(NO_SELECTED_SERVER, stack, size);
      return;
    }

    Channel selectedChannel = selectedServer.getSelectedChannel();
    if (selectedChannel == null) {
      this.renderErrorComponent(NO_SELECTED_CHANNEL, stack, size);
      return;
    }

    this.renderChannel(selectedChannel, stack, size);
  }

  @Override
  public boolean isVisibleInGame() {
    if (!this.teamSpeakAPI.isConnected()) {
      return Laby.references().chatAccessor().isChatOpen();
    }

    Server selectedServer = this.teamSpeakAPI.getSelectedServer();
    return (selectedServer != null && selectedServer.getSelectedChannel() != null)
        || Laby.references().chatAccessor().isChatOpen();
  }

  private void renderErrorComponent(Component component, Stack stack, HudSize size) {
    RenderableComponent renderableComponent = RenderableComponent.of(component);
    if (stack != null) {
      this.labyAPI.renderPipeline().componentRenderer().builder()
          .text(renderableComponent)
          .pos(1, 1)
          .render(stack);
    }

    size.setWidth((int) (renderableComponent.getWidth() + 2));
    size.setHeight((int) (renderableComponent.getHeight() + 2));
  }

  private void renderChannel(Channel channel, Stack stack, HudSize size) {
    ComponentRenderer componentRenderer = this.labyAPI.renderPipeline().componentRenderer();
    int x = 1;
    int y = 1;
    Component channelNameComponent = null;
    if (this.config.prettyChannelName.get()) {
      String prettyName = channel.getPrettyName();
      if (prettyName != null) {
        channelNameComponent = channel.prettyDisplayName();
      }
    }

    if (channelNameComponent == null) {
      channelNameComponent = channel.displayName();
    }

    RenderableComponent channelName = RenderableComponent.of(channelNameComponent);
    if (stack != null) {
      componentRenderer.builder()
          .text(channelName)
          .pos(x, y)
          .render(stack);
    }

    size.setWidth((int) (x + channelName.getWidth() + 1));
    x += 5;
    y += channelName.getHeight() + 1;
    int rowHeight = (int) componentRenderer.height();

    for (User user : channel.getUsers()) {
      if (user.getNickname() == null || (user.isQuery()
          && !this.config.displayServerQueries.get())) {
        continue;
      }

      int userX = x + 2;
      if (stack != null) {
        Icon icon = TeamSpeakUserIcon.of(user).icon();
        icon.render(stack, userX, y, rowHeight);
      }

      userX += rowHeight + 4;
      Component component = user.displayName();
      if ((this.teamSpeakAPI.isConnected() && this.teamSpeakAPI.getClientId() == user.getId()) ||
          (user instanceof DummyUser && ((DummyUser) user).name == null)) {
        component.decorate(TextDecoration.BOLD);
      }

      String awayMessage = user.getAwayMessage();
      if (awayMessage != null && this.config.displayAwayMessage.get()) {
        component = Component.empty().append(component)
            .append(Component.text(" [").decorate(TextDecoration.ITALIC))
            .append(Component.text(awayMessage).decorate(TextDecoration.ITALIC))
            .append(Component.text("]").decorate(TextDecoration.ITALIC));
      }

      RenderableComponent userName = RenderableComponent.of(component);
      if (stack != null) {
        componentRenderer.builder()
            .text(userName)
            .pos(userX, y)
            .render(stack);
      }

      y += userName.getHeight() + 1;
      userX += userName.getWidth();
      size.setWidth(Math.max(size.getWidth(), userX + 1));
    }

    size.setHeight(y);
  }

  public static class TeamSpeakHudWidgetConfig extends HudWidgetConfig {

    @SwitchSetting
    private final ConfigProperty<Boolean> displayAwayMessage = new ConfigProperty<>(true);

    @SwitchSetting
    private final ConfigProperty<Boolean> prettyChannelName = new ConfigProperty<>(true);

    @SwitchSetting
    private final ConfigProperty<Boolean> displayServerQueries = new ConfigProperty<>(false);
  }

  private static class DummyChannel implements Channel {

    private final String name;
    private final List<User> users;
    private final String prettyName;
    private final Component displayName;
    private final Component prettyDisplayName;

    private DummyChannel() {
      this.name = "[cspacer4] SkyBlock";
      this.displayName = Component.text(this.name);
      this.prettyName = DefaultChannel.PRETTY_PATTERN.matcher(this.name).replaceAll("$1").trim();
      this.prettyDisplayName = Component.text(this.prettyName);

      this.users = new ArrayList<>();

      this.users.add(new DummyUser("serveradmin", false, false, true, null));
      this.users.add(new DummyUser(null, true, false, false, null));
      this.users.add(new DummyUser("LabyModUser", false, false, false, "Talking in VoiceChat"));
      this.users.add(new DummyUser("TeamSpeakUser", false, false, false, null));
    }

    @Override
    public int getId() {
      return 0;
    }

    @Override
    public @NotNull List<User> getUsers() {
      return this.users;
    }

    @Override
    public @Nullable String getName() {
      return this.name;
    }

    @Override
    public @Nullable String getPrettyName() {
      return this.prettyName;
    }

    @Override
    public @NotNull Component displayName() {
      return this.displayName;
    }

    @Override
    public @NotNull Component prettyDisplayName() {
      return this.prettyDisplayName;
    }

    @Override
    public @Nullable User getUser(int id) {
      return null;
    }
  }

  private static class DummyUser implements User {

    private final String name;
    private final boolean muted;
    private final boolean deafened;
    private final String awayMessage;
    private final boolean query;

    private DummyUser(
        String name,
        boolean muted,
        boolean deafened,
        boolean query,
        String awayMessage
    ) {
      this.name = name;
      this.muted = muted;
      this.deafened = deafened;
      this.query = query;
      this.awayMessage = awayMessage;
    }

    @Override
    public int getId() {
      return 0;
    }

    @Override
    public @Nullable String getNickname() {
      if (this.name == null) {
        return Laby.labyAPI().getName();
      }

      return this.name;
    }

    @Override
    public @NotNull Component displayName() {
      return Component.text(this.getNickname());
    }

    @Override
    public boolean isTalking() {
      return false;
    }

    @Override
    public boolean isMuted() {
      return this.muted;
    }

    @Override
    public boolean isHardwareMuted() {
      return false;
    }

    @Override
    public boolean isDeafened() {
      return this.deafened;
    }

    @Override
    public boolean isHardwareDeafened() {
      return this.query;
    }

    @Override
    public boolean isQuery() {
      return this.query;
    }

    @Override
    public int getTalkPower() {
      return 0;
    }

    @Override
    public boolean isAway() {
      return this.awayMessage != null;
    }

    @Override
    public @Nullable String getAwayMessage() {
      return this.awayMessage;
    }
  }
}
