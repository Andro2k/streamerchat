package com.streamerplugin.kick;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class KickWebSocketClient implements WebSocket.Listener {

    private static final Logger LOGGER = Logger.getLogger(KickWebSocketClient.class.getName());

    @FunctionalInterface
    public interface ChatMessageConsumer {
        void accept(String sender, String message, String badgeType);
    }

    private final String chatroomId;
    private final String streamerName;
    private final ChatMessageConsumer messageHandler;
    private WebSocket webSocket;
    private boolean connected = false;
    private boolean userClosed = false;
    private StringBuilder messageBuffer = new StringBuilder();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final String PUSHER_WS_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=7.3.0&flash=false";

    public KickWebSocketClient(String chatroomId, String streamerName, ChatMessageConsumer messageHandler, Logger logger) {
        this.chatroomId = chatroomId;
        this.streamerName = streamerName;
        this.messageHandler = messageHandler;
    }

    public synchronized void connect() {
        if (connected || userClosed) return;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(PUSHER_WS_URL), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.connected = true;
                    LOGGER.log(Level.INFO, "[StreamerChat] WebSocket conectado exitosamente para el canal Kick: {0}", streamerName);
                    subscribeToChatroom();
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "[StreamerChat] Fallo al conectar WebSocket para {0}: {1}",
                            new Object[]{streamerName, ex.getMessage()});
                    scheduleReconnect();
                    return null;
                });
    }

    private void subscribeToChatroom() {
        if (webSocket == null || !connected) return;

        String subscribeJson = String.format(
                "{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"chatrooms.%s.v2\"}}",
                chatroomId
        );

        webSocket.sendText(subscribeJson, true);
        LOGGER.log(Level.INFO, "[StreamerChat] Suscrito al chatroom Kick ID: {0} ({1})",
                new Object[]{chatroomId, streamerName});
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer = new StringBuilder();
            handleIncomingPayload(fullMessage);
        }
        webSocket.request(1);
        return null;
    }

    private void handleIncomingPayload(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String event = json.has("event") ? json.get("event").getAsString() : "";

            if ("pusher:ping".equalsIgnoreCase(event)) {
                webSocket.sendText("{\"event\":\"pusher:pong\",\"data\":{}}", true);
                return;
            }

            if ("App\\Events\\ChatMessageEvent".equalsIgnoreCase(event)) {
                if (json.has("data")) {
                    String dataStr = json.get("data").getAsString();
                    JsonObject dataJson = JsonParser.parseString(dataStr).getAsJsonObject();

                    String content = dataJson.has("content") ? dataJson.get("content").getAsString() : "";
                    String senderName = "Anónimo";
                    String badgeType = "default";

                    if (dataJson.has("sender")) {
                        JsonObject sender = dataJson.getAsJsonObject("sender");
                        if (sender.has("username")) {
                            senderName = sender.get("username").getAsString();
                        }

                        JsonArray badgesArray = null;
                        if (sender.has("identity") && sender.getAsJsonObject("identity").has("badges")) {
                            badgesArray = sender.getAsJsonObject("identity").getAsJsonArray("badges");
                        } else if (sender.has("badges")) {
                            badgesArray = sender.getAsJsonArray("badges");
                        }

                        if (badgesArray != null && badgesArray.size() > 0) {
                            for (JsonElement badgeElem : badgesArray) {
                                if (badgeElem.isJsonObject()) {
                                    JsonObject badgeObj = badgeElem.getAsJsonObject();
                                    String type = badgeObj.has("type") ? badgeObj.get("type").getAsString().toLowerCase() : "";
                                    if ("broadcaster".equals(type) || "streamer".equals(type)) {
                                        badgeType = "broadcaster";
                                        break;
                                    } else if ("moderator".equals(type) || "mod".equals(type)) {
                                        badgeType = "moderator";
                                    } else if ("vip".equals(type) && !"moderator".equals(badgeType)) {
                                        badgeType = "vip";
                                    } else if ("subscriber".equals(type) || "sub".equals(type)) {
                                        if (!"moderator".equals(badgeType) && !"vip".equals(badgeType)) {
                                            badgeType = "subscriber";
                                        }
                                    } else if ("og".equals(type) && "default".equals(badgeType)) {
                                        badgeType = "og";
                                    }
                                }
                            }
                        }
                    }

                    if (messageHandler != null && !content.isEmpty()) {
                        messageHandler.accept(senderName, content, badgeType);
                    }
                }
            }
        } catch (JsonSyntaxException | IllegalStateException | NullPointerException e) {
            LOGGER.log(Level.FINE, "[StreamerChat] Error procesando payload de WebSocket Kick", e);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOGGER.log(Level.WARNING, "[StreamerChat] WebSocket de Kick cerrado para {0}. Código: {1}, Razón: {2}",
                new Object[]{streamerName, statusCode, reason});
        this.connected = false;
        if (!userClosed) {
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.log(Level.SEVERE, "[StreamerChat] Error en WebSocket para {0}: {1}",
                new Object[]{streamerName, error != null ? error.getMessage() : "Desconocido"});
        this.connected = false;
        if (!userClosed) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (userClosed) return;
        scheduler.schedule(() -> {
            LOGGER.log(Level.INFO, "[StreamerChat] Reintentando conexión WebSocket para {0}...", streamerName);
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    public synchronized void disconnect() {
        this.userClosed = true;
        this.connected = false;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin Shutdown").thenRun(() -> {
                LOGGER.log(Level.INFO, "[StreamerChat] Desconectado WebSocket de Kick para {0}", streamerName);
            });
        }
        scheduler.shutdownNow();
    }

    public boolean isConnected() {
        return connected;
    }

    public String getStreamerName() {
        return streamerName;
    }
}
