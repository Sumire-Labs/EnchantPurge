package com.example.enchantpurge.core;

import com.example.enchantpurge.EnchantPurgePlugin;
import com.example.enchantpurge.config.ConfigManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calculates the total cost for an enchantment removal operation.
 * <p>
 * Aggregates experience, money, and item costs from config.
 * Applies curse multiplier when curse enchantments are involved.
 * Returns zero cost if the player has the {@code disenchant.bypass.cost} permission.
 */
public class CostCalculator {

    private final ConfigManager configManager;

    /**
     * Construct a CostCalculator from the plugin instance.
     */
    public CostCalculator(EnchantPurgePlugin plugin) {
        this(plugin.getConfigManager());
    }

    /**
     * Construct a CostCalculator with a ConfigManager directly.
     * Useful for testing without requiring a full plugin instance.
     */
    public CostCalculator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Calculate cost for removing a single enchantment.
     *
     * @param player       the player (checked for bypass permission)
     * @param isCurse      whether the enchantment is a curse
     * @return a CostResult describing exp/money/item requirements
     */
    public CostResult calculateIndividualCost(Player player, boolean isCurse) {
        boolean bypass = player.hasPermission("disenchant.bypass.cost");
        return calculateIndividualCost(bypass, isCurse);
    }

    /**
     * Calculate cost for removing a single enchantment (test-friendly overload).
     *
     * @param bypass   whether cost bypass is active
     * @param isCurse  whether the enchantment is a curse
     * @return a CostResult describing exp/money/item requirements
     */
    public CostResult calculateIndividualCost(boolean bypass, boolean isCurse) {
        if (bypass) {
            return CostResult.FREE;
        }

        double curseMultiplier = isCurse ? configManager.getCursesExtraCostMultiplier() : 1.0;

        int expLevels = (int) Math.round(configManager.getIndividualExpLevel() * curseMultiplier);
        double money = configManager.getIndividualMoney() * curseMultiplier;
        List<ItemCostEntry> itemCosts = parseItemCosts(
                configManager.getIndividualItemCosts(), curseMultiplier);

        return new CostResult(expLevels, money, itemCosts);
    }

    /**
     * Calculate cost for bulk removal.
     * If any curse is being removed, the multiplier applies to the entire cost.
     *
     * @param player     the player
     * @param curseCount number of curse enchantments being removed
     * @return a CostResult describing exp/money/item requirements
     */
    public CostResult calculateBulkCost(Player player, int curseCount) {
        boolean bypass = player.hasPermission("disenchant.bypass.cost");
        return calculateBulkCost(bypass, curseCount);
    }

    /**
     * Calculate cost for bulk removal (test-friendly overload).
     *
     * @param bypass     whether cost bypass is active
     * @param curseCount number of curse enchantments being removed
     * @return a CostResult describing exp/money/item requirements
     */
    public CostResult calculateBulkCost(boolean bypass, int curseCount) {
        if (bypass) {
            return CostResult.FREE;
        }

        double curseMultiplier = curseCount > 0
                ? configManager.getCursesExtraCostMultiplier() : 1.0;

        int expLevels = (int) Math.round(configManager.getBulkExpLevel() * curseMultiplier);
        double money = configManager.getBulkMoney() * curseMultiplier;
        List<ItemCostEntry> itemCosts = parseItemCosts(
                configManager.getBulkItemCosts(), curseMultiplier);

        return new CostResult(expLevels, money, itemCosts);
    }

    /**
     * Parse item cost entries from config list with multiplier applied.
     * Config format supports both Map<String,Object> and plain entries.
     */
    @SuppressWarnings("unchecked")
    private List<ItemCostEntry> parseItemCosts(List<?> rawList, double multiplier) {
        List<ItemCostEntry> result = new ArrayList<>();
        if (rawList == null || rawList.isEmpty()) {
            return result;
        }

        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> map) {
                String material = null;
                int amount = 1;
                Object matObj = map.get("material");
                if (matObj != null) {
                    material = matObj.toString();
                }
                Object amtObj = map.get("amount");
                if (amtObj instanceof Number num) {
                    amount = num.intValue();
                }
                if (material != null && !material.isBlank()) {
                    amount = (int) Math.round(amount * multiplier);
                    result.add(new ItemCostEntry(material, Math.max(1, amount)));
                }
            }
        }
        return result;
    }

    /**
     * Immutable cost calculation result.
     *
     * @param expLevels experience levels required
     * @param money     Vault money required (0.0 if none)
     * @param itemCosts list of item cost entries
     */
    public record CostResult(int expLevels, double money, List<ItemCostEntry> itemCosts) {
        /** Zero-cost result instance. */
        public static final CostResult FREE = new CostResult(0, 0.0, List.of());

        /** @return true if no cost of any type is required */
        public boolean isFree() {
            return expLevels == 0 && money == 0.0 && itemCosts.isEmpty();
        }

        @Override
        public String toString() {
            return "exp:" + expLevels + " money:" + money + " items:" + itemCosts.size();
        }
    }

    /**
     * Represents a single item cost requirement.
     *
     * @param material the required material (as string from config, e.g. "DIAMOND")
     * @param amount   the quantity required
     */
    public record ItemCostEntry(String material, int amount) {
        @Override
        public String toString() {
            return material + " x" + amount;
        }
    }
}
