package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        setIfMissing(config, "ffa.kits.enabled", FfaKit.defaultActiveKits().stream().map(FfaKit::key).toList());
        boolean migrateBalancedArmor = config.getInt("ffa.kits.armor-balance-version", 0) < 2;
        boolean migratePermanentKits = config.getInt("ffa.kits.kit-balance-version", 0) < 4;
        setIfMissing(config, "ffa.kits.default", "sword");
        setDisplayName(config, "ffa.kits.bow.display-name", "§aアーチャー", "§a弓キット");
        setIfMissing(config, "ffa.kits.bow.icon", "bow");
        setIfMissing(config, "ffa.kits.bow.armor-tier", "chainmail");
        setArmorDefault(config, "bow", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots");
        setIfMissingOrForce(config, "ffa.kits.bow.weapon", "stone_sword", migratePermanentKits);
        setIfMissing(config, "ffa.kits.bow.bow", true);
        setIfMissing(config, "ffa.kits.bow.food", 16);
        setIfMissingOrForce(config, "ffa.kits.bow.arrows", 1, migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.food-items", List.of("cooked_chicken:8"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.potions", List.of(), migratePermanentKits);
        setListIfMissingOrEmpty(config, "ffa.kits.bow.bow-enchantments", List.of("power:1", "infinity:1"));
        setDisplayName(config, "ffa.kits.sword.display-name", "§cソードマン", "§c剣キット");
        setIfMissingOrForce(config, "ffa.kits.sword.icon", "iron_sword", migratePermanentKits);
        setIfMissingOrForce(config, "ffa.kits.sword.armor-tier", "iron", migratePermanentKits);
        setArmorDefault(config, "sword", migrateBalancedArmor || migratePermanentKits, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
        setIfMissingOrForce(config, "ffa.kits.sword.weapon", "iron_sword", migratePermanentKits);
        setIfMissing(config, "ffa.kits.sword.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.food-items", List.of("cooked_beef:6"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.potions", List.of(), migratePermanentKits);
        setListIfMissingOrEmpty(config, "ffa.kits.sword.weapon-enchantments", List.of("sharpness:1"));
        setDisplayName(config, "ffa.kits.shield.display-name", "§9シールダー", "§9盾キット");
        setIfMissing(config, "ffa.kits.shield.icon", "shield");
        setIfMissingOrForce(config, "ffa.kits.shield.armor-tier", "chainmail", migratePermanentKits);
        setArmorDefault(config, "shield", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots");
        setIfMissingOrForce(config, "ffa.kits.shield.weapon", "stone_sword", migratePermanentKits);
        setIfMissing(config, "ffa.kits.shield.shield", true);
        setIfMissing(config, "ffa.kits.shield.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.food-items", List.of("cooked_beef:5"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.potions", List.of(), migratePermanentKits);
        setDisplayName(config, "ffa.kits.spear.display-name", "§eスピアー", "§e槍キット");
        setIfMissing(config, "ffa.kits.spear.icon", "iron_spear");
        setIfMissing(config, "ffa.kits.spear.armor-tier", "chainmail");
        setArmorDefault(config, "spear", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots");
        setIfMissing(config, "ffa.kits.spear.weapon", "iron_spear");
        setIfMissing(config, "ffa.kits.spear.backup-weapon", "stone_sword");
        setIfMissing(config, "ffa.kits.spear.allow-fallback", false);
        setIfMissing(config, "ffa.kits.spear.fallback-weapon", "trident");
        setIfMissing(config, "ffa.kits.spear.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.food-items", List.of("baked_potato:10"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.potions", List.of(), migratePermanentKits);
        setListIfMissingOrEmpty(config, "ffa.kits.spear.weapon-enchantments", List.of("lunge:1"));
        setDisplayName(config, "ffa.kits.axe.display-name", "§6ウォリアー", "§6斧キット");
        setIfMissingOrForce(config, "ffa.kits.axe.icon", "iron_axe", migratePermanentKits);
        setIfMissingOrForce(config, "ffa.kits.axe.armor-tier", "iron", migratePermanentKits);
        setArmorDefault(config, "axe", migrateBalancedArmor || migratePermanentKits, "iron_helmet", "iron_chestplate", "chainmail_leggings", "iron_boots");
        setIfMissingOrForce(config, "ffa.kits.axe.weapon", "iron_axe", migratePermanentKits);
        setIfMissing(config, "ffa.kits.axe.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.food-items", List.of("cooked_porkchop:5"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.potions", List.of(), migratePermanentKits);
        setListIfMissingOrEmpty(config, "ffa.kits.axe.weapon-enchantments", List.of("sharpness:1"));
        setIfMissing(config, "ffa.kits.axe.slowness-amplifier", 0);
        setDisplayName(config, "ffa.kits.crossbow.display-name", "§dリボルバー", "§dクロスボウキット");
        setIfMissing(config, "ffa.kits.crossbow.icon", "crossbow");
        setIfMissingOrForce(config, "ffa.kits.crossbow.armor-tier", "chainmail", migratePermanentKits);
        setArmorDefault(config, "crossbow", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots");
        setIfMissingOrForce(config, "ffa.kits.crossbow.weapon", "stone_sword", migratePermanentKits);
        setIfMissing(config, "ffa.kits.crossbow.loaded-crossbows", 6);
        setIfMissing(config, "ffa.kits.crossbow.arrows", 64);
        setIfMissing(config, "ffa.kits.crossbow.quick-charge-level", 1);
        setIfMissing(config, "ffa.kits.crossbow.multishot", false);
        setIfMissing(config, "ffa.kits.crossbow.piercing-level", 0);
        setIfMissing(config, "ffa.kits.crossbow.ammo-capacity", 6);
        setIfMissing(config, "ffa.kits.crossbow.reload-ticks", 75);
        setIfMissing(config, "ffa.kits.crossbow.damage-multiplier", 0.70D);
        setIfMissing(config, "ffa.kits.crossbow.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.food-items", List.of("bread:8"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.potions", List.of(), migratePermanentKits);
        setDisplayName(config, "ffa.kits.wizard.display-name", "§5ウィザード", "§5ウィザード");
        setIfMissing(config, "ffa.kits.wizard.icon", "splash_potion");
        setIfMissingOrForce(config, "ffa.kits.wizard.armor-tier", "golden", migratePermanentKits);
        setArmorDefault(config, "wizard", migrateBalancedArmor || migratePermanentKits, "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots");
        setIfMissingOrForce(config, "ffa.kits.wizard.weapon", "wooden_sword", migratePermanentKits);
        setIfMissing(config, "ffa.kits.wizard.food", 16);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.food-items", List.of("bread:8"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.potions", List.of(
                "splash_harm:1:1:2",
                "splash_poison:1:10:1",
                "splash_slowness:1:15:1",
                "splash_heal:1:1:2"), migratePermanentKits);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldown-ticks", 60);
        setDisplayName(config, "ffa.kits.trident.display-name", "§bトライデント", "§bトライデント");
        setIfMissing(config, "ffa.kits.trident.icon", "trident");
        setIfMissingOrForce(config, "ffa.kits.trident.armor-tier", "chainmail", migratePermanentKits);
        setArmorDefault(config, "trident", migrateBalancedArmor || migratePermanentKits, "turtle_helmet", "iron_chestplate", "chainmail_leggings", "iron_boots");
        setIfMissingOrForce(config, "ffa.kits.trident.weapon", "trident", migratePermanentKits);
        setIfMissing(config, "ffa.kits.trident.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.trident.weapon-enchantments", List.of("loyalty:3"));
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.food-items", List.of("cooked_salmon:7"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.potions", List.of(), migratePermanentKits);
        setDisplayName(config, "ffa.kits.mace.display-name", "§fメイス", "§fメイス");
        setIfMissing(config, "ffa.kits.mace.icon", "mace");
        setIfMissingOrForce(config, "ffa.kits.mace.armor-tier", "chainmail", migratePermanentKits);
        setArmorDefault(config, "mace", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "leather_boots");
        setIfMissingOrForce(config, "ffa.kits.mace.weapon", "mace", migratePermanentKits);
        setIfMissing(config, "ffa.kits.mace.wind-charge", 3);
        setIfMissing(config, "ffa.kits.mace.max-final-damage", 12.0D);
        setIfMissing(config, "ffa.kits.mace.food", 16);
        setListIfMissingOrEmpty(config, "ffa.kits.mace.boots-enchantments", List.of("feather_falling:4"));
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.food-items", List.of("cooked_beef:5"), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.items", List.of(), migratePermanentKits);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.potions", List.of(), migratePermanentKits);
        applyFfa17Defaults(config, migrateBalancedArmor || migratePermanentKits, migratePermanentKits);
        if (migrateBalancedArmor || migratePermanentKits) {
            config.set("ffa.kits.armor-balance-version", 2);
            config.set("ffa.kits.kit-balance-version", 4);
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

    private void applyFfa17Defaults(FileConfiguration config, boolean forceArmor, boolean forceKitBalance) {
        if (forceKitBalance) {
            config.set("ffa.kits.enabled", FfaKit.defaultActiveKits().stream().map(FfaKit::key).toList());
        }
        setIfMissing(config, "ffa.kits.default", "sword");
        kit(config, "axe", "§6戦士", "iron_axe", "chainmail",
                "none", "iron_chestplate", "chainmail_leggings", "iron_boots", "cooked_porkchop:1", forceArmor, forceKitBalance);
        kit(config, "bow", "§a狩人", "bow", "leather",
                "leather_helmet", "chainmail_chestplate", "leather_leggings", "none", "cooked_rabbit:1", forceArmor, forceKitBalance);
        kit(config, "spear", "§e槍使い", "iron_spear", "leather",
                "leather_helmet", "chainmail_chestplate", "none", "leather_boots", "baked_potato:1", forceArmor, forceKitBalance);
        kit(config, "crossbow", "§dリボルバー", "crossbow", "chainmail",
                "leather_helmet", "chainmail_chestplate", "none", "chainmail_boots", "bread:1", forceArmor, forceKitBalance);
        kit(config, "sword", "§c剣士", "iron_sword", "chainmail",
                "iron_helmet", "chainmail_chestplate", "iron_leggings", "none", "cooked_beef:1", forceArmor, forceKitBalance);
        kit(config, "shield", "§9シールダー", "shield", "chainmail",
                "chainmail_helmet", "iron_chestplate", "leather_leggings", "chainmail_boots", "pumpkin_pie:1", forceArmor, forceKitBalance);
        kit(config, "trident", "§b海の戦士", "trident", "chainmail",
                "turtle_helmet", "chainmail_chestplate", "chainmail_leggings", "none", "cooked_salmon:1", forceArmor, forceKitBalance);
        kit(config, "mace", "§7重戦士", "mace", "iron",
                "chainmail_helmet", "iron_chestplate", "iron_leggings", "chainmail_boots", "cooked_beef:1", forceArmor, forceKitBalance);
        kit(config, "gambler", "§6ギャンブラー", "golden_sword", "leather",
                "golden_helmet", "leather_chestplate", "chainmail_leggings", "none", "cookie:1", forceArmor, forceKitBalance);
        kit(config, "wizard", "§5ケミスト", "splash_potion", "leather",
                "golden_helmet", "leather_chestplate", "golden_leggings", "none", "honey_bottle:1", forceArmor, forceKitBalance);
        kit(config, "sniper", "§8スナイパー", "crossbow", "leather",
                "none", "leather_chestplate", "none", "leather_boots", "baked_potato:1", forceArmor, forceKitBalance);
        kit(config, "vampire", "§4ヴァンパイア", "redstone", "leather",
                "chainmail_helmet", "leather_chestplate", "chainmail_leggings", "none", "cooked_beef:1", forceArmor, forceKitBalance);
        kit(config, "grappler", "§2グラップラー", "golden_carrot", "none",
                "none", "none", "none", "none", "golden_carrot:1", forceArmor, forceKitBalance);
        kit(config, "assassin", "§5アサシン", "golden_sword", "leather",
                "leather_helmet", "leather_chestplate", "none", "chainmail_boots", "sweet_berries:1", forceArmor, forceKitBalance);
        kit(config, "necromancer", "§5ネクロマンサー", "zombie_spawn_egg", "leather",
                "golden_helmet", "leather_chestplate", "none", "leather_boots", "rotten_flesh:1", forceArmor, forceKitBalance);
        kit(config, "trapper", "§eトラッパー", "stone_pressure_plate", "leather",
                "chainmail_helmet", "leather_chestplate", "chainmail_leggings", "none", "baked_potato:1", forceArmor, forceKitBalance);
        kit(config, "bug_mania", "§2バグマニア", "silverfish_spawn_egg", "leather",
                "leather_helmet", "chainmail_chestplate", "leather_leggings", "none", "spider_eye:1", forceArmor, forceKitBalance);

        setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.potions", List.of(
                Map.of("effect", "slowness", "material", "splash_potion", "level", 1, "seconds", 8, "amount", 1, "ability", "slow", "name", "§7鈍化のスプラッシュポーション"),
                Map.of("effect", "harm", "material", "splash_potion", "level", 1, "seconds", 1, "amount", 1, "ability", "harm", "name", "§5負傷のスプラッシュポーション"),
                Map.of("effect", "poison", "material", "splash_potion", "level", 1, "seconds", 8, "amount", 1, "ability", "poison", "name", "§2毒のスプラッシュポーション"),
                Map.of("effect", "weakness", "material", "splash_potion", "level", 1, "seconds", 10, "amount", 1, "ability", "weakness", "name", "§8弱化のスプラッシュポーション"),
                Map.of("effect", "blindness", "material", "splash_potion", "level", 1, "seconds", 5, "amount", 1, "ability", "blindness", "name", "§0盲目のスプラッシュポーション")), forceKitBalance);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.slow", 12);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.harm", 15);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.poison", 18);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.weakness", 15);
        setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.blindness", 25);
        setIfMissingOrForce(config, "ffa.kits.crossbow.ammo-capacity", 6, forceKitBalance);
        setIfMissingOrForce(config, "ffa.kits.crossbow.reload-ticks", 75, forceKitBalance);
        setIfMissing(config, "ffa.kits.crossbow.damage-multiplier", 0.70D);
        setIfMissingOrForce(config, "ffa.kits.sniper.ammo-capacity", 2, forceKitBalance);
        setIfMissingOrForce(config, "ffa.kits.sniper.reload-ticks", 125, forceKitBalance);
        setIfMissing(config, "ffa.kits.sniper.damage-multiplier", 5.0D);
        setListIfMissingOrEmpty(config, "ffa.kits.sniper.crossbow-enchantments", List.of("piercing:4"));
        setDisplayName(config, "ffa.kits.assassin.display-name", "§5アサシン", "§0アサシン");
        setIfMissingOrLegacy(config, "ffa.kits.assassin.icon", "golden_sword", "iron_sword");
        setListIfMissingOrEmpty(config, "ffa.kits.assassin.fatal-sword-enchantments", List.of("unbreaking:1"));
        setIfMissingOrForce(config, "ffa.kits.mace.wind-charge", 10, forceKitBalance);
        setIfMissing(config, "ffa.kits.mace.wind-charge-refill-seconds", 10);
        setIfMissing(config, "ffa.kits.mace.max-final-damage", 12.0D);
        setIfMissing(config, "ffa.kits.sword.golden-apple-cooldown-seconds", 100);
        setIfMissing(config, "ffa.kits.food-cooldown-seconds", 15);
        setIfMissing(config, "ffa.kits.vampire.lifesteal-percent", 50);
        setIfMissing(config, "ffa.kits.vampire.damage-per-strength-level", 100);
        setIfMissing(config, "ffa.kits.vampire.sun-damage", 1.0D);
        setIfMissing(config, "ffa.kits.gambler.min-random-damage", -10);
        setIfMissing(config, "ffa.kits.gambler.max-random-damage", 20);
        setIfMissing(config, "ffa.kits.gambler.min-incoming-reduction", -5);
        setIfMissing(config, "ffa.kits.gambler.max-incoming-reduction", 5);
        setIfMissingFromLegacy(config, "ffa.kits.gambler.mp-min", "ffa.kits.gambler.em-min", -10);
        setIfMissingFromLegacy(config, "ffa.kits.gambler.mp-max", "ffa.kits.gambler.em-max", 10);
        setIfMissing(config, "ffa.kits.necromancer.max-summons", 5);
        setIfMissing(config, "ffa.kits.trapper.trap-duration-seconds", 30);
        setIfMissing(config, "ffa.kits.trapper.trap-cooldown-seconds", 20);
        setIfMissing(config, "ffa.kits.bug_mania.max-owned-silverfish", 6);
        setIfMissing(config, "ffa.kits.bug_mania.max-global-silverfish", 30);
        setIfMissingFromLegacy(config, "ffa.rewards.kill-mp", "ffa.rewards.kill-em", 50);
        setIfMissing(config, "ffa.rewards.same-target-reset-seconds", 600);
        setIfMissing(config, "ffa.field-items.enabled", true);
        setIfMissing(config, "ffa.field-items.min-players", 2);
        setIfMissing(config, "ffa.field-items.event-despawn-seconds", 60);
        setIfMissing(config, "ffa.field-items.loot-despawn-seconds", 90);
        setIfMissing(config, "ffa.field-items.spawnpoints", List.of());
        setIfMissing(config, "ffa.field-items.event-interval-min-seconds", 150);
        setIfMissing(config, "ffa.field-items.event-interval-max-seconds", 240);
        setIfMissing(config, "ffa.field-items.rarities.common", 50);
        setIfMissing(config, "ffa.field-items.rarities.uncommon", 30);
        setIfMissing(config, "ffa.field-items.rarities.rare", 14);
        setIfMissing(config, "ffa.field-items.rarities.epic", 5);
        setIfMissing(config, "ffa.field-items.rarities.legendary", 1);
        setIfMissing(config, "ffa.field-items.events.rain.weight", 10);
        setIfMissing(config, "ffa.field-items.events.snow.weight", 10);
        setIfMissing(config, "ffa.field-items.events.blizzard.weight", 4);
        setIfMissing(config, "ffa.field-items.events.berserk.weight", 10);
        setIfMissing(config, "ffa.field-items.events.speed.weight", 10);
        setIfMissing(config, "ffa.field-items.events.iron_body.weight", 8);
        setIfMissing(config, "ffa.field-items.events.overdrive.weight", 6);
        setIfMissing(config, "ffa.field-items.events.one_shot_bow.weight", 4);
        setIfMissingFromLegacy(config, "ffa.field-items.events.mp_fever.weight", "ffa.field-items.events.em_fever.weight", 8);
        setIfMissing(config, "ffa.field-items.events.sky_spear.weight", 3);
        setIfMissing(config, "ffa.field-items.events.time_shift.weight", 8);
        setIfMissing(config, "ffa.field-items.events.heal_self.weight", 12);
        setIfMissing(config, "ffa.field-items.events.heal_all.weight", 7);
        setIfMissing(config, "ffa.field-items.events.rain.duration-seconds", 90);
        setIfMissing(config, "ffa.field-items.events.snow.duration-seconds", 90);
        setIfMissing(config, "ffa.field-items.events.blizzard.duration-seconds", 45);
        setIfMissing(config, "ffa.field-items.events.berserk.duration-seconds", 25);
        setIfMissing(config, "ffa.field-items.events.speed.duration-seconds", 20);
        setIfMissing(config, "ffa.field-items.events.iron_body.duration-seconds", 25);
        setIfMissing(config, "ffa.field-items.events.overdrive.duration-seconds", 45);
        setIfMissing(config, "ffa.field-items.events.one_shot_bow.duration-seconds", 25);
        setIfMissingFromLegacy(config, "ffa.field-items.events.mp_fever.duration-seconds", "ffa.field-items.events.em_fever.duration-seconds", 120);
        setIfMissing(config, "ffa.field-items.events.sky_spear.duration-seconds", 45);
        setIfMissing(config, "ffa.field-items.events.time_shift.duration-seconds", 90);
    }

    private void kit(
            FileConfiguration config,
            String key,
            String displayName,
            String icon,
            String armorTier,
            String helmet,
            String chestplate,
            String leggings,
            String boots,
            String food,
            boolean forceArmor,
            boolean forceKitBalance) {
        setDisplayName(config, "ffa.kits." + key + ".display-name", displayName, displayName);
        setIfMissing(config, "ffa.kits." + key + ".icon", icon);
        setIfMissingOrForce(config, "ffa.kits." + key + ".armor-tier", armorTier, forceKitBalance);
        setArmorDefault(config, key, forceArmor, helmet, chestplate, leggings, boots);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".food-items", List.of(food), forceKitBalance);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".items", List.of(), forceKitBalance);
        setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".potions", List.of(), forceKitBalance);
    }

    private void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    private void setIfMissingFromLegacy(FileConfiguration config, String path, String legacyPath, Object value) {
        if (!config.contains(path)) {
            config.set(path, config.contains(legacyPath) ? config.get(legacyPath) : value);
        }
    }

    private void setIfMissingOrLegacy(FileConfiguration config, String path, String value, String legacyValue) {
        if (!config.contains(path) || legacyValue.equalsIgnoreCase(config.getString(path, ""))) {
            config.set(path, value);
        }
    }

    private void setListIfMissingOrEmpty(FileConfiguration config, String path, List<?> value) {
        if (!config.contains(path) || config.getList(path, List.of()).isEmpty()) {
            config.set(path, value);
        }
    }

    private void setIfMissingOrForce(FileConfiguration config, String path, Object value, boolean force) {
        if (force || !config.contains(path)) {
            config.set(path, value);
        }
    }

    private void setListIfMissingOrEmptyOrForce(FileConfiguration config, String path, List<?> value, boolean force) {
        if (force || !config.contains(path) || config.getList(path, List.of()).isEmpty()) {
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
