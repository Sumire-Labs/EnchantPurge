package com.example.enchantpurge.cost;

import com.example.enchantpurge.EnchantPurgePlugin;
import com.example.enchantpurge.integration.VaultIntegration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

/**
 * Handles Vault-based money costs.
 * <p>
 * Checks Vault availability and performs economy transactions.
 * When Vault is unavailable, all money costs are treated as satisfied (disabled).
 * <p>
 * Withdraw failures are treated as insufficient funds to preserve atomicity
 * with other cost types.
 */
public class MoneyCost {

    private final VaultIntegration vaultIntegration;

    public MoneyCost(EnchantPurgePlugin plugin) {
        this.vaultIntegration = plugin.getVaultIntegration();
    }

    /**
     * Check if the player has enough money.
     *
     * @param player the player
     * @param amount the required amount
     * @return true if player can afford, or if Vault is unavailable (cost disabled)
     */
    public boolean hasEnough(Player player, double amount) {
        if (amount <= 0) return true;
        Economy economy = vaultIntegration.getEconomy();
        if (economy == null) return true; // Vault not present, skip cost
        return economy.has(player, amount);
    }

    /**
     * Deduct money from the player.
     * <p>
     * Checks the {@link EconomyResponse} after withdrawal. If the transaction
     * fails for any reason, the deduction is treated as unsuccessful — callers
     * should treat this as an affordability failure and must not consume other
     * cost types.
     *
     * @param player the player
     * @param amount the amount to deduct
     * @return true if the deduction succeeded (or money cost is disabled)
     */
    public boolean deduct(Player player, double amount) {
        if (amount <= 0) return true;
        Economy economy = vaultIntegration.getEconomy();
        if (economy == null) return true; // Vault not present, skip cost

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }
}
