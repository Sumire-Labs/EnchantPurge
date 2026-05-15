package com.example.disenchanter.cost;

import com.example.disenchanter.core.CostCalculator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles item-based costs.
 * <p>
 * Scans the player's inventory for required items and removes them on consumption.
 * Supports material matching via {@link Material#matchMaterial(String)}.
 * Invalid material names are treated as not present.
 */
public class ItemCost {

    /**
     * Check if the player has the required items in their inventory.
     *
     * @param player    the player
     * @param itemCosts the list of required item costs
     * @return true if all required items are present
     */
    public boolean hasItems(Player player, List<CostCalculator.ItemCostEntry> itemCosts) {
        if (itemCosts == null || itemCosts.isEmpty()) return true;

        for (CostCalculator.ItemCostEntry entry : itemCosts) {
            Material mat = Material.matchMaterial(entry.material());
            if (mat == null) return false;
            if (countItems(player, mat) < entry.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Remove the required items from the player's inventory.
     * Re-validates item availability immediately before consuming
     * to minimize risk of partial consumption.
     * Call only after {@link #hasItems} returns true.
     *
     * @param player    the player
     * @param itemCosts the list of required item costs
     * @return true if all items were successfully consumed
     */
    public boolean consumeItems(Player player, List<CostCalculator.ItemCostEntry> itemCosts) {
        if (itemCosts == null || itemCosts.isEmpty()) return true;

        // Re-validate immediately before consuming to reduce risk of partial consumption
        if (!hasItems(player, itemCosts)) return false;

        for (CostCalculator.ItemCostEntry entry : itemCosts) {
            Material mat = Material.matchMaterial(entry.material());
            if (mat == null) continue;

            int remaining = entry.amount();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != mat) continue;
                int toRemove = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
                if (remaining <= 0) break;
            }
        }
        return true;
    }

    /**
     * Count the total number of items of the given material in the player's inventory.
     */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
