package com.example.disenchanter.gui;

import com.example.disenchanter.DisenchanterPlugin;
import com.example.disenchanter.core.CostCalculator;
import com.example.disenchanter.core.CurseDetector;
import com.example.disenchanter.core.EnchantmentRemover;
import com.example.disenchanter.util.MessageFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and manages the disenchantment GUI.
 * <p>
 * Manages per-player sessions ({@link GUISession}) via a concurrent map.
 * Each session tracks the item snapshot, hand slot, enchantment list,
 * and the inventory reference to detect tampering.
 */
public class DisenchantGUI {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<UUID, GUISession> sessions = new ConcurrentHashMap<>();

    private final DisenchanterPlugin plugin;
    private final EnchantmentRemover enchantmentRemover;
    private final CurseDetector curseDetector;

    public DisenchantGUI(DisenchanterPlugin plugin, EnchantmentRemover enchantmentRemover,
                         CurseDetector curseDetector) {
        this.plugin = plugin;
        this.enchantmentRemover = enchantmentRemover;
        this.curseDetector = curseDetector;
    }

    // ── Session management ───────────────────────────────────

    /**
     * Get the session for a player, or null if none.
     */
    public static GUISession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Remove and return the session for a player.
     */
    public static GUISession removeSession(UUID uuid) {
        return sessions.remove(uuid);
    }

    /**
     * Check if a player has an active disenchantment GUI session.
     */
    public static boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ── Open GUI ─────────────────────────────────────────────

    /**
     * Open the disenchantment GUI for the given player and item.
     * Creates an inventory with enchantment icons and a bulk removal button.
     *
     * @param player the player
     * @param item   the ItemStack to process (from main hand)
     */
    public void open(Player player, ItemStack item) {
        Map<Enchantment, Integer> enchantments = EnchantmentRemover.getEnchantments(item);
        if (enchantments.isEmpty()) {
            return; // Should have been validated before calling
        }

        int size = calculateInventorySize(enchantments.size());
        Component title = parseTitle();

        Inventory inv = plugin.getServer().createInventory(
                player, size, title);

        // Build enchantment icons
        List<Enchantment> enchantList = new ArrayList<>(enchantments.keySet());
        int slot = 0;
        for (Enchantment enchantment : enchantList) {
            if (slot >= size) break;
            int level = enchantments.get(enchantment);
            ItemStack icon = createEnchantIcon(enchantment, level, player);
            inv.setItem(slot, icon);
            slot++;
        }

        // Add bulk removal button
        int bulkSlot = plugin.getConfigManager().getBulkButtonSlot();
        if (bulkSlot >= 0 && bulkSlot < size) {
            ItemStack bulkButton = createBulkButton(player, enchantments);
            inv.setItem(bulkSlot, bulkButton);
        }

        // Create session
        GUISession session = new GUISession(
                player.getUniqueId(),
                item.clone(), // snapshot
                player.getInventory().getHeldItemSlot(),
                new LinkedHashMap<>(enchantments),
                new ArrayList<>(enchantList),
                inv
        );
        sessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    /**
     * Update the GUI after a removal operation.
     * If no enchantments remain, closes the GUI.
     * Otherwise rebuilds the inventory with remaining enchantments.
     */
    public void updateGui(Player player) {
        GUISession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        ItemStack currentItem = player.getInventory().getItem(session.handSlot());
        if (currentItem == null || currentItem.getType().isAir()) {
            player.closeInventory();
            return;
        }

        Map<Enchantment, Integer> currentEnchants = EnchantmentRemover.getEnchantments(currentItem);
        if (currentEnchants.isEmpty()) {
            // No enchantments left — close GUI
            player.closeInventory();
            return;
        }

        // Rebuild inventory
        Inventory topInv = player.getOpenInventory().getTopInventory();
        topInv.clear();

        List<Enchantment> enchantList = new ArrayList<>(currentEnchants.keySet());
        int slot = 0;
        for (Enchantment enchantment : enchantList) {
            if (slot >= topInv.getSize()) break;
            int level = currentEnchants.get(enchantment);
            ItemStack icon = createEnchantIcon(enchantment, level, player);
            topInv.setItem(slot, icon);
            slot++;
        }

        int bulkSlot = plugin.getConfigManager().getBulkButtonSlot();
        if (bulkSlot >= 0 && bulkSlot < topInv.getSize()) {
            topInv.setItem(bulkSlot, createBulkButton(player, currentEnchants));
        }

        // Update session enchantment map
        session.enchantments().clear();
        session.enchantments().putAll(currentEnchants);
        session.enchantList().clear();
        session.enchantList().addAll(enchantList);
    }

    // ── Icon creation ────────────────────────────────────────

    private ItemStack createEnchantIcon(Enchantment enchantment, int level, Player player) {
        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        // Display enchantment name + level
        String displayName = getEnchantDisplayName(enchantment);
        boolean isCursed = curseDetector.isCurse(enchantment);
        String colorTag = isCursed ? "<red>" : "<aqua>";
        meta.displayName(MINI_MESSAGE.deserialize(
                colorTag + displayName + " Lv." + level + "</" + (isCursed ? "red" : "aqua") + ">"));

        // Calculate individual cost for this enchantment
        CostCalculator.CostResult cost = plugin.getCostCalculator()
                .calculateIndividualCost(player, isCursed);
        String costString = formatCost(cost);

        // Lore
        List<Component> lore = new ArrayList<>();
        String enchantKey = enchantment.getKey().asString();
        lore.add(MINI_MESSAGE.deserialize("<gray>Key: " + enchantKey + "</gray>"));
        lore.add(MINI_MESSAGE.deserialize("<gray>Level: " + level + "</gray>"));

        if (isCursed) {
            lore.add(MINI_MESSAGE.deserialize("<dark_red>⚠ Curse enchantment</dark_red>"));
        }

        // Add lore from language file with <cost> placeholder resolved and sanitized
        List<String> loreStrings = plugin.getLanguageManager().getMessageList("gui.enchant_lore");
        if (loreStrings.isEmpty()) {
            lore.add(MINI_MESSAGE.deserialize("<gray>Click to remove this enchantment</gray>"));
        } else {
            String sanitizedCost = MessageFormatter.sanitize(costString);
            for (String loreStr : loreStrings) {
                lore.add(MINI_MESSAGE.deserialize(loreStr.replace("<cost>", sanitizedCost)));
            }
        }

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createBulkButton(Player player, Map<Enchantment, Integer> enchantments) {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) return button;

        String name = plugin.getLanguageManager().getMessage("gui.bulk_button_name");
        if (name == null || name.equals("gui.bulk_button_name")) {
            name = "<yellow>Remove All Enchantments</yellow>";
        }
        meta.displayName(MINI_MESSAGE.deserialize(name));

        // Calculate bulk cost based on current enchantments
        int curseCount = curseDetector.countCurses(enchantments.keySet());
        boolean canRemoveCurse = curseCount > 0
                && plugin.getConfigManager().isCursesRemovable();
        int curseCountInRemoval = canRemoveCurse ? curseCount : 0;
        CostCalculator.CostResult cost = plugin.getCostCalculator()
                .calculateBulkCost(player, curseCountInRemoval);
        String costString = formatCost(cost);

        List<Component> lore = new ArrayList<>();
        List<String> loreStrings = plugin.getLanguageManager().getMessageList("gui.bulk_button_lore");
        if (loreStrings.isEmpty()) {
            lore.add(MINI_MESSAGE.deserialize("<gray>Click to remove all removable enchantments</gray>"));
        } else {
            String sanitizedCost = MessageFormatter.sanitize(costString);
            for (String loreStr : loreStrings) {
                lore.add(MINI_MESSAGE.deserialize(loreStr.replace("<cost>", sanitizedCost)));
            }
        }

        meta.lore(lore);
        button.setItemMeta(meta);
        return button;
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Format a CostResult into a human-readable short string for GUI lore.
     * Vault-unavailable money costs are omitted to avoid display/actual mismatch.
     */
    private String formatCost(CostCalculator.CostResult cost) {
        if (cost.isFree()) {
            return "Free";
        }
        StringBuilder sb = new StringBuilder();
        boolean hasVault = plugin.getVaultIntegration().isEnabled();

        if (cost.expLevels() > 0) {
            sb.append(cost.expLevels()).append(" XP");
        }
        if (cost.money() > 0 && hasVault) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(plugin.getVaultIntegration().format(cost.money()));
        }
        if (!cost.itemCosts().isEmpty()) {
            for (CostCalculator.ItemCostEntry entry : cost.itemCosts()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(entry.material()).append(" x").append(entry.amount());
            }
        }
        return sb.isEmpty() ? "Free" : sb.toString();
    }

    private int calculateInventorySize(int enchantmentCount) {
        // Minimum size is 9 (1 row). Round up to next multiple of 9.
        int bulkSlot = plugin.getConfigManager().getBulkButtonSlot();
        // Need enough slots for all enchantments + bulk button
        int needed = Math.max(enchantmentCount + 1, bulkSlot + 1);
        int rows = (needed + 8) / 9;
        rows = Math.min(rows, 6); // Max 6 rows (54 slots)
        return Math.max(9, rows * 9);
    }

    private Component parseTitle() {
        // Config gui.title takes priority over lang gui.title
        String configTitle = plugin.getConfigManager().getGuiTitle();
        if (configTitle != null && !configTitle.isBlank()
                && !configTitle.equals(plugin.getLanguageManager().getMessage("gui.title"))) {
            // Config has a custom title
        }
        // Use config title
        String title = configTitle != null && !configTitle.isBlank()
                ? configTitle
                : plugin.getLanguageManager().getMessage("gui.title");
        return MINI_MESSAGE.deserialize(title);
    }

    private String getEnchantDisplayName(Enchantment enchantment) {
        // Use the enchantment's key name, formatted nicely
        String key = enchantment.getKey().value();
        // Convert "sharpness" → "Sharpness"
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── GUISession ───────────────────────────────────────────

    /**
     * Holds the state of an open disenchantment GUI for a player.
     *
     * @param playerUuid   the player's UUID
     * @param itemSnapshot a clone of the original ItemStack when GUI was opened
     * @param handSlot     the player's held item slot index
     * @param enchantments the enchantment map at open time (mutable for updates)
     * @param enchantList  ordered list of enchantments for slot mapping
     * @param inventory    the created inventory reference
     */
    public record GUISession(
            UUID playerUuid,
            ItemStack itemSnapshot,
            int handSlot,
            Map<Enchantment, Integer> enchantments,
            List<Enchantment> enchantList,
            Inventory inventory
    ) {}
}
