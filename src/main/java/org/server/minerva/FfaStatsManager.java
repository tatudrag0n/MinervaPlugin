package org.server.minerva;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class FfaStatsManager {
   private final Minerva plugin;
   private final File file;
   private YamlConfiguration config;

   FfaStatsManager(Minerva plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "ffa-stats.yml");
   }

   void load() {
      if (!this.file.exists()) {
         try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.exists()) {
               parent.mkdirs();
            }

            YamlConfiguration empty = new YamlConfiguration();
            empty.createSection("players");
            empty.save(this.file);
         } catch (IOException e) {
            this.plugin.getLogger().severe("Could not create ffa-stats.yml: " + e.getMessage());
         }
      }

      this.config = YamlConfiguration.loadConfiguration(this.file);
   }

   void save() {
      if (this.config != null) {
         try {
            this.config.save(this.file);
         } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save ffa-stats.yml: " + e.getMessage());
         }
      }
   }

   void recordKill(OfflinePlayer player) {
      if (player != null) {
         ConfigurationSection section = this.section(player.getUniqueId());
         section.set("name", this.safeName(player));
         section.set("kills", section.getInt("kills", 0) + 1);
         int streak = section.getInt("current-streak", 0) + 1;
         section.set("current-streak", streak);
         section.set("max-streak", Math.max(section.getInt("max-streak", 0), streak));
         this.save();
      }
   }

   void recordDeath(OfflinePlayer player) {
      if (player != null) {
         ConfigurationSection section = this.section(player.getUniqueId());
         section.set("name", this.safeName(player));
         section.set("deaths", section.getInt("deaths", 0) + 1);
         section.set("current-streak", 0);
         this.save();
      }
   }

   String summary(OfflinePlayer player) {
      ConfigurationSection section = this.section(player.getUniqueId());
      int kills = section.getInt("kills", 0);
      int deaths = section.getInt("deaths", 0);
      double kd = deaths == 0 ? kills : (double)kills / deaths;
      return "§aFFA戦績 §7| §fキル: §a"
         + kills
         + " §7/ §fデス: §c"
         + deaths
         + " §7/ §fK/D: §e"
         + String.format(Locale.ROOT, "%.2f", kd)
         + " §7/ §f連続キル: §b"
         + section.getInt("current-streak", 0)
         + " §7/ §f最大: §d"
         + section.getInt("max-streak", 0);
   }

   int currentStreak(UUID uuid) {
      return this.section(uuid).getInt("current-streak", 0);
   }

   int kills(UUID uuid) {
      return this.section(uuid).getInt("kills", 0);
   }

   int deaths(UUID uuid) {
      return this.section(uuid).getInt("deaths", 0);
   }

   int maxStreak(UUID uuid) {
      return this.section(uuid).getInt("max-streak", 0);
   }

   private ConfigurationSection section(UUID uuid) {
      if (this.config == null) {
         this.load();
      }

      String path = "players." + uuid;
      ConfigurationSection section = this.config.getConfigurationSection(path);
      return section != null ? section : this.config.createSection(path);
   }

   private String safeName(OfflinePlayer player) {
      return player.getName() == null ? player.getUniqueId().toString() : player.getName();
   }
}
