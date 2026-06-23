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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.labymod.addons.teamspeak.core.TeamSpeak;
import net.labymod.api.models.OperatingSystem;
import net.labymod.api.util.io.IOUtil;

public class TeamSpeakAuthenticator {

  private static final String CLIENT_QUERY_FILE = "clientquery.ini";
  private static final String CONFIG_DIRECTORY = "config";
  private static final String API_KEY_PREFIX = "api_key=";
  private static final int MAX_SEARCH_DEPTH = 5;

  private final TeamSpeak teamSpeak;
  private final DefaultTeamSpeakAPI teamSpeakAPI;
  private final List<Path> knownDirectories;

  public TeamSpeakAuthenticator(TeamSpeak teamSpeak, DefaultTeamSpeakAPI teamSpeakAPI) {
    this.teamSpeak = teamSpeak;
    this.teamSpeakAPI = teamSpeakAPI;
    this.knownDirectories = new ArrayList<>();

    OperatingSystem platform = OperatingSystem.getPlatform();
    if (platform == OperatingSystem.WINDOWS) {
      this.loadWindowsDirectories();
    } else if (platform == OperatingSystem.MACOS) {
      this.loadMacOsDirectories();
    } else if (platform == OperatingSystem.LINUX) {
      this.loadLinuxDirectories();
    } else {
      teamSpeak.logger().warn("Cannot automatically resolve the API key on {}!", platform);
    }
  }

  public boolean authenticate() {
    boolean debug = this.teamSpeak.configuration().debug().get();

    // Resolve roots fresh on every attempt: the running client is locatable now (we are connected),
    // and its install directory covers portable / non-default installs the static paths miss.
    List<Path> roots = new ArrayList<>(this.knownDirectories);
    this.addRunningClientDirectories(roots);

    List<String> apiKeys = new ArrayList<>();
    for (Path rootDirectory : roots) {
      if (!IOUtil.exists(rootDirectory)) {
        continue;
      }

      List<Path> clientQueryFiles = this.findClientQueryFiles(rootDirectory);
      for (Path clientQueryFile : clientQueryFiles) {
        String apiKey = this.readApiKey(clientQueryFile);
        if (apiKey == null || apiKey.isEmpty()) {
          continue;
        }

        if (debug) {
          this.teamSpeak.logger().info("Found an API key in {}", clientQueryFile);
        }

        if (!apiKeys.contains(apiKey)) {
          apiKeys.add(apiKey);
        }
      }
    }

    if (apiKeys.isEmpty()) {
      if (debug) {
        this.teamSpeak.logger().info(
            "No clientquery.ini with an api_key found. Searched: {}",
            roots
        );
      }

      return false;
    }

    for (String apiKey : apiKeys) {
      this.teamSpeakAPI.authenticate(apiKey);
    }

    return true;
  }

  private List<Path> findClientQueryFiles(Path root) {
    List<Path> found = new ArrayList<>();

    // Fast path: the conventional locations. Avoids deep-walking a full install directory.
    this.addIfPresent(found, root.resolve(CLIENT_QUERY_FILE));
    this.addIfPresent(found, root.resolve(CONFIG_DIRECTORY).resolve(CLIENT_QUERY_FILE));
    if (!found.isEmpty()) {
      return found;
    }

    // Fallback: bounded recursive search for unconventional layouts.
    try {
      Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_SEARCH_DEPTH,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
              if (file.getFileName().toString().equalsIgnoreCase(CLIENT_QUERY_FILE)) {
                found.add(file);
              }

              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException exception) {
      // Directory inaccessible; nothing to resolve here.
    }

    return found;
  }

  private void addIfPresent(List<Path> found, Path file) {
    if (IOUtil.exists(file)) {
      found.add(file);
    }
  }

  private String readApiKey(Path clientQueryFile) {
    try {
      String contents = new String(Files.readAllBytes(clientQueryFile), StandardCharsets.UTF_8);
      String[] lines = contents.split("\n");
      for (String rawLine : lines) {
        String line = rawLine.trim();
        if (line.startsWith(API_KEY_PREFIX)) {
          return line.substring(API_KEY_PREFIX.length()).replace("\r", "").trim();
        }
      }
    } catch (IOException exception) {
      this.teamSpeak.logger().warn("Failed to read {}", clientQueryFile, exception);
    }

    return null;
  }

  private void addRunningClientDirectories(List<Path> roots) {
    try {
      Iterator<ProcessHandle> iterator = ProcessHandle.allProcesses().iterator();
      while (iterator.hasNext()) {
        ProcessHandle process = iterator.next();
        Optional<String> command = process.info().command();
        if (command.isEmpty()) {
          continue;
        }

        String executable = command.get();
        String lowerCase = executable.toLowerCase(Locale.ROOT);
        if (!lowerCase.contains("ts3client") && !lowerCase.contains("teamspeak")) {
          continue;
        }

        Path installDirectory = Paths.get(executable).getParent();
        if (installDirectory != null && !roots.contains(installDirectory)) {
          roots.add(installDirectory);
        }
      }
    } catch (Exception exception) {
      // Process enumeration may be restricted; fall back to the known directories.
    }
  }

  private void loadWindowsDirectories() {
    this.addRootFromEnvironment("AppData", "TS3Client");
    this.addRootFromEnvironment("AppData", "TeamSpeak");
    this.addRootFromEnvironment("LocalAppData", "TS3Client");
    this.addRootFromEnvironment("LocalAppData", "TeamSpeak");

    Path userHome = Paths.get(System.getProperty("user.home"));
    this.addRoot(userHome.resolve("TS3Client"));
    this.addRoot(userHome.resolve(".ts3client"));
  }

  private void loadMacOsDirectories() {
    Path applicationSupport = Paths.get(System.getProperty("user.home"))
        .resolve("Library")
        .resolve("Application Support");

    this.addRoot(applicationSupport.resolve("TeamSpeak 3"));
    this.addRoot(applicationSupport.resolve("TeamSpeak"));
  }

  private void loadLinuxDirectories() {
    Path userHome = Paths.get(System.getProperty("user.home"));
    this.addRoot(userHome.resolve(".ts3client"));
    this.addRoot(userHome.resolve(".local").resolve("share").resolve("TeamSpeak 3"));
    this.addRoot(userHome.resolve(".config").resolve("TeamSpeak"));
  }

  private void addRootFromEnvironment(String environmentVariable, String directoryName) {
    String value = System.getenv(environmentVariable);
    if (value == null || value.isEmpty()) {
      return;
    }

    this.addRoot(Paths.get(value).resolve(directoryName));
  }

  private void addRoot(Path root) {
    if (!this.knownDirectories.contains(root)) {
      this.knownDirectories.add(root);
    }
  }
}
