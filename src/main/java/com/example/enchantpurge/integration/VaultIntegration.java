package com.example.enchantpurge.integration;

import com.example.enchantpurge.EnchantPurgePlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integrates with Vault for economy support.
 * <p>
 * Sets up Vault Economy provider if available.
 * Money-based cost transactions are handled by {@link com.example.enchantpurge.cost.MoneyCost}.
 * <p>
 * Warning messages are emitted at most once per plugin lifecycle to avoid
 * log spam on reload.
 */
public class VaultIntegration {

    private final EnchantPurgePlugin plugin;
    private Economy economy;
    private boolean warningShown;

    public VaultIntegration(EnchantPurgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempt to hook into Vault's economy provider.
     * Logs a single warning if Vault is not present or no economy provider is registered.
     * Safe to call on reload (warning shown only once).
     */
    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            if (!warningShown) {
                plugin.getLogger().warning("Vault not found. Money-based costs will be disabled.");
                warningShown = true;
            }
            economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            if (!warningShown) {
                plugin.getLogger().warning("Vault found but no economy provider registered. Money costs disabled.");
                warningShown = true;
            }
            economy = null;
            return;
        }

        economy = rsp.getProvider();
        warningShown = false; // reset on successful hook so reload can re-warn if provider disappears
        plugin.getLogger().info("Vault economy hooked: " + economy.getName());
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    /**
     * Format a monetary amount using Vault's economy format.
     * Falls back to a plain decimal string when Vault is unavailable.
     *
     * @param amount the amount to format
     * @return a formatted currency string
     */
    public String format(double amount) {
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (Exception ignored) {
                // fall through to fallback
            }
        }
        return String.format("%.2f", amount);
    }
}
