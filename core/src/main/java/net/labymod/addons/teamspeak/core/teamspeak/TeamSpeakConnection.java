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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Owns a single TeamSpeak ClientQuery socket: opening it, the blocking read loop, serialized writes
 * and closing. One instance per connection attempt; never reused. All lifecycle, reconnect and model
 * handling lives in {@link DefaultTeamSpeakAPI} - this class is pure transport.
 */
public class TeamSpeakConnection {

  private static final String HOST = "127.0.0.1";
  private static final int PORT = 25639;

  private final DefaultTeamSpeakAPI api;
  private final int generation;
  private final Object writeLock;

  private Socket socket;
  private PrintWriter outputStream;
  private BufferedReader inputStream;
  private volatile boolean closed;

  public TeamSpeakConnection(DefaultTeamSpeakAPI api, int generation) {
    this.api = api;
    this.generation = generation;
    this.writeLock = new Object();
  }

  public void open() throws IOException {
    Socket socket = new Socket(HOST, PORT);
    this.outputStream = new PrintWriter(
        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
        true
    );
    this.inputStream = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
    );
    this.socket = socket;
  }

  public void readLoop() {
    try {
      String line;
      while (!this.closed && (line = this.inputStream.readLine()) != null) {
        this.api.dispatchLine(line, this.generation);
      }
    } catch (IOException exception) {
      // Socket closed by us or dropped by the peer; the read loop ends and the caller reconnects.
    }
  }

  public boolean write(String message) {
    synchronized (this.writeLock) {
      if (this.closed || this.outputStream == null) {
        return false;
      }

      this.outputStream.println(message);
      return !this.outputStream.checkError();
    }
  }

  public void close() {
    this.closed = true;
    Socket socket = this.socket;
    if (socket == null) {
      return;
    }

    try {
      socket.close();
    } catch (IOException exception) {
      // Nothing actionable; closing unblocks the read loop regardless.
    }
  }

  public int generation() {
    return this.generation;
  }
}
