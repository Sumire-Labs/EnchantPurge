package com.example.enchantpurge.integration;

import com.example.enchantpurge.EnchantPurgePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Integrates with PlaceholderAPI to provide custom placeholders.
 * <p>
 * Exports read-only placeholders for plugin status and configuration.
 * All placeholders are player-agnostic and safe to query with a null player.
 *
 * <h3>Provided placeholders</h3>
 * <ul>
 *   <li>{@code %enchantpurge_version%} — plugin version</li>
 *   <li>{@code %enchantpurge_vault_enabled%} — {@code true} / {@code false}</li>
 *   <li>{@code %enchantpurge_placeholderapi_enabled%} — {@code true} / {@code false}</li>
 *   <li>{@code %enchantpurge_logging_console%} — {@code true} / {@code false}</li>
 *   <li>{@code %enchantpurge_logging_file%} — {@code true} / {@code false}</li>
 *   <li>{@code %enchantpurge_removal_mode%} — config removal mode string</li>
 * </ul>
 */
public class PlaceholderAPIIntegration extends PlaceholderExpansion {

    private final EnchantPurgePlugin plugin;
    private volatile boolean registered = false;

    public PlaceholderAPIIntegration(EnchantPurgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "enchantpurge";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // All placeholders are player-agnostic; player may be null.
        return switch (params) {
            case "version" -> plugin.getDescription().getVersion();
            case "vault_enabled" -> String.valueOf(plugin.getVaultIntegration().isEnabled());
            case "placeholderapi_enabled" -> String.valueOf(
                    Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
            case "logging_console" -> String.valueOf(plugin.getConfigManager().isConsoleLogging());
            case "logging_file" -> String.valueOf(plugin.getConfigManager().isFileLogging());
            case "removal_mode" -> plugin.getConfigManager().getRemovalMode();
            default -> null; // unknown placeholder
        };
    }

    /**
     * Register this expansion with PlaceholderAPI if the plugin is present.
     * This method does NOT override the parent's {@code boolean register()};
     * it is a separate setup hook called from {@code EnchantPurgePlugin#onEnable}.
     *
     * @return true if the expansion was successfully registered
     */
    public boolean tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholder expansion skipped.");
            return false;
        }

        // isRegistered() is final in parent; use our own flag
        if (registered) {
            plugin.getLogger().info("PlaceholderAPI expansion already registered; skipping.");
            return true;
        }

        boolean result = register(); // calls PlaceholderExpansion#register() -> PlaceholderAPI
        if (result) {
            registered = true;
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            plugin.getLogger().warning("PlaceholderAPI expansion registration failed.");
        }
        return result;
    }

    /**
     * @return true if this expansion has been registered via {@link #tryRegister()}
     */
    public boolean isExpansionRegistered() {
        return registered;
    }

    /**
     * Unregister this expansion from PlaceholderAPI.
     * Safe to call even if not registered.
     *
     * @return true if the expansion was unregistered
     */
    public boolean unregisterExpansion() {
        registered = false;
        // Call parent's final unregister()
        return super.unregister();
    }
}