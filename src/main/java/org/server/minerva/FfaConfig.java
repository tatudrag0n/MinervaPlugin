package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

final class FfaConfig {
    private final Minerva plugin;

    FfaConfig(Minerva plugin) {
        this.plugin = plugin;
    }

    Minerva plugin() {
        return plugin;
    }

    void ensureDefaults() {
        FileConfiguration config = plugin.getConfig();
        setIfMissing(config, "ffa.enabled", true);
        setIfMissing(config, "ffa.restrictions.drop-items", false);
        setIfMissing(config, "ffa.restrictions.pickup-items", false);
        setIfMissing(config, "ffa.restrictions.block-break", false);
        setIfMissing(config, "ffa.restrictions.block-place", false);
        setIfMissing(config, "ffa.restrictions.restrict-commands", true);
        setIfMissing(config, "ffa.restrictions.allowed-commands", List.of("/mva ffa leave", "/mva ffa stats"));
        setIfMissing(config, "ffa.world-change.leave-on-exit", true);
        setIfMissing(config, "ffa.respawn.delay-ticks", 2);
        setIfMissing(config, "ffa.respawn.location", "world-spawn");
        setIfMissing(config, "ffa.stands.spacing", 2.5D);
        setIfMissing(config, "ffa.stands.selected-kit", "sword");
        boolean migrateBalancedArmor = config.getInt("ffa.kits.armor-balance-version", 0) < 2;
        setDisplayName(config, "ffa.kits.bow.display-name", "§aアーチャー", "§a弓キット");
        setIfMissing(config, "ffa.kits.bow.icon", "bow");
        setIfMissing(config, "ffa.kits.bow.armor-tier", "chainmail");
        setArmorDefault(config, "bow", migrateBalancedArmor, "chainmail_helmet", "leather_chestplate", "chainmail_leggings", "leather_boots");
        setIfMissing(config, "ffa.kits.bow.weapon", "iron_sword");
        setIfMissing(config, "ffa.kits.bow.bow", true);
        setIfMissing(config, "ffa.kits.bow.arrows", 64);
        setIfMissing(config, "ffa.kits.bow.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.bow.food-items", List.of("cooked_chicken:10", "sweet_berries:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.bow.items", List.of("golden_carrot:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.bow.potions", List.of("speed:1:45"));
        setDisplayName(config, "ffa.kits.sword.display-name", "§cソードマン", "§c剣キット");
        setIfMissing(config, "ffa.kits.sword.icon", "diamond_sword");
        setIfMissingOrLegacy(config, "ffa.kits.sword.armor-tier", "diamond", "iron");
        setArmorDefault(config, "sword", migrateBalancedArmor, "diamond_helmet", "iron_chestplate", "diamond_leggings", "iron_boots");
        setIfMissing(config, "ffa.kits.sword.weapon", "diamond_sword");
        setIfMissing(config, "ffa.kits.sword.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.sword.food-items", List.of("cooked_beef:12", "bread:6"));
        setListIfMissingOrEmpty(config, "ffa.kits.sword.items", List.of("golden_apple:1"));
        setListIfMissingOrEmpty(config, "ffa.kits.sword.potions", List.of());
        setDisplayName(config, "ffa.kits.shield.display-name", "§9シールダー", "§9盾キット");
        setIfMissing(config, "ffa.kits.shield.icon", "shield");
        setIfMissingOrLegacy(config, "ffa.kits.shield.armor-tier", "gold", "iron");
        setArmorDefault(config, "shield", migrateBalancedArmor, "golden_helmet", "iron_chestplate", "golden_leggings", "chainmail_boots");
        setIfMissingOrLegacy(config, "ffa.kits.shield.weapon", "golden_sword", "iron_sword");
        setIfMissing(config, "ffa.kits.shield.shield", true);
        setIfMissing(config, "ffa.kits.shield.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.shield.food-items", List.of("golden_carrot:12", "baked_potato:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.shield.items", List.of("golden_carrot:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.shield.potions", List.of("instant_health:1:1"));
        setDisplayName(config, "ffa.kits.spear.display-name", "§eランサー", "§e槍キット");
        setIfMissing(config, "ffa.kits.spear.icon", "iron_spear");
        setIfMissing(config, "ffa.kits.spear.armor-tier", "chainmail");
        setArmorDefault(config, "spear", migrateBalancedArmor, "chainmail_helmet", "chainmail_chestplate", "leather_leggings", "iron_boots");
        setIfMissing(config, "ffa.kits.spear.weapon", "iron_spear");
        setIfMissing(config, "ffa.kits.spear.backup-weapon", "stone_sword");
        setIfMissing(config, "ffa.kits.spear.allow-fallback", false);
        setIfMissing(config, "ffa.kits.spear.fallback-weapon", "trident");
        setIfMissing(config, "ffa.kits.spear.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.spear.food-items", List.of("cooked_mutton:10", "apple:6"));
        setListIfMissingOrEmpty(config, "ffa.kits.spear.items", List.of("golden_carrot:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.spear.potions", List.of("speed:1:45"));
        setDisplayName(config, "ffa.kits.axe.display-name", "§6ウォリアー", "§6斧キット");
        setIfMissing(config, "ffa.kits.axe.icon", "netherite_axe");
        setIfMissingOrLegacy(config, "ffa.kits.axe.armor-tier", "netherite", "iron");
        setArmorDefault(config, "axe", migrateBalancedArmor, "netherite_helmet", "diamond_chestplate", "iron_leggings", "netherite_boots");
        setIfMissingOrLegacy(config, "ffa.kits.axe.weapon", "netherite_axe", "diamond_axe");
        setIfMissing(config, "ffa.kits.axe.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.axe.food-items", List.of("cooked_porkchop:10", "golden_apple:1"));
        setListIfMissingOrEmpty(config, "ffa.kits.axe.items", List.of("golden_apple:1"));
        setListIfMissingOrEmpty(config, "ffa.kits.axe.potions", List.of());
        setDisplayName(config, "ffa.kits.crossbow.display-name", "§dリボルバー", "§dクロスボウキット");
        setIfMissing(config, "ffa.kits.crossbow.icon", "crossbow");
        setIfMissingOrLegacy(config, "ffa.kits.crossbow.armor-tier", "iron", "chainmail");
        setArmorDefault(config, "crossbow", migrateBalancedArmor, "leather_helmet", "iron_chestplate", "chainmail_leggings", "leather_boots");
        setIfMissingOrLegacy(config, "ffa.kits.crossbow.weapon", "iron_sword", "stone_sword");
        setIfMissing(config, "ffa.kits.crossbow.loaded-crossbows", 6);
        setIfMissing(config, "ffa.kits.crossbow.arrows", 64);
        setIfMissing(config, "ffa.kits.crossbow.quick-charge-level", 1);
        setIfMissing(config, "ffa.kits.crossbow.multishot", false);
        setIfMissing(config, "ffa.kits.crossbow.piercing-level", 0);
        setIfMissing(config, "ffa.kits.crossbow.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.crossbow.food-items", List.of("cooked_rabbit:10", "golden_carrot:6"));
        setListIfMissingOrEmpty(config, "ffa.kits.crossbow.items", List.of("golden_carrot:8"));
        setListIfMissingOrEmpty(config, "ffa.kits.crossbow.potions", List.of("speed:1:45"));
        if (migrateBalancedArmor) {
            config.set("ffa.kits.armor-balance-version", 2);
        }
        plugin.saveConfig();
    }

    void reload() {
        plugin.reloadConfig();
        ensureDefaults();
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("ffa.enabled", true);
    }

    Location center() {
        String worldName = plugin.getConfig().getString("ffa.center.world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                plugin.getConfig().getDouble("ffa.center.x"),
                plugin.getConfig().getDouble("ffa.center.y"),
                plugin.getConfig().getDouble("ffa.center.z"),
                (float) plugin.getConfig().getDouble("ffa.center.yaw"),
                (float) plugin.getConfig().getDouble("ffa.center.pitch"));
    }

    void setCenter(Location location) {
        plugin.getConfig().set("ffa.center.world", location.getWorld().getName());
        plugin.getConfig().set("ffa.center.x", location.getX());
        plugin.getConfig().set("ffa.center.y", location.getY());
        plugin.getConfig().set("ffa.center.z", location.getZ());
        plugin.getConfig().set("ffa.center.yaw", location.getYaw());
        plugin.getConfig().set("ffa.center.pitch", location.getPitch());
        plugin.saveConfig();
    }

    Location kitSelection() {
        String worldName = plugin.getConfig().getString("ffa.kit-selection.world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                plugin.getConfig().getDouble("ffa.kit-selection.x"),
                plugin.getConfig().getDouble("ffa.kit-selection.y"),
                plugin.getConfig().getDouble("ffa.kit-selection.z"),
                (float) plugin.getConfig().getDouble("ffa.kit-selection.yaw"),
                (float) plugin.getConfig().getDouble("ffa.kit-selection.pitch"));
    }

    void setKitSelection(Location location) {
        plugin.getConfig().set("ffa.kit-selection.world", location.getWorld().getName());
        plugin.getConfig().set("ffa.kit-selection.x", location.getX());
        plugin.getConfig().set("ffa.kit-selection.y", location.getY());
        plugin.getConfig().set("ffa.kit-selection.z", location.getZ());
        plugin.getConfig().set("ffa.kit-selection.yaw", location.getYaw());
        plugin.getConfig().set("ffa.kit-selection.pitch", location.getPitch());
        plugin.saveConfig();
    }

    boolean restriction(String key) {
        return plugin.getConfig().getBoolean("ffa.restrictions." + key, false);
    }

    boolean restrictCommands() {
        return plugin.getConfig().getBoolean("ffa.restrictions.restrict-commands", true);
    }

    List<String> allowedCommands() {
        return plugin.getConfig().getStringList("ffa.restrictions.allowed-commands").stream()
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .filter(value -> !value.isBlank())
                .toList();
    }

    boolean leaveOnWorldExit() {
        return plugin.getConfig().getBoolean("ffa.world-change.leave-on-exit", true);
    }

    long respawnDelayTicks() {
        return Math.max(1L, plugin.getConfig().getLong("ffa.respawn.delay-ticks", 2L));
    }

    String respawnLocationMode() {
        return plugin.getConfig().getString("ffa.respawn.location", "world-spawn").toLowerCase(Locale.ROOT);
    }

    double standSpacing() {
        return Math.max(1.0D, plugin.getConfig().getDouble("ffa.stands.spacing", 2.5D));
    }

    String kitPath(FfaKit kit, String key) {
        return "ffa.kits." + kit.key() + "." + key;
    }

    private void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    private void setIfMissingOrLegacy(FileConfiguration config, String path, String value, String legacyValue) {
        if (!config.contains(path) || legacyValue.equalsIgnoreCase(config.getString(path, ""))) {
            config.set(path, value);
        }
    }

    private void setListIfMissingOrEmpty(FileConfiguration config, String path, List<String> value) {
        if (!config.contains(path) || config.getList(path, List.of()).isEmpty()) {
            config.set(path, value);
        }
    }

    private void setArmorDefault(
            FileConfiguration config,
            String kit,
            boolean force,
            String helmet,
            String chestplate,
            String leggings,
            String boots) {
        String path = "ffa.kits." + kit + ".armor.";
        if (force
                || !config.contains(path + "helmet")
                || !config.contains(path + "chestplate")
                || !config.contains(path + "leggings")
                || !config.contains(path + "boots")
                || isUniformArmorSet(config, path)) {
            config.set(path + "helmet", helmet);
            config.set(path + "chestplate", chestplate);
            config.set(path + "leggings", leggings);
            config.set(path + "boots", boots);
        }
    }

    private boolean isUniformArmorSet(FileConfiguration config, String path) {
        String helmet = config.getString(path + "helmet", "").toLowerCase(Locale.ROOT);
        String chestplate = config.getString(path + "chestplate", "").toLowerCase(Locale.ROOT);
        String leggings = config.getString(path + "leggings", "").toLowerCase(Locale.ROOT);
        String boots = config.getString(path + "boots", "").toLowerCase(Locale.ROOT);
        for (String tier : List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite")) {
            if (helmet.equals(tier + "_helmet")
                    && chestplate.equals(tier + "_chestplate")
                    && leggings.equals(tier + "_leggings")
                    && boots.equals(tier + "_boots")) {
                return true;
            }
        }
        return false;
    }

    private void setDisplayName(FileConfiguration config, String path, String value, String legacyValue) {
        if (!config.contains(path) || legacyValue.equals(config.getString(path))) {
            config.set(path, value);
        }
    }
}
