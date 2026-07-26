package com.streamerplugin.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import com.streamerplugin.util.MessageUtil;

public class StreamKickCommand implements CommandExecutor, TabCompleter {

    private final StreamerChatPlugin plugin;
    private final AuthManager authManager;
    private final KickSessionManager sessionManager;

    private static final List<String> SUBCOMMANDS = Arrays.asList("menu", "auth", "code", "send", "disconnect", "status", "toggle", "reload", "help");

    public StreamKickCommand(StreamerChatPlugin plugin, AuthManager authManager, KickSessionManager sessionManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.sessionManager = sessionManager;
    }

    private boolean checkUserPermission(Player player) {
        boolean permissionsEnabled = plugin.getConfig().getBoolean("permissions.enabled", false);
        if (!permissionsEnabled) {
            return true;
        }
        String userPerm = plugin.getConfig().getString("permissions.user_permission", "streamerchat.use");
        String adminPerm = plugin.getConfig().getString("permissions.admin_permission", "streamerchat.admin");
        if (player.hasPermission(userPerm) || player.hasPermission(adminPerm)) {
            return true;
        }
        MessageUtil.sendMessage(player, "<red>No tienes permiso para usar los comandos de StreamerChat. Requieres el permiso '" + userPerm + "'.</red>");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender == null || !(sender instanceof Player)) {
            if (sender != null) {
                MessageUtil.sendMessage(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            }
            return true;
        }

        Player player = (Player) sender;

        if (!checkUserPermission(player)) {
            return true;
        }

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
            case "code" -> {
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "<red>Uso correcto: /streamkick code <código_o_url></red>");
                    return true;
                }
                authManager.handleManualCodeInput(player, args[1]);
            }
            case "send", "msg" -> handleSendMessage(player, args);
            case "disconnect", "unlink" -> {
                if (sessionManager.hasSession(player.getUniqueId())) {
                    sessionManager.unregisterSession(player.getUniqueId());
                    authManager.removeUserSession(player.getUniqueId());
                    MessageUtil.sendMessage(player, "<yellow>[StreamerChat] Tu cuenta de Kick ha sido desvinculada y la sesión cerrada.</yellow>");
                } else {
                    MessageUtil.sendMessage(player, "<red>[StreamerChat] No tienes ninguna cuenta de Kick vinculada.</red>");
                }
            }
            case "status" -> {
                KickUserSession session = sessionManager.getSession(player.getUniqueId());
                MessageUtil.sendMessage(player, "<gold>=== Estado de Kick Chat ===</gold>");
                if (session != null) {
                    MessageUtil.sendMessage(player, "<yellow>Cuenta Kick: <green>" + session.getKickUsername() + "</green></yellow>");
                    MessageUtil.sendMessage(player, "<yellow>Chatroom ID: <white>" + session.getChatroomId() + "</white></yellow>");
                    boolean wsConnected = session.getWsClient() != null && session.getWsClient().isConnected();
                    MessageUtil.sendMessage(player, "<yellow>WebSocket Conectado: " + (wsConnected ? "<green>SÍ</green>" : "<red>NO</red>") + "</yellow>");
                    MessageUtil.sendMessage(player, "<yellow>Chat en juego: " + (session.isChatEnabled() ? "<green>Activado</green>" : "<red>Desactivado</red>") + "</yellow>");
                } else {
                    MessageUtil.sendMessage(player, "<red>Estado: No vinculado. Usa /streamkick auth para vincular tu cuenta.</red>");
                }
            }
            case "toggle" -> {
                KickUserSession currentSession = sessionManager.getSession(player.getUniqueId());
                if (currentSession != null) {
                    boolean enabled = currentSession.toggleChatEnabled();
                    MessageUtil.sendMessage(player, "<yellow>[StreamerChat] Chat de Kick en juego: " + (enabled ? "<green>ACTIVADO</green>" : "<red>DESACTIVADO</red>") + "</yellow>");
                } else {
                    MessageUtil.sendMessage(player, "<red>[StreamerChat] Primero debes vincular tu cuenta de Kick usando /streamkick auth.</red>");
                }
            }
            case "reload" -> {
                if (!player.hasPermission("streamerchat.admin")) {
                    MessageUtil.sendMessage(player, "<red>No tienes permiso para ejecutar este comando.</red>");
                    return true;
                }
                plugin.reloadPluginConfig();
                MessageUtil.sendMessage(player, "<green>[StreamerChat] Configuración y credenciales recargadas exitosamente.</green>");
            }
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void handleSendMessage(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "<red>Uso correcto: /streamkick send <mensaje></red>");
            return;
        }

        KickUserSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || session.getToken() == null) {
            MessageUtil.sendMessage(player, "<red>[StreamerChat] Primero debes vincular tu cuenta de Kick usando /streamkick auth.</red>");
            return;
        }

        String messageContent = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        MessageUtil.sendMessage(player, "<yellow>[StreamerChat] Enviando mensaje a Kick...</yellow>");

        plugin.getApiClient().sendChatMessage(
                session.getToken().getAccessToken(),
                session.getBroadcasterUserId(),
                messageContent
        ).thenAccept(success -> {
            if (success) {
                MessageUtil.sendMessage(player, "<green>[StreamerChat] Mensaje enviado a Kick: <white>" + messageContent + "</white></green>");
            } else {
                MessageUtil.sendMessage(player, "<red>[StreamerChat] No se pudo enviar el mensaje a Kick. Verifica tus permisos/tokens.</red>");
            }
        });
    }

    private void sendHelpMessage(Player player) {
        MessageUtil.sendMessage(player, "<gold>=== Comandos de StreamerChat (Kick) ===</gold>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick auth <white>- Vincula tu cuenta de Kick mediante OAuth2</white></yellow>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick code <código_o_url> <white>- Ingresa manualmente el código OAuth2 si estás en servidor remoto/Docker</white></yellow>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick send <mensaje> <white>- Envía un mensaje a tu chat de Kick</white></yellow>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick disconnect <white>- Cierra la sesión y desvincula tu cuenta</white></yellow>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick status <white>- Muestra el estado de tu conexión con Kick</white></yellow>");
        MessageUtil.sendMessage(player, "<yellow>/streamkick toggle <white>- Activa o desactiva la visibilidad del chat</white></yellow>");
        if (player.hasPermission("streamerchat.admin")) {
            MessageUtil.sendMessage(player, "<yellow>/streamkick reload <white>- Recarga la configuración del plugin</white></yellow>");
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
