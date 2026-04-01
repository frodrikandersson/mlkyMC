package com.mlkymc.twitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import com.mlkymc.config.MlkyConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StreamerManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STREAMER_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final Path dataFile;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    // mcName (lowercase) -> twitchName
    private Map<String, String> streamers = new LinkedHashMap<>();
    // twitchName (lowercase) -> StreamInfo (currently live)
    private final Map<String, StreamInfo> liveStreams = new HashMap<>();
    // Track previously live to detect new streams
    private final Set<String> previouslyLive = new HashSet<>();

    private MinecraftServer server;
    private volatile boolean running = false;

    public StreamerManager(Path configDir) {
        this.dataFile = configDir.resolve("streamers.json");
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mlkymc-streams");
            t.setDaemon(true);
            return t;
        });
        load();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void start() {
        if (running) return;
        running = true;

        // Refresh token on startup, then every 24 hours
        refreshAccessToken();
        scheduler.scheduleAtFixedRate(this::refreshAccessToken, 24, 24, TimeUnit.HOURS);

        int intervalSeconds = MlkyConfig.getStreamerPollInterval();
        scheduler.scheduleAtFixedRate(this::pollStreams, 10, intervalSeconds, TimeUnit.SECONDS);
        MlkyMC.LOGGER.info("Stream status polling started ({} second interval, {} streamers registered)",
                intervalSeconds, streamers.size());
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
    }

    public void registerStreamer(String mcName, String twitchName) {
        streamers.put(mcName.toLowerCase(), twitchName.toLowerCase());
        save();
        refreshPlayerName(mcName);
    }

    public void unregisterStreamer(String mcName) {
        String removed = streamers.remove(mcName.toLowerCase());
        if (removed != null) {
            liveStreams.remove(removed);
            previouslyLive.remove(removed);
        }
        save();
        refreshPlayerName(mcName);
    }

    private void refreshPlayerName(String mcName) {
        if (server == null) return;
        server.execute(() -> {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getName().getString().equalsIgnoreCase(mcName)) {
                    player.refreshDisplayName();
                    player.refreshTabListName();
                    updatePlayerTeam(player);
                    break;
                }
            }
        });
    }

    /**
     * Update a player's scoreboard team to show stream status prefix above their head.
     * Scoreboard teams are the only server-side way to modify the 3D nameplate.
     */
    public void updatePlayerTeam(net.minecraft.server.level.ServerPlayer player) {
        var scoreboard = server.getScoreboard();
        String mcName = player.getName().getString();
        String teamName = "mlkymc_" + mcName.toLowerCase();
        // Team names limited to 16 chars
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        var team = scoreboard.getPlayerTeam(teamName);

        if (!isRegistered(mcName)) {
            // Not a streamer - remove team if it exists
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
            return;
        }

        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        boolean isLive;
        synchronized (liveStreams) {
            isLive = liveStreams.values().stream()
                    .anyMatch(info -> info.mcName().equalsIgnoreCase(mcName));
        }

        if (isLive) {
            team.setPlayerPrefix(Component.literal("\u25CF ").withColor(0x55FF55)); // Green circle
        } else {
            team.setPlayerPrefix(Component.literal("\u25CF ").withColor(0xFF5555)); // Red circle
        }

        // Add the player to the team if not already there
        if (!team.getPlayers().contains(mcName)) {
            scoreboard.addPlayerToTeam(mcName, team);
        }
    }

    public boolean isRegistered(String mcName) {
        return streamers.containsKey(mcName.toLowerCase());
    }

    public Map<String, String> getStreamers() {
        return Collections.unmodifiableMap(streamers);
    }

    public List<StreamInfo> getLiveStreams() {
        synchronized (liveStreams) {
            return new ArrayList<>(liveStreams.values());
        }
    }

    private void refreshAccessToken() {
        String clientId = MlkyConfig.getTwitchClientId();
        String clientSecret = MlkyConfig.getTwitchClientSecret();
        if (clientId.isEmpty() || clientSecret.isEmpty()) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://id.twitch.tv/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + clientId +
                            "&client_secret=" + clientSecret +
                            "&grant_type=client_credentials"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String newToken = json.get("access_token").getAsString();
                MlkyConfig.setTwitchAccessToken(newToken);
                MlkyMC.LOGGER.info("Twitch access token refreshed successfully");
            } else {
                MlkyMC.LOGGER.warn("Failed to refresh Twitch token: {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            MlkyMC.LOGGER.warn("Failed to refresh Twitch token: {}", e.getMessage());
        }
    }

    private void pollStreams() {
        if (streamers.isEmpty()) return;

        String clientId = MlkyConfig.getTwitchClientId();
        String accessToken = MlkyConfig.getTwitchAccessToken();
        if (clientId.isEmpty() || accessToken.isEmpty()) return;

        try {
            // Build query: ?user_login=name1&user_login=name2...
            StringBuilder url = new StringBuilder("https://api.twitch.tv/helix/streams?");
            boolean first = true;
            for (String twitchName : streamers.values()) {
                if (!first) url.append("&");
                url.append("user_login=").append(twitchName);
                first = false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Client-Id", clientId)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                MlkyMC.LOGGER.warn("Twitch streams API returned {}", response.statusCode());
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");

            Set<String> nowLive = new HashSet<>();
            Map<String, StreamInfo> newLiveStreams = new HashMap<>();

            for (int i = 0; i < data.size(); i++) {
                JsonObject stream = data.get(i).getAsJsonObject();
                String twitchName = stream.get("user_login").getAsString().toLowerCase();
                String displayName = stream.get("user_name").getAsString();
                String title = stream.get("title").getAsString();
                String gameName = stream.has("game_name") ? stream.get("game_name").getAsString() : "";
                int viewerCount = stream.get("viewer_count").getAsInt();

                // Find ALL MC names linked to this Twitch name
                nowLive.add(twitchName);
                for (Map.Entry<String, String> entry : streamers.entrySet()) {
                    if (entry.getValue().equals(twitchName)) {
                        String mcName = entry.getKey();
                        // Store per MC name so each linked player gets the live indicator
                        newLiveStreams.put(twitchName + ":" + mcName,
                                new StreamInfo(mcName, displayName, twitchName, title, gameName, viewerCount));
                    }
                }
            }

            // Stream status shown via TAB list and name icons only — no chat announcement

            synchronized (liveStreams) {
                liveStreams.clear();
                liveStreams.putAll(newLiveStreams);
            }

            previouslyLive.clear();
            previouslyLive.addAll(nowLive);

            // Refresh display names and scoreboard teams so the stream icons update
            if (server != null) {
                server.execute(() -> {
                    for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.refreshDisplayName();
                        player.refreshTabListName();
                        if (isRegistered(player.getName().getString())) {
                            updatePlayerTeam(player);
                        }
                    }
                });
            }

        } catch (IOException | InterruptedException e) {
            MlkyMC.LOGGER.warn("Failed to poll Twitch streams: {}", e.getMessage());
        }
    }

    public Component buildStreamListMessage() {
        List<StreamInfo> live = getLiveStreams();

        if (live.isEmpty()) {
            return Component.literal("No content creators are currently streaming.").withColor(0xAAAAAA);
        }

        MutableComponent msg = Component.literal("--- Live Streams ---\n").withStyle(s -> s.withColor(0xFF0000).withBold(true));

        for (StreamInfo info : live) {
            String twitchUrl = "https://twitch.tv/" + info.twitchName;

            msg.append(Component.literal("\n"))
                    .append(Component.literal(info.displayName).withStyle(s -> s.withColor(0xFFFFFF).withBold(true)))
                    .append(Component.literal(" - " + info.viewerCount + " viewers").withColor(0xAAAAAA))
                    .append(Component.literal(" [Watch]").withStyle(Style.EMPTY
                            .withColor(0x9146FF)
                            .withBold(true)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(twitchUrl)))));

            if (!info.title.isEmpty()) {
                msg.append(Component.literal("\n  " + info.title).withColor(0xDDDDDD));
            }
            if (!info.gameName.isEmpty()) {
                msg.append(Component.literal(" [" + info.gameName + "]").withColor(0x888888));
            }
        }

        return msg;
    }

    private void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                Map<String, String> loaded = GSON.fromJson(reader, STREAMER_MAP_TYPE);
                if (loaded != null) {
                    streamers = new LinkedHashMap<>(loaded);
                }
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load streamers.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(streamers, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save streamers.json", e);
        }
    }

    public record StreamInfo(String mcName, String displayName, String twitchName, String title, String gameName, int viewerCount) {}
}
