package com.example.disenchanter.config;

import com.example.disenchanter.DisenchanterPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Manages multi-language message files (lang/*.yml).
 * <p>
 * Loads language files and provides message retrieval with built-in fallbacks.
 * On load failure, falls back to hardcoded default messages so the plugin
 * remains operational.
 * MiniMessage formatting is handled by {@link com.example.disenchanter.util.MessageFormatter}.
 */
public class LanguageManager {

    private final DisenchanterPlugin plugin;
    private FileConfiguration langConfig;

    public LanguageManager(DisenchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load the language file for the given locale.
     * Falls back to built-in defaults on any failure.
     *
     * @param locale the locale string (e.g. "ja_JP", "en_US")
     */
    public void load(String locale) {
        String fileName = "lang/" + locale + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);

        try {
            // Save default from resources if not present
            if (!langFile.exists()) {
                plugin.saveResource(fileName, false);
            }

            langConfig = YamlConfiguration.loadConfiguration(langFile);

            // Merge with built-in defaults for missing keys
            var defaultStream = plugin.getResource(fileName);
            if (defaultStream != null) {
                var defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                langConfig.setDefaults(defaultConfig);
            }

            plugin.getLogger().info("Loaded language file: " + fileName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language file '" + fileName
                    + "': " + e.getMessage() + ". Using built-in default messages.");
            // Try to load defaults from jar resource only
            try {
                var defaultStream = plugin.getResource(fileName);
                if (defaultStream != null) {
                    langConfig = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
                // Nothing more we can do
            }
            // If still null, accessors will return fallback strings
        }
    }

    /**
     * Get a raw message string from the language file.
     *
     * @param path YAML path (e.g. "messages.no_permission")
     * @return the message string, or the path itself if not found
     */
    public String getMessage(String path) {
        if (langConfig == null) {
            return path;
        }
        try {
            return langConfig.getString(path, path);
        } catch (Exception e) {
            return path;
        }
    }

    /**
     * Get the prefix string.
     */
    public String getPrefix() {
        return getMessage("prefix");
    }

    /**
     * Get a list of strings from the language file.
     *
     * @param path YAML path
     * @return the string list, or an empty list if not found
     */
    public List<String> getMessageList(String path) {
        if (langConfig == null) {
            return List.of();
        }
        try {
            return langConfig.getStringList(path);
        } catch (Exception e) {
            return List.of();
        }
    }
}
