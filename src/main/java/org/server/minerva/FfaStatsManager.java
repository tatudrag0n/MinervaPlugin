package org.server.minerva;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

final class FfaStatsManager {
    private final Minerva plugin;
    private final File file;
    private YamlConfiguration config;

    FfaStatsManager(Minerva plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ffa-stats.yml");
    }

    void load() {
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                YamlConfiguration empty = new YamlConfiguration();
                empty.createSection("players");
                empty.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create ffa-stats.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    void save() {
        if (config == null) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save ffa-stats.yml: " + e.getMessage());
        }
    }

    void recordKill(OfflinePlayer player) {
        if (player == null) {
            return;
        }
        ConfigurationSection section = section(player.getUniqueId());
        section.set("name", safeName(player));
        section.set("kills", section.getInt("kills", 0) + 1);
        int streak = section.getInt("current-streak", 0) + 1;
        section.set("current-streak", streak);
        section.set("max-streak", Math.max(section.getInt("max-streak", 0), streak));
        save();
    }

    void recordDeath(OfflinePlayer player) {
        if (player == null) {
            return;
        }
        ConfigurationSection section = section(player.getUniqueId());
        section.set("name", safeName(player));
        section.set("deaths", section.getInt("deaths", 0) + 1);
        section.set("current-streak", 0);
        save();
    }

    String summary(OfflinePlayer player) {
        ConfigurationSection section = section(player.getUniqueId());
        int kills = section.getInt("kills", 0);
        int deaths = section.getInt("deaths", 0);
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        return "§aFFA戦績 §7| §fキル: §a" + kills
                + " §7/ §fデス: §c" + deaths
                + " §7/ §fK/D: §e" + String.format(Locale.ROOT, "%.2f", kd)
                + " §7/ §f連続キル: §b" + section.getInt("current-streak", 0)
                + " §7/ §f最大: §d" + section.getInt("max-streak", 0);
    }

    int currentStreak(UUID uuid) {
        return section(uuid).getInt("current-streak", 0);
    }

    int kills(UUID uuid) {
        return section(uuid).getInt("kills", 0);
    }

    int deaths(UUID uuid) {
        return section(uuid).getInt("deaths", 0);
    }

    int maxStreak(UUID uuid) {
        return section(uuid).getInt("max-streak", 0);
    }

    private ConfigurationSection section(UUID uuid) {
        if (config == null) {
            load();
        }
        String path = "players." + uuid;
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : config.createSection(path);
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }
}
