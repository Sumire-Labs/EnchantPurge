package com.example.enchantpurge;

import com.example.enchantpurge.command.DisenchantCommand;
import com.example.enchantpurge.command.DisenchantTabCompleter;
import com.example.enchantpurge.config.ConfigManager;
import com.example.enchantpurge.config.LanguageManager;
import com.example.enchantpurge.core.CostCalculator;
import com.example.enchantpurge.core.CurseDetector;
import com.example.enchantpurge.core.EnchantmentFilter;
import com.example.enchantpurge.core.EnchantmentRemover;
import com.example.enchantpurge.cost.CostHandler;
import com.example.enchantpurge.gui.DisenchantGUI;
import com.example.enchantpurge.gui.DisenchantGUIListener;
import com.example.enchantpurge.integration.PlaceholderAPIIntegration;
import com.example.enchantpurge.integration.VaultIntegration;
import com.example.enchantpurge.logging.DisenchantLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for the EnchantPurge plugin.
 * <p>
 * Phase 4: Complete Vault/PAPI integration, async logging,
 * MiniMessage consistency, and safe reload handling.
 */
public final class EnchantPurgePlugin extends JavaPlugin {

    private static EnchantPurgePlugin instance;

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private VaultIntegration vaultIntegration;
    private PlaceholderAPIIntegration placeholderAPIIntegration;
    private DisenchantLogger disenchantLogger;

    // Core components
    private CurseDetector curseDetector;
    private EnchantmentFilter enchantmentFilter;
    private EnchantmentRemover enchantmentRemover;
    private CostCalculator costCalculator;
    private CostHandler costHandler;
    private DisenchantGUI disenchantGUI;

    // Reload guard — blocks new operations during reload
    private volatile boolean reloading = false;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config.yml and lang files if not present
        saveDefaultConfig();
        saveResource("lang/ja_JP.yml", false);
        saveResource("lang/en_US.yml", false);

        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.load();

        languageManager = new LanguageManager(this);
        languageManager.load(configManager.getLanguage());

        vaultIntegration = new VaultIntegration(this);
        vaultIntegration.setup();

        placeholderAPIIntegration = new PlaceholderAPIIntegration(this);
        placeholderAPIIntegration.tryRegister();

        disenchantLogger = new DisenchantLogger(this);
        disenchantLogger.init();

        // Initialize core components
        curseDetector = new CurseDetector(this);
        enchantmentFilter = new EnchantmentFilter(this);
        enchantmentRemover = new EnchantmentRemover(this, curseDetector);
        costCalculator = new CostCalculator(this);
        costHandler = new CostHandler(this);
        disenchantGUI = new DisenchantGUI(this, enchantmentRemover, curseDetector);

        // Register commands
        var disenchantCmd = getCommand("disenchant");
        if (disenchantCmd != null) {
            disenchantCmd.setExecutor(new DisenchantCommand(
                    this, enchantmentFilter, enchantmentRemover, curseDetector));
            disenchantCmd.setTabCompleter(new DisenchantTabCompleter());
        }

        // Register listeners — uses plugin reference to always get latest components
        getServer().getPluginManager().registerEvents(
                new DisenchantGUIListener(this), this);

        getLogger().info("EnchantPurge v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // Close all open GUIs and clean up sessions
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            if (DisenchantGUI.hasSession(player.getUniqueId())) {
                player.closeInventory();
            }
        }

        // Unregister PAPI expansion
        if (placeholderAPIIntegration != null && placeholderAPIIntegration.isExpansionRegistered()) {
            placeholderAPIIntegration.unregisterExpansion();
        }

        if (disenchantLogger != null) {
            disenchantLogger.shutdown();
        }
        getLogger().info("EnchantPurge disabled.");
        instance = null;
    }

    /** Reload configuration, language files, and refresh cached state. */
    public void reload() {
        reloading = true;
        try {
            // Close all open GUIs first for safety
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                if (DisenchantGUI.hasSession(player.getUniqueId())) {
                    player.closeInventory();
                }
            }

            reloadConfig();
            configManager.load();
            languageManager.load(configManager.getLanguage());

            // Re-setup Vault (may have changed if economy plugin loaded/unloaded)
            vaultIntegration.setup();

            disenchantLogger.init();

            // Refresh material caches
            if (enchantmentFilter != null) {
                enchantmentFilter.refreshMaterials();
            }

            // Re-create CurseDetector to refresh curse cache
            curseDetector = new CurseDetector(this);
            // Update EnchantmentRemover with new CurseDetector
            enchantmentRemover = new EnchantmentRemover(this, curseDetector);
            // Re-create cost components (config/Vault state may have changed)
            costCalculator = new CostCalculator(this);
            costHandler = new CostHandler(this);
            // Update DisenchantGUI with new components
            disenchantGUI = new DisenchantGUI(this, enchantmentRemover, curseDetector);

            // Re-register PAPI expansion only if not already registered (avoids double registration)
            if (placeholderAPIIntegration != null) {
                if (!placeholderAPIIntegration.isExpansionRegistered()) {
                    placeholderAPIIntegration.tryRegister();
                }
            }

            getLogger().info("Configuration reloaded.");
        } finally {
            reloading = false;
        }
    }

    /** Whether a reload is currently in progress. */
    public boolean isReloading() {
        return reloading;
    }

    // ── Getters ──────────────────────────────────────────────

    public static EnchantPurgePlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }

    public PlaceholderAPIIntegration getPlaceholderAPIIntegration() {
        return placeholderAPIIntegration;
    }

    public DisenchantLogger getDisenchantLogger() {
        return disenchantLogger;
    }

    public CurseDetector getCurseDetector() {
        return curseDetector;
    }

    public EnchantmentFilter getEnchantmentFilter() {
        return enchantmentFilter;
    }

    public EnchantmentRemover getEnchantmentRemover() {
        return enchantmentRemover;
    }

    public CostCalculator getCostCalculator() {
        return costCalculator;
    }

    public CostHandler getCostHandler() {
        return costHandler;
    }

    public DisenchantGUI getDisenchantGUI() {
        return disenchantGUI;
    }
}