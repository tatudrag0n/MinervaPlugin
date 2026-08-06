from pathlib import Path


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = source.find('{', start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[:start] + replacement + source[index + 1:]
    raise SystemExit(f'unclosed method: {signature}')


path = Path('src/main/java/org/server/minerva/FfaFieldItemManager.java')
text = path.read_text(encoding='utf-8')

# Keep the exact reward selected at spawn time, so the visible dropped item and
# the acquired reward cannot diverge.
if 'private final NamespacedKey fieldLootKey;' not in text:
    marker = '   private final NamespacedKey fieldEventKey;'
    if marker not in text:
        raise SystemExit('fieldEventKey declaration not found')
    text = text.replace(marker, marker + '\n   private final NamespacedKey fieldLootKey;', 1)

if 'private final Map<UUID, List<ItemStack>> pendingLoot' not in text:
    marker = '   private final Map<UUID, ItemStack> skyChestplates = new HashMap<>();'
    if marker not in text:
        raise SystemExit('skyChestplates declaration not found')
    text = text.replace(marker, marker + '\n   private final Map<UUID, List<ItemStack>> pendingLoot = new HashMap<>();', 1)

if 'this.fieldLootKey = new NamespacedKey(plugin, "ffa_field_loot");' not in text:
    marker = '      this.fieldEventKey = new NamespacedKey(plugin, "ffa_field_event");'
    if marker not in text:
        raise SystemExit('fieldEventKey constructor assignment not found')
    text = text.replace(marker, marker + '\n      this.fieldLootKey = new NamespacedKey(plugin, "ffa_field_loot");', 1)

shutdown_marker = '      this.activeEvents.clear();'
if '      this.pendingLoot.clear();' not in text[text.find('   void shutdown()'):text.find('   boolean handleCommand(')]:
    if shutdown_marker not in text:
        raise SystemExit('shutdown marker not found')
    text = text.replace(shutdown_marker, shutdown_marker + '\n      this.pendingLoot.clear();', 1)

handle_pickup = '''   boolean handlePickup(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player) || !this.ffa.isPlaying(player)) {
         return false;
      }

      Item item = event.getItem();
      if (!this.isFieldItem(item)) {
         return false;
      }

      event.setCancelled(true);
      String eventType = item.getPersistentDataContainer().get(this.fieldEventKey, PersistentDataType.STRING);
      if (eventType != null && !eventType.isBlank()) {
         this.startEvent(eventType, player);
      } else {
         String rarity = item.getPersistentDataContainer().get(this.fieldTypeKey, PersistentDataType.STRING);
         String lootId = item.getPersistentDataContainer().get(this.fieldLootKey, PersistentDataType.STRING);
         List<ItemStack> exactReward = this.pendingLoot.remove(item.getUniqueId());
         this.giveLoot(player, rarity, lootId, exactReward);
      }

      this.fieldItems.remove(item.getUniqueId());
      this.pendingLoot.remove(item.getUniqueId());
      item.remove();
      return true;
   }'''
text = replace_method(text, '   boolean handlePickup(EntityPickupItemEvent event)', handle_pickup)

spawn_loot = '''   private void spawnLoot(Location location, String requestedType) {
      if (location == null || location.getWorld() == null) {
         return;
      }

      String rarity = "random".equalsIgnoreCase(requestedType) ? this.randomRarity() : requestedType.toLowerCase(Locale.ROOT);
      FfaFieldItemManager.LootDrop drop = this.rollLoot(rarity);
      ItemStack display = drop.items().get(0).clone();
      Item item = location.getWorld().dropItem(location, display);
      this.tagFieldItem(item, rarity, null);
      item.getPersistentDataContainer().set(this.fieldLootKey, PersistentDataType.STRING, drop.id());
      this.pendingLoot.put(item.getUniqueId(), this.cloneLoot(drop.items()));
      this.scheduleRemove(item, this.plugin.getConfig().getLong("ffa.field-items.loot-despawn-seconds", 90L));
      if ("legendary".equals(rarity)) {
         this.broadcast("§6レジェンダリーアイテムが出現しました: §f" + this.itemName(display));
      }
   }'''
text = replace_method(text, '   private void spawnLoot(Location location, String requestedType)', spawn_loot)

give_loot = '''   private void giveLoot(Player player, String rarity, String lootId, List<ItemStack> exactReward) {
      FfaFieldItemManager.LootDrop storedDrop = this.lootById(lootId);
      List<ItemStack> items;
      if (exactReward != null && !exactReward.isEmpty()) {
         items = this.cloneLoot(exactReward);
      } else if (storedDrop != null) {
         items = this.cloneLoot(storedDrop.items());
      } else {
         items = this.cloneLoot(this.rollLoot(rarity == null ? "common" : rarity).items());
      }

      if (this.ffa.currentKit(player) == FfaKit.GRAPPLER && items.stream().anyMatch(item -> this.isFieldEquipment(item.getType()))) {
         player.sendActionBar(Component.text("グラップラーはフィールド装備を取得できません", NamedTextColor.RED));
         return;
      }

      for (ItemStack item : items) {
         FfaKit.tagItem(this.plugin, FfaKit.SWORD, "field_" + (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)), item, Map.of());
         ItemMeta meta = item.getItemMeta();
         meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "ffa_field_owned"), PersistentDataType.BOOLEAN, true);
         meta.getPersistentDataContainer().set(this.ffaOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         item.setItemMeta(meta);
         player.getInventory().addItem(item);
      }

      player.sendActionBar(Component.text("フィールドアイテム取得: " + this.itemName(items.get(0)), NamedTextColor.GOLD));
   }'''
text = replace_method(text, '   private void giveLoot(Player player, String rarity)', give_loot)

max_loot = '''   private int maxLoot(int players) {
      if (players >= 9) {
         return Math.max(1, this.plugin.getConfig().getInt("ffa.field-items.max-active.large", 7));
      } else if (players >= 5) {
         return Math.max(1, this.plugin.getConfig().getInt("ffa.field-items.max-active.medium", 5));
      } else {
         return Math.max(1, this.plugin.getConfig().getInt("ffa.field-items.max-active.small", 3));
      }
   }'''
text = replace_method(text, '   private int maxLoot(int players)', max_loot)

loot_interval = '''   private long[] lootInterval(int players) {
      String size = players >= 9 ? "large" : players >= 5 ? "medium" : "small";
      long fallbackMin = players >= 9 ? 18000L : players >= 5 ? 25000L : 35000L;
      long fallbackMax = players >= 9 ? 32000L : players >= 5 ? 40000L : 55000L;
      long min = Math.max(1000L, this.plugin.getConfig().getLong("ffa.field-items.spawn-intervals." + size + ".min-seconds", fallbackMin / 1000L) * 1000L);
      long max = Math.max(min, this.plugin.getConfig().getLong("ffa.field-items.spawn-intervals." + size + ".max-seconds", fallbackMax / 1000L) * 1000L);
      return new long[]{min, max};
   }'''
text = replace_method(text, '   private long[] lootInterval(int players)', loot_interval)

# Replace the old rarity placeholder display and independently rerolled reward
# tables with deterministic loot definitions. The first stack is both the world
# display and the first stack actually granted to the player.
block_start = text.find('   private ItemStack lootItem(String rarity)')
block_end = text.find('   private ItemStack fieldPotion(', block_start)
if block_start < 0 or block_end < 0:
    raise SystemExit('loot method block not found')

loot_block = '''   private FfaFieldItemManager.LootDrop rollLoot(String rarity) {
      List<FfaFieldItemManager.LootDrop> pool = this.lootPool(rarity);
      return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
   }

   private FfaFieldItemManager.LootDrop lootById(String id) {
      if (id == null || id.isBlank()) {
         return null;
      }

      for (String rarity : List.of("common", "uncommon", "rare", "epic", "legendary")) {
         for (FfaFieldItemManager.LootDrop drop : this.lootPool(rarity)) {
            if (id.equals(drop.id())) {
               return drop;
            }
         }
      }
      return null;
   }

   private List<FfaFieldItemManager.LootDrop> lootPool(String rarity) {
      return switch (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)) {
         case "legendary" -> this.legendaryLootPool();
         case "epic" -> this.epicLootPool();
         case "rare" -> this.rareLootPool();
         case "uncommon" -> this.uncommonLootPool();
         default -> this.commonLootPool();
      };
   }

   private List<FfaFieldItemManager.LootDrop> commonLootPool() {
      return List.of(
         this.drop("common_beef", this.namedAmount(Material.COOKED_BEEF, 3, "§f焼き牛肉 ×3")),
         this.drop("common_bread", this.namedAmount(Material.BREAD, 5, "§fパン ×5")),
         this.drop("common_potato", this.namedAmount(Material.BAKED_POTATO, 6, "§fベイクドポテト ×6")),
         this.drop("common_arrows", this.namedAmount(Material.ARROW, 10, "§f矢 ×10")),
         this.drop("common_snowballs", this.namedAmount(Material.SNOWBALL, 12, "§f雪玉 ×12")),
         this.drop("common_eggs", this.namedAmount(Material.EGG, 12, "§f卵 ×12")),
         this.drop("common_wind", this.namedAmount(Material.WIND_CHARGE, 2, "§fウィンドチャージ ×2")),
         this.drop("common_honey", this.namedAmount(Material.HONEY_BOTTLE, 2, "§fハチミツ入りの瓶 ×2")),
         this.drop("common_heal", this.fieldPotion("heal", 1, 1, "§d即時回復ポーション")),
         this.drop("common_stone_sword", this.enchanted(Material.STONE_SWORD, "§f石の剣", Map.of("sharpness", 1))),
         this.drop("common_leather_helmet", this.enchanted(Material.LEATHER_HELMET, "§f革の帽子", Map.of("protection", 2))),
         this.drop("common_leather_chest", this.enchanted(Material.LEATHER_CHESTPLATE, "§f革の上着", Map.of("protection", 2))),
         this.drop("common_leather_legs", this.enchanted(Material.LEATHER_LEGGINGS, "§f革のズボン", Map.of("protection", 2))),
         this.drop("common_leather_boots", this.enchanted(Material.LEATHER_BOOTS, "§f革のブーツ", Map.of("protection", 2)))
      );
   }

   private List<FfaFieldItemManager.LootDrop> uncommonLootPool() {
      return List.of(
         this.drop("uncommon_pearls", this.namedAmount(Material.ENDER_PEARL, 2, "§aエンダーパール ×2")),
         this.drop("uncommon_carrots", this.namedAmount(Material.GOLDEN_CARROT, 4, "§a金のニンジン ×4")),
         this.drop("uncommon_speed", this.fieldPotion("speed", 1, 20, "§a移動速度上昇ポーション")),
         this.drop("uncommon_resistance", this.fieldPotion("resistance", 1, 18, "§a耐性ポーション")),
         this.drop("uncommon_heal_pair", this.fieldPotion("heal", 1, 1, "§a即時回復ポーション"), this.fieldPotion("heal", 1, 1, "§a即時回復ポーション")),
         this.drop("uncommon_iron_sword", this.enchanted(Material.IRON_SWORD, "§a鉄の剣", Map.of("sharpness", 1))),
         this.drop("uncommon_iron_axe", this.enchanted(Material.IRON_AXE, "§a鉄の斧", Map.of("sharpness", 1))),
         this.drop("uncommon_bow", this.enchanted(Material.BOW, "§a強化弓", Map.of("power", 1)), this.namedAmount(Material.ARROW, 8, "§a矢 ×8")),
         this.drop("uncommon_crossbow", this.enchanted(Material.CROSSBOW, "§a高速装填クロスボウ", Map.of("quick_charge", 1)), this.namedAmount(Material.ARROW, 6, "§a矢 ×6")),
         this.drop("uncommon_shield", this.named(Material.SHIELD, "§a盾")),
         this.drop("uncommon_turtle", this.named(Material.TURTLE_HELMET, "§a亀の甲羅")),
         this.drop("uncommon_chain_helmet", this.enchanted(Material.CHAINMAIL_HELMET, "§aチェーンのヘルメット", Map.of("protection", 2))),
         this.drop("uncommon_chain_chest", this.enchanted(Material.CHAINMAIL_CHESTPLATE, "§aチェーンのチェストプレート", Map.of("protection", 2))),
         this.drop("uncommon_chain_legs", this.enchanted(Material.CHAINMAIL_LEGGINGS, "§aチェーンのレギンス", Map.of("protection", 2))),
         this.drop("uncommon_chain_boots", this.enchanted(Material.CHAINMAIL_BOOTS, "§aチェーンのブーツ", Map.of("protection", 2))),
         this.drop("uncommon_wind", this.namedAmount(Material.WIND_CHARGE, 4, "§aウィンドチャージ ×4"))
      );
   }

   private List<FfaFieldItemManager.LootDrop> rareLootPool() {
      return List.of(
         this.drop("rare_diamond_sword", this.named(Material.DIAMOND_SWORD, "§bダイヤモンドの剣")),
         this.drop("rare_diamond_axe", this.named(Material.DIAMOND_AXE, "§bダイヤモンドの斧")),
         this.drop("rare_bow", this.enchanted(Material.BOW, "§b強化弓", Map.of("power", 2)), this.namedAmount(Material.ARROW, 10, "§b矢 ×10")),
         this.drop("rare_crossbow", this.enchanted(Material.CROSSBOW, "§b高速装填クロスボウ", Map.of("quick_charge", 2)), this.namedAmount(Material.ARROW, 8, "§b矢 ×8")),
         this.drop("rare_spear", this.enchanted(this.material("IRON_SPEAR", Material.TRIDENT), "§b鉄の槍", Map.of("lunge", 1))),
         this.drop("rare_trident", this.enchanted(Material.TRIDENT, "§bトライデント", Map.of("loyalty", 1))),
         this.drop("rare_iron_helmet", this.enchanted(Material.IRON_HELMET, "§b鉄のヘルメット", Map.of("protection", 3))),
         this.drop("rare_iron_chest", this.enchanted(Material.IRON_CHESTPLATE, "§b鉄のチェストプレート", Map.of("protection", 3))),
         this.drop("rare_iron_legs", this.enchanted(Material.IRON_LEGGINGS, "§b鉄のレギンス", Map.of("protection", 3))),
         this.drop("rare_iron_boots", this.enchanted(Material.IRON_BOOTS, "§b鉄のブーツ", Map.of("protection", 3))),
         this.drop("rare_diamond_boots", this.enchanted(Material.DIAMOND_BOOTS, "§bダイヤモンドのブーツ", Map.of("protection", 1))),
         this.drop("rare_golden_apples", this.namedAmount(Material.GOLDEN_APPLE, 2, "§b金のリンゴ ×2")),
         this.drop("rare_strength", this.fieldPotion("strength", 1, 20, "§b攻撃力上昇ポーション")),
         this.drop("rare_regeneration", this.fieldPotion("regeneration", 1, 20, "§b再生能力ポーション")),
         this.drop("rare_pearls", this.namedAmount(Material.ENDER_PEARL, 4, "§bエンダーパール ×4")),
         this.drop("rare_mace", this.enchanted(Material.MACE, "§bメイス", Map.of("density", 1)), this.namedAmount(Material.WIND_CHARGE, 3, "§bウィンドチャージ ×3"))
      );
   }

   private List<FfaFieldItemManager.LootDrop> epicLootPool() {
      return List.of(
         this.drop("epic_diamond_sword", this.enchanted(Material.DIAMOND_SWORD, "§5ダイヤモンドの剣", Map.of("sharpness", 2))),
         this.drop("epic_diamond_axe", this.enchanted(Material.DIAMOND_AXE, "§5ダイヤモンドの斧", Map.of("sharpness", 2))),
         this.drop("epic_diamond_spear", this.enchanted(this.material("DIAMOND_SPEAR", this.material("IRON_SPEAR", Material.TRIDENT)), "§5ダイヤモンドの槍", Map.of("lunge", 2))),
         this.drop("epic_bow", this.enchanted(Material.BOW, "§5強化弓", Map.of("power", 3)), this.namedAmount(Material.ARROW, 14, "§5矢 ×14")),
         this.drop("epic_crossbow", this.enchanted(Material.CROSSBOW, "§5高速装填クロスボウ", Map.of("quick_charge", 3)), this.namedAmount(Material.ARROW, 12, "§5矢 ×12")),
         this.drop("epic_mace", this.enchanted(Material.MACE, "§5メイス", Map.of("density", 2)), this.namedAmount(Material.WIND_CHARGE, 5, "§5ウィンドチャージ ×5")),
         this.drop("epic_trident", this.enchanted(Material.TRIDENT, "§5トライデント", Map.of("loyalty", 3))),
         this.drop("epic_diamond_chest", this.enchanted(Material.DIAMOND_CHESTPLATE, "§5ダイヤモンドのチェストプレート", Map.of("protection", 2))),
         this.drop("epic_diamond_legs", this.enchanted(Material.DIAMOND_LEGGINGS, "§5ダイヤモンドのレギンス", Map.of("protection", 2))),
         this.drop("epic_totem", this.named(Material.TOTEM_OF_UNDYING, "§5不死のトーテム")),
         this.drop("epic_god_apple", this.named(Material.ENCHANTED_GOLDEN_APPLE, "§5エンチャントされた金のリンゴ")),
         this.drop("epic_strength_two", this.fieldPotion("strength", 2, 15, "§5攻撃力上昇IIポーション"))
      );
   }

   private List<FfaFieldItemManager.LootDrop> legendaryLootPool() {
      return List.of(
         this.drop("legendary_netherite_sword", this.enchanted(Material.NETHERITE_SWORD, "§6ネザライトの剣", Map.of("sharpness", 2))),
         this.drop("legendary_netherite_axe", this.enchanted(Material.NETHERITE_AXE, "§6ネザライトの斧", Map.of("sharpness", 2))),
         this.drop("legendary_netherite_spear", this.enchanted(this.material("NETHERITE_SPEAR", this.material("DIAMOND_SPEAR", this.material("IRON_SPEAR", Material.TRIDENT))), "§6ネザライトの槍", Map.of("lunge", 3))),
         this.drop("legendary_bow", this.enchanted(Material.BOW, "§6強化弓", Map.of("power", 5)), this.namedAmount(Material.ARROW, 20, "§6矢 ×20")),
         this.drop("legendary_mace", this.enchanted(Material.MACE, "§6メイス", Map.of("density", 3)), this.namedAmount(Material.WIND_CHARGE, 8, "§6ウィンドチャージ ×8")),
         this.drop("legendary_diamond_helmet", this.enchanted(Material.DIAMOND_HELMET, "§6ダイヤモンドのヘルメット", Map.of("protection", 4))),
         this.drop("legendary_diamond_chest", this.enchanted(Material.DIAMOND_CHESTPLATE, "§6ダイヤモンドのチェストプレート", Map.of("protection", 4))),
         this.drop("legendary_diamond_legs", this.enchanted(Material.DIAMOND_LEGGINGS, "§6ダイヤモンドのレギンス", Map.of("protection", 4))),
         this.drop("legendary_diamond_boots", this.enchanted(Material.DIAMOND_BOOTS, "§6ダイヤモンドのブーツ", Map.of("protection", 4))),
         this.drop("legendary_totems", this.namedAmount(Material.TOTEM_OF_UNDYING, 2, "§6不死のトーテム ×2")),
         this.drop("legendary_god_apples", this.namedAmount(Material.ENCHANTED_GOLDEN_APPLE, 2, "§6エンチャントされた金のリンゴ ×2"))
      );
   }

   private FfaFieldItemManager.LootDrop drop(String id, ItemStack... items) {
      return new FfaFieldItemManager.LootDrop(id, List.of(items));
   }

   private List<ItemStack> cloneLoot(List<ItemStack> items) {
      List<ItemStack> copies = new ArrayList<>();
      for (ItemStack item : items) {
         copies.add(item.clone());
      }
      return copies;
   }

   private ItemStack namedAmount(Material material, int amount, String name) {
      ItemStack item = this.named(material, name);
      item.setAmount(Math.max(1, Math.min(64, amount)));
      return item;
   }

   private String itemName(ItemStack item) {
      if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
         return ChatColor.stripColor(item.getItemMeta().getDisplayName());
      }
      return item == null ? "不明" : item.getType().name().toLowerCase(Locale.ROOT);
   }

'''
text = text[:block_start] + loot_block + text[block_end:]

schedule_remove = '''   private void scheduleRemove(Item item, long seconds) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         this.fieldItems.remove(item.getUniqueId());
         this.pendingLoot.remove(item.getUniqueId());
         if (item.isValid()) {
            item.remove();
         }
      }, Math.max(1L, seconds) * 20L);
   }'''
text = replace_method(text, '   private void scheduleRemove(Item item, long seconds)', schedule_remove)

remove_items = '''   private void removeFieldItems() {
      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item && this.fieldItems.contains(item.getUniqueId())) {
               item.remove();
            }
         }
      }

      this.fieldItems.clear();
      this.pendingLoot.clear();
   }'''
text = replace_method(text, '   private void removeFieldItems()', remove_items)

# Persisted configuration for the moderately increased spawn density.
ensure_defaults_sig = '   private void ensureDefaults()'
ensure_start = text.find(ensure_defaults_sig)
if ensure_start < 0:
    raise SystemExit('ensureDefaults not found')
ensure_end = text.find('   private void setIfMissing(', ensure_start)
ensure_block = text[ensure_start:ensure_end]
new_defaults = '''      this.setIfMissing(config, "ffa.field-items.max-active.small", 3);
      this.setIfMissing(config, "ffa.field-items.max-active.medium", 5);
      this.setIfMissing(config, "ffa.field-items.max-active.large", 7);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.small.min-seconds", 35);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.small.max-seconds", 55);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.medium.min-seconds", 25);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.medium.max-seconds", 40);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.large.min-seconds", 18);
      this.setIfMissing(config, "ffa.field-items.spawn-intervals.large.max-seconds", 32);'''
if 'ffa.field-items.max-active.small' not in ensure_block:
    marker = '      this.setIfMissing(config, "ffa.field-items.spawnpoints", List.of());'
    if marker not in ensure_block:
        raise SystemExit('field-item defaults insertion point not found')
    text = text.replace(marker, marker + '\n' + new_defaults, 1)

if 'private record LootDrop(' not in text:
    marker = '   private void ensureDefaults() {'
    if marker not in text:
        raise SystemExit('LootDrop insertion point not found')
    text = text.replace(marker, '   private record LootDrop(String id, List<ItemStack> items) {\n   }\n\n' + marker, 1)

path.write_text(text, encoding='utf-8', newline='\n')
