package com.example.enchantpurge.cost;

import com.example.enchantpurge.EnchantPurgePlugin;
import com.example.enchantpurge.core.CostCalculator;
import org.bukkit.entity.Player;

/**
 * Orchestrates cost validation and consumption across all cost types.
 * <p>
 * Validates all costs before consuming any, ensuring atomicity:
 * if any cost type cannot be paid, nothing is consumed and an error
 * message key is returned.
 */
public class CostHandler {

    private final EnchantPurgePlugin plugin;
    private final ExpCost expCost;
    private final MoneyCost moneyCost;
    private final ItemCost itemCost;

    public CostHandler(EnchantPurgePlugin plugin) {
        this.plugin = plugin;
        this.expCost = new ExpCost();
        this.moneyCost = new MoneyCost(plugin);
        this.itemCost = new ItemCost();
    }

    /**
     * Test-only constructor with injectable cost handlers.
     * Bypasses plugin dependency; plugin field may be null.
     */
    CostHandler(ExpCost expCost, MoneyCost moneyCost, ItemCost itemCost) {
        this.plugin = null;
        this.expCost = expCost;
        this.moneyCost = moneyCost;
        this.itemCost = itemCost;
    }

    /**
     * Result of a cost affordability check.
     */
    public enum AffordResult {
        OK(null),
        INSUFFICIENT_EXP("messages.insufficient_exp"),
        INSUFFICIENT_MONEY("messages.insufficient_money"),
        INSUFFICIENT_ITEMS("messages.insufficient_items");

        private final String messageKey;

        AffordResult(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public boolean isOk() {
            return this == OK;
        }
    }

    /**
     * Check if the player can afford all cost types.
     *
     * @param player the player
     * @param cost   the cost to validate
     * @return OK if affordable, otherwise the first insufficient resource type
     */
    public AffordResult canAffordAll(Player player, CostCalculator.CostResult cost) {
        if (cost.isFree()) {
            return AffordResult.OK;
        }

        // Check exp
        if (cost.expLevels() > 0 && !expCost.hasEnough(player, cost.expLevels())) {
            return AffordResult.INSUFFICIENT_EXP;
        }

        // Check money
        if (cost.money() > 0 && !moneyCost.hasEnough(player, cost.money())) {
            return AffordResult.INSUFFICIENT_MONEY;
        }

        // Check items
        if (!cost.itemCosts().isEmpty() && !itemCost.hasItems(player, cost.itemCosts())) {
            return AffordResult.INSUFFICIENT_ITEMS;
        }

        return AffordResult.OK;
    }

    /**
     * Consume all cost types from the player.
     * Call only after {@link #canAffordAll} returns OK.
     * <p>
     * Consumption order: money → items → exp.
     * Money is consumed first because its transaction may fail;
     * items are re-validated before consumption to reduce partial risk;
     * exp is consumed last because it cannot be rolled back.
     *
     * @param player the player
     * @param cost   the cost to consume
     * @return true if all costs were consumed successfully
     */
    public boolean consumeAll(Player player, CostCalculator.CostResult cost) {
        if (cost.isFree()) {
            return true;
        }

        // Money first — may fail; nothing consumed before it
        if (cost.money() > 0) {
            if (!moneyCost.deduct(player, cost.money())) {
                return false;
            }
        }

        // Items — re-validated internally; exp not yet consumed if this fails
        if (!cost.itemCosts().isEmpty()) {
            if (!itemCost.consumeItems(player, cost.itemCosts())) {
                return false;
            }
        }

        // Exp last — always succeeds if canAffordAll passed (no rollback needed)
        if (cost.expLevels() > 0) {
            expCost.deduct(player, cost.expLevels());
        }

        return true;
    }

    /**
     * Convenience: check and consume in one operation.
     * <p>
     * If money withdrawal fails despite the {@code has()} check passing
     * (rare, but possible with async economy plugins), returns
     * {@link AffordResult#INSUFFICIENT_MONEY} and no costs are consumed.
     *
     * @param player the player
     * @param cost   the cost
     * @return OK and consumed if affordable, otherwise error without consuming anything
     */
    public AffordResult checkAndConsume(Player player, CostCalculator.CostResult cost) {
        AffordResult result = canAffordAll(player, cost);
        if (!result.isOk()) {
            return result;
        }
        if (!consumeAll(player, cost)) {
            return AffordResult.INSUFFICIENT_MONEY;
        }
        return AffordResult.OK;
    }
}
