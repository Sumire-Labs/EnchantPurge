package com.example.enchantpurge.config;

import com.example.enchantpurge.EnchantPurgePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.logging.Level;

/**
 * Manages plugin configuration from config.yml.
 * <p>
 * Loads and exposes config values with defaults.
 * Handles corrupt or missing config gracefully by falling back to defaults.
 */
public class ConfigManager {

    private final EnchantPurgePlugin plugin;
    private FileConfiguration config;

    public ConfigManager(EnchantPurgePlugin plugin) {
        this.plugin = plugin;
    }

    /** Load (or reload) configuration from disk. */
    public void load() {
        try {
            plugin.reloadConfig();
            this.config = plugin.getConfig();
            // Ensure defaults are merged for any missing keys
            this.config.options().copyDefaults(true);
            plugin.saveDefaultConfig();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load config.yml: " + e.getMessage()
                            + ". Using default values.", e);
            // Try to recover with default config
            try {
                plugin.saveDefaultConfig();
                plugin.reloadConfig();
                this.config = plugin.getConfig();
                this.config.options().copyDefaults(true);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not recover config. Plugin may not function correctly.", ex);
                // Keep null config — accessors will return their fallback defaults
                this.config = null;
            }
        }
    }

    // ── Accessors ────────────────────────────────────────────
    // All accessors safely handle config == null (corrupt config → use defaults)

    public String getLanguage() {
        return config != null ? config.getString("language", "ja_JP") : "ja_JP";
    }

    public String getRemovalMode() {
        return config != null ? config.getString("removal_mode", "book") : "book";
    }

    public int getExpConversionMultiplier() {
        return config != null ? config.getInt("exp_conversion.multiplier", 30) : 30;
    }

    // ── Individual cost ──────────────────────────────────────

    public int getIndividualExpLevel() {
        return config != null ? config.getInt("cost.individual.exp_level", 3) : 3;
    }

    public double getIndividualMoney() {
        return config != null ? config.getDouble("cost.individual.money", 100.0) : 100.0;
    }

    public List<?> getIndividualItemCosts() {
        return config != null ? config.getList("cost.individual.items", List.of()) : List.of();
    }

    // ── Bulk cost ────────────────────────────────────────────

    public int getBulkExpLevel() {
        return config != null ? config.getInt("cost.bulk.exp_level", 10) : 10;
    }

    public double getBulkMoney() {
        return config != null ? config.getDouble("cost.bulk.money", 500.0) : 500.0;
    }

    public List<?> getBulkItemCosts() {
        return config != null ? config.getList("cost.bulk.items", List.of()) : List.of();
    }

    // ── Target ───────────────────────────────────────────────

    public String getTargetMode() {
        return config != null ? config.getString("target.mode", "blacklist") : "blacklist";
    }

    public List<String> getWhitelist() {
        return config != null ? config.getStringList("target.whitelist") : List.of();
    }

    public List<String> getBlacklist() {
        return config != null ? config.getStringList("target.blacklist") : List.of();
    }

    public boolean isIncludeEnchantedBooks() {
        return config != null && config.getBoolean("target.include_enchanted_books", false);
    }

    // ── Curses ───────────────────────────────────────────────

    public boolean isCursesRemovable() {
        return config == null || config.getBoolean("curses.removable", true);
    }

    public double getCursesExtraCostMultiplier() {
        return config != null ? config.getDouble("curses.extra_cost_multiplier", 2.0) : 2.0;
    }

    // ── GUI ──────────────────────────────────────────────────

    public String getGuiTitle() {
        return config != null ? config.getString("gui.title", "<gold>エンチャント剥がし</gold>")
                : "<gold>エンチャント剥がし</gold>";
    }

    public int getBulkButtonSlot() {
        return config != null ? config.getInt("gui.bulk_button_slot", 49) : 49;
    }

    // ── Logging ──────────────────────────────────────────────

    public boolean isConsoleLogging() {
        return config == null || config.getBoolean("logging.console", true);
    }

    public boolean isFileLogging() {
        return config == null || config.getBoolean("logging.file", true);
    }

    public String getLogFormat() {
        return config != null ? config.getString("logging.format",
                "[{time}] {player} removed {enchant} Lv.{level} from {item} (cost: {cost})")
                : "[{time}] {player} removed {enchant} Lv.{level} from {item} (cost: {cost})";
    }
}
