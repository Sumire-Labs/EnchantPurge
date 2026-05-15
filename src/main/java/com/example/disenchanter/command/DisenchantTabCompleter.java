package com.example.disenchanter.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Tab completer for /disenchant.
 * <p>
 * Phase 2 (skeleton): Offers reload and help sub-commands.
 */
public class DisenchantTabCompleter implements TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reload", "help");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
