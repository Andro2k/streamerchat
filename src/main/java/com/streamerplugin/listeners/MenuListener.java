package com.streamerplugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.streamerplugin.StreamerChatPlugin;
import com.streamerplugin.gui.StreamKickMenu;
import com.streamerplugin.kick.KickUserSession;

public class MenuListener implements Listener {

    private final StreamerChatPlugin plugin;

    public MenuListener(StreamerChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null)
            return;

        if (StreamKickMenu.MENU_TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player))
                return;
            Player player = (Player) event.getWhoClicked();

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 27)
                return;

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

            KickUserSession session = plugin.getSessionManager().getSession(player.getUniqueId());

            switch (slot) {
                case 13 -> {
                    if (session != null) {
                        boolean enabled = session.toggleChatEnabled();
                        player.sendMessage(ChatColor.YELLOW + "[StreamerChat] Chat de Kick en juego: "
                                + (enabled ? ChatColor.GREEN + "ACTIVADO" : ChatColor.RED + "DESACTIVADO"));
                        StreamKickMenu.openMenu(player, plugin);
                    } else {
                        player.sendMessage(ChatColor.RED + "[StreamerChat] Primero debes vincular tu cuenta de Kick.");
                    }
                }
                case 15 -> {
                    player.closeInventory();
                    plugin.getAuthManager().startAuthFlow(player);
                }
                case 22 -> {
                    if (session != null) {
                        plugin.getSessionManager().unregisterSession(player.getUniqueId());
                        plugin.getAuthManager().removeUserSession(player.getUniqueId());
                        player.sendMessage(
                                ChatColor.YELLOW + "[StreamerChat] Cuenta de Kick desvinculada exitosamente.");
                        StreamKickMenu.openMenu(player, plugin);
                    } else {
                        player.sendMessage(ChatColor.RED + "[StreamerChat] No tienes ninguna cuenta vinculada.");
                    }
                }
            }
        }
    }
}
