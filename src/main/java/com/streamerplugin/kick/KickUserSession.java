package com.streamerplugin.kick;

import java.util.UUID;

import com.streamerplugin.auth.KickToken;

public class KickUserSession {

    private final UUID playerUuid;
    private String kickUsername;
    private String chatroomId;
    private int broadcasterUserId;
    private KickToken token;
    private KickWebSocketClient wsClient;
    private boolean chatEnabled = true;

    public KickUserSession(UUID playerUuid, String kickUsername, String chatroomId, KickToken token) {
        this.playerUuid = playerUuid;
        this.kickUsername = kickUsername;
        this.chatroomId = chatroomId;
        this.token = token;
    }

    public int getBroadcasterUserId() {
        return broadcasterUserId;
    }

    public void setBroadcasterUserId(int broadcasterUserId) {
        this.broadcasterUserId = broadcasterUserId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getKickUsername() {
        return kickUsername;
    }

    public void setKickUsername(String kickUsername) {
        this.kickUsername = kickUsername;
    }

    public String getChatroomId() {
        return chatroomId;
    }

    public void setChatroomId(String chatroomId) {
        this.chatroomId = chatroomId;
    }

    public KickToken getToken() {
        return token;
    }

    public void setToken(KickToken token) {
        this.token = token;
    }

    public KickWebSocketClient getWsClient() {
        return wsClient;
    }

    public void setWsClient(KickWebSocketClient wsClient) {
        this.wsClient = wsClient;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }

    public boolean toggleChatEnabled() {
        this.chatEnabled = !this.chatEnabled;
        return this.chatEnabled;
    }
}
