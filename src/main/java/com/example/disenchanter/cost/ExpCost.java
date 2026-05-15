package com.example.disenchanter.cost;

import org.bukkit.entity.Player;

/**
 * Handles experience-level based costs.
 * Checks and consumes experience levels via {@link Player#getLevel()}
 * and {@link Player#setLevel(int)}.
 */
public class ExpCost {

    /**
     * Check if the player has enough experience levels.
     *
     * @param player        the player
     * @param requiredLevels required exp levels
     * @return true if the player has enough
     */
    public boolean hasEnough(Player player, int requiredLevels) {
        if (requiredLevels <= 0) return true;
        return player.getLevel() >= requiredLevels;
    }

    /**
     * Deduct experience levels from the player.
     *
     * @param player the player
     * @param levels levels to deduct
     */
    public void deduct(Player player, int levels) {
        if (levels <= 0) return;
        player.setLevel(player.getLevel() - levels);
    }
}
