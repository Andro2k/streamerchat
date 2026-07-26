package com.streamerplugin.auth;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.streamerplugin.StreamerChatPlugin;
import com.streamerplugin.kick.KickApiClient;
import com.streamerplugin.kick.KickSessionManager;
import com.streamerplugin.kick.KickUserSession;

public class AuthManager {

    private final StreamerChatPlugin plugin;
    private final KickApiClient apiClient;
    private final KickSessionManager sessionManager;
    private final OAuthLocalServer localServer;
    private final File usersFile;
    private YamlConfiguration usersConfig;

    public AuthManager(StreamerChatPlugin plugin, KickApiClient apiClient, KickSessionManager sessionManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.sessionManager = sessionManager;

        int port = plugin.getConfig().getInt("kick.auth_server_port", 8080);
        this.localServer = new OAuthLocalServer(port, this, plugin.getLogger());
        this.usersFile = new File(plugin.getDataFolder(), "users.yml");
    }

    public void start() {
        localServer.start();
        loadUsersFile();
    }

    public void stop() {
        localServer.stop();
        saveUsersFile();
    }

    public void startAuthFlow(Player player) {
        String clientId = plugin.getCredentialString("kick.client_id", StreamerChatPlugin.DEFAULT_CLIENT_ID);
        String redirectUri = plugin.getConfig().getString("kick.redirect_uri", "http://localhost:8080/auth/callback");
        String scopes = plugin.getConfig().getString("kick.scopes", "user:read chat:write");

        if (clientId == null || clientId.trim().isEmpty() || "YOUR_KICK_CLIENT_ID".equalsIgnoreCase(clientId.trim())) {
            clientId = StreamerChatPlugin.DEFAULT_CLIENT_ID;
        }

        String state = PKCEUtil.generateState();
        String codeVerifier = PKCEUtil.generateCodeVerifier();
        String codeChallenge = PKCEUtil.generateCodeChallenge(codeVerifier);

        localServer.registerPendingState(state, player.getUniqueId(), codeVerifier);

        String authUrl = String.format(
                "https://id.kick.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&code_challenge=%s&code_challenge_method=S256",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(scopes, StandardCharsets.UTF_8),
                URLEncoder.encode(state, StandardCharsets.UTF_8),
                URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
        );

        player.sendMessage(ChatColor.GOLD + "=== Vinculación con Kick Chat ===");
        player.sendMessage(ChatColor.YELLOW + "Haz clic en el siguiente enlace para iniciar sesión en Kick:");
        player.sendMessage(ChatColor.AQUA + authUrl);
        player.sendMessage(ChatColor.GRAY + "(El enlace expirará en 10 minutos)");
    }

    public void handleAuthorizationCode(UUID playerUuid, String code, String codeVerifier) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "[StreamerChat] Procesando token de autenticación con Kick...");
        }

        apiClient.exchangeAuthorizationCode(code, codeVerifier).thenAccept(token -> {
            apiClient.getUserProfile(token.getAccessToken()).thenAccept(userJson -> {
                String username = "desconocido";
                if (userJson != null && userJson.has("data") && userJson.get("data").isJsonArray()) {
                    JsonObject userData = userJson.getAsJsonArray("data").get(0).getAsJsonObject();
                    if (userData.has("name")) {
                        username = userData.get("name").getAsString();
                    } else if (userData.has("username")) {
                        username = userData.get("username").getAsString();
                    }
                }

                final String kickUsername = username;

                apiClient.getChannelInfo(kickUsername).thenAccept(channelJson -> {
                    String chatroomId = null;
                    int broadcasterUserId = 0;
                    if (channelJson != null) {
                        if (channelJson.has("chatroom") && channelJson.getAsJsonObject("chatroom").has("id")) {
                            chatroomId = channelJson.getAsJsonObject("chatroom").get("id").getAsString();
                        } else if (channelJson.has("id")) {
                            chatroomId = channelJson.get("id").getAsString();
                        }

                        if (channelJson.has("user_id")) {
                            broadcasterUserId = channelJson.get("user_id").getAsInt();
                        } else if (channelJson.has("id")) {
                            broadcasterUserId = channelJson.get("id").getAsInt();
                        }
                    }

                    if (chatroomId == null) {
                        chatroomId = kickUsername;
                    }

                    KickUserSession session = new KickUserSession(playerUuid, kickUsername, chatroomId, token);
                    session.setBroadcasterUserId(broadcasterUserId);
                    sessionManager.registerSession(session);
                    saveUserSession(session);

                    if (player != null && player.isOnline()) {
                        player.sendMessage(ChatColor.GREEN + "[StreamerChat] ¡Cuenta de Kick '" + kickUsername + "' vinculada exitosamente!");
                        player.sendMessage(ChatColor.GREEN + "[StreamerChat] Conectado al chat de Kick en tiempo real.");
                    }
                });
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[StreamerChat] Error intercambiando código OAuth2 para " + playerUuid, ex);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED + "[StreamerChat] Error al vincular tu cuenta con Kick. Inténtalo de nuevo.");
            }
            return null;
        });
    }

    public void loadUserSessions() {
        if (!usersFile.exists()) return;
        for (String uuidStr : usersConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String username = usersConfig.getString(uuidStr + ".username");
                String chatroomId = usersConfig.getString(uuidStr + ".chatroom_id");
                int broadcasterUserId = usersConfig.getInt(uuidStr + ".broadcaster_user_id", 0);
                String accessToken = usersConfig.getString(uuidStr + ".access_token");
                String refreshToken = usersConfig.getString(uuidStr + ".refresh_token");
                long expiresAt = usersConfig.getLong(uuidStr + ".expires_at");

                if (username != null && chatroomId != null && accessToken != null) {
                    KickToken token = new KickToken(accessToken, refreshToken, expiresAt);

                    if (token.isExpired() && refreshToken != null && !refreshToken.isEmpty()) {
                        apiClient.refreshToken(refreshToken).thenAccept(newToken -> {
                            KickUserSession session = new KickUserSession(uuid, username, chatroomId, newToken);
                            session.setBroadcasterUserId(broadcasterUserId);
                            sessionManager.registerSession(session);
                            saveUserSession(session);
                        });
                    } else {
                        KickUserSession session = new KickUserSession(uuid, username, chatroomId, token);
                        session.setBroadcasterUserId(broadcasterUserId);
                        sessionManager.registerSession(session);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[StreamerChat] Error cargando sesión guardada para {0}", uuidStr);
            }
        }
    }

    private synchronized void saveUserSession(KickUserSession session) {
        String key = session.getPlayerUuid().toString();
        usersConfig.set(key + ".username", session.getKickUsername());
        usersConfig.set(key + ".chatroom_id", session.getChatroomId());
        usersConfig.set(key + ".broadcaster_user_id", session.getBroadcasterUserId());
        usersConfig.set(key + ".access_token", session.getToken().getAccessToken());
        usersConfig.set(key + ".refresh_token", session.getToken().getRefreshToken());
        usersConfig.set(key + ".expires_at", session.getToken().getExpiresAtMillis());
        saveUsersFile();
    }

    public synchronized void removeUserSession(UUID playerUuid) {
        usersConfig.set(playerUuid.toString(), null);
        saveUsersFile();
    }

    private void loadUsersFile() {
        if (!usersFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                usersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[StreamerChat] No se pudo crear users.yml: {0}", e.getMessage());
            }
        }
        usersConfig = YamlConfiguration.loadConfiguration(usersFile);
    }

    private void saveUsersFile() {
        if (usersConfig != null && usersFile != null) {
            try {
                usersConfig.save(usersFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[StreamerChat] Error guardando users.yml: {0}", e.getMessage());
            }
        }
    }
}
