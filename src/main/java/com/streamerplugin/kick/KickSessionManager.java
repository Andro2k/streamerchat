package com.streamerplugin.kick;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.streamerplugin.StreamerChatPlugin;

public class KickSessionManager {

    private final StreamerChatPlugin plugin;
    private final Map<UUID, KickUserSession> activeSessions = new ConcurrentHashMap<>();

    public KickSessionManager(StreamerChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerSession(KickUserSession session) {
        unregisterSession(session.getPlayerUuid());

        activeSessions.put(session.getPlayerUuid(), session);

        KickWebSocketClient wsClient = new KickWebSocketClient(
                session.getChatroomId(),
                session.getKickUsername(),
                (sender, message, badgeType) -> handleIncomingKickMessage(session.getPlayerUuid(), sender, message, badgeType),
                plugin.getLogger()
        );

        session.setWsClient(wsClient);
        wsClient.connect();
    }

    private void handleIncomingKickMessage(UUID playerUuid, String sender, String message, String badgeType) {
        KickUserSession session = activeSessions.get(playerUuid);
        if (session == null || !session.isChatEnabled()) return;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;

        String rawFormat = plugin.getConfig().getString("chat.format");
        if (rawFormat == null) {
            rawFormat = "&a[Kick] {badge} &b{user}&7: &f{message}";
        }

        String streamerName = session.getKickUsername() != null ? session.getKickUsername() : "";
        String safeSender = sender != null ? sender : "Anónimo";
        String cleanMessage = filterEmotes(message != null ? message : "");

        if (cleanMessage.isEmpty()) return;

        String rawBadge = plugin.getConfig().getString("badges." + (badgeType != null ? badgeType : "default"));
        if (rawBadge == null) {
            rawBadge = plugin.getConfig().getString("badges.default", "&7[VIEWER]");
        }

        String formattedMessage = rawFormat
                .replace("{streamer}", streamerName)
                .replace("{badge}", rawBadge)
                .replace("{user}", safeSender)
                .replace("{message}", cleanMessage);

        String colorizedMessage = ChatColor.translateAlternateColorCodes('&', formattedMessage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(colorizedMessage);
            }
        });
    }

    private String filterEmotes(String message) {
        if (message == null) return "";
        String filtered = message.replaceAll("\\[emote:\\d+:[^\\]]+\\]", "").trim();
        return filtered.replaceAll(" +", " ");
    }

    public void unregisterSession(UUID playerUuid) {
        KickUserSession session = activeSessions.remove(playerUuid);
        if (session != null && session.getWsClient() != null) {
            session.getWsClient().disconnect();
        }
    }

    public KickUserSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public boolean hasSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public void onPlayerJoin(Player player) {
        KickUserSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            if (session.getWsClient() == null || !session.getWsClient().isConnected()) {
                registerSession(session);
            }
            player.sendMessage(ChatColor.GREEN + "[StreamerChat] Reconectado al chat de Kick para " + session.getKickUsername());
        }
    }

    public void onPlayerQuit(Player player) {
        KickUserSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.getWsClient() != null) {
            session.getWsClient().disconnect();
        }
    }

    public void shutdown() {
        for (KickUserSession session : activeSessions.values()) {
            if (session.getWsClient() != null) {
                session.getWsClient().disconnect();
            }
        }
        activeSessions.clear();
    }
}
