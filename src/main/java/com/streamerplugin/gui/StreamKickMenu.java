package com.streamerplugin.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.streamerplugin.StreamerChatPlugin;
import com.streamerplugin.kick.KickUserSession;

public class StreamKickMenu {

    public static final String MENU_TITLE = ChatColor.translateAlternateColorCodes('&',
            "&8[📺] StreamerChat - Kick Menu");

    public static void openMenu(Player player, StreamerChatPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        KickUserSession session = plugin.getSessionManager().getSession(player.getUniqueId());

        List<String> statusLore = new ArrayList<>();
        if (session != null) {
            statusLore.add(ChatColor.GRAY + "Streamer: " + ChatColor.GREEN + session.getKickUsername());
            statusLore.add(ChatColor.GRAY + "Chatroom ID: " + ChatColor.WHITE + session.getChatroomId());
            boolean connected = session.getWsClient() != null && session.getWsClient().isConnected();
            statusLore.add(ChatColor.GRAY + "WebSocket: "
                    + (connected ? ChatColor.GREEN + "Conectado" : ChatColor.RED + "Desconectado"));
            statusLore.add(ChatColor.GRAY + "Chat en juego: "
                    + (session.isChatEnabled() ? ChatColor.GREEN + "Activado" : ChatColor.RED + "Desactivado"));
        } else {
            statusLore.add(ChatColor.RED + "Estado: No Vinculado");
            statusLore.add(ChatColor.YELLOW + "Usa la brújula para vincular tu cuenta.");
        }
        ItemStack statusItem = createItem(
                session != null ? Material.NETHER_STAR : Material.REDSTONE_TORCH,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Estado de la Cuenta Kick",
                statusLore);
        inv.setItem(11, statusItem);

        boolean chatEnabled = session != null && session.isChatEnabled();
        Material toggleMat = chatEnabled ? Material.LIME_DYE : Material.RED_DYE;
        String toggleTitle = chatEnabled
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "Chat de Kick: ACTIVADO"
                : ChatColor.RED + "" + ChatColor.BOLD + "Chat de Kick: DESACTIVADO";
        List<String> toggleLore = new ArrayList<>();
        toggleLore.add(ChatColor.GRAY + "Haz clic para "
                + (chatEnabled ? ChatColor.RED + "DESACTIVAR" : ChatColor.GREEN + "ACTIVAR") + ChatColor.GRAY
                + " los mensajes de Kick en tu pantalla.");
        inv.setItem(13, createItem(toggleMat, toggleTitle, toggleLore));

        List<String> authLore = new ArrayList<>();
        authLore.add(ChatColor.GRAY + "Haz clic para generar el enlace de");
        authLore.add(ChatColor.GRAY + "autenticación OAuth2 de Kick.");
        inv.setItem(15, createItem(Material.COMPASS, ChatColor.AQUA + "" + ChatColor.BOLD + "Vincular Cuenta OAuth2",
                authLore));

        List<String> unbindLore = new ArrayList<>();
        unbindLore.add(ChatColor.GRAY + "Haz clic para desvincular tu cuenta");
        unbindLore.add(ChatColor.GRAY + "y cerrar la conexión WebSocket.");
        inv.setItem(22, createItem(Material.BARRIER,
                ChatColor.RED + "" + ChatColor.BOLD + "Desvincular / Cerrar Sesión", unbindLore));

        player.openInventory(inv);
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
