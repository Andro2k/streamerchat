package com.streamerplugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.streamerplugin.kick.KickSessionManager;

public class PlayerListener implements Listener {

    private final KickSessionManager sessionManager;

    public PlayerListener(KickSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sessionManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.onPlayerQuit(event.getPlayer());
    }
}
