package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class WorldRulesFeature {
    private static final long FIXED_DAY_TIME = 6000L;

    private final Minerva plugin;

    WorldRulesFeature(Minerva plugin) {
        this.plugin = plugin;
    }

    void apply() {
        Set<String> fixedDayWorlds = fixedDayWorldNames();
        Set<String> pvpWorlds = pvpWorldNames();
        boolean defaultPvp = plugin.getConfig().getBoolean("world-rules.pvp.default", false);
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            boolean pvpEnabled = defaultPvp || pvpWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
            world.setGameRule(GameRules.PVP, pvpEnabled);
            world.setPVP(pvpEnabled);
            if (fixedDayWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) {
                world.setGameRule(GameRules.ADVANCE_TIME, false);
                world.setTime(FIXED_DAY_TIME);
            }
        }
    }

    private Set<String> fixedDayWorldNames() {
        Set<String> names = new HashSet<>();
        addWorldName(names, "athletic");
        addWorldName(names, "minigame");
        addWorldName(names, plugin.getConfig().getString("servers.athletic.world"));
        addWorldName(names, plugin.getConfig().getString("servers.minigame.world"));
        for (String configuredName : plugin.getConfig().getStringList("world-rules.fixed-day-worlds")) {
            addWorldName(names, configuredName);
        }
        return names;
    }

    private Set<String> pvpWorldNames() {
        Set<String> names = new HashSet<>();
        addWorldName(names, "minigame");
        addWorldName(names, plugin.getConfig().getString("servers.minigame.world"));
        List<String> configured = plugin.getConfig().getStringList("world-rules.pvp.enabled-worlds");
        for (String configuredName : configured) {
            addWorldName(names, configuredName);
        }
        return names;
    }

    private void addWorldName(Set<String> names, String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            names.add(worldName.toLowerCase(Locale.ROOT));
        }
    }
}
