package com.mlkymc.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mlkymc.MlkyMC;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwitchWebSocketClient implements WebSocket.Listener {

    private static final String TWITCH_WS_URL = "wss://eventsub.wss.twitch.tv/ws";
    private final TwitchConfig config;
    private final TwitchRedemptionHandler handler;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private WebSocket webSocket;
    private final StringBuilder messageBuffer = new StringBuilder();
    private volatile boolean running = true;

    public TwitchWebSocketClient(TwitchConfig config, TwitchRedemptionHandler handler) {
        this.config = config;
        this.handler = handler;
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mlkymc-twitch");
            t.setDaemon(true);
            return t;
        });
    }

    public void connect() {
        connectTo(TWITCH_WS_URL);
    }

    private void connectTo(String url) {
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    MlkyMC.LOGGER.info("Connected to Twitch EventSub WebSocket");
                })
                .exceptionally(e -> {
                    MlkyMC.LOGGER.error("Failed to connect to Twitch WebSocket", e);
                    scheduleReconnect();
                    return null;
                });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            handleMessage(messageBuffer.toString());
            messageBuffer.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        MlkyMC.LOGGER.info("Twitch WebSocket closed: {} {}", statusCode, reason);
        if (running) scheduleReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        MlkyMC.LOGGER.error("Twitch WebSocket error", error);
        if (running) scheduleReconnect();
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            JsonObject metadata = json.getAsJsonObject("metadata");
            String messageType = metadata.get("message_type").getAsString();

            switch (messageType) {
                case "session_welcome" -> {
                    String sessionId = json.getAsJsonObject("payload")
                            .getAsJsonObject("session").get("id").getAsString();
                    subscribeToRedemptions(sessionId);
                }
                case "notification" -> {
                    JsonObject event = json.getAsJsonObject("payload").getAsJsonObject("event");
                    String redeemerName = event.get("user_name").getAsString();
                    String rewardId = event.getAsJsonObject("reward").get("id").getAsString();
                    if (rewardId.equals(config.getRewardId())) {
                        handler.handleRedemption(redeemerName);
                    }
                }
                case "session_reconnect" -> {
                    String reconnectUrl = json.getAsJsonObject("payload")
                            .getAsJsonObject("session").get("reconnect_url").getAsString();
                    connectTo(reconnectUrl);
                }
            }
        } catch (Exception e) {
            MlkyMC.LOGGER.error("Error processing Twitch message", e);
        }
    }

    private void subscribeToRedemptions(String sessionId) {
        getBroadcasterId().thenAccept(broadcasterId -> {
            if (broadcasterId == null) return;

            JsonObject body = new JsonObject();
            body.addProperty("type", "channel.channel_points_custom_reward_redemption.add");
            body.addProperty("version", "1");

            JsonObject condition = new JsonObject();
            condition.addProperty("broadcaster_user_id", broadcasterId);
            condition.addProperty("reward_id", config.getRewardId());
            body.add("condition", condition);

            JsonObject transport = new JsonObject();
            transport.addProperty("method", "websocket");
            transport.addProperty("session_id", sessionId);
            body.add("transport", transport);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twitch.tv/helix/eventsub/subscriptions"))
                    .header("Authorization", "Bearer " + config.getAccessToken())
                    .header("Client-Id", config.getClientId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        if (resp.statusCode() == 202) {
                            MlkyMC.LOGGER.info("Subscribed to Twitch channel point redemptions");
                        } else {
                            MlkyMC.LOGGER.error("Failed to subscribe: {} {}", resp.statusCode(), resp.body());
                        }
                    });
        });
    }

    private java.util.concurrent.CompletableFuture<String> getBroadcasterId() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twitch.tv/helix/users"))
                .header("Authorization", "Bearer " + config.getAccessToken())
                .header("Client-Id", config.getClientId())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    return json.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
                })
                .exceptionally(e -> {
                    MlkyMC.LOGGER.error("Failed to get broadcaster ID", e);
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (!running) return;
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    public void disconnect() {
        running = false;
        scheduler.shutdown();
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down");
        }
    }
}
