package com.streamerplugin.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public static Component parse(String input) {
        if (input == null || input.isEmpty())
            return Component.empty();

        if (input.contains("<") && input.contains(">")) {
            return MINI_MESSAGE.deserialize(input);
        }
        return LEGACY_SERIALIZER.deserialize(input);
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty())
            return;
        Component component = parse(message);
        BaseComponent[] components = BungeeComponentSerializer.get().serialize(component);

        if (sender instanceof Player player) {
            player.spigot().sendMessage(components);
        } else {
            sender.spigot().sendMessage(components);
        }
    }
}
