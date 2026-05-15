package com.example.enchantpurge.gui;

import com.example.enchantpurge.EnchantPurgePlugin;
import com.example.enchantpurge.core.CostCalculator;
import com.example.enchantpurge.core.EnchantmentRemover;
import com.example.enchantpurge.core.EnchantmentRemover.RemovalResult;
import com.example.enchantpurge.cost.CostHandler;
import com.example.enchantpurge.util.MessageFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * Handles inventory interactions within the disenchantment GUI.
 * <p>
 * Listens for click, drag, and close events. Validates permissions,
 * costs, and item integrity on every click before performing removal.
 * Prevents item theft by cancelling all non-GUI interactions within
 * the disenchantment inventory.
 * <p>
 * All core components are fetched from {@link EnchantPurgePlugin} on each
 * use so that the listener remains correct across configuration reloads.
 */
public class DisenchantGUIListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final EnchantPurgePlugin plugin;

    public DisenchantGUIListener(EnchantPurgePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Click handling ───────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        DisenchantGUI.GUISession session = DisenchantGUI.getSession(player.getUniqueId());
        if (session == null) return;

        // Verify this is our GUI inventory
        if (!topInv.equals(session.inventory())) return;

        // Cancel ALL interaction within our GUI
        event.setCancelled(true);

        // Only process clicks in the top inventory
        if (event.getClickedInventory() != topInv) {
            // Bottom inventory or outside — cancelled only
            return;
        }

        // Block shift-click, number keys, double click, drop, offhand swap
        if (event.getClick().isShiftClick()
                || event.getClick().isKeyboardClick()
                || event.getClick().isRightClick()
                || event.getClick().isCreativeAction()) {
            return;
        }

        int slot = event.getSlot();

        // Check reload guard
        if (plugin.isReloading()) {
            MessageFormatter.send(plugin, player, "messages.operation_blocked_reloading");
            return;
        }

        // Verify item still matches snapshot
        if (!validateItemIntegrity(player, session)) {
            player.closeInventory();
            return;
        }

        int bulkSlot = plugin.getConfigManager().getBulkButtonSlot();
        if (slot == bulkSlot) {
            handleBulkRemoval(player, session);
        } else if (slot >= 0 && slot < session.enchantList().size()) {
            Enchantment enchantment = session.enchantList().get(slot);
            Map<Enchantment, Integer> enchants = session.enchantments();
            int level = enchants.getOrDefault(enchantment, 0);
            handleIndividualRemoval(player, session, enchantment, level);
        }
    }

    // ── Drag handling ────────────────────────────────────────

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        DisenchantGUI.GUISession session = DisenchantGUI.getSession(player.getUniqueId());
        if (session == null) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!topInv.equals(session.inventory())) return;

        // Check if any of the dragged slots are in the top inventory
        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Close handling ───────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        DisenchantGUI.removeSession(player.getUniqueId());
        // Session cleanup is complete — the inventory is closed
    }

    // ── Quit handling ────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up any lingering session
        DisenchantGUI.removeSession(event.getPlayer().getUniqueId());
    }

    // ── Core removal logic ───────────────────────────────────

    private void handleIndividualRemoval(Player player, DisenchantGUI.GUISession session,
                                          Enchantment enchantment, int level) {
        // Permission check
        if (plugin.getCurseDetector().isCurse(enchantment)) {
            if (!player.hasPermission("disenchant.use.curse")
                    || !plugin.getConfigManager().isCursesRemovable()) {
                MessageFormatter.send(plugin, player, "messages.curse_not_removable");
                return;
            }
        }

        if (!player.hasPermission("disenchant.use.individual")) {
            MessageFormatter.send(plugin, player, "messages.no_permission");
            return;
        }

        // --- Re-validation before cost (atomicity) ---

        // Verify item still exists and matches snapshot
        ItemStack currentItem = player.getInventory().getItem(session.handSlot());
        if (currentItem == null || currentItem.getType().isAir()
                || currentItem.getType() != session.itemSnapshot().getType()) {
            player.closeInventory();
            return;
        }

        // Verify item still passes filter
        if (!plugin.getEnchantmentFilter().isAllowed(currentItem)) {
            MessageFormatter.send(plugin, player, "messages.item_not_supported");
            return;
        }

        // Verify target enchantment still exists on item
        Map<Enchantment, Integer> currentEnchants = EnchantmentRemover.getEnchantments(currentItem);
        if (!currentEnchants.containsKey(enchantment)) {
            // Re-sync GUI — enchantment already gone
            plugin.getDisenchantGUI().updateGui(player);
            return;
        }

        // Calculate cost and check affordability (no consumption yet)
        boolean isCurse = plugin.getCurseDetector().isCurse(enchantment);
        CostCalculator.CostResult cost = plugin.getCostCalculator()
                .calculateIndividualCost(player, isCurse);
        CostHandler.AffordResult affordResult = plugin.getCostHandler()
                .canAffordAll(player, cost);

        if (!affordResult.isOk()) {
            sendAffordErrorMessage(player, affordResult, cost);
            return;
        }

        // Consume costs right before removal (all validation already done)
        if (!plugin.getCostHandler().consumeAll(player, cost)) {
            MessageFormatter.send(plugin, player, "messages.insufficient_money");
            return;
        }

        // Remove enchantment
        RemovalResult result = plugin.getEnchantmentRemover()
                .removeSingle(player, currentItem, enchantment, level);

        if (result.hasRemoved()) {
            // Log
            plugin.getDisenchantLogger().log(
                    player.getName(),
                    player.getUniqueId().toString(),
                    enchantment.getKey().asString(),
                    level,
                    currentItem.getType().name(),
                    cost.toString(),
                    "individual",
                    "success"
            );

            // Send success message
            MessageFormatter.send(plugin, player, "messages.remove_success_individual",
                    "enchant", enchantment.getKey().asString(),
                    "level", String.valueOf(level));

            // Update GUI or close
            plugin.getDisenchantGUI().updateGui(player);
        }
    }

    private void handleBulkRemoval(Player player, DisenchantGUI.GUISession session) {
        if (!player.hasPermission("disenchant.use.bulk")) {
            MessageFormatter.send(plugin, player, "messages.no_permission");
            return;
        }

        // --- Re-validation before cost (atomicity) ---

        // Verify item still exists and matches snapshot
        ItemStack currentItem = player.getInventory().getItem(session.handSlot());
        if (currentItem == null || currentItem.getType().isAir()
                || currentItem.getType() != session.itemSnapshot().getType()) {
            player.closeInventory();
            return;
        }

        // Verify item still passes filter
        if (!plugin.getEnchantmentFilter().isAllowed(currentItem)) {
            MessageFormatter.send(plugin, player, "messages.item_not_supported");
            return;
        }

        // Determine enchantments and curses from current item
        Map<Enchantment, Integer> enchants = EnchantmentRemover.getEnchantments(currentItem);
        if (enchants.isEmpty()) {
            player.closeInventory();
            return;
        }

        Set<Enchantment> enchantKeys = enchants.keySet();
        int curseCount = plugin.getCurseDetector().countCurses(enchantKeys);
        int normalCount = enchantKeys.size() - curseCount;

        int effectiveRemovalCount = normalCount;
        boolean canRemoveCurse = player.hasPermission("disenchant.use.curse")
                && plugin.getConfigManager().isCursesRemovable();
        if (canRemoveCurse) {
            effectiveRemovalCount += curseCount;
        }

        if (effectiveRemovalCount == 0) {
            // Only curses, cannot remove
            MessageFormatter.send(plugin, player, "messages.curse_not_removable");
            return;
        }

        // Calculate cost and check affordability (no consumption yet)
        int curseCountInRemoval = canRemoveCurse ? curseCount : 0;
        CostCalculator.CostResult cost = plugin.getCostCalculator()
                .calculateBulkCost(player, curseCountInRemoval);
        CostHandler.AffordResult affordResult = plugin.getCostHandler()
                .canAffordAll(player, cost);

        if (!affordResult.isOk()) {
            sendAffordErrorMessage(player, affordResult, cost);
            return;
        }

        // Consume costs right before removal (all validation already done)
        if (!plugin.getCostHandler().consumeAll(player, cost)) {
            MessageFormatter.send(plugin, player, "messages.insufficient_money");
            return;
        }

        // Remove all eligible enchantments
        RemovalResult result = plugin.getEnchantmentRemover()
                .removeBulk(player, currentItem);

        if (result.hasRemoved()) {
            // Log
            plugin.getDisenchantLogger().log(
                    player.getName(),
                    player.getUniqueId().toString(),
                    "ALL (" + result.removedCount() + " enchants)",
                    0,
                    currentItem.getType().name(),
                    cost.toString(),
                    "bulk",
                    "success"
            );

            // Send success message
            MessageFormatter.send(plugin, player, "messages.remove_success_bulk",
                    "count", String.valueOf(result.removedCount()));

            // Notify about skipped curses
            if (result.skippedCount() > 0) {
                MessageFormatter.send(plugin, player, "messages.skipped_curses",
                        "count", String.valueOf(result.skippedCount()));
            }
        }

        // Close GUI — all enchantments should be gone
        player.closeInventory();
    }

    // ── Validation helpers ───────────────────────────────────

    /**
     * Check that the item in the player's hand still matches the snapshot.
     *
     * @return true if the item is valid (same type and enchantments)
     */
    private boolean validateItemIntegrity(Player player, DisenchantGUI.GUISession session) {
        ItemStack currentItem = player.getInventory().getItem(session.handSlot());
        ItemStack snapshot = session.itemSnapshot();

        if (currentItem == null || currentItem.getType().isAir()) {
            MessageFormatter.send(plugin, player, "messages.no_item_in_hand");
            return false;
        }

        // Simple check: same material and similar enchantment count
        if (currentItem.getType() != snapshot.getType()) {
            MessageFormatter.send(plugin, player, "messages.item_not_supported");
            return false;
        }

        // Could add deeper equality check here in future
        return true;
    }

    private void sendAffordErrorMessage(Player player, CostHandler.AffordResult result,
                                         CostCalculator.CostResult cost) {
        switch (result) {
            case INSUFFICIENT_EXP -> MessageFormatter.send(plugin, player,
                    "messages.insufficient_exp",
                    "required", String.valueOf(cost.expLevels()));
            case INSUFFICIENT_MONEY -> MessageFormatter.send(plugin, player,
                    "messages.insufficient_money",
                    "required", plugin.getVaultIntegration().format(cost.money()));
            case INSUFFICIENT_ITEMS -> {
                StringBuilder itemsDesc = new StringBuilder();
                for (var entry : cost.itemCosts()) {
                    if (!itemsDesc.isEmpty()) itemsDesc.append(", ");
                    itemsDesc.append(entry.material()).append(" x").append(entry.amount());
                }
                MessageFormatter.send(plugin, player,
                        "messages.insufficient_items",
                        "items", itemsDesc.toString());
            }
        }
    }
}