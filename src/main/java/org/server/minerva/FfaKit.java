package org.server.minerva;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

enum FfaKit {
   AXE("axe", "§6戦士", Material.IRON_AXE, List.of("warrior")),
   BOW("bow", "§a狩人", Material.BOW, List.of("hunter", "archer")),
   SPEAR("spear", "§e槍使い", Material.IRON_SPEAR, List.of()),
   CROSSBOW("crossbow", "§dリボルバー", Material.CROSSBOW, List.of("revolver")),
   SWORD("sword", "§c剣士", Material.IRON_SWORD, List.of("swordsman", "swordman")),
   SHIELD("shield", "§9シールダー", Material.SHIELD, List.of("shielder")),
   TRIDENT("trident", "§b海の戦士", Material.TRIDENT, List.of("sea_warrior")),
   MACE("mace", "§7重戦士", Material.MACE, List.of("heavy_warrior")),
   GAMBLER("gambler", "§6ギャンブラー", Material.GOLDEN_SWORD, List.of()),
   WIZARD("wizard", "§5ケミスト", Material.SPLASH_POTION, List.of("chemist")),
   SNIPER("sniper", "§8スナイパー", Material.CROSSBOW, List.of()),
   VAMPIRE("vampire", "§4ヴァンパイア", Material.REDSTONE, List.of()),
   GRAPPLER("grappler", "§2グラップラー", Material.GOLDEN_CARROT, List.of()),
   ASSASSIN("assassin", "§5アサシン", Material.GOLDEN_SWORD, List.of()),
   NECROMANCER("necromancer", "§5ネクロマンサー", Material.ZOMBIE_SPAWN_EGG, List.of()),
   TRAPPER("trapper", "§eトラッパー", Material.STONE_PRESSURE_PLATE, List.of()),
   BUG_MANIA("bug_mania", "§2バグマニア", Material.SILVERFISH_SPAWN_EGG, List.of("bugmania")),
   CRUSHER("crusher", "§cクラッシャー", Material.TNT, List.of());

   private final String key;
   private final String defaultDisplayName;
   private final Material icon;
   private final List<String> aliases;

   FfaKit(String key, String defaultDisplayName, Material icon, List<String> aliases) {
      this.key = key;
      this.defaultDisplayName = defaultDisplayName;
      this.icon = icon;
      this.aliases = aliases;
   }

   String key() {
      return this.key;
   }

   String displayName(FfaConfig config) {
      return this.configValue(config, "display-name", this.defaultDisplayName);
   }

   Material icon() {
      return this.icon;
   }

   Material icon(FfaConfig config) {
      return material(this.configValue(config, "icon", this.icon.name()), this.icon);
   }

   static List<FfaKit> defaultActiveKits() {
      return List.of(
         AXE,
         BOW,
         SPEAR,
         CROSSBOW,
         SWORD,
         SHIELD,
         TRIDENT,
         MACE,
         GAMBLER,
         WIZARD,
         SNIPER,
         VAMPIRE,
         GRAPPLER,
         ASSASSIN,
         NECROMANCER,
         TRAPPER,
         BUG_MANIA,
         CRUSHER
      );
   }

   static List<FfaKit> activeKits(FfaConfig config) {
      List<String> configured = config.plugin().getConfig().getStringList("ffa.kits.enabled");
      if (configured.isEmpty()) {
         return defaultActiveKits();
      }

      List<FfaKit> kits = new ArrayList<>();

      for (String raw : configured) {
         FfaKit kit = fromKey(raw);
         if (kit != null && !kits.contains(kit)) {
            kits.add(kit);
         }
      }

      if (!kits.contains(CRUSHER)) {
         kits.add(CRUSHER);
      }

      return kits.isEmpty() ? defaultActiveKits() : kits;
   }

   boolean isActive(FfaConfig config) {
      return activeKits(config).contains(this);
   }

   List<Component> details(FfaConfig config, Minerva plugin) {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.text("役割: " + this.role(), NamedTextColor.GRAY));
      lines.add(Component.text("主武器: " + this.mainWeaponLabel(), NamedTextColor.GRAY));
      lines.add(Component.text("防御力: " + this.defenseLabel(), NamedTextColor.GRAY));
      lines.add(Component.text("長所: " + this.strengthLabel(), NamedTextColor.GRAY));
      lines.add(Component.text("弱点: " + this.weaknessLabel(), NamedTextColor.GRAY));
      lines.add(Component.text("食料: " + this.foodLabel(config), NamedTextColor.GRAY));
      lines.add(Component.text("クリックでこのキットを選択", NamedTextColor.YELLOW));
      return lines;
   }

   void applyTo(PlayerInventory inventory, FfaConfig config, Minerva plugin) {
      inventory.clear();
      inventory.setArmorContents(armor(config, this, plugin));
      inventory.setItemInOffHand(null);
      switch (this) {
         case AXE:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(
                     plugin,
                     this,
                     "weapon",
                     material(this.configValue(config, "weapon", "diamond_axe"), Material.DIAMOND_AXE),
                     "§6戦士のダイヤ斧",
                     1,
                     this.enchantments(config, "weapon-enchantments", Map.of("sharpness", 2))
                  )
               }
            );
            break;
         case BOW:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(plugin, this, "bow", Material.BOW, "§a狩人の弓", 1, this.enchantments(config, "bow-enchantments", Map.of("power", 1, "infinity", 1)))
               }
            );
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "arrow", Material.ARROW, "§f狩人の矢", this.amount(config, "arrows", 1), Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", Material.STONE_SWORD, "§a狩人の石剣", 1, Map.of())});
            break;
         case SPEAR:
            Material spear = this.spearMaterial(config, plugin, true);
            if (spear != null) {
               inventory.addItem(
                  new ItemStack[]{kitItem(plugin, this, "spear", spear, "§e槍使いの鉄槍", 1, this.enchantments(config, "weapon-enchantments", Map.of("lunge", 2)))}
               );
            }
            Material spearBackup = material(this.configValue(config, "backup-weapon", "iron_sword"), Material.IRON_SWORD);
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", spearBackup, "§e槍使いの鉄剣", 1, Map.of())});
            break;
         case CROSSBOW:
            inventory.addItem(
               new ItemStack[]{
                  loadedCrossbow(
                     config,
                     plugin,
                     this,
                     "revolver",
                     "§dリボルバー",
                     this.revolverCapacity(config),
                     this.revolverCapacity(config),
                     this.enchantments(config, "crossbow-enchantments", Map.of())
                  )
               }
            );
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", Material.STONE_SWORD, "§dリボルバーの石剣", 1, Map.of())});
            break;
         case SWORD:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(
                     plugin,
                     this,
                     "weapon",
                     material(this.configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD),
                     "§c剣士の鉄剣",
                     1,
                     this.enchantments(config, "weapon-enchantments", Map.of("sharpness", 1))
                  )
               }
            );
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "golden_apple", Material.GOLDEN_APPLE, "§6剣士の金リンゴ", 1, Map.of())});
            break;
         case SHIELD:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(plugin, this, "weapon", material(this.configValue(config, "weapon", "stone_sword"), Material.STONE_SWORD), "§9シールダーの石剣", 1, Map.of())
               }
            );
            inventory.setItemInOffHand(kitItem(plugin, this, "shield", Material.SHIELD, "§9シールダーの盾", 1, Map.of()));
            break;
         case TRIDENT:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(plugin, this, "trident", Material.TRIDENT, "§b海の戦士のトライデント", 1, this.enchantments(config, "weapon-enchantments", Map.of("loyalty", 3)))
               }
            );
            break;
         case MACE:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "mace", Material.MACE, "§7重戦士のメイス", 1, Map.of())});
            inventory.addItem(
               new ItemStack[]{kitItem(plugin, this, "wind_charge", Material.WIND_CHARGE, "§f重戦士のウィンドチャージ", 1, Map.of())}
            );
            break;
         case GAMBLER:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "weapon", Material.GOLDEN_SWORD, "§6運命の金剣", 1, Map.of())});
            break;
         case WIZARD:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(plugin, this, "weapon", material(this.configValue(config, "weapon", "wooden_sword"), Material.WOODEN_SWORD), "§5ケミストの木剣", 1, Map.of())
               }
            );

            for (ItemStack potion : configuredPotions(config, this, plugin)) {
               inventory.addItem(new ItemStack[]{potion});
            }
            break;
         case SNIPER:
            inventory.addItem(
               new ItemStack[]{
                  loadedCrossbow(
                     config,
                     plugin,
                     this,
                     "sniper",
                     "§8スナイパーライフル",
                     this.sniperCapacity(config),
                     this.sniperCapacity(config),
                     this.enchantments(config, "crossbow-enchantments", Map.of("piercing", 4))
                  )
               }
            );
            break;
         case VAMPIRE:
            inventory.addItem(
               new ItemStack[]{
                  kitItem(plugin, this, "weapon", Material.IRON_SWORD, "§4吸血の鉄剣", 1, this.enchantments(config, "weapon-enchantments", Map.of("sharpness", 1)))
               }
            );
         case GRAPPLER:
         default:
            break;
         case ASSASSIN:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "fatal_dagger", Material.GOLDEN_SWORD, "§4致命の短剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "poison_sword", Material.STONE_SWORD, "§2毒の短剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "invisibility_potion", Material.POTION, "§7透明化ポーション", 1, Map.of())});
            break;
         case NECROMANCER:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "weapon", Material.WOODEN_SWORD, "§5死霊術師の木剣", 1, Map.of())});

            for (String mob : List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton")) {
               inventory.addItem(new ItemStack[]{kitItem(plugin, this, "summon_" + mob, spawnEgg(mob), summonName(mob), 1, Map.of())});
            }
            inventory.setItem(8, kitItem(plugin, this, "food", Material.ROTTEN_FLESH, "§5死霊術師の腐肉", 1, Map.of()));
            break;
         case TRAPPER:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "weapon", Material.STONE_SWORD, "§eトラッパーの石剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_explosion", Material.STONE_PRESSURE_PLATE, "§c爆発する感圧板", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_web", Material.OAK_PRESSURE_PLATE, "§f蜘蛛の巣になる感圧板", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_poison", Material.SPRUCE_PRESSURE_PLATE, "§2毒になる感圧板", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_fire", Material.CRIMSON_PRESSURE_PLATE, "§6火炎トラップ", 1, Map.of())});
            break;
         case BUG_MANIA:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "bug_sword", Material.STONE_SWORD, "§2虫食いの剣", 1, Map.of())});
            break;
         case CRUSHER:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "weapon", Material.IRON_AXE, "§cクラッシャーアックス", 1, Map.of())});
      }

      if (this != VAMPIRE) {
         for (ItemStack food : configuredFoodItems(config, this, plugin)) {
            inventory.addItem(new ItemStack[]{food});
         }
      }

      if (this != WIZARD) {
         for (ItemStack potion : configuredPotions(config, this, plugin)) {
            inventory.addItem(new ItemStack[]{potion});
         }
      }

      for (ItemStack item : configuredItems(config, this, plugin)) {
         inventory.addItem(new ItemStack[]{item});
      }
   }

   void applyStandEquipment(EntityEquipment equipment, FfaConfig config, Minerva plugin) {
      if (equipment != null) {
         equipment.setArmorContents(armor(config, this, plugin));
         equipment.setItemInOffHand(null);

         Material hand = switch (this) {
            case AXE -> Material.IRON_AXE;
            case BOW -> Material.BOW;
            case SPEAR -> {
               Material spear = this.spearMaterial(config, plugin, false);
               yield spear == null ? Material.BARRIER : spear;
            }
            case CROSSBOW, SNIPER -> Material.CROSSBOW;
            case SWORD -> Material.IRON_SWORD;
            case SHIELD -> Material.STONE_SWORD;
            case TRIDENT -> Material.TRIDENT;
            case MACE -> Material.MACE;
            case GAMBLER -> Material.GOLDEN_SWORD;
            case WIZARD -> Material.SPLASH_POTION;
            case VAMPIRE -> Material.IRON_SWORD;
            case GRAPPLER -> Material.GOLDEN_CARROT;
            case ASSASSIN -> Material.GOLDEN_SWORD;
            case NECROMANCER -> Material.ZOMBIE_SPAWN_EGG;
            case TRAPPER -> Material.STONE_PRESSURE_PLATE;
            case BUG_MANIA -> Material.SILVERFISH_SPAWN_EGG;
            case CRUSHER -> Material.TNT;
         };
         equipment.setItemInMainHand(kitItem(plugin, this, "stand", hand, this.displayName(config), 1, Map.of()));
         if (this == SHIELD) {
            equipment.setItemInOffHand(kitItem(plugin, this, "shield", Material.SHIELD, "§9シールダーの盾", 1, Map.of()));
         }
      }
   }

   Material spearMaterial(FfaConfig config, Minerva plugin, boolean notify) {
      Material configured = Material.matchMaterial(this.configValue(config, "weapon", "iron_spear").toUpperCase(Locale.ROOT));
      if (configured != null && configured.name().endsWith("_SPEAR")) {
         return configured;
      }

      if (notify) {
         plugin.getLogger().warning("FFA spear material was not found: " + this.configValue(config, "weapon", "iron_spear"));
      }

      return null;
   }

   int revolverCapacity(FfaConfig config) {
      return this.amount(config, "ammo-capacity", 6);
   }

   int sniperCapacity(FfaConfig config) {
      return 1;
   }

   static FfaKit fromKey(String key) {
      if (key == null) {
         return null;
      }

      String normalized = key.toLowerCase(Locale.ROOT);

      for (FfaKit kit : values()) {
         if (kit.key.equalsIgnoreCase(normalized) || kit.name().equalsIgnoreCase(normalized) || kit.aliases.contains(normalized)) {
            return kit;
         }
      }

      return null;
   }

   private String role() {
      return switch (this) {
         case AXE -> "重い一撃の近接型";
         case BOW -> "遠距離特化";
         case SPEAR -> "速度と間合いの中距離型";
         case CROSSBOW -> "6発式の遠距離型";
         case SWORD -> "標準的な万能型";
         case SHIELD -> "盾を使う防御型";
         case TRIDENT -> "天候で変化する中距離型";
         case MACE -> "高低差を使う重装型";
         case GAMBLER -> "運で火力と報酬が揺れる型";
         case WIZARD -> "再使用型ポーション妨害";
         case SNIPER -> "高倍率クロスボウ狙撃";
         case VAMPIRE -> "吸血で伸びる近接型";
         case GRAPPLER -> "素手の高速格闘型";
         case ASSASSIN -> "一度だけHPを削る奇襲型";
         case NECROMANCER -> "召喚で圧をかける型";
         case TRAPPER -> "罠で戦場を制御する型";
         case BUG_MANIA -> "虫食いと虫召喚で妨害";
         case CRUSHER -> "爆発反撃の近接型";
      };
   }

   private String mainWeaponLabel() {
      return switch (this) {
         case AXE -> "鉄の斧";
         case BOW -> "弓";
         case SPEAR -> "鉄の槍";
         case CROSSBOW -> "6発式クロスボウ";
         case SWORD -> "鉄の剣";
         case SHIELD -> "盾 / 石の剣";
         case TRIDENT -> "トライデント";
         case MACE -> "メイス";
         case GAMBLER -> "金の剣";
         case WIZARD -> "ポーション / 木の剣";
         case SNIPER -> "単発式クロスボウ";
         case VAMPIRE -> "鉄の剣";
         case GRAPPLER -> "素手";
         case ASSASSIN -> "致命の剣 / 毒の剣";
         case NECROMANCER -> "召喚卵 / 木の剣";
         case TRAPPER -> "罠 / 石の剣";
         case BUG_MANIA -> "虫食いの剣";
         case CRUSHER -> "クラッシャーアックス";
      };
   }

   private String defenseLabel() {
      return switch (this) {
         case AXE, SWORD, SHIELD, TRIDENT -> "標準";
         case BOW, WIZARD, SNIPER, ASSASSIN, NECROMANCER -> "かなり低い";
         case SPEAR, CROSSBOW, GAMBLER, VAMPIRE, TRAPPER, BUG_MANIA, CRUSHER -> "低め";
         case MACE -> "高め";
         case GRAPPLER -> "なし";
      };
   }

   private String strengthLabel() {
      return switch (this) {
         case AXE -> "近接の一撃が重い";
         case BOW -> "距離を取るほど強い";
         case SPEAR -> "速度と射程がある";
         case CROSSBOW -> "撃ち切るまで連射できる";
         case SWORD -> "扱いやすく金リンゴを持つ";
         case SHIELD -> "正面防御に強い";
         case TRIDENT -> "雨や雷雨で性能が変わる";
         case MACE -> "落下攻撃とウィンドチャージ";
         case GAMBLER -> "高倍率を引くと強い";
         case WIZARD -> "複数の妨害ポーション";
         case SNIPER -> "当たれば高火力";
         case VAMPIRE -> "与ダメージで回復する";
         case GRAPPLER -> "速く素手火力が高い";
         case ASSASSIN -> "致命の剣でHPを削れる";
         case NECROMANCER -> "召喚で人数差を作る";
         case TRAPPER -> "踏ませれば強い";
         case BUG_MANIA -> "虫食いと虫で妨害";
         case CRUSHER -> "被弾時と攻撃時に爆発を起こす";
      };
   }

   private String weaknessLabel() {
      return switch (this) {
         case AXE -> "移動が遅い";
         case BOW -> "接近戦用武器がない";
         case SPEAR -> "防具が薄い";
         case CROSSBOW -> "リロードが長い";
         case SWORD -> "突出した強みはない";
         case SHIELD -> "火力が低め";
         case TRIDENT -> "投擲中に隙ができる";
         case MACE -> "機動の難度が高い";
         case GAMBLER -> "自傷の危険がある";
         case WIZARD -> "防具が薄い";
         case SNIPER -> "移動が遅く近接に弱い";
         case VAMPIRE -> "常時弱体化";
         case GRAPPLER -> "武器防具を使えない";
         case ASSASSIN -> "致命の剣は一度きり";
         case NECROMANCER -> "本体が脆い";
         case TRAPPER -> "設置に隙がある";
         case BUG_MANIA -> "火力は控えめ";
         case CRUSHER -> "爆発は確率発動";
      };
   }

   private String foodLabel(FfaConfig config) {
      List<String> foods = foodSummaries(config, this);
      return foods.isEmpty() ? "なし" : String.join(", ", foods);
   }

   private String defaultArmorTier() {
      return switch (this) {
         case AXE, SWORD -> "chainmail";
         case MACE -> "iron";
         case GRAPPLER -> "none";
         default -> "leather";
      };
   }

   private String configValue(FfaConfig config, String field, String fallback) {
      String value = config.plugin().getConfig().getString(config.kitPath(this, field), fallback);
      return value == null ? fallback : value;
   }

   private int amount(FfaConfig config, String field, int fallback) {
      return Math.max(0, config.plugin().getConfig().getInt(config.kitPath(this, field), fallback));
   }

   static ItemStack loadedCrossbow(
      FfaConfig config, Minerva plugin, FfaKit kit, String kind, String name, int ammo, int capacity, Map<Enchantment, Integer> enchants
   ) {
      ItemStack item = kitItem(plugin, kit, kind, Material.CROSSBOW, name + " §7[" + ammo + "/" + capacity + "]", 1, enchants);
      if (item.getItemMeta() instanceof CrossbowMeta crossbowMeta && ammo > 0) {
         crossbowMeta.removeEnchant(Enchantment.MULTISHOT);
         crossbowMeta.setChargedProjectiles(List.of(new ItemStack(Material.ARROW)));
         item.setItemMeta(crossbowMeta);
      }

      return item;
   }

   private static ItemStack[] armor(FfaConfig config, FfaKit kit, Minerva plugin) {
      if (kit != ASSASSIN && !"none".equalsIgnoreCase(kit.configValue(config, "armor-tier", kit.defaultArmorTier()))) {
         ItemStack[] tierArmor = armorByTier(kit.configValue(config, "armor-tier", kit.defaultArmorTier()));
         ItemStack[] byPiece = armorPieces(config, kit, tierArmor, plugin);
         if (byPiece != null) {
            return byPiece;
         }

         for (int i = 0; i < tierArmor.length; i++) {
            tierArmor[i] = tagItem(plugin, kit, "armor", tierArmor[i], Map.of());
         }

         return tierArmor;
      } else {
         return new ItemStack[]{null, null, null, null};
      }
   }

   private static ItemStack[] armorByTier(String tier) {
      String normalized = tier == null ? "" : tier.toLowerCase(Locale.ROOT);

      return switch (normalized) {
         case "leather" -> new ItemStack[]{
            new ItemStack(Material.LEATHER_BOOTS),
            new ItemStack(Material.LEATHER_LEGGINGS),
            new ItemStack(Material.LEATHER_CHESTPLATE),
            new ItemStack(Material.LEATHER_HELMET)
         };
         case "chain", "chainmail" -> new ItemStack[]{
            new ItemStack(Material.CHAINMAIL_BOOTS),
            new ItemStack(Material.CHAINMAIL_LEGGINGS),
            new ItemStack(Material.CHAINMAIL_CHESTPLATE),
            new ItemStack(Material.CHAINMAIL_HELMET)
         };
         case "gold", "golden" -> new ItemStack[]{
            new ItemStack(Material.GOLDEN_BOOTS),
            new ItemStack(Material.GOLDEN_LEGGINGS),
            new ItemStack(Material.GOLDEN_CHESTPLATE),
            new ItemStack(Material.GOLDEN_HELMET)
         };
         case "diamond" -> new ItemStack[]{
            new ItemStack(Material.DIAMOND_BOOTS),
            new ItemStack(Material.DIAMOND_LEGGINGS),
            new ItemStack(Material.DIAMOND_CHESTPLATE),
            new ItemStack(Material.DIAMOND_HELMET)
         };
         case "netherite" -> new ItemStack[]{
            new ItemStack(Material.NETHERITE_BOOTS),
            new ItemStack(Material.NETHERITE_LEGGINGS),
            new ItemStack(Material.NETHERITE_CHESTPLATE),
            new ItemStack(Material.NETHERITE_HELMET)
         };
         default -> new ItemStack[]{
            new ItemStack(Material.IRON_BOOTS),
            new ItemStack(Material.IRON_LEGGINGS),
            new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_HELMET)
         };
      };
   }

   private static ItemStack[] armorPieces(FfaConfig config, FfaKit kit, ItemStack[] fallback, Minerva plugin) {
      ConfigurationSection section = config.plugin().getConfig().getConfigurationSection("ffa.kits." + kit.key() + ".armor");
      return section == null
         ? null
         : new ItemStack[]{
            tagItem(plugin, kit, "armor_boots", armorPiece(section, "boots", fallback[0]), enchantsForArmorPiece(config, kit, "boots")),
            tagItem(plugin, kit, "armor_leggings", armorPiece(section, "leggings", fallback[1]), enchantsForArmorPiece(config, kit, "leggings")),
            tagItem(plugin, kit, "armor_chestplate", armorPiece(section, "chestplate", fallback[2]), enchantsForArmorPiece(config, kit, "chestplate")),
            tagItem(plugin, kit, "armor_helmet", armorPiece(section, "helmet", fallback[3]), enchantsForArmorPiece(config, kit, "helmet"))
         };
   }

   private static Map<Enchantment, Integer> enchantsForArmorPiece(FfaConfig config, FfaKit kit, String piece) {
      return enchantments(config, kit, piece + "-enchantments", Map.of());
   }

   private static ItemStack armorPiece(ConfigurationSection section, String key, ItemStack fallback) {
      String raw = section.getString(key, "");
      if (raw != null && !raw.isBlank() && !"none".equalsIgnoreCase(raw)) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material != null && material != Material.AIR ? new ItemStack(material) : (fallback == null ? null : fallback.clone());
      } else {
         return "none".equalsIgnoreCase(raw) ? null : (fallback == null ? null : fallback.clone());
      }
   }

   private static List<ItemStack> configuredItems(FfaConfig config, FfaKit kit, Minerva plugin) {
      List<ItemStack> items = new ArrayList<>();

      for (Object raw : config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".items", List.of())) {
         ItemStack item = parseItem(raw, plugin, kit, "item");
         if (item != null) {
            items.add(item);
         }
      }

      return items;
   }

   private static List<ItemStack> configuredFoodItems(FfaConfig config, FfaKit kit, Minerva plugin) {
      List<ItemStack> items = new ArrayList<>();

      for (Object raw : config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".food-items", List.of())) {
         ItemStack item = parseItem(raw, plugin, kit, "food");
         if (item != null) {
            item.setAmount(1);
            items.add(item);
         }
      }

      return items;
   }

   private static List<String> foodSummaries(FfaConfig config, FfaKit kit) {
      List<String> summaries = new ArrayList<>();

      for (Object raw : config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".food-items", List.of())) {
         ItemStack item = parseItem(raw, null, kit, "food");
         if (item != null) {
            summaries.add(stripMaterialName(item.getType()) + " x1");
         }
      }

      return summaries;
   }

   private static ItemStack parseItem(Object raw, Minerva plugin, FfaKit kit, String kind) {
      if (raw instanceof String text) {
         String[] parts = text.split(":");
         Material material = material(parts[0], null);
         if (material == null) {
            return null;
         }

         int amount = parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1;
         return plugin == null ? new ItemStack(material, Math.min(64, amount)) : kitItem(plugin, kit, kind, material, null, Math.min(64, amount), Map.of());
      } else if (raw instanceof Map<?, ?> map) {
         Material material = material(String.valueOf(map.get("material")), null);
         if (material == null) {
            return null;
         }

         int amount = parsePositiveInt(mapValue(map, "amount", "1"), 1);
         String name = map.containsKey("name") ? String.valueOf(map.get("name")) : null;
         Map<Enchantment, Integer> enchantments = enchantmentsFromRaw(map.get("enchantments"), Map.of());
         return plugin == null ? new ItemStack(material, Math.min(64, amount)) : kitItem(plugin, kit, kind, material, name, Math.min(64, amount), enchantments);
      } else {
         return null;
      }
   }

   private static List<ItemStack> configuredPotions(FfaConfig config, FfaKit kit, Minerva plugin) {
      List<ItemStack> potions = new ArrayList<>();

      for (Object raw : config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".potions", List.of())) {
         ItemStack potion = parsePotion(raw, plugin, kit);
         if (potion != null) {
            addPotionItems(potions, potion);
         }
      }

      return potions;
   }

   private static void addPotionItems(List<ItemStack> potions, ItemStack potion) {
      if (isPotionMaterial(potion.getType()) && potion.getAmount() > 1) {
         int amount = potion.getAmount();

         for (int i = 0; i < amount; i++) {
            ItemStack single = potion.clone();
            single.setAmount(1);
            potions.add(single);
         }
      } else {
         potions.add(potion);
      }
   }

   private static boolean isPotionMaterial(Material material) {
      return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
   }

   private static ItemStack parsePotion(Object raw, Minerva plugin, FfaKit kit) {
      if (raw instanceof String text) {
         String[] parts = text.split(":");
         String effectName = parts.length > 0 ? parts[0] : "";
         int level = parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1;
         int seconds = parts.length > 2 ? parsePositiveInt(parts[2], 1) : 30;
         int amount = parts.length > 3 ? parsePositiveInt(parts[3], 1) : 1;
         Material material = effectName.startsWith("splash_") ? Material.SPLASH_POTION : Material.POTION;
         return potion(plugin, kit, material, effectName.replaceFirst("^splash_", ""), level, seconds, amount, null);
      } else if (raw instanceof Map<?, ?> map) {
         String effect = mapValue(map, "effect", "");
         Material material = material(mapValue(map, "material", "splash_potion"), Material.SPLASH_POTION);
         int level = parsePositiveInt(mapValue(map, "level", "1"), 1);
         int seconds = parsePositiveInt(mapValue(map, "seconds", "30"), 1);
         int amount = parsePositiveInt(mapValue(map, "amount", "1"), 1);
         String ability = mapValue(map, "ability", effect.toLowerCase(Locale.ROOT));
         String name = mapValue(map, "name", "");
         return potion(plugin, kit, material, effect, level, seconds, amount, name.isBlank() ? null : name, ability);
      } else {
         return null;
      }
   }

   static ItemStack potion(Minerva plugin, FfaKit kit, Material material, String effectName, int level, int seconds, int amount, String name) {
      return potion(plugin, kit, material, effectName, level, seconds, amount, name, effectName.toLowerCase(Locale.ROOT));
   }

   static ItemStack potion(Minerva plugin, FfaKit kit, Material material, String effectName, int level, int seconds, int amount, String name, String ability) {
      PotionEffectType type = potionEffectType(effectName);
      if (type == null) {
         return null;
      }

      ItemStack item = new ItemStack(material == null ? Material.SPLASH_POTION : material, Math.min(64, amount));
      if (item.getItemMeta() instanceof PotionMeta meta) {
         meta.addCustomEffect(new PotionEffect(type, Math.max(1, seconds) * 20, Math.max(0, level - 1)), true);
         meta.displayName(Component.text(name == null ? ChatColor.LIGHT_PURPLE + potionEffectName(effectName) + " I" : name));
         item.setItemMeta(meta);
      }

      String kind = isNegative(type) ? "chemist_potion_" + ability : "chemist_potion_" + ability;
      return plugin == null ? item : tagItem(plugin, kit, kind, item, Map.of());
   }

   static PotionEffectType potionEffectType(String raw) {
      return switch (raw.toLowerCase(Locale.ROOT)) {
         case "instant_damage", "harming", "harm" -> PotionEffectType.INSTANT_DAMAGE;
         case "instant_health", "heal", "healing" -> PotionEffectType.INSTANT_HEALTH;
         case "poison" -> PotionEffectType.POISON;
         case "slowness", "slow" -> PotionEffectType.SLOWNESS;
         case "weakness", "weak" -> PotionEffectType.WEAKNESS;
         case "blindness", "blind" -> PotionEffectType.BLINDNESS;
         case "speed" -> PotionEffectType.SPEED;
         case "strength", "increase_damage" -> PotionEffectType.STRENGTH;
         case "regeneration", "regen" -> PotionEffectType.REGENERATION;
         case "resistance" -> PotionEffectType.RESISTANCE;
         default -> PotionEffectType.getByName(raw.toUpperCase(Locale.ROOT));
      };
   }

   static boolean isNegative(PotionEffectType type) {
      return type == PotionEffectType.INSTANT_DAMAGE
         || type == PotionEffectType.POISON
         || type == PotionEffectType.SLOWNESS
         || type == PotionEffectType.WEAKNESS
         || type == PotionEffectType.BLINDNESS
         || type == PotionEffectType.WITHER;
   }

   private static String potionEffectName(String raw) {
      return switch (raw.toLowerCase(Locale.ROOT)) {
         case "instant_damage", "harming", "harm" -> "負傷";
         case "instant_health", "heal", "healing" -> "即時回復";
         case "poison" -> "毒";
         case "slowness", "slow" -> "鈍化";
         case "weakness", "weak" -> "弱化";
         case "blindness", "blind" -> "盲目";
         case "speed" -> "移動速度上昇";
         case "strength", "increase_damage" -> "攻撃力上昇";
         case "regeneration", "regen" -> "再生能力";
         case "resistance" -> "耐性";
         default -> raw.toUpperCase(Locale.ROOT);
      };
   }

   static ItemStack kitItem(Minerva plugin, FfaKit kit, String kind, Material material, String name, int amount, Map<Enchantment, Integer> enchantments) {
      ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
      if (name != null && !name.isBlank()) {
         ItemMeta meta = item.getItemMeta();
         meta.displayName(Component.text(name));
         item.setItemMeta(meta);
      }

      return tagItem(plugin, kit, kind, item, enchantments);
   }

   static ItemStack tagItem(Minerva plugin, FfaKit kit, String kind, ItemStack item, Map<Enchantment, Integer> enchantments) {
      if (item != null && item.getType() != Material.AIR) {
         ItemMeta meta = item.getItemMeta();
         boolean fatalSword = "fatal_sword".equals(kind) || "fatal_dagger".equals(kind);
         if (!fatalSword) {
            meta.setUnbreakable(true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_UNBREAKABLE});
         }

         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
         if (!fatalSword) {
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         }

         meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ffa_item"), PersistentDataType.BOOLEAN, true);
         meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ffa_kit"), PersistentDataType.STRING, kit.key());
         meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ffa_item_kind"), PersistentDataType.STRING, kind);
         meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ffa_ability"), PersistentDataType.STRING, kind);
         item.setItemMeta(meta);

         for (Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
         }

         return item;
      } else {
         return item;
      }
   }

   private Map<Enchantment, Integer> enchantments(FfaConfig config, String field, Map<String, Integer> fallback) {
      return enchantments(config, this, field, fallback);
   }

   static Map<Enchantment, Integer> enchantments(FfaConfig config, FfaKit kit, String field, Map<String, Integer> fallback) {
      List<?> raw = config.plugin().getConfig().getList(config.kitPath(kit, field), List.of());
      return raw.isEmpty() ? enchantmentsFromNames(fallback) : enchantmentsFromRaw(raw, enchantmentsFromNames(fallback));
   }

   private static Map<Enchantment, Integer> enchantmentsFromRaw(Object raw, Map<Enchantment, Integer> fallback) {
      if (raw instanceof List<?> list) {
         LinkedHashMap values = new LinkedHashMap();

         for (Object entry : list) {
            String[] parts = String.valueOf(entry).split(":");
            if (parts.length > 0 && !parts[0].isBlank()) {
               values.put(parts[0], parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1);
            }
         }

         Map<Enchantment, Integer> parsed = enchantmentsFromNames(values);
         return parsed.isEmpty() ? fallback : parsed;
      } else {
         return fallback;
      }
   }

   private static Map<Enchantment, Integer> enchantmentsFromNames(Map<String, Integer> raw) {
      Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();

      for (Entry<String, Integer> entry : raw.entrySet()) {
         Enchantment enchantment = enchantment(entry.getKey());
         if (enchantment != null) {
            enchantments.put(enchantment, Math.max(1, entry.getValue()));
         }
      }

      return enchantments;
   }

   private static Enchantment enchantment(String raw) {
      String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');

      return switch (normalized) {
         case "sharpness" -> Enchantment.SHARPNESS;
         case "power" -> Enchantment.POWER;
         case "infinity" -> Enchantment.INFINITY;
         case "loyalty" -> Enchantment.LOYALTY;
         case "riptide" -> Enchantment.RIPTIDE;
         case "channeling" -> Enchantment.CHANNELING;
         case "quick_charge" -> Enchantment.QUICK_CHARGE;
         case "piercing" -> Enchantment.PIERCING;
         case "multishot" -> Enchantment.MULTISHOT;
         case "unbreaking", "durability" -> Enchantment.UNBREAKING;
         case "protection" -> Enchantment.PROTECTION;
         case "feather_falling", "fall_protection" -> Enchantment.FEATHER_FALLING;
         case "density" -> Enchantment.DENSITY;
         case "lunge" -> Enchantment.LUNGE;
         default -> (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized));
      };
   }

   private static Material spawnEgg(String mob) {
      return switch (mob) {
         case "husk" -> Material.HUSK_SPAWN_EGG;
         case "drowned" -> Material.DROWNED_SPAWN_EGG;
         case "skeleton" -> Material.SKELETON_SPAWN_EGG;
         case "stray" -> Material.STRAY_SPAWN_EGG;
         case "bogged" -> Material.BOGGED_SPAWN_EGG;
         case "wither_skeleton" -> Material.WITHER_SKELETON_SPAWN_EGG;
         case "phantom" -> Material.PHANTOM_SPAWN_EGG;
         default -> Material.ZOMBIE_SPAWN_EGG;
      };
   }

   private static String summonName(String mob) {
      return switch (mob) {
         case "husk" -> "§5ハスク召喚";
         case "drowned" -> "§5ドラウンド召喚";
         case "skeleton" -> "§5スケルトン召喚";
         case "stray" -> "§5ストレイ召喚";
         case "bogged" -> "§5ボグド召喚";
         case "wither_skeleton" -> "§5ウィザースケルトン召喚";
         case "phantom" -> "§5ファントム召喚";
         default -> "§5ゾンビ召喚";
      };
   }

   private static String mapValue(Map<?, ?> map, String key, String fallback) {
      Object value = map.get(key);
      return value == null ? fallback : String.valueOf(value);
   }

   private static int parsePositiveInt(String raw, int fallback) {
      try {
         return Math.max(1, Integer.parseInt(raw));
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   static Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank() && !"null".equalsIgnoreCase(raw)) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   private static String stripMaterialName(Material material) {
      return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
   }
}
