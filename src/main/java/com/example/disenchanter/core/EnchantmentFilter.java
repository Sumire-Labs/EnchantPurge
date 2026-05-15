package com.example.disenchanter.core;

import com.example.disenchanter.DisenchanterPlugin;
import com.example.disenchanter.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Determines whether an item is eligible for disenchantment.
 * <p>
 * Supports whitelist/blacklist modes, enchanted-book filtering,
 * and case-insensitive material matching. Invalid configured materials
 * are logged as warnings and ignored.
 */
public class EnchantmentFilter {

    private final ConfigManager configManager;
    private final Logger logger;
    private final List<Material> effectiveMaterials;

    /**
     * Construct from plugin instance.
     */
    public EnchantmentFilter(DisenchanterPlugin plugin) {
        this(plugin.getConfigManager(), plugin.getLogger());
    }

    /**
     * Construct with ConfigManager and Logger directly.
     * Useful for testing without a full plugin instance.
     */
    public EnchantmentFilter(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        this.effectiveMaterials = new ArrayList<>();
        refreshMaterials();
    }

    /**
     * Re-parse the configured material list from config.
     * Should be called after config reload.
     */
    public void refreshMaterials() {
        effectiveMaterials.clear();
        String mode = configManager.getTargetMode();
        List<String> rawList = "whitelist".equalsIgnoreCase(mode)
                ? configManager.getWhitelist()
                : configManager.getBlacklist();

        for (String name : rawList) {
            if (name == null || name.isBlank()) continue;
            Material mat = Material.matchMaterial(name.trim());
            if (mat == null) {
                logger.warning("Invalid material in target." + mode + ": '" + name
                        + "'. Skipping.");
            } else {
                effectiveMaterials.add(mat);
            }
        }
    }

    /**
     * Check if the given item is allowed to be disenchanted.
     *
     * @param item the ItemStack (non-null, non-air expected)
     * @return true if the item passes the configured filter
     */
    public boolean isAllowed(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        Material type = item.getType();

        // Enchanted book check
        if (type == Material.ENCHANTED_BOOK) {
            if (!configManager.isIncludeEnchantedBooks()) {
                return false;
            }
            // Allow if include_enchanted_books is true and list is empty (no restriction)
            if (effectiveMaterials.isEmpty()) {
                return true;
            }
        }

        String mode = configManager.getTargetMode();
        boolean isWhitelist = "whitelist".equalsIgnoreCase(mode);

        if (effectiveMaterials.isEmpty()) {
            // No materials configured → all items pass (except enchanted book rule above)
            return true;
        }

        boolean materialInList = effectiveMaterials.contains(type);

        if (isWhitelist) {
            return materialInList;
        } else {
            // blacklist mode: allowed if NOT in the list
            return !materialInList;
        }
    }

    /**
     * Get the list of materials currently configured as blacklist/whitelist.
     *
     * @return an unmodifiable view of the parsed material list
     */
    public List<Material> getConfiguredMaterials() {
        return Collections.unmodifiableList(effectiveMaterials);
    }
}
