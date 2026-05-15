package com.example.enchantpurge.core;

import com.example.enchantpurge.EnchantPurgePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs the actual enchantment removal from an ItemStack.
 * <p>
 * Handles both regular items and enchanted books.
 * Supports removal modes: vanish, book (enchanted book), exp (experience points).
 * Empty enchanted books are converted to regular BOOKs.
 * Item meta (durability, custom data, display name, lore) is preserved.
 */
public class EnchantmentRemover {

    private final EnchantPurgePlugin plugin;
    private final CurseDetector curseDetector;

    public EnchantmentRemover(EnchantPurgePlugin plugin, CurseDetector curseDetector) {
        this.plugin = plugin;
        this.curseDetector = curseDetector;
    }

    // ── Result types ─────────────────────────────────────────

    /**
     * Result of a removal operation.
     */
    public record RemovalResult(
            int removedCount,
            int skippedCount,
            Map<Enchantment, Integer> removedEnchants,
            Map<Enchantment, Integer> skippedEnchants
    ) {
        public static RemovalResult empty() {
            return new RemovalResult(0, 0, Map.of(), Map.of());
        }

        public boolean hasRemoved() {
            return removedCount > 0;
        }
    }

    /** Defines what happens to removed enchantments. */
    public enum RemovalMode {
        VANISH,
        BOOK,
        EXP
    }

    // ── Utility: get enchantment map ─────────────────────────

    /**
     * Get all enchantments from an ItemStack as a Map.
     * Handles both regular items (ItemMeta#getEnchants) and
     * enchanted books (EnchantmentStorageMeta#getStoredEnchants).
     *
     * @param item the ItemStack
     * @return a mutable map of enchantment → level, or empty map if none
     */
    public static Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Map.of();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Map.of();
        }

        Map<Enchantment, Integer> enchants;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchants = new LinkedHashMap<>(storageMeta.getStoredEnchants());
        } else {
            enchants = new LinkedHashMap<>(meta.getEnchants());
        }
        return enchants;
    }

    /**
     * Check if the item has any enchantments.
     */
    public static boolean hasEnchantments(ItemStack item) {
        return !getEnchantments(item).isEmpty();
    }

    // ── Single removal ───────────────────────────────────────

    /**
     * Remove a single enchantment from the item.
     *
     * @param player      the player (for giving back books/exp)
     * @param item        the ItemStack (modified in-place via setItemMeta)
     * @param enchantment the enchantment to remove
     * @param level       the enchantment level (for result tracking)
     * @return RemovalResult describing what happened
     */
    public RemovalResult removeSingle(Player player, ItemStack item,
                                       Enchantment enchantment, int level) {
        if (item == null || item.getType().isAir() || enchantment == null) {
            return RemovalResult.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return RemovalResult.empty();
        }

        boolean isBook = meta instanceof EnchantmentStorageMeta;
        boolean removed;

        if (isBook) {
            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
            if (!storageMeta.hasStoredEnchant(enchantment)) {
                return RemovalResult.empty();
            }
            storageMeta.removeStoredEnchant(enchantment);
            item.setItemMeta(storageMeta);

            // If book is now empty, convert to regular BOOK
            if (storageMeta.getStoredEnchants().isEmpty()) {
                convertToRegularBook(item);
            }
            removed = true;
        } else {
            if (!meta.hasEnchant(enchantment)) {
                return RemovalResult.empty();
            }
            meta.removeEnchant(enchantment);
            item.setItemMeta(meta);
            removed = true;
        }

        if (!removed) {
            return RemovalResult.empty();
        }

        // Handle removal mode
        RemovalMode mode = parseMode();
        switch (mode) {
            case BOOK -> giveEnchantedBook(player, enchantment, level);
            case EXP -> giveExp(player, level);
            case VANISH -> { /* nothing */ }
        }

        Map<Enchantment, Integer> removedMap = Map.of(enchantment, level);
        return new RemovalResult(1, 0, removedMap, Map.of());
    }

    // ── Bulk removal ─────────────────────────────────────────

    /**
     * Remove all eligible enchantments from the item.
     * Respects curse permissions: if the player lacks {@code disenchant.use.curse},
     * curse enchantments are skipped.
     *
     * @param player the player
     * @param item   the ItemStack (modified in-place)
     * @return RemovalResult with counts of removed and skipped enchantments
     */
    public RemovalResult removeBulk(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return RemovalResult.empty();
        }

        Map<Enchantment, Integer> allEnchants = getEnchantments(item);
        if (allEnchants.isEmpty()) {
            return RemovalResult.empty();
        }

        boolean canRemoveCurse = player.hasPermission("disenchant.use.curse");
        boolean curseRemovableConfig = plugin.getConfigManager().isCursesRemovable();
        boolean canRemoveCurseEffective = canRemoveCurse && curseRemovableConfig;

        Set<Enchantment> enchantKeys = allEnchants.keySet();
        Set<Enchantment> curses = new HashSet<>();
        Set<Enchantment> normals = curseDetector.filterCurses(enchantKeys, curses);

        Map<Enchantment, Integer> removedMap = new LinkedHashMap<>();
        Map<Enchantment, Integer> skippedMap = new LinkedHashMap<>();

        // Collect which to remove
        Set<Enchantment> toRemove = new HashSet<>();
        toRemove.addAll(normals);
        if (canRemoveCurseEffective) {
            toRemove.addAll(curses);
        } else {
            // Skip all curses
            for (Enchantment curse : curses) {
                skippedMap.put(curse, allEnchants.get(curse));
            }
        }

        if (toRemove.isEmpty()) {
            return new RemovalResult(0, skippedMap.size(), Map.of(), skippedMap);
        }

        // Perform removal
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return RemovalResult.empty();
        }

        boolean isBook = meta instanceof EnchantmentStorageMeta;
        EnchantmentStorageMeta storageMeta = isBook ? (EnchantmentStorageMeta) meta : null;

        for (Enchantment enchant : toRemove) {
            int level = allEnchants.getOrDefault(enchant, 0);
            if (level <= 0) continue;

            if (isBook && storageMeta != null) {
                storageMeta.removeStoredEnchant(enchant);
            } else {
                meta.removeEnchant(enchant);
            }
            removedMap.put(enchant, level);
        }

        item.setItemMeta(meta);

        // Convert empty enchanted book to regular BOOK
        if (isBook && storageMeta != null && storageMeta.getStoredEnchants().isEmpty()) {
            convertToRegularBook(item);
        }

        // Handle removal mode for removed enchantments
        RemovalMode mode = parseMode();
        for (Map.Entry<Enchantment, Integer> entry : removedMap.entrySet()) {
            switch (mode) {
                case BOOK -> giveEnchantedBook(player, entry.getKey(), entry.getValue());
                case EXP -> giveExp(player, entry.getValue());
                case VANISH -> { /* nothing */ }
            }
        }

        return new RemovalResult(removedMap.size(), skippedMap.size(), removedMap, skippedMap);
    }

    // ── Helper methods ───────────────────────────────────────

    private RemovalMode parseMode() {
        String modeStr = plugin.getConfigManager().getRemovalMode();
        return switch (modeStr.toLowerCase(java.util.Locale.ROOT)) {
            case "vanish" -> RemovalMode.VANISH;
            case "exp" -> RemovalMode.EXP;
            default -> RemovalMode.BOOK; // book is default
        };
    }

    /**
     * Give the player an enchanted book with the specified enchantment.
     * If the player's inventory is full, drops the book at the player's location.
     */
    private void giveEnchantedBook(Player player, Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta != null) {
            meta.addStoredEnchant(enchantment, level, true);
            book.setItemMeta(meta);
        }

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(book);
        // Drop any items that couldn't fit
        for (ItemStack overflowItem : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
        }
    }

    /**
     * Give the player experience points based on enchantment level.
     * Formula: level × exp_conversion.multiplier
     */
    private void giveExp(Player player, int level) {
        int multiplier = plugin.getConfigManager().getExpConversionMultiplier();
        int points = level * multiplier;
        if (points > 0) {
            player.giveExp(points);
        }
    }

    /**
     * Convert an empty enchanted book to a regular BOOK,
     * preserving safe display meta (name, lore, custom model data).
     * <p>
     * Uses {@code item.setType(Material.BOOK)} and selectively copies
     * only compatible meta fields, avoiding type-incompatible
     * {@code EnchantmentStorageMeta} on a plain BOOK.
     */
    private void convertToRegularBook(ItemStack item) {
        ItemMeta oldMeta = item.getItemMeta();
        if (oldMeta == null) return;

        // Save safe meta fields before type change
        Component displayName = oldMeta.hasDisplayName() ? oldMeta.displayName() : null;
        List<Component> lore = oldMeta.hasLore() ? oldMeta.lore() : null;
        Integer customModelData = oldMeta.hasCustomModelData() ? oldMeta.getCustomModelData() : null;

        // Switch to plain BOOK — this discards EnchantmentStorageMeta
        item.setType(Material.BOOK);
        ItemMeta newMeta = item.getItemMeta();
        if (newMeta == null) return;

        // Restore safe meta fields onto the fresh BOOK ItemMeta
        if (displayName != null) {
            newMeta.displayName(displayName);
        }
        if (lore != null) {
            newMeta.lore(lore);
        }
        if (customModelData != null) {
            newMeta.setCustomModelData(customModelData);
        }
        item.setItemMeta(newMeta);
    }
}
