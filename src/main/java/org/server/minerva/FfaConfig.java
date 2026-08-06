package org.server.minerva;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

final class FfaConfig {
   private final Minerva plugin;

   FfaConfig(Minerva plugin) {
      this.plugin = plugin;
   }

   Minerva plugin() {
      return this.plugin;
   }

   void ensureDefaults() {
      FileConfiguration config = this.plugin.getConfig();
      this.setIfMissing(config, "ffa.enabled", true);
      this.setIfMissing(config, "ffa.restrictions.drop-items", false);
      this.setIfMissing(config, "ffa.restrictions.pickup-items", false);
      this.setIfMissing(config, "ffa.restrictions.block-break", false);
      this.setIfMissing(config, "ffa.restrictions.block-place", false);
      this.setIfMissing(config, "ffa.restrictions.restrict-commands", true);
      this.setIfMissing(config, "ffa.restrictions.allowed-commands", List.of("/mva ffa leave", "/mva ffa stats"));
      this.setIfMissing(config, "ffa.world-change.leave-on-exit", true);
      this.setIfMissing(config, "ffa.respawn.delay-ticks", 2);
      this.setIfMissing(config, "ffa.respawn.location", "world-spawn");
      this.setIfMissing(config, "ffa.stands.spacing", 2.5);
      this.setIfMissing(config, "ffa.stands.selected-kit", "sword");
      this.setIfMissing(config, "ffa.kits.enabled", FfaKit.defaultActiveKits().stream().map(FfaKit::key).toList());
      int armorBalanceVersion = config.getInt("ffa.kits.armor-balance-version", 0);
      boolean migrateBalancedArmor = armorBalanceVersion < 2;
      boolean migrateArmorInflation = armorBalanceVersion < 3;
      boolean migrateVampireBalance = config.getInt("ffa.kits.vampire-balance-version", 0) < 1;
      boolean migratePermanentKits = config.getInt("ffa.kits.kit-balance-version", 0) < 4;
      boolean migrateGameplayBalance = config.getInt("ffa.kits.gameplay-balance-version", 0) < 1;
      this.setIfMissingOrForce(config, "ffa.kits.armor-points-bonus", 3.0, migrateArmorInflation);
      this.setIfMissing(config, "ffa.kits.default", "sword");
      this.setDisplayName(config, "ffa.kits.bow.display-name", "§aアーチャー", "§a弓キット");
      this.setIfMissing(config, "ffa.kits.bow.icon", "bow");
      this.setIfMissing(config, "ffa.kits.bow.armor-tier", "chainmail");
      this.setArmorDefault(
         config, "bow", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.bow.weapon", "stone_sword", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.bow.bow", true);
      this.setIfMissing(config, "ffa.kits.bow.food", 16);
      this.setIfMissingOrForce(config, "ffa.kits.bow.arrows", 1, migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.food-items", List.of("cooked_chicken:8"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.potions", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmpty(config, "ffa.kits.bow.bow-enchantments", List.of("power:1", "infinity:1"));
      this.setDisplayName(config, "ffa.kits.sword.display-name", "§cソードマン", "§c剣キット");
      this.setIfMissingOrForce(config, "ffa.kits.sword.icon", "iron_sword", migratePermanentKits);
      this.setIfMissingOrForce(config, "ffa.kits.sword.armor-tier", "iron", migratePermanentKits);
      this.setArmorDefault(config, "sword", migrateBalancedArmor || migratePermanentKits, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
      this.setIfMissingOrForce(config, "ffa.kits.sword.weapon", "iron_sword", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.sword.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.food-items", List.of("cooked_beef:6"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.sword.potions", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmpty(config, "ffa.kits.sword.weapon-enchantments", List.of("sharpness:1"));
      this.setDisplayName(config, "ffa.kits.shield.display-name", "§9シールダー", "§9盾キット");
      this.setIfMissing(config, "ffa.kits.shield.icon", "shield");
      this.setIfMissingOrForce(config, "ffa.kits.shield.armor-tier", "chainmail", migratePermanentKits);
      this.setArmorDefault(
         config, "shield", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.shield.weapon", "stone_sword", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.shield.shield", true);
      this.setIfMissing(config, "ffa.kits.shield.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.food-items", List.of("cooked_beef:5"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.shield.potions", List.of(), migratePermanentKits);
      this.setDisplayName(config, "ffa.kits.spear.display-name", "§eスピアー", "§e槍キット");
      this.setIfMissing(config, "ffa.kits.spear.icon", "iron_spear");
      this.setIfMissing(config, "ffa.kits.spear.armor-tier", "chainmail");
      this.setArmorDefault(
         config, "spear", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots"
      );
      this.setIfMissing(config, "ffa.kits.spear.weapon", "iron_spear");
      this.setIfMissing(config, "ffa.kits.spear.backup-weapon", "stone_sword");
      this.setIfMissing(config, "ffa.kits.spear.allow-fallback", false);
      this.setIfMissing(config, "ffa.kits.spear.fallback-weapon", "trident");
      this.setIfMissing(config, "ffa.kits.spear.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.food-items", List.of("baked_potato:10"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.potions", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmpty(config, "ffa.kits.spear.weapon-enchantments", List.of("lunge:1"));
      this.setDisplayName(config, "ffa.kits.axe.display-name", "§6ウォリアー", "§6斧キット");
      this.setIfMissingOrForce(config, "ffa.kits.axe.icon", "iron_axe", migratePermanentKits);
      this.setIfMissingOrForce(config, "ffa.kits.axe.armor-tier", "iron", migratePermanentKits);
      this.setArmorDefault(config, "axe", migrateBalancedArmor || migratePermanentKits, "iron_helmet", "iron_chestplate", "chainmail_leggings", "iron_boots");
      this.setIfMissingOrForce(config, "ffa.kits.axe.weapon", "iron_axe", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.axe.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.food-items", List.of("cooked_porkchop:5"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.potions", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmpty(config, "ffa.kits.axe.weapon-enchantments", List.of("sharpness:1"));
      this.setIfMissing(config, "ffa.kits.axe.slowness-amplifier", 0);
      this.setDisplayName(config, "ffa.kits.crossbow.display-name", "§dリボルバー", "§dクロスボウキット");
      this.setIfMissing(config, "ffa.kits.crossbow.icon", "crossbow");
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.armor-tier", "chainmail", migratePermanentKits);
      this.setArmorDefault(
         config, "crossbow", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.weapon", "stone_sword", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.crossbow.loaded-crossbows", 6);
      this.setIfMissing(config, "ffa.kits.crossbow.arrows", 64);
      this.setIfMissing(config, "ffa.kits.crossbow.quick-charge-level", 1);
      this.setIfMissing(config, "ffa.kits.crossbow.multishot", false);
      this.setIfMissing(config, "ffa.kits.crossbow.piercing-level", 0);
      this.setIfMissing(config, "ffa.kits.crossbow.ammo-capacity", 6);
      this.setIfMissing(config, "ffa.kits.crossbow.reload-ticks", 75);
      this.setIfMissing(config, "ffa.kits.crossbow.damage-multiplier", 0.7);
      this.setIfMissing(config, "ffa.kits.crossbow.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.food-items", List.of("bread:8"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.crossbow.potions", List.of(), migratePermanentKits);
      this.setDisplayName(config, "ffa.kits.wizard.display-name", "§5ウィザード", "§5ウィザード");
      this.setIfMissing(config, "ffa.kits.wizard.icon", "splash_potion");
      this.setIfMissingOrForce(config, "ffa.kits.wizard.armor-tier", "golden", migratePermanentKits);
      this.setArmorDefault(
         config, "wizard", migrateBalancedArmor || migratePermanentKits, "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.wizard.weapon", "wooden_sword", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.wizard.food", 16);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.food-items", List.of("bread:8"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.wizard.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(
         config,
         "ffa.kits.wizard.potions",
         List.of("splash_harm:1:1:2", "splash_poison:1:10:1", "splash_slowness:1:15:1", "splash_heal:1:1:2"),
         migratePermanentKits
      );
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldown-ticks", 60);
      this.setDisplayName(config, "ffa.kits.trident.display-name", "§bトライデント", "§bトライデント");
      this.setIfMissing(config, "ffa.kits.trident.icon", "trident");
      this.setIfMissingOrForce(config, "ffa.kits.trident.armor-tier", "chainmail", migratePermanentKits);
      this.setArmorDefault(
         config, "trident", migrateBalancedArmor || migratePermanentKits, "turtle_helmet", "iron_chestplate", "chainmail_leggings", "iron_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.trident.weapon", "trident", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.trident.food", 16);
      this.setListIfMissingOrEmpty(config, "ffa.kits.trident.weapon-enchantments", List.of("loyalty:3"));
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.food-items", List.of("cooked_salmon:7"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.trident.potions", List.of(), migratePermanentKits);
      this.setDisplayName(config, "ffa.kits.mace.display-name", "§fメイス", "§fメイス");
      this.setIfMissing(config, "ffa.kits.mace.icon", "mace");
      this.setIfMissingOrForce(config, "ffa.kits.mace.armor-tier", "chainmail", migratePermanentKits);
      this.setArmorDefault(
         config, "mace", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "leather_boots"
      );
      this.setIfMissingOrForce(config, "ffa.kits.mace.weapon", "mace", migratePermanentKits);
      this.setIfMissing(config, "ffa.kits.mace.wind-charge", 3);
      this.setIfMissing(config, "ffa.kits.mace.max-final-damage", 12.0);
      this.setIfMissing(config, "ffa.kits.mace.food", 16);
      this.setListIfMissingOrEmpty(config, "ffa.kits.mace.boots-enchantments", List.of("feather_falling:4"));
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.food-items", List.of("cooked_beef:5"), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.items", List.of(), migratePermanentKits);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.mace.potions", List.of(), migratePermanentKits);
      this.applyFfa17Defaults(config, migrateBalancedArmor || migratePermanentKits, migratePermanentKits, migrateVampireBalance);
      this.applyGameplayBalancePass(config, migrateGameplayBalance);
      if (migrateBalancedArmor || migrateArmorInflation || migratePermanentKits) {
         config.set("ffa.kits.armor-balance-version", 3);
         config.set("ffa.kits.kit-balance-version", 4);
      }

      if (migrateVampireBalance) {
         config.set("ffa.kits.vampire-balance-version", 1);
      }

      if (migrateGameplayBalance) {
         config.set("ffa.kits.gameplay-balance-version", 1);
      }

      this.plugin.saveConfig();
   }

   void reload() {
      this.plugin.reloadConfig();
      this.ensureDefaults();
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("ffa.enabled", true);
   }

   Location center() {
      String worldName = this.plugin.getConfig().getString("ffa.center.world");
      if (worldName != null && !worldName.isBlank()) {
         World world = Bukkit.getWorld(worldName);
         return world == null
            ? null
            : new Location(
               world,
               this.plugin.getConfig().getDouble("ffa.center.x"),
               this.plugin.getConfig().getDouble("ffa.center.y"),
               this.plugin.getConfig().getDouble("ffa.center.z"),
               (float)this.plugin.getConfig().getDouble("ffa.center.yaw"),
               (float)this.plugin.getConfig().getDouble("ffa.center.pitch")
            );
      } else {
         return null;
      }
   }

   void setCenter(Location location) {
      this.plugin.getConfig().set("ffa.center.world", location.getWorld().getName());
      this.plugin.getConfig().set("ffa.center.x", location.getX());
      this.plugin.getConfig().set("ffa.center.y", location.getY());
      this.plugin.getConfig().set("ffa.center.z", location.getZ());
      this.plugin.getConfig().set("ffa.center.yaw", location.getYaw());
      this.plugin.getConfig().set("ffa.center.pitch", location.getPitch());
      this.plugin.saveConfig();
   }

   Location kitSelection() {
      String worldName = this.plugin.getConfig().getString("ffa.kit-selection.world");
      if (worldName != null && !worldName.isBlank()) {
         World world = Bukkit.getWorld(worldName);
         return world == null
            ? null
            : new Location(
               world,
               this.plugin.getConfig().getDouble("ffa.kit-selection.x"),
               this.plugin.getConfig().getDouble("ffa.kit-selection.y"),
               this.plugin.getConfig().getDouble("ffa.kit-selection.z"),
               (float)this.plugin.getConfig().getDouble("ffa.kit-selection.yaw"),
               (float)this.plugin.getConfig().getDouble("ffa.kit-selection.pitch")
            );
      } else {
         return null;
      }
   }

   void setKitSelection(Location location) {
      this.plugin.getConfig().set("ffa.kit-selection.world", location.getWorld().getName());
      this.plugin.getConfig().set("ffa.kit-selection.x", location.getX());
      this.plugin.getConfig().set("ffa.kit-selection.y", location.getY());
      this.plugin.getConfig().set("ffa.kit-selection.z", location.getZ());
      this.plugin.getConfig().set("ffa.kit-selection.yaw", location.getYaw());
      this.plugin.getConfig().set("ffa.kit-selection.pitch", location.getPitch());
      this.plugin.saveConfig();
   }

   boolean restriction(String key) {
      return this.plugin.getConfig().getBoolean("ffa.restrictions." + key, false);
   }

   boolean restrictCommands() {
      return this.plugin.getConfig().getBoolean("ffa.restrictions.restrict-commands", true);
   }

   List<String> allowedCommands() {
      return this.plugin
         .getConfig()
         .getStringList("ffa.restrictions.allowed-commands")
         .stream()
         .map(value -> value.toLowerCase(Locale.ROOT).trim())
         .filter(value -> !value.isBlank())
         .toList();
   }

   boolean leaveOnWorldExit() {
      return this.plugin.getConfig().getBoolean("ffa.world-change.leave-on-exit", true);
   }

   long respawnDelayTicks() {
      return Math.max(1L, this.plugin.getConfig().getLong("ffa.respawn.delay-ticks", 2L));
   }

   String respawnLocationMode() {
      return this.plugin.getConfig().getString("ffa.respawn.location", "world-spawn").toLowerCase(Locale.ROOT);
   }

   double standSpacing() {
      return Math.max(1.0, this.plugin.getConfig().getDouble("ffa.stands.spacing", 2.5));
   }

   String kitPath(FfaKit kit, String key) {
      return "ffa.kits." + kit.key() + "." + key;
   }

   private void applyFfa17Defaults(FileConfiguration config, boolean forceArmor, boolean forceKitBalance, boolean forceVampireBalance) {
      if (forceKitBalance) {
         config.set("ffa.kits.enabled", FfaKit.defaultActiveKits().stream().map(FfaKit::key).toList());
      }

      this.setIfMissing(config, "ffa.kits.default", "sword");
      this.kit(
         config,
         "axe",
         "§6戦士",
         "iron_axe",
         "chainmail",
         "none",
         "iron_chestplate",
         "chainmail_leggings",
         "iron_boots",
         "cooked_porkchop:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "bow",
         "§a狩人",
         "bow",
         "leather",
         "leather_helmet",
         "chainmail_chestplate",
         "leather_leggings",
         "none",
         "cooked_rabbit:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "spear",
         "§e槍使い",
         "iron_spear",
         "leather",
         "leather_helmet",
         "chainmail_chestplate",
         "none",
         "leather_boots",
         "baked_potato:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "crossbow",
         "§dリボルバー",
         "crossbow",
         "chainmail",
         "leather_helmet",
         "chainmail_chestplate",
         "none",
         "chainmail_boots",
         "bread:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "sword",
         "§c剣士",
         "iron_sword",
         "chainmail",
         "iron_helmet",
         "chainmail_chestplate",
         "iron_leggings",
         "none",
         "cooked_beef:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "shield",
         "§9シールダー",
         "shield",
         "chainmail",
         "chainmail_helmet",
         "iron_chestplate",
         "leather_leggings",
         "chainmail_boots",
         "pumpkin_pie:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "trident",
         "§b海の戦士",
         "trident",
         "chainmail",
         "turtle_helmet",
         "chainmail_chestplate",
         "chainmail_leggings",
         "none",
         "cooked_salmon:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "mace",
         "§7重戦士",
         "mace",
         "iron",
         "chainmail_helmet",
         "iron_chestplate",
         "iron_leggings",
         "chainmail_boots",
         "cooked_beef:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "gambler",
         "§6ギャンブラー",
         "golden_sword",
         "leather",
         "golden_helmet",
         "leather_chestplate",
         "chainmail_leggings",
         "none",
         "cookie:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "wizard",
         "§5ケミスト",
         "splash_potion",
         "leather",
         "golden_helmet",
         "leather_chestplate",
         "golden_leggings",
         "none",
         "honey_bottle:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "sniper",
         "§8スナイパー",
         "crossbow",
         "leather",
         "none",
         "leather_chestplate",
         "none",
         "leather_boots",
         "baked_potato:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "vampire",
         "§4ヴァンパイア",
         "redstone",
         "leather",
         "chainmail_helmet",
         "leather_chestplate",
         "chainmail_leggings",
         "none",
         "cooked_beef:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(config, "grappler", "§2グラップラー", "golden_carrot", "none", "none", "none", "none", "none", "golden_carrot:1", forceArmor, forceKitBalance);
      this.kit(
         config,
         "assassin",
         "§5アサシン",
         "golden_sword",
         "leather",
         "leather_helmet",
         "leather_chestplate",
         "none",
         "chainmail_boots",
         "sweet_berries:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "necromancer",
         "§5ネクロマンサー",
         "zombie_spawn_egg",
         "leather",
         "golden_helmet",
         "leather_chestplate",
         "none",
         "leather_boots",
         "rotten_flesh:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "trapper",
         "§eトラッパー",
         "stone_pressure_plate",
         "leather",
         "chainmail_helmet",
         "leather_chestplate",
         "chainmail_leggings",
         "none",
         "baked_potato:1",
         forceArmor,
         forceKitBalance
      );
      this.kit(
         config,
         "bug_mania",
         "§2バグマニア",
         "silverfish_spawn_egg",
         "leather",
         "leather_helmet",
         "chainmail_chestplate",
         "leather_leggings",
         "none",
         "spider_eye:1",
         forceArmor,
         forceKitBalance
      );
      this.setListIfMissingOrEmptyOrForce(
         config,
         "ffa.kits.wizard.potions",
         List.of(
            Map.of("effect", "slowness", "material", "splash_potion", "level", 1, "seconds", 8, "amount", 1, "ability", "slow", "name", "§7鈍化のスプラッシュポーション"),
            Map.of("effect", "harm", "material", "splash_potion", "level", 1, "seconds", 1, "amount", 1, "ability", "harm", "name", "§5負傷のスプラッシュポーション"),
            Map.of("effect", "poison", "material", "splash_potion", "level", 1, "seconds", 8, "amount", 1, "ability", "poison", "name", "§2毒のスプラッシュポーション"),
            Map.of("effect", "weakness", "material", "splash_potion", "level", 1, "seconds", 10, "amount", 1, "ability", "weakness", "name", "§8弱化のスプラッシュポーション"),
            Map.of(
               "effect", "blindness", "material", "splash_potion", "level", 1, "seconds", 5, "amount", 1, "ability", "blindness", "name", "§0盲目のスプラッシュポーション"
            )
         ),
         forceKitBalance
      );
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.slow", 12);
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.harm", 15);
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.poison", 18);
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.weakness", 15);
      this.setIfMissing(config, "ffa.kits.wizard.potion-cooldowns.blindness", 25);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.ammo-capacity", 6, forceKitBalance);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.reload-ticks", 75, forceKitBalance);
      this.setIfMissing(config, "ffa.kits.crossbow.damage-multiplier", 0.7);
      this.setIfMissingOrForce(config, "ffa.kits.sniper.ammo-capacity", 2, forceKitBalance);
      this.setIfMissingOrForce(config, "ffa.kits.sniper.reload-ticks", 125, forceKitBalance);
      this.setIfMissingOrForce(config, "ffa.kits.sniper.damage-multiplier", 3.0, true);
      this.setListIfMissingOrEmpty(config, "ffa.kits.sniper.crossbow-enchantments", List.of("piercing:4"));
      this.setDisplayName(config, "ffa.kits.assassin.display-name", "§5アサシン", "§0アサシン");
      this.setIfMissingOrLegacy(config, "ffa.kits.assassin.icon", "golden_sword", "iron_sword");
      this.setIfMissingOrForce(config, "ffa.kits.assassin.armor-tier", "none", true);
      this.setIfMissingOrForce(config, "ffa.kits.assassin.armor.helmet", "none", true);
      this.setIfMissingOrForce(config, "ffa.kits.assassin.armor.chestplate", "none", true);
      this.setIfMissingOrForce(config, "ffa.kits.assassin.armor.leggings", "none", true);
      this.setIfMissingOrForce(config, "ffa.kits.assassin.armor.boots", "none", true);
      this.setListIfMissingOrEmpty(config, "ffa.kits.assassin.fatal-sword-enchantments", List.of("unbreaking:1"));
      this.setIfMissingOrForce(config, "ffa.kits.mace.wind-charge", 10, forceKitBalance);
      this.setIfMissing(config, "ffa.kits.mace.wind-charge-refill-seconds", 10);
      this.setIfMissing(config, "ffa.kits.mace.max-final-damage", 12.0);
      this.setIfMissing(config, "ffa.kits.sword.golden-apple-cooldown-seconds", 100);
      this.setIfMissing(config, "ffa.kits.food-cooldown-seconds", 15);
      this.setIfMissing(config, "ffa.kits.vampire.lifesteal-percent", 50);
      this.setIfMissing(config, "ffa.kits.vampire.damage-per-strength-level", 100);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.sun-damage", 2.0, forceVampireBalance);
      this.setIfMissingOrForce(config, "ffa.kits.gambler.min-damage-multiplier", -10.0, true);
      this.setIfMissingOrForce(config, "ffa.kits.gambler.max-damage-multiplier", 15.0, true);
      this.setIfMissingFromLegacy(config, "ffa.kits.gambler.mp-min", "ffa.kits.gambler.em-min", -10);
      this.setIfMissingFromLegacy(config, "ffa.kits.gambler.mp-max", "ffa.kits.gambler.em-max", 10);
      this.setIfMissing(config, "ffa.kits.necromancer.max-summons", 5);
      this.setIfMissing(config, "ffa.kits.trapper.trap-duration-seconds", 30);
      this.setIfMissing(config, "ffa.kits.trapper.trap-cooldown-seconds", 20);
      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-initial-damage", 2.0);
      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-burn-seconds", 6);
      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-radius", 2.5);
      this.setIfMissing(config, "ffa.kits.bug_mania.max-owned-silverfish", 6);
      this.setIfMissing(config, "ffa.kits.bug_mania.max-global-silverfish", 30);
      this.setIfMissing(config, "ffa.kits.crusher.display-name", "§cクラッシャー");
      this.setIfMissing(config, "ffa.kits.crusher.icon", "tnt");
      this.setIfMissing(config, "ffa.kits.crusher.armor-tier", "leather");
      this.setIfMissing(config, "ffa.kits.crusher.armor.helmet", "leather_helmet");
      this.setIfMissing(config, "ffa.kits.crusher.armor.chestplate", "leather_chestplate");
      this.setIfMissing(config, "ffa.kits.crusher.armor.leggings", "leather_leggings");
      this.setIfMissing(config, "ffa.kits.crusher.armor.boots", "leather_boots");
      this.setListIfMissingOrEmpty(config, "ffa.kits.crusher.food-items", List.of("cooked_beef:1"));
      this.setListIfMissingOrEmpty(config, "ffa.kits.crusher.items", List.of());
      this.setListIfMissingOrEmpty(config, "ffa.kits.crusher.potions", List.of());
      this.setIfMissingFromLegacy(config, "ffa.rewards.kill-mp", "ffa.rewards.kill-em", 50);
      this.setIfMissing(config, "ffa.rewards.same-target-reset-seconds", 600);
      this.setIfMissing(config, "ffa.field-items.enabled", true);
      this.setIfMissing(config, "ffa.field-items.min-players", 2);
      this.setIfMissing(config, "ffa.field-items.event-despawn-seconds", 60);
      this.setIfMissing(config, "ffa.field-items.loot-despawn-seconds", 90);
      this.setIfMissing(config, "ffa.field-items.spawnpoints", List.of());
      this.setIfMissing(config, "ffa.field-items.event-interval-min-seconds", 150);
      this.setIfMissing(config, "ffa.field-items.event-interval-max-seconds", 240);
      this.setIfMissing(config, "ffa.field-items.rarities.common", 50);
      this.setIfMissing(config, "ffa.field-items.rarities.uncommon", 30);
      this.setIfMissing(config, "ffa.field-items.rarities.rare", 14);
      this.setIfMissing(config, "ffa.field-items.rarities.epic", 5);
      this.setIfMissing(config, "ffa.field-items.rarities.legendary", 1);
      this.setIfMissing(config, "ffa.field-items.events.rain.weight", 10);
      this.setIfMissing(config, "ffa.field-items.events.snow.weight", 10);
      this.setIfMissing(config, "ffa.field-items.events.blizzard.weight", 4);
      this.setIfMissing(config, "ffa.field-items.events.berserk.weight", 10);
      this.setIfMissing(config, "ffa.field-items.events.speed.weight", 10);
      this.setIfMissing(config, "ffa.field-items.events.iron_body.weight", 8);
      this.setIfMissing(config, "ffa.field-items.events.overdrive.weight", 6);
      this.setIfMissing(config, "ffa.field-items.events.one_shot_bow.weight", 4);
      this.setIfMissingFromLegacy(config, "ffa.field-items.events.mp_fever.weight", "ffa.field-items.events.em_fever.weight", 8);
      this.setIfMissing(config, "ffa.field-items.events.sky_spear.weight", 3);
      this.setIfMissing(config, "ffa.field-items.events.time_shift.weight", 8);
      this.setIfMissing(config, "ffa.field-items.events.heal_self.weight", 12);
      this.setIfMissing(config, "ffa.field-items.events.heal_all.weight", 7);
      this.setIfMissing(config, "ffa.field-items.events.rain.duration-seconds", 90);
      this.setIfMissing(config, "ffa.field-items.events.snow.duration-seconds", 90);
      this.setIfMissing(config, "ffa.field-items.events.blizzard.duration-seconds", 45);
      this.setIfMissing(config, "ffa.field-items.events.berserk.duration-seconds", 25);
      this.setIfMissing(config, "ffa.field-items.events.speed.duration-seconds", 20);
      this.setIfMissing(config, "ffa.field-items.events.iron_body.duration-seconds", 25);
      this.setIfMissing(config, "ffa.field-items.events.overdrive.duration-seconds", 45);
      this.setIfMissing(config, "ffa.field-items.events.one_shot_bow.duration-seconds", 25);
      this.setIfMissingFromLegacy(config, "ffa.field-items.events.mp_fever.duration-seconds", "ffa.field-items.events.em_fever.duration-seconds", 120);
      this.setIfMissing(config, "ffa.field-items.events.sky_spear.duration-seconds", 45);
      this.setIfMissing(config, "ffa.field-items.events.time_shift.duration-seconds", 90);
   }

   private void applyGameplayBalancePass(FileConfiguration config, boolean force) {
      // Basic kits: reliable equipment and modest, consistent utility.
      this.setArmorDefault(config, "axe", force, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
      this.setArmorDefault(config, "bow", force, "leather_helmet", "chainmail_chestplate", "leather_leggings", "leather_boots");
      this.setArmorDefault(config, "spear", force, "leather_helmet", "chainmail_chestplate", "chainmail_leggings", "leather_boots");
      this.setArmorDefault(config, "crossbow", force, "leather_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots");
      this.setArmorDefault(config, "sword", force, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
      this.setArmorDefault(config, "shield", force, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots");
      this.setArmorDefault(config, "trident", force, "turtle_helmet", "chainmail_chestplate", "chainmail_leggings", "iron_boots");
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.bow-enchantments", List.of("power:2", "infinity:1"), force);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.damage-multiplier", 0.85, force);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.reload-ticks", 65, force);
      this.setIfMissingOrForce(config, "ffa.kits.sword.golden-apple-cooldown-seconds", 90, force);

      // Special kits: higher ceiling, but clearer risks and setup requirements.
      this.setIfMissingOrForce(config, "ffa.kits.sniper.damage-multiplier", 2.5, force);
      this.setIfMissingOrForce(config, "ffa.kits.sniper.reload-ticks", 100, force);
      this.setIfMissingOrForce(config, "ffa.kits.mace.max-final-damage", 10.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.mace.wind-charge-refill-seconds", 8, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.damage-buff-threshold", 60.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.max-damage-buff-tier", 4, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.damage-buff-per-tier-percent", 12.5, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.lifesteal-percent", 30.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.hunger-steal-percent", 30.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.trapper.trap-cooldown-seconds", 16, force);
      this.setIfMissingOrForce(config, "ffa.kits.bug_mania.max-owned-silverfish", 5, force);
      this.setIfMissingOrForce(config, "ffa.kits.crusher.activation-cooldown-ticks", 30, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.slow", 10, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.harm", 13, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.poison", 16, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.weakness", 13, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.blindness", 22, force);
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
      boolean forceKitBalance
   ) {
      this.setDisplayName(config, "ffa.kits." + key + ".display-name", displayName, displayName);
      this.setIfMissing(config, "ffa.kits." + key + ".icon", icon);
      this.setIfMissingOrForce(config, "ffa.kits." + key + ".armor-tier", armorTier, forceKitBalance);
      this.setArmorDefault(config, key, forceArmor, helmet, chestplate, leggings, boots);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".food-items", List.of(food), forceKitBalance);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".items", List.of(), forceKitBalance);
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits." + key + ".potions", List.of(), forceKitBalance);
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

   private void setArmorDefault(FileConfiguration config, String kit, boolean force, String helmet, String chestplate, String leggings, String boots) {
      String path = "ffa.kits." + kit + ".armor.";
      if (force
         || !config.contains(path + "helmet")
         || !config.contains(path + "chestplate")
         || !config.contains(path + "leggings")
         || !config.contains(path + "boots")
         || this.isUniformArmorSet(config, path)) {
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
         if (helmet.equals(tier + "_helmet") && chestplate.equals(tier + "_chestplate") && leggings.equals(tier + "_leggings") && boots.equals(tier + "_boots")
            )
          {
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
