package com.example.disenchanter.util;

import com.example.disenchanter.DisenchanterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility for formatting and sending MiniMessage-formatted messages.
 * <p>
 * Supports placeholder replacement ({@code <key> → value}) from language files.
 * Placeholder values are sanitized to prevent accidental MiniMessage tag injection:
 * {@code <}, {@code >}, and backslash characters are escaped before
 * being substituted into the message template.
 * <p>
 * All player-facing messages are routed through this class to ensure
 * consistent prefix handling and MiniMessage deserialization.
 */
public final class MessageFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageFormatter() {
        // Utility class
    }

    /**
     * Send a message from the language file to a CommandSender.
     * Prepends the plugin prefix automatically.
     *
     * @param plugin  the plugin instance
     * @param sender  the recipient (player or console)
     * @param path    language file path (e.g. "messages.no_permission")
     */
    public static void send(DisenchanterPlugin plugin, CommandSender sender, String path) {
        String prefix = plugin.getLanguageManager().getPrefix();
        String message = plugin.getLanguageManager().getMessage(path);

        Component prefixComponent = MINI_MESSAGE.deserialize(prefix);
        Component messageComponent = MINI_MESSAGE.deserialize(message);

        sender.sendMessage(Component.empty()
                .append(prefixComponent)
                .append(messageComponent));
    }

    /**
     * Send a message with placeholder replacements.
     * <p>
     * Placeholder values are sanitized (escaping {@code < > \}) before
     * substitution to prevent MiniMessage injection via user-provided values
     * (e.g. enchantment keys from the game registry).
     *
     * @param plugin       the plugin instance
     * @param sender       the recipient (player or console)
     * @param path         language file path
     * @param placeholders key-value pairs for replacement (e.g. "enchant", "Sharpness")
     */
    public static void send(DisenchanterPlugin plugin, CommandSender sender, String path,
                            String... placeholders) {
        String prefix = plugin.getLanguageManager().getPrefix();
        String message = plugin.getLanguageManager().getMessage(path);

        // Replace <key> placeholders with sanitized values
        if (placeholders != null) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                String key = placeholders[i];
                String rawValue = placeholders[i + 1];
                String safeValue = sanitize(rawValue);
                message = message.replace("<" + key + ">", safeValue);
            }
        }

        Component prefixComponent = MINI_MESSAGE.deserialize(prefix);
        Component messageComponent = MINI_MESSAGE.deserialize(message);

        sender.sendMessage(Component.empty()
                .append(prefixComponent)
                .append(messageComponent));
    }

    /**
     * Deserialize a MiniMessage string to a Component.
     * Useful for GUI items and other places where Component is needed directly.
     */
    public static Component deserialize(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }

    /**
     * Sanitize a value to prevent MiniMessage tag injection.
     * Escapes {@code <}, {@code >}, and {@code \} characters so that
     * user-controlled data (enchantment names, material names, etc.)
     * cannot inject formatting tags into MiniMessage templates.
     * <p>
     * This is useful both for placeholder substitution within messages
     * and for sanitizing dynamic values before embedding them into
     * MiniMessage strings (e.g., GUI lore lines).
     *
     * @param value the raw value to sanitize
     * @return a sanitized string safe for MiniMessage context
     */
    public static String sanitize(String value) {
        if (value == null) return "";
        // Escape backslash first to avoid double-escaping
        return value
                .replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}