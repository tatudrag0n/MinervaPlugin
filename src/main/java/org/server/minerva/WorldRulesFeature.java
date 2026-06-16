package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

final class WorldRulesFeature {
    void apply() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            world.setGameRule(GameRules.PVP, false);
        }
    }
}
