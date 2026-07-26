package com.streamerplugin.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.streamerplugin.StreamerChatPlugin;
import com.streamerplugin.auth.AuthManager;
import com.streamerplugin.gui.StreamKickMenu;
import com.streamerplugin.kick.KickSessionManager;
import com.streamerplugin.kick.KickUserSession;

public class StreamKickCommand implements CommandExecutor, TabCompleter {

    private final StreamerChatPlugin plugin;
    private final AuthManager authManager;
    private final KickSessionManager sessionManager;

    private static final List<String> SUBCOMMANDS = Arrays.asList("menu", "auth", "send", "disconnect", "status", "toggle", "reload", "help");

    public StreamKickCommand(StreamerChatPlugin plugin, AuthManager authManager, KickSessionManager sessionManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender == null || !(sender instanceof Player)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Este comando solo puede ser ejecutado por un jugador en el juego.");
            }
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            StreamKickMenu.openMenu(player, plugin);
            return true;
        }

        if ("help".equalsIgnoreCase(args[0])) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "auth" -> authManager.startAuthFlow(player);
            case "send", "msg" -> handleSendMessage(player, args);
            case "disconnect", "unlink" -> {
                if (sessionManager.hasSession(player.getUniqueId())) {
                    sessionManager.unregisterSession(player.getUniqueId());
                    authManager.removeUserSession(player.getUniqueId());
                    player.sendMessage(ChatColor.YELLOW + "[StreamerChat] Tu cuenta de Kick ha sido desvinculada y la sesión cerrada.");
                } else {
                    player.sendMessage(ChatColor.RED + "[StreamerChat] No tienes ninguna cuenta de Kick vinculada.");
                }
            }
            case "status" -> {
                KickUserSession session = sessionManager.getSession(player.getUniqueId());
                player.sendMessage(ChatColor.GOLD + "=== Estado de Kick Chat ===");
                if (session != null) {
                    player.sendMessage(ChatColor.YELLOW + "Cuenta Kick: " + ChatColor.GREEN + session.getKickUsername());
                    player.sendMessage(ChatColor.YELLOW + "Chatroom ID: " + ChatColor.WHITE + session.getChatroomId());
                    boolean wsConnected = session.getWsClient() != null && session.getWsClient().isConnected();
                    player.sendMessage(ChatColor.YELLOW + "WebSocket Conectado: " + (wsConnected ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
                    player.sendMessage(ChatColor.YELLOW + "Chat en juego: " + (session.isChatEnabled() ? ChatColor.GREEN + "Activado" : ChatColor.RED + "Desactivado"));
                } else {
                    player.sendMessage(ChatColor.RED + "Estado: No vinculado. Usa /streamkick auth para vincular tu cuenta.");
                }
            }
            case "toggle" -> {
                KickUserSession currentSession = sessionManager.getSession(player.getUniqueId());
                if (currentSession != null) {
                    boolean enabled = currentSession.toggleChatEnabled();
                    player.sendMessage(ChatColor.YELLOW + "[StreamerChat] Chat de Kick en juego: " + (enabled ? ChatColor.GREEN + "ACTIVADO" : ChatColor.RED + "DESACTIVADO"));
                } else {
                    player.sendMessage(ChatColor.RED + "[StreamerChat] Primero debes vincular tu cuenta de Kick usando /streamkick auth.");
                }
            }
            case "reload" -> {
                if (!player.hasPermission("streamerchat.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
                    return true;
                }
                plugin.reloadPluginConfig();
                player.sendMessage(ChatColor.GREEN + "[StreamerChat] Configuración y credenciales recargadas exitosamente.");
            }
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void handleSendMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso correcto: /streamkick send <mensaje>");
            return;
        }

        KickUserSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || session.getToken() == null) {
            player.sendMessage(ChatColor.RED + "[StreamerChat] Primero debes vincular tu cuenta de Kick usando /streamkick auth.");
            return;
        }

        String messageContent = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        player.sendMessage(ChatColor.YELLOW + "[StreamerChat] Enviando mensaje a Kick...");

        plugin.getApiClient().sendChatMessage(
                session.getToken().getAccessToken(),
                session.getBroadcasterUserId(),
                messageContent
        ).thenAccept(success -> {
            if (success) {
                player.sendMessage(ChatColor.GREEN + "[StreamerChat] Mensaje enviado a Kick: " + ChatColor.WHITE + messageContent);
            } else {
                player.sendMessage(ChatColor.RED + "[StreamerChat] No se pudo enviar el mensaje a Kick. Verifica tus permisos/tokens.");
            }
        });
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Comandos de StreamerChat (Kick) ===");
        player.sendMessage(ChatColor.YELLOW + "/streamkick auth " + ChatColor.WHITE + "- Vincula tu cuenta de Kick mediante OAuth2");
        player.sendMessage(ChatColor.YELLOW + "/streamkick send <mensaje> " + ChatColor.WHITE + "- Envía un mensaje a tu chat de Kick");
        player.sendMessage(ChatColor.YELLOW + "/streamkick disconnect " + ChatColor.WHITE + "- Cierra la sesión y desvincula tu cuenta");
        player.sendMessage(ChatColor.YELLOW + "/streamkick status " + ChatColor.WHITE + "- Muestra el estado de tu conexión con Kick");
        player.sendMessage(ChatColor.YELLOW + "/streamkick toggle " + ChatColor.WHITE + "- Activa o desactiva la visibilidad del chat");
        if (player.hasPermission("streamerchat.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/streamkick reload " + ChatColor.WHITE + "- Recarga la configuración del plugin");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
