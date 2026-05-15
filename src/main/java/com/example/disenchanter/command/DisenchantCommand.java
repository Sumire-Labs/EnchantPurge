package com.example.disenchanter.command;

import com.example.disenchanter.DisenchanterPlugin;
import com.example.disenchanter.core.CurseDetector;
import com.example.disenchanter.core.EnchantmentFilter;
import com.example.disenchanter.core.EnchantmentRemover;
import com.example.disenchanter.gui.DisenchantGUI;
import com.example.disenchanter.util.MessageFormatter;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Handles the /disenchant command.
 * <p>
 * Opens the disenchantment GUI for eligible items held in the main hand.
 * Supports sub-commands: reload (admin) and help.
 */
public class DisenchantCommand implements CommandExecutor {

    private final DisenchanterPlugin plugin;
    private final EnchantmentFilter enchantmentFilter;
    private final EnchantmentRemover enchantmentRemover;
    private final CurseDetector curseDetector;

    public DisenchantCommand(DisenchanterPlugin plugin, EnchantmentFilter enchantmentFilter,
                              EnchantmentRemover enchantmentRemover, CurseDetector curseDetector) {
        this.plugin = plugin;
        this.enchantmentFilter = enchantmentFilter;
        this.enchantmentRemover = enchantmentRemover;
        this.curseDetector = curseDetector;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase(java.util.Locale.ROOT);
            if ("reload".equals(sub)) {
                return handleReload(sender);
            }
            if ("help".equals(sub)) {
                return handleHelp(sender);
            }
        }
        return handleOpenGUI(sender);
    }

    // ── Open GUI ─────────────────────────────────────────────

    private boolean handleOpenGUI(CommandSender sender) {
        // Permission check
        if (!sender.hasPermission("disenchant.use")) {
            MessageFormatter.send(plugin, sender, "messages.no_permission");
            return true;
        }

        // Must be a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        // Reload guard
        if (plugin.isReloading()) {
            MessageFormatter.send(plugin, player, "messages.operation_blocked_reloading");
            return true;
        }

        // Get main hand item
        PlayerInventory inv = player.getInventory();
        ItemStack heldItem = inv.getItemInMainHand();

        // Edge case 1: Empty hand
        if (heldItem == null || heldItem.getType().isAir()) {
            MessageFormatter.send(plugin, player, "messages.no_item_in_hand");
            return true;
        }

        // Edge case 2: No enchantments
        if (!EnchantmentRemover.hasEnchantments(heldItem)) {
            MessageFormatter.send(plugin, player, "messages.no_enchantments");
            return true;
        }

        // Edge case 3: Target filter (blacklist/whitelist, enchanted book)
        if (!enchantmentFilter.isAllowed(heldItem)) {
            MessageFormatter.send(plugin, player, "messages.item_not_supported");
            return true;
        }

        // Open GUI — use the shared instance from the plugin
        DisenchantGUI gui = plugin.getDisenchantGUI();
        gui.open(player, heldItem);
        return true;
    }

    // ── Reload ───────────────────────────────────────────────

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("disenchant.admin.reload")) {
            MessageFormatter.send(plugin, sender, "messages.no_permission");
            return true;
        }

        try {
            plugin.reload();
            // Re-parse material lists in filter after config reload
            enchantmentFilter.refreshMaterials();
            MessageFormatter.send(plugin, sender, "messages.reload_success");
        } catch (Exception e) {
            plugin.getLogger().severe("Reload failed: " + e.getMessage());
            MessageFormatter.send(plugin, sender, "messages.reload_failed");
        }
        return true;
    }

    // ── Help ─────────────────────────────────────────────────

    private boolean handleHelp(CommandSender sender) {
        String prefix = plugin.getLanguageManager().getPrefix().replace("<", "").replace(">", "");
        // Try to use lang messages; fall back to plain text
        sender.sendMessage("§6/disenchant §7- Open enchantment removal GUI");
        sender.sendMessage("§6/disenchant reload §7- Reload configuration (admin)");
        sender.sendMessage("§6/disenchant help §7- Show this help");
        return true;
    }
}
