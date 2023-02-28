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

package net.labymod.addons.teamspeak.api;

import java.util.List;
import java.util.function.Consumer;
import net.labymod.addons.teamspeak.api.models.Server;
import net.labymod.api.reference.annotation.Referenceable;

@Referenceable
public interface TeamSpeakAPI {

  boolean isConnected();

  void request(String query, int expectedResponses, Consumer<String> answer);

  Server getSelectedServer();

  List<Server> getServers();

  Server getServer(int id);

  int getClientId();

  default void request(String query, Consumer<String> answer) {
    this.request(query, 1, answer);
  }
}
