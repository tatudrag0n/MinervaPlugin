package org.server.minerva;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

final class WorldRulesFeature {
   private static final long FIXED_DAY_TIME = 6000L;
   private final Minerva plugin;

   WorldRulesFeature(Minerva plugin) {
      this.plugin = plugin;
   }

   void apply() {
      Set<String> fixedDayWorlds = this.fixedDayWorldNames();
      Set<String> pvpWorlds = this.pvpWorldNames();
      boolean defaultPvp = this.plugin.getConfig().getBoolean("world-rules.pvp.default", false);

      for (World world : Bukkit.getWorlds()) {
         world.setGameRule(GameRules.KEEP_INVENTORY, true);
         boolean pvpEnabled = defaultPvp || pvpWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
         world.setGameRule(GameRules.PVP, pvpEnabled);
         world.setPVP(pvpEnabled);
         if (fixedDayWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setTime(6000L);
         }
      }
   }

   private Set<String> fixedDayWorldNames() {
      Set<String> names = new HashSet<>();
      this.addWorldName(names, "athletic");
      this.addWorldName(names, "minigame");
      this.addWorldName(names, this.plugin.getConfig().getString("servers.athletic.world"));
      this.addWorldName(names, this.plugin.getConfig().getString("servers.minigame.world"));

      for (String configuredName : this.plugin.getConfig().getStringList("world-rules.fixed-day-worlds")) {
         this.addWorldName(names, configuredName);
      }

      return names;
   }

   private Set<String> pvpWorldNames() {
      Set<String> names = new HashSet<>();
      this.addWorldName(names, "minigame");
      this.addWorldName(names, this.plugin.getConfig().getString("servers.minigame.world"));

      for (String configuredName : this.plugin.getConfig().getStringList("world-rules.pvp.enabled-worlds")) {
         this.addWorldName(names, configuredName);
      }

      return names;
   }

   private void addWorldName(Set<String> names, String worldName) {
      if (worldName != null && !worldName.isBlank()) {
         names.add(worldName.toLowerCase(Locale.ROOT));
      }
   }
}
