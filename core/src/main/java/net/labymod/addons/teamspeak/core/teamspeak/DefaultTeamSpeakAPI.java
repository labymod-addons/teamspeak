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

package net.labymod.addons.teamspeak.core.teamspeak;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import net.labymod.addons.teamspeak.api.TeamSpeakAPI;
import net.labymod.addons.teamspeak.api.listener.Listener;
import net.labymod.addons.teamspeak.api.models.Server;
import net.labymod.addons.teamspeak.api.util.Request;
import net.labymod.addons.teamspeak.core.TeamSpeak;
import net.labymod.addons.teamspeak.core.TeamSpeakConfiguration;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ChannelEditedListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ClientEnterViewListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ClientLeftViewListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ClientMovedListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ClientUpdatedListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.ConnectStatusChange;
import net.labymod.addons.teamspeak.core.teamspeak.listener.CurrentServerConnectionChangedListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.SelectedListener;
import net.labymod.addons.teamspeak.core.teamspeak.listener.TalkStatusChangeListener;
import net.labymod.addons.teamspeak.core.teamspeak.misc.TeamSpeakController;
import net.labymod.addons.teamspeak.core.teamspeak.models.DefaultServer;
import net.labymod.api.models.Implements;
import net.labymod.api.util.ThreadSafe;
import net.labymod.api.util.io.LabyExecutors;

/**
 * Lifecycle owner of the TeamSpeak connection.
 *
 * <p>Threads (all daemon, owned here):
 * <ul>
 *   <li>{@code reader} - one reusable single-thread executor that performs only blocking IO:
 *   {@link #connect(int)} opens a {@link TeamSpeakConnection} and blocks in its read loop.</li>
 *   <li>{@code scheduler} - one single-thread scheduled executor that owns ALL lifecycle state
 *   transitions (start/stop, connected flag, heartbeat, reconnect, generation). Because every
 *   transition runs here, they can never race each other.</li>
 * </ul>
 *
 * <p>The model and the {@link #requests} list are mutated only on the render thread via
 * {@link #handleLine(String, int)}, which the reader posts per line. The HUD reads the same model on
 * the render thread, so no locking is needed there. The {@link #generation} counter (bumped only on
 * the scheduler in {@link #startInternal()}/{@link #stopInternal()}) lets stale connections
 * short-circuit, and {@link #reconnectScheduled} collapses concurrent reconnect triggers into one.
 */
@Singleton
@Implements(TeamSpeakAPI.class)
public class DefaultTeamSpeakAPI implements TeamSpeakAPI {

  private static final long HEARTBEAT_SECONDS = 5L;
  private static final long RECONNECT_SECONDS = 10L;

  private final TeamSpeakAuthenticator authenticator;
  private final TeamSpeakController controller;
  private final TeamSpeak teamSpeak;
  private final List<Listener> listeners;
  private final List<Request> requests;

  private final ExecutorService reader;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean reconnectScheduled;

  private volatile TeamSpeakConnection connection;
  private volatile ScheduledFuture<?> heartbeatFuture;
  private volatile ScheduledFuture<?> reconnectFuture;

  private volatile boolean stopped;
  private volatile boolean shuttingDown;
  private volatile int generation;
  private volatile boolean connected;
  private volatile boolean invalidKey;
  private volatile boolean connectFailureLogged;

  private int clientId;
  private int channelId;

  public DefaultTeamSpeakAPI(TeamSpeak teamSpeak) {
    this.teamSpeak = teamSpeak;
    this.authenticator = new TeamSpeakAuthenticator(teamSpeak, this);
    this.controller = new TeamSpeakController(this);
    this.requests = new ArrayList<>();
    this.listeners = new ArrayList<>();

    this.reader = LabyExecutors.newSingleThreadExecutor("TeamSpeak-Reader-%d");
    this.scheduler = LabyExecutors.newSingleThreadScheduledExecutor("TeamSpeak-Scheduler-%d");
    this.reconnectScheduled = new AtomicBoolean(false);

    this.listeners.add(new ClientMovedListener());
    this.listeners.add(new SelectedListener());
    this.listeners.add(new ClientUpdatedListener());
    this.listeners.add(new TalkStatusChangeListener());
    this.listeners.add(new CurrentServerConnectionChangedListener());
    this.listeners.add(new ConnectStatusChange());
    this.listeners.add(new ClientLeftViewListener());
    this.listeners.add(new ClientEnterViewListener());
    this.listeners.add(new ChannelEditedListener());
  }

  public void start() {
    this.submitScheduler(this::startInternal);
  }

  public void stop() {
    this.submitScheduler(this::stopInternal);
  }

  public void shutdown() {
    this.shuttingDown = true;
    this.stopped = true;
    TeamSpeakConnection connection = this.connection;
    if (connection != null) {
      connection.close();
    }

    this.reader.shutdownNow();
    this.scheduler.shutdownNow();
  }

  private void startInternal() {
    if (this.shuttingDown) {
      return;
    }

    this.stopped = false;
    int generation = ++this.generation;
    this.submitReader(() -> this.connect(generation));
  }

  private void stopInternal() {
    this.generation++;
    this.stopped = true;
    this.connected = false;
    this.cancelHeartbeat();
    this.cancelReconnect();
    this.reconnectScheduled.set(false);

    TeamSpeakConnection connection = this.connection;
    if (connection != null) {
      connection.close();
      this.connection = null;
    }

    ThreadSafe.executeOnRenderThread(this::reset);
  }

  private void connect(int generation) {
    if (this.stopped || generation != this.generation) {
      return;
    }

    if (!this.connectFailureLogged) {
      this.teamSpeak.logger().info("Connecting to TeamSpeak client...");
    }

    TeamSpeakConnection connection = new TeamSpeakConnection(this, generation);
    try {
      connection.open();
    } catch (IOException exception) {
      if (!this.connectFailureLogged) {
        this.connectFailureLogged = true;
        this.teamSpeak.logger().warn(
            "Could not connect to TeamSpeak client, retrying every " + RECONNECT_SECONDS
                + " seconds...");
      }

      this.onConnectionLost(generation);
      return;
    }

    this.connectFailureLogged = false;

    // Publish before the read loop so a concurrent stop() can close the socket and unblock it.
    this.connection = connection;
    if (this.stopped || generation != this.generation) {
      connection.close();
      return;
    }

    this.invalidKey = false;
    ThreadSafe.executeOnRenderThread(this::reset);

    if (!this.authenticateOnConnect()) {
      connection.close();
      return;
    }

    this.clientNotifyRegister(0);
    this.submitScheduler(() -> this.onConnected(connection, generation));

    connection.readLoop();

    if (this.stopped) {
      return;
    }

    this.onConnectionLost(generation);
  }

  private void onConnected(TeamSpeakConnection connection, int generation) {
    if (this.stopped || generation != this.generation) {
      connection.close();
      return;
    }

    this.connected = true;
    this.startHeartbeat();
    this.teamSpeak.logger().info("Successfully connected to the TeamSpeak client.");
  }

  private boolean authenticateOnConnect() {
    TeamSpeakConfiguration configuration = this.teamSpeak.configuration();
    if (configuration.resolveAPIKey().get()) {
      if (!this.authenticator.authenticate()) {
        this.invalidKey = true;
      }

      return true;
    }

    String apiKey = configuration.apiKey().get().trim();
    if (apiKey.isEmpty()) {
      this.invalidKey = true;
      return false;
    }

    this.authenticate(apiKey);
    return true;
  }

  private void onConnectionLost(int generation) {
    this.submitScheduler(() -> {
      if (this.stopped || generation != this.generation) {
        return;
      }

      this.connected = false;
      this.cancelHeartbeat();

      TeamSpeakConnection connection = this.connection;
      if (connection != null) {
        connection.close();
        this.connection = null;
      }

      if (this.reconnectScheduled.compareAndSet(false, true)) {
        this.reconnectFuture = this.scheduler.schedule(
            () -> this.reconnect(generation),
            RECONNECT_SECONDS,
            TimeUnit.SECONDS
        );
      }
    });
  }

  private void reconnect(int generation) {
    this.reconnectScheduled.set(false);
    if (this.stopped || generation != this.generation) {
      return;
    }

    this.submitReader(() -> this.connect(generation));
  }

  private void startHeartbeat() {
    this.cancelHeartbeat();
    this.heartbeatFuture = this.scheduler.scheduleAtFixedRate(
        this::heartbeat,
        HEARTBEAT_SECONDS,
        HEARTBEAT_SECONDS,
        TimeUnit.SECONDS
    );
  }

  private void heartbeat() {
    try {
      if (this.stopped || !this.connected) {
        return;
      }

      TeamSpeakConnection connection = this.connection;
      if (connection == null) {
        return;
      }

      if (!connection.write("whoami")) {
        this.onConnectionLost(connection.generation());
      }
    } catch (Throwable throwable) {
      this.teamSpeak.logger().error("Error in TeamSpeak heartbeat", throwable);
    }
  }

  private void cancelHeartbeat() {
    ScheduledFuture<?> future = this.heartbeatFuture;
    if (future != null) {
      future.cancel(false);
      this.heartbeatFuture = null;
    }
  }

  private void cancelReconnect() {
    ScheduledFuture<?> future = this.reconnectFuture;
    if (future != null) {
      future.cancel(false);
      this.reconnectFuture = null;
    }
  }

  private void submitReader(Runnable runnable) {
    try {
      this.reader.execute(runnable);
    } catch (RejectedExecutionException exception) {
      // Executor was shut down; nothing to do.
    }
  }

  private void submitScheduler(Runnable runnable) {
    try {
      this.scheduler.execute(runnable);
    } catch (RejectedExecutionException exception) {
      // Executor was shut down; nothing to do.
    }
  }

  void dispatchLine(String line, int generation) {
    if (this.isDebug()) {
      this.teamSpeak.logger().info("[TeamSpeak <<] " + line);
    }

    ThreadSafe.executeOnRenderThread(() -> this.handleLine(line, generation));
  }

  private boolean isDebug() {
    return this.teamSpeak.configuration().debug().get();
  }

  private static String maskApiKey(String message) {
    if (message.startsWith("auth apikey=")) {
      return "auth apikey=***";
    }

    return message;
  }

  private void handleLine(String line, int generation) {
    if (this.stopped || generation != this.generation || line.isEmpty()) {
      return;
    }

    String[] s = line.split(" ");
    if (s.length == 2 && s[0].startsWith("clid=") && s[1].startsWith("cid=")) {
      int clientId = Integer.parseInt(s[0].substring(5));
      if (this.clientId != clientId) {
        this.clientId = clientId;
      }

      int channelId = Integer.parseInt(s[1].substring(4));
      if (this.channelId != channelId) {
        this.channelId = channelId;
      }

      return;
    }

    boolean ok = line.equals("error id=0 msg=ok");
    Request[] requests = this.requests.toArray(new Request[0]);
    boolean handledRequest = false;
    for (Request request : requests) {
      if (request.isFinished()) {
        this.requests.remove(request);
        continue;
      }

      if (request.handle(s[0], line)) {
        handledRequest = true;
      }
    }

    if (ok || handledRequest) {
      return;
    }

    for (Listener listener : this.listeners) {
      if (listener.getIdentifier().equals(s[0])) {
        listener.execute(this, s);
      }
    }
  }

  @Override
  public boolean isConnected() {
    return this.connected;
  }

  public void clientNotifyRegister(int id) {
    for (Listener listener : this.listeners) {
      if (!listener.needsToBeRegistered()) {
        continue;
      }

      this.write("clientnotifyregister schandlerid=" + id + " event=" + listener.getIdentifier());
    }
  }

  private boolean write(String message) {
    TeamSpeakConnection connection = this.connection;
    if (connection == null) {
      return false;
    }

    if (this.isDebug()) {
      this.teamSpeak.logger().info("[TeamSpeak >>] " + maskApiKey(message));
    }

    return connection.write(message);
  }

  public boolean authenticate(String apiKey) {
    return this.write("auth apikey=" + apiKey);
  }

  @Override
  public boolean hasInvalidKey() {
    return this.invalidKey;
  }

  public void setInvalidKey(boolean invalidKey) {
    this.invalidKey = invalidKey;
  }

  @Override
  public void request(Request request) {
    String query = request.getQuery();
    Request[] pendingRequests = this.requests.toArray(new Request[0]);
    for (Request pendingRequest : pendingRequests) {
      if (pendingRequest.getQuery().equals(query)) {
        this.requests.remove(pendingRequest);
      }
    }

    this.requests.add(request);
    this.query(query);
  }

  @Override
  public void query(String query) {
    this.write(query);
  }

  @Override
  public DefaultServer getSelectedServer() {
    return this.controller.getSelectedServer();
  }

  @Override
  public List<Server> getServers() {
    return (List) this.controller.getServers();
  }

  @Override
  public DefaultServer getServer(int id) {
    return this.controller.getServer(id);
  }

  @Override
  public int getClientId() {
    return this.clientId;
  }

  public List<Request> getRequests() {
    return this.requests;
  }

  public TeamSpeakController controller() {
    return this.controller;
  }

  private void reset() {
    this.controller.getServers().clear();
    this.controller.setSelectedServer(null);
    this.clientId = 0;
    this.channelId = 0;
  }
}
