package com.example.enchantpurge.core;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Detects whether an enchantment is a curse type using Paper's Tag API.
 * <p>
 * Uses {@code EnchantmentTagKeys.CURSE} tag from the enchantment registry.
 * Caches the set of cursed enchantments at construction time for performance.
 * Falls back to {@link Enchantment#isCursed()} per-enchantment if the Tag API
 * is unavailable — without using the deprecated {@code Registry.ENCHANTMENT}.
 */
public class CurseDetector {

    private final Set<Enchantment> cursedEnchantments;
    private final Predicate<Enchantment> curseChecker;
    private boolean tagApiAvailable;

    /**
     * Construct the CurseDetector and cache cursed enchantments.
     *
     * @param plugin the plugin instance (for logging)
     */
    public CurseDetector(JavaPlugin plugin) {
        this.cursedEnchantments = new HashSet<>();
        this.curseChecker = null; // determined after loading
        this.tagApiAvailable = false;
        loadCursedEnchantments(plugin.getLogger());
    }

    /**
     * Construct a CurseDetector with a pre-populated curse set and explicit checker.
     * Package-private for testing — bypasses Paper's Tag API entirely.
     *
     * @param cursedEnchantments pre-populated set of curse enchantments
     * @param curseChecker       predicate to use for isCurse(); if null, uses set membership
     */
    CurseDetector(Set<Enchantment> cursedEnchantments, Predicate<Enchantment> curseChecker) {
        this.cursedEnchantments = cursedEnchantments != null
                ? new HashSet<>(cursedEnchantments) : new HashSet<>();
        this.curseChecker = curseChecker;
        this.tagApiAvailable = curseChecker != null;
    }

    /**
     * Load cursed enchantments from the Paper Tag API into cache.
     * On failure, leaves the cache empty and relies on per-enchantment
     * {@link Enchantment#isCursed()} fallback in {@link #isCurse(Enchantment)}.
     */
    private void loadCursedEnchantments(Logger logger) {
        try {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            var curseTag = registry.getTag(EnchantmentTagKeys.CURSE);
            if (curseTag != null) {
                cursedEnchantments.addAll(curseTag.resolve(registry));
                tagApiAvailable = true;
                logger.info("Loaded " + cursedEnchantments.size()
                        + " cursed enchantments from EnchantmentTagKeys.CURSE");
            }
        } catch (Exception e) {
            logger.warning("Failed to load curse tag from Paper API: " + e.getMessage()
                    + ". Will fall back to Enchantment#isCursed() per enchantment.");
            // Cache remains empty; isCurse() will use fallback per enchantment
        }
    }

    /**
     * Check if the given enchantment is a curse enchantment.
     * Uses the cached set populated from Paper's Tag API when available.
     * Falls back to {@link Enchantment#isCursed()} per enchantment if the
     * Tag API is unavailable.
     *
     * @param enchantment the enchantment to check
     * @return true if it is a curse
     */
    public boolean isCurse(Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        // Use injected predicate when available (test mode)
        if (curseChecker != null) {
            return curseChecker.test(enchantment);
        }
        // Use cached Tag API result when available
        if (tagApiAvailable) {
            return cursedEnchantments.contains(enchantment);
        }
        // Fallback: per-enchantment isCursed() call (no Registry.ENCHANTMENT enumeration)
        try {
            return enchantment.isCursed();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Filter a set of enchantments, separating normal from curse types.
     * Returns a NEW set containing only non-curse enchantments.
     * Curse enchantments are added to the provided {@code curses} output set.
     *
     * @param enchantments full set of enchantments (as keys from a Map)
     * @param curses       output set — curse enchantments are added here
     * @return a new set containing only non-curse enchantments
     */
    public Set<Enchantment> filterCurses(Set<Enchantment> enchantments, Set<Enchantment> curses) {
        Set<Enchantment> normal = new HashSet<>();
        for (Enchantment enchantment : enchantments) {
            if (isCurse(enchantment)) {
                curses.add(enchantment);
            } else {
                normal.add(enchantment);
            }
        }
        return normal;
    }

    /**
     * Check whether any of the given enchantments is a curse.
     *
     * @param enchantments the enchantments to check
     * @return true if at least one is a curse
     */
    public boolean containsAnyCurse(Iterable<Enchantment> enchantments) {
        for (Enchantment enchantment : enchantments) {
            if (isCurse(enchantment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count how many of the given enchantments are curses.
     *
     * @param enchantments the enchantments to count
     * @return the number of curse enchantments
     */
    public int countCurses(Iterable<Enchantment> enchantments) {
        int count = 0;
        for (Enchantment enchantment : enchantments) {
            if (isCurse(enchantment)) {
                count++;
            }
        }
        return count;
    }
}
