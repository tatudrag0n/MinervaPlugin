package org.server.minerva;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

final class FfaFieldItemManager {
   private final Minerva plugin;
   private final FfaManager ffa;
   private final NamespacedKey fieldKey;
   private final NamespacedKey fieldTypeKey;
   private final NamespacedKey fieldIdKey;
   private final NamespacedKey fieldEventKey;
   private final NamespacedKey ffaItemKindKey;
   private final NamespacedKey ffaOwnerKey;
   private final NamespacedKey projectileKindKey;
   private final Set<UUID> fieldItems = new HashSet<>();
   private final Map<String, BukkitTask> channelTasks = new HashMap<>();
   private final Map<String, String> activeEvents = new HashMap<>();
   private final Map<UUID, ItemStack> skyChestplates = new HashMap<>();
   private BukkitTask spawnTask;
   private long nextLootAt;
   private long nextEventAt;

   FfaFieldItemManager(Minerva plugin, FfaManager ffa) {
      this.plugin = plugin;
      this.ffa = ffa;
      this.fieldKey = new NamespacedKey(plugin, "ffa_field_item");
      this.fieldTypeKey = new NamespacedKey(plugin, "ffa_field_type");
      this.fieldIdKey = new NamespacedKey(plugin, "ffa_field_id");
      this.fieldEventKey = new NamespacedKey(plugin, "ffa_field_event");
      this.ffaItemKindKey = new NamespacedKey(plugin, "ffa_item_kind");
      this.ffaOwnerKey = new NamespacedKey(plugin, "ffa_item_owner");
      this.projectileKindKey = new NamespacedKey(plugin, "ffa_projectile_kind");
   }

   void load() {
      this.ensureDefaults();
      this.start();
   }

   void shutdown() {
      if (this.spawnTask != null) {
         this.spawnTask.cancel();
         this.spawnTask = null;
      }

      for (BukkitTask task : this.channelTasks.values()) {
         task.cancel();
      }

      this.channelTasks.clear();
      this.activeEvents.clear();
      this.removeEventItems("one_shot_bow");
      this.removeEventItems("sky_spear");
      this.removeFieldItems();
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (args.length < 2 || !"fielditem".equalsIgnoreCase(args[1])) {
         return false;
      }

      if (!sender.hasPermission("minerva.ffa.admin") && !sender.hasPermission("minerva.admin")) {
         sender.sendMessage(ChatColor.RED + "権限がありません。");
         return true;
      }

      if (args.length < 3) {
         sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint|spawn|start|stop|reload");
         return true;
      }

      String action = args[2].toLowerCase(Locale.ROOT);
      switch (action) {
         case "spawnpoint":
            this.handleSpawnPoint(sender, args);
            break;
         case "spawn":
            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            String type = args.length >= 4 ? args[3] : "random";
            this.spawnLoot(player.getLocation(), type);
            sender.sendMessage(ChatColor.GREEN + "フィールドアイテムを出現させました: " + type);
            break;
         case "start":
            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            String event = args.length >= 4 ? args[3] : "random";
            this.startEvent(event, player);
            break;
         case "stop":
            this.shutdown();
            this.start();
            sender.sendMessage(ChatColor.YELLOW + "FFAフィールドアイテムとイベントを停止しました。");
            break;
         case "reload":
            this.shutdown();
            this.load();
            sender.sendMessage(ChatColor.GREEN + "FFAフィールドアイテム設定を再読み込みしました。");
            break;
         default:
            sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint|spawn|start|stop|reload");
      }

      return true;
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 3) {
         return List.of("spawnpoint", "spawn", "start", "stop", "reload");
      } else if (args.length == 4 && "spawnpoint".equalsIgnoreCase(args[2])) {
         return List.of("add", "remove", "list");
      } else if (args.length == 4 && "start".equalsIgnoreCase(args[2])) {
         return this.eventTypes();
      } else {
         return args.length == 4 && "spawn".equalsIgnoreCase(args[2]) ? List.of("random", "common", "uncommon", "rare", "epic", "legendary") : List.of();
      }
   }

   boolean handlePickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player player && this.ffa.isPlaying(player)) {
         Item item = event.getItem();
         if (!this.isFieldItem(item)) {
            return false;
         }

         event.setCancelled(true);
         String eventType = (String)item.getPersistentDataContainer().get(this.fieldEventKey, PersistentDataType.STRING);
         if (eventType != null && !eventType.isBlank()) {
            this.startEvent(eventType, player);
         } else {
            this.giveLoot(player, (String)item.getPersistentDataContainer().get(this.fieldTypeKey, PersistentDataType.STRING));
         }

         this.fieldItems.remove(item.getUniqueId());
         item.remove();
         return true;
      } else {
         return false;
      }
   }

   boolean handleEntityDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Item item && this.isFieldItem(item)) {
         event.setCancelled(true);
         return true;
      } else {
         return false;
      }
   }

   boolean handleInventoryPickup(InventoryPickupItemEvent event) {
      if (this.isFieldItem(event.getItem())) {
         event.setCancelled(true);
         return true;
      } else {
         return false;
      }
   }

   private void handleSpawnPoint(CommandSender sender, String[] args) {
      if (args.length < 4) {
         sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint add|remove|list");
      } else {
         String sub = args[3].toLowerCase(Locale.ROOT);
         List<String> points = new ArrayList<>(this.plugin.getConfig().getStringList("ffa.field-items.spawnpoints"));
         switch (sub) {
            case "add":
               if (!(sender instanceof Player player)) {
                  sender.sendMessage("Player only.");
                  return;
               }

               points.add(this.serialize(player.getLocation()));
               this.plugin.getConfig().set("ffa.field-items.spawnpoints", points);
               this.plugin.saveConfig();
               sender.sendMessage(ChatColor.GREEN + "出現地点を追加しました: " + points.size());
               break;
            case "remove":
               if (args.length < 5) {
                  sender.sendMessage(ChatColor.RED + "/mva ffa fielditem spawnpoint remove <番号>");
                  return;
               }

               int index = this.parseInt(args[4], -1) - 1;
               if (index < 0 || index >= points.size()) {
                  sender.sendMessage(ChatColor.RED + "番号が不正です。");
                  return;
               }

               points.remove(index);
               this.plugin.getConfig().set("ffa.field-items.spawnpoints", points);
               this.plugin.saveConfig();
               sender.sendMessage(ChatColor.YELLOW + "出現地点を削除しました。");
               break;
            case "list":
               if (points.isEmpty()) {
                  sender.sendMessage(ChatColor.GRAY + "出現地点は未登録です。");
                  return;
               }

               for (int i = 0; i < points.size(); i++) {
                  sender.sendMessage("" + ChatColor.GREEN + (i + 1) + ". " + points.get(i));
               }
               break;
            default:
               sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint add|remove|list");
         }
      }
   }

   private void start() {
      if (this.plugin.getConfig().getBoolean("ffa.field-items.enabled", true)) {
         if (this.spawnTask != null) {
            this.spawnTask.cancel();
         }

         this.nextLootAt = System.currentTimeMillis() + 30000L;
         this.nextEventAt = System.currentTimeMillis() + this.eventIntervalMillis();
         this.spawnTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 40L, 40L);
      }
   }

   private void tick() {
      List<Player> players = this.ffaPlayers();
      if (players.isEmpty()) {
         this.removeFieldItems();
      } else {
         long now = System.currentTimeMillis();
         if (players.size() >= this.plugin.getConfig().getInt("ffa.field-items.min-players", 2) && now >= this.nextEventAt && this.countEventItems() == 0) {
            this.spawnEventItem(this.randomSpawnPoint(), this.randomEvent());
            this.nextEventAt = now + this.eventIntervalMillis();
         }

         if (now >= this.nextLootAt && this.countLootItems() < this.maxLoot(players.size())) {
            this.spawnLoot(this.randomSpawnPoint(), "random");
            long[] range = this.lootInterval(players.size());
            this.nextLootAt = now + ThreadLocalRandom.current().nextLong(range[0], range[1] + 1L);
         }
      }
   }

   private void spawnEventItem(Location location, String eventType) {
      if (location != null) {
         ItemStack stack = this.named(this.eventIcon(eventType), "§dイベント: " + this.eventDisplay(eventType));
         Item item = location.getWorld().dropItem(location, stack);
         this.tagFieldItem(item, "event", eventType);
         this.scheduleRemove(item, this.plugin.getConfig().getLong("ffa.field-items.event-despawn-seconds", 60L));
         this.broadcast("§dイベントアイテムが出現しました: §f" + this.eventDisplay(eventType));
      }
   }

   private void spawnLoot(Location location, String requestedType) {
      if (location != null) {
         String rarity = "random".equalsIgnoreCase(requestedType) ? this.randomRarity() : requestedType.toLowerCase(Locale.ROOT);
         ItemStack stack = this.lootItem(rarity);
         Item item = location.getWorld().dropItem(location, stack);
         this.tagFieldItem(item, rarity, null);
         this.scheduleRemove(item, this.plugin.getConfig().getLong("ffa.field-items.loot-despawn-seconds", 90L));
         if ("legendary".equals(rarity)) {
            this.broadcast("§6レジェンダリーアイテムが出現しました。");
         }
      }
   }

   private void giveLoot(Player player, String rarity) {
      List<ItemStack> items = this.lootItems(rarity == null ? "common" : rarity);
      if (this.ffa.currentKit(player) == FfaKit.GRAPPLER && items.stream().anyMatch(itemx -> this.isFieldEquipment(itemx.getType()))) {
         player.sendActionBar(Component.text("グラップラーはフィールド装備を取得できません", NamedTextColor.RED));
      } else {
         for (ItemStack item : items) {
            FfaKit.tagItem(this.plugin, FfaKit.SWORD, "field_" + (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)), item, Map.of());
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "ffa_field_owned"), PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(this.ffaOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            player.getInventory().addItem(new ItemStack[]{item});
         }

         player.sendActionBar(Component.text("フィールドアイテム取得: " + rarity, NamedTextColor.GOLD));
      }
   }

   private void startEvent(String rawType, Player activator) {
      String type = "random".equalsIgnoreCase(rawType) ? this.randomEvent() : this.normalizeEvent(rawType);
      String channel = this.eventChannel(type);
      BukkitTask old = this.channelTasks.remove(channel);
      if (old != null) {
         old.cancel();
         String oldType = this.activeEvents.remove(channel);
         if (oldType != null) {
            this.removeEventItems(oldType);
         }
      }

      long seconds = this.eventDuration(type);
      this.applyEvent(type, activator, (int)seconds);
      this.activeEvents.put(channel, type);
      if ("mp_fever".equals(type)) {
         this.plugin.data().set("ffa.events.mp-fever-until", System.currentTimeMillis() + seconds * 1000L);
         this.plugin.data().set("ffa.events.em-fever-until", null);
         this.plugin.saveData();
      }

      BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.endEvent(type, channel), seconds * 20L);
      this.channelTasks.put(channel, task);
      this.broadcast("§d" + this.eventDisplay(type) + " が発動しました。");
   }

   private void applyEvent(String type, Player activator, int seconds) {
      World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();
      switch (type) {
         case "rain":
            if (world != null) {
               int durationTicks = seconds * 20;
               world.setStorm(true);
               world.setWeatherDuration(durationTicks);
               world.setThundering(false);
               world.setThunderDuration(durationTicks);
            }

            for (Player player : this.ffaPlayers()) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, false, true));
               player.setFireTicks(0);
            }
            break;
         case "snow":
         case "blizzard":
            for (Player player : this.ffaPlayers()) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0, false, false, true));
               if ("blizzard".equals(type)) {
                  player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 0, false, false, true));
               }
            }
            break;
         case "berserk":
            activator.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, false, true));
            break;
         case "speed":
            activator.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, false, false, true));
            break;
         case "iron_body":
            activator.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0, false, false, true));
            activator.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 0, false, false, true));
            break;
         case "overdrive":
            for (Player player : this.ffaPlayers()) {
               player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, false, true));
               player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, false, true));
            }
            break;
         case "one_shot_bow":
            this.ffaPlayers().forEach(this::giveOneShotBow);
            break;
         case "sky_spear":
            this.ffaPlayers().forEach(this::giveSkySpearGear);
            break;
         case "time_shift":
            if (world != null) {
               world.setTime(List.of(1000L, 6000L, 12000L, 18000L).get(ThreadLocalRandom.current().nextInt(4)));
            }
            break;
         case "heal_self":
            this.heal(activator, 10.0);
            break;
         case "heal_all":
            this.ffaPlayers().forEach(playerx -> this.heal(playerx, 8.0));
      }
   }

   private void endEvent(String type, String channel) {
      this.channelTasks.remove(channel);
      this.activeEvents.remove(channel);
      this.removeEventItems(type);
      if ("rain".equals(type)) {
         World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();
         if (world != null) {
            world.setStorm(false);
            world.setWeatherDuration(0);
            world.setThundering(false);
            world.setThunderDuration(0);
            world.setClearWeatherDuration(20);
         }
      }

      if ("mp_fever".equals(type)) {
         this.plugin.data().set("ffa.events.mp-fever-until", null);
         this.plugin.data().set("ffa.events.em-fever-until", null);
         this.plugin.saveData();
      }

      this.broadcast("§7" + this.eventDisplay(type) + " が終了しました。");
   }

   void applyActiveEventGear(Player player) {
      if (this.activeEvents.containsValue("one_shot_bow")) {
         this.giveOneShotBow(player);
      }

      if (this.activeEvents.containsValue("sky_spear")) {
         this.giveSkySpearGear(player);
      }
   }

   private void giveOneShotBow(Player player) {
      if (!this.hasKind(player, "event_one_shot_bow")) {
         ItemStack bow = FfaKit.kitItem(this.plugin, FfaKit.BOW, "event_one_shot_bow", Material.BOW, "§c一撃必殺弓", 1, Map.of(Enchantment.INFINITY, 1));
         ItemStack arrow = FfaKit.kitItem(this.plugin, FfaKit.BOW, "event_one_shot_arrow", Material.ARROW, "§c一撃必殺の矢", 1, Map.of());
         this.tagOwner(bow, player.getUniqueId());
         this.tagOwner(arrow, player.getUniqueId());
         player.getInventory().addItem(new ItemStack[]{bow, arrow});
         player.updateInventory();
      }
   }

   private void giveSkySpearGear(Player player) {
      UUID uuid = player.getUniqueId();
      if (!this.skyChestplates.containsKey(uuid)) {
         ItemStack chestplate = player.getInventory().getChestplate();
         this.skyChestplates.put(uuid, chestplate == null ? null : chestplate.clone());
      }

      ItemStack elytra = FfaKit.kitItem(this.plugin, FfaKit.SPEAR, "event_sky_elytra", Material.ELYTRA, "§d天空槍撃戦のエリトラ", 1, Map.of());
      ItemStack spear = FfaKit.kitItem(this.plugin, FfaKit.SPEAR, "event_sky_spear", Material.IRON_SPEAR, "§d天空槍撃戦の鉄槍", 1, Map.of());
      ItemStack rockets = FfaKit.kitItem(this.plugin, FfaKit.SPEAR, "event_sky_firework", Material.FIREWORK_ROCKET, "§d天空槍撃戦のロケット花火", 12, Map.of());
      this.tagOwner(elytra, uuid);
      this.tagOwner(spear, uuid);
      this.tagOwner(rockets, uuid);
      player.getInventory().setChestplate(elytra);
      if (!this.hasKind(player, "event_sky_spear")) {
         player.getInventory().addItem(new ItemStack[]{spear});
      }

      if (!this.hasKind(player, "event_sky_firework")) {
         player.getInventory().addItem(new ItemStack[]{rockets});
      }

      player.updateInventory();
   }

   private void removeEventItems(String type) {
      if ("one_shot_bow".equals(type)) {
         for (Player player : this.ffaPlayers()) {
            this.removeKinds(player, Set.of("event_one_shot_bow", "event_one_shot_arrow"));
         }

         this.removeProjectiles("event_one_shot_arrow");
      } else {
         if ("sky_spear".equals(type)) {
            for (Player player : this.ffaPlayers()) {
               this.removeKinds(player, Set.of("event_sky_elytra", "event_sky_spear", "event_sky_firework"));
               ItemStack chestplate = this.skyChestplates.remove(player.getUniqueId());
               player.getInventory().setChestplate(chestplate == null ? null : chestplate.clone());
               player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, true));
               player.updateInventory();
            }

            this.skyChestplates.clear();
            this.removeProjectiles("event_sky_spear");
         }
      }
   }

   private void removeProjectiles(String kind) {
      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (kind.equals(entity.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
               entity.remove();
            }
         }
      }
   }

   private boolean hasKind(Player player, String kind) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (kind.equals(this.itemKind(item))) {
            return true;
         }
      }

      return kind.equals(this.itemKind(player.getInventory().getChestplate())) || kind.equals(this.itemKind(player.getInventory().getItemInOffHand()));
   }

   private void removeKinds(Player player, Set<String> kinds) {
      ItemStack[] contents = player.getInventory().getContents();

      for (int i = 0; i < contents.length; i++) {
         if (kinds.contains(this.itemKind(contents[i]))) {
            contents[i] = null;
         }
      }

      player.getInventory().setContents(contents);
      ItemStack[] armor = player.getInventory().getArmorContents();

      for (int i = 0; i < armor.length; i++) {
         if (kinds.contains(this.itemKind(armor[i]))) {
            armor[i] = null;
         }
      }

      player.getInventory().setArmorContents(armor);
      if (kinds.contains(this.itemKind(player.getInventory().getItemInOffHand()))) {
         player.getInventory().setItemInOffHand(null);
      }
   }

   private void tagOwner(ItemStack item, UUID owner) {
      if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         meta.getPersistentDataContainer().set(this.ffaOwnerKey, PersistentDataType.STRING, owner.toString());
         item.setItemMeta(meta);
      }
   }

   private String itemKind(ItemStack item) {
      if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
         String value = (String)item.getItemMeta().getPersistentDataContainer().get(this.ffaItemKindKey, PersistentDataType.STRING);
         return value == null ? "" : value;
      } else {
         return "";
      }
   }

   private void tagFieldItem(Item item, String type, String eventType) {
      UUID id = UUID.randomUUID();
      item.getPersistentDataContainer().set(this.fieldKey, PersistentDataType.BOOLEAN, true);
      item.getPersistentDataContainer().set(this.fieldTypeKey, PersistentDataType.STRING, type);
      item.getPersistentDataContainer().set(this.fieldIdKey, PersistentDataType.STRING, id.toString());
      if (eventType != null) {
         item.getPersistentDataContainer().set(this.fieldEventKey, PersistentDataType.STRING, eventType);
      }

      item.setGlowing(true);
      item.setCanMobPickup(false);
      item.setUnlimitedLifetime(false);
      this.fieldItems.add(item.getUniqueId());
   }

   private boolean isFieldItem(Item item) {
      return Boolean.TRUE.equals(item.getPersistentDataContainer().get(this.fieldKey, PersistentDataType.BOOLEAN));
   }

   private void scheduleRemove(Item item, long seconds) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         this.fieldItems.remove(item.getUniqueId());
         if (item.isValid()) {
            item.remove();
         }
      }, Math.max(1L, seconds) * 20L);
   }

   private void removeFieldItems() {
      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item && this.fieldItems.contains(item.getUniqueId())) {
               item.remove();
            }
         }
      }

      this.fieldItems.clear();
   }

   private Location randomSpawnPoint() {
      List<Location> points = this.plugin
         .getConfig()
         .getStringList("ffa.field-items.spawnpoints")
         .stream()
         .map(this::deserialize)
         .filter(location -> location != null && location.getWorld() != null)
         .toList();
      if (!points.isEmpty()) {
         return points.get(ThreadLocalRandom.current().nextInt(points.size()));
      }

      Location center = this.ffa.center();
      return center == null ? null : center.clone().add(ThreadLocalRandom.current().nextInt(-8, 9), 1.0, ThreadLocalRandom.current().nextInt(-8, 9));
   }

   private List<Player> ffaPlayers() {
      List<Player> players = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.ffa.isPlaying(player)) {
            players.add(player);
         }
      }

      return players;
   }

   private int maxLoot(int players) {
      if (players >= 9) {
         return 6;
      } else {
         return players >= 5 ? 4 : 2;
      }
   }

   private long[] lootInterval(int players) {
      if (players >= 9) {
         return new long[]{20000L, 40000L};
      } else {
         return players >= 5 ? new long[]{30000L, 50000L} : new long[]{45000L, 70000L};
      }
   }

   private int countLootItems() {
      return (int)this.fieldItems
         .stream()
         .map(this::entityById)
         .filter(entity -> entity instanceof Item item && item.getPersistentDataContainer().get(this.fieldEventKey, PersistentDataType.STRING) == null)
         .count();
   }

   private int countEventItems() {
      return (int)this.fieldItems
         .stream()
         .map(this::entityById)
         .filter(entity -> entity instanceof Item item && item.getPersistentDataContainer().get(this.fieldEventKey, PersistentDataType.STRING) != null)
         .count();
   }

   private Entity entityById(UUID uuid) {
      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (uuid.equals(entity.getUniqueId())) {
               return entity;
            }
         }
      }

      return null;
   }

   private ItemStack lootItem(String rarity) {
      return switch (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)) {
         case "legendary" -> this.enchanted(Material.NETHERITE_SWORD, "§6レジェンダリー武器", Map.of("sharpness", 1));
         case "epic" -> this.enchanted(Material.DIAMOND_SWORD, "§5エピック武器", Map.of("sharpness", 1));
         case "rare" -> this.named(Material.GOLDEN_APPLE, "§bレア金リンゴ");
         case "uncommon" -> this.named(Material.ENDER_PEARL, "§aアンコモン エンダーパール");
         default -> new ItemStack(Material.COOKED_BEEF, 2);
      };
   }

   private List<ItemStack> lootItems(String rarity) {
      String type = rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT);

      return switch (type) {
         case "legendary" -> this.legendaryLoot();
         case "epic" -> this.epicLoot();
         case "rare" -> this.rareLoot();
         case "uncommon" -> this.uncommonLoot();
         default -> this.commonLoot();
      };
   }

   private List<ItemStack> commonLoot() {
      return switch (ThreadLocalRandom.current().nextInt(8)) {
         case 0 -> List.of(new ItemStack(Material.COOKED_BEEF, 2));
         case 1 -> List.of(new ItemStack(Material.BREAD, 4));
         case 2 -> List.of(new ItemStack(Material.ARROW, 6));
         case 3 -> List.of(new ItemStack(Material.SNOWBALL, 8));
         case 4 -> List.of(new ItemStack(Material.WIND_CHARGE, 1));
         case 5 -> List.of(this.fieldPotion("heal", 1, 1, "§d即時回復ポーション"));
         case 6 -> List.of(this.enchanted(Material.STONE_SWORD, "§f石の剣", Map.of("sharpness", 1)));
         default -> List.of(this.enchanted(this.randomArmor("leather"), "§f革防具", Map.of("protection", 2)));
      };
   }

   private List<ItemStack> uncommonLoot() {
      return switch (ThreadLocalRandom.current().nextInt(11)) {
         case 0 -> List.of(new ItemStack(Material.ENDER_PEARL, 1));
         case 1 -> List.of(new ItemStack(Material.GOLDEN_CARROT, 3));
         case 2 -> List.of(this.fieldPotion("speed", 1, 20, "§a移動速度上昇ポーション"));
         case 3 -> List.of(this.fieldPotion("heal", 1, 1, "§d即時回復ポーション"), this.fieldPotion("heal", 1, 1, "§d即時回復ポーション"));
         case 4 -> List.of(this.enchanted(Material.IRON_SWORD, "§a鉄の剣", Map.of("sharpness", 1)));
         case 5 -> List.of(this.enchanted(Material.BOW, "§a強化弓", Map.of("power", 1)), new ItemStack(Material.ARROW, 5));
         case 6 -> List.of(new ItemStack(Material.CROSSBOW, 1), new ItemStack(Material.ARROW, 3));
         case 7 -> List.of(this.named(Material.SHIELD, "§a耐久制限付きの盾"));
         case 8 -> List.of(this.named(Material.TURTLE_HELMET, "§a亀の甲羅"));
         case 9 -> List.of(this.enchanted(this.randomArmor("iron"), "§a鉄防具", Map.of("protection", 1)));
         default -> List.of(this.enchanted(this.randomArmor("leather"), "§a革防具", Map.of("protection", 3)));
      };
   }

   private List<ItemStack> rareLoot() {
      return switch (ThreadLocalRandom.current().nextInt(11)) {
         case 0 -> List.of(this.named(Material.DIAMOND_SWORD, "§bダイヤモンドの剣"));
         case 1 -> List.of(this.named(Material.DIAMOND_AXE, "§bダイヤモンドの斧"));
         case 2 -> List.of(this.enchanted(Material.BOW, "§b強化弓", Map.of("power", 2)), new ItemStack(Material.ARROW, 6));
         case 3 -> List.of(this.enchanted(Material.CROSSBOW, "§b高速装填クロスボウ", Map.of("quick_charge", 1)), new ItemStack(Material.ARROW, 4));
         case 4 -> List.of(this.enchanted(this.material("IRON_SPEAR", Material.TRIDENT), "§b鉄の槍", Map.of("lunge", 1)));
         case 5 -> List.of(this.enchanted(Material.TRIDENT, "§bトライデント", Map.of("loyalty", 1)));
         case 6 -> List.of(this.enchanted(this.randomArmor("iron"), "§b鉄防具", Map.of("protection", 2)));
         case 7 -> List.of(this.named(Material.DIAMOND_BOOTS, "§bダイヤモンドのブーツ"));
         case 8 -> List.of(new ItemStack(Material.GOLDEN_APPLE, 1));
         case 9 -> List.of(this.fieldPotion("strength", 1, 20, "§b攻撃力上昇ポーション"));
         default -> List.of(this.fieldPotion("regeneration", 1, 20, "§b再生能力ポーション"));
      };
   }

   private List<ItemStack> epicLoot() {
      return switch (ThreadLocalRandom.current().nextInt(8)) {
         case 0 -> List.of(this.enchanted(Material.DIAMOND_SWORD, "§5ダイヤモンドの剣", Map.of("sharpness", 1)));
         case 1 -> List.of(this.enchanted(this.material("DIAMOND_SPEAR", this.material("IRON_SPEAR", Material.TRIDENT)), "§5ダイヤモンドの槍", Map.of("lunge", 1)));
         case 2 -> List.of(this.enchanted(Material.BOW, "§5強化弓", Map.of("power", 3)), new ItemStack(Material.ARROW, 6));
         case 3 -> List.of(this.enchanted(Material.MACE, "§5メイス", Map.of("density", 1)), new ItemStack(Material.WIND_CHARGE, 2));
         case 4 -> List.of(this.enchanted(Material.TRIDENT, "§5トライデント", Map.of("loyalty", 3)));
         case 5 -> List.of(this.named(Material.DIAMOND_CHESTPLATE, "§5ダイヤモンドのチェストプレート"));
         case 6 -> List.of(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
         default -> List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
      };
   }

   private List<ItemStack> legendaryLoot() {
      return switch (ThreadLocalRandom.current().nextInt(6)) {
         case 0 -> List.of(this.enchanted(Material.NETHERITE_SWORD, "§6ネザライトの剣", Map.of("sharpness", 1)));
         case 1 -> List.of(
            this.enchanted(
               this.material("NETHERITE_SPEAR", this.material("DIAMOND_SPEAR", this.material("IRON_SPEAR", Material.TRIDENT))), "§6ネザライトの槍", Map.of("lunge", 2)
            )
         );
         case 2 -> List.of(this.enchanted(Material.DIAMOND_CHESTPLATE, "§6ダイヤモンドのチェストプレート", Map.of("protection", 2)));
         case 3 -> List.of(this.enchanted(Material.MACE, "§6メイス", Map.of("density", 2)), new ItemStack(Material.WIND_CHARGE, 3));
         case 4 -> List.of(this.enchanted(Material.BOW, "§6強化弓", Map.of("power", 4)), new ItemStack(Material.ARROW, 8));
         default -> List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
      };
   }

   private ItemStack fieldPotion(String effect, int level, int seconds, String name) {
      ItemStack item = FfaKit.potion(null, FfaKit.SWORD, Material.SPLASH_POTION, effect, level, seconds, 1, name);
      return item == null ? new ItemStack(Material.SPLASH_POTION) : item;
   }

   private Material randomArmor(String tier) {
      String[] slots = new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
      return this.material(tier.toUpperCase(Locale.ROOT) + "_" + slots[ThreadLocalRandom.current().nextInt(slots.length)], Material.LEATHER_BOOTS);
   }

   private Material material(String name, Material fallback) {
      Material material = Material.matchMaterial(name);
      return material == null ? fallback : material;
   }

   private ItemStack named(Material material, String name) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack enchanted(Material material, String name, Map<String, Integer> enchants) {
      ItemStack item = this.named(material, name);

      for (Entry<String, Integer> entry : enchants.entrySet()) {
         Enchantment enchantment = this.enchantment(entry.getKey());
         if (enchantment != null) {
            item.addUnsafeEnchantment(enchantment, entry.getValue());
         }
      }

      return item;
   }

   private Enchantment enchantment(String key) {
      return switch (key.toLowerCase(Locale.ROOT)) {
         case "sharpness" -> Enchantment.SHARPNESS;
         case "power" -> Enchantment.POWER;
         case "protection" -> Enchantment.PROTECTION;
         case "density" -> Enchantment.DENSITY;
         case "quick_charge" -> Enchantment.QUICK_CHARGE;
         case "loyalty" -> Enchantment.LOYALTY;
         case "lunge" -> Enchantment.LUNGE;
         default -> null;
      };
   }

   private boolean isFieldEquipment(Material material) {
      String name = material.name();
      return name.endsWith("_SWORD")
         || name.endsWith("_AXE")
         || name.endsWith("_HELMET")
         || name.endsWith("_CHESTPLATE")
         || name.endsWith("_LEGGINGS")
         || name.endsWith("_BOOTS")
         || material == Material.BOW
         || material == Material.CROSSBOW
         || material == Material.SHIELD
         || material == Material.TRIDENT
         || material == Material.MACE
         || name.endsWith("_SPEAR");
   }

   private String randomRarity() {
      return this.weighted(
         Map.of(
            "common",
            this.plugin.getConfig().getInt("ffa.field-items.rarities.common", 50),
            "uncommon",
            this.plugin.getConfig().getInt("ffa.field-items.rarities.uncommon", 30),
            "rare",
            this.plugin.getConfig().getInt("ffa.field-items.rarities.rare", 14),
            "epic",
            this.plugin.getConfig().getInt("ffa.field-items.rarities.epic", 5),
            "legendary",
            this.plugin.getConfig().getInt("ffa.field-items.rarities.legendary", 1)
         ),
         "common"
      );
   }

   private String randomEvent() {
      Map<String, Integer> weights = new HashMap<>();

      for (String type : this.eventTypes()) {
         weights.put(type, this.plugin.getConfig().getInt("ffa.field-items.events." + type + ".weight", this.defaultEventWeight(type)));
      }

      return this.weighted(weights, "heal_self");
   }

   private String weighted(Map<String, Integer> weights, String fallback) {
      int total = weights.values().stream().mapToInt(value -> Math.max(0, value)).sum();
      if (total <= 0) {
         return fallback;
      }

      int roll = ThreadLocalRandom.current().nextInt(total);
      int cursor = 0;

      for (Entry<String, Integer> entry : weights.entrySet()) {
         cursor += Math.max(0, entry.getValue());
         if (roll < cursor) {
            return entry.getKey();
         }
      }

      return fallback;
   }

   private long eventIntervalMillis() {
      long min = Math.max(1L, this.plugin.getConfig().getLong("ffa.field-items.event-interval-min-seconds", 150L));
      long max = Math.max(min, this.plugin.getConfig().getLong("ffa.field-items.event-interval-max-seconds", 240L));
      return ThreadLocalRandom.current().nextLong(min * 1000L, max * 1000L + 1L);
   }

   private int defaultEventWeight(String type) {
      return switch (type) {
         case "rain", "snow", "berserk", "speed" -> 10;
         case "blizzard", "one_shot_bow" -> 4;
         case "iron_body", "mp_fever", "time_shift" -> 8;
         case "overdrive" -> 6;
         case "sky_spear" -> 3;
         case "heal_all" -> 7;
         default -> 12;
      };
   }

   private List<String> eventTypes() {
      return List.of(
         "rain",
         "snow",
         "blizzard",
         "berserk",
         "speed",
         "iron_body",
         "overdrive",
         "one_shot_bow",
         "mp_fever",
         "sky_spear",
         "time_shift",
         "heal_self",
         "heal_all"
      );
   }

   private String normalizeEvent(String raw) {
      String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
      if ("em_fever".equals(normalized)) {
         return "mp_fever";
      } else {
         return this.eventTypes().contains(normalized) ? normalized : this.randomEvent();
      }
   }

   private String eventChannel(String type) {
      return switch (type) {
         case "rain", "snow", "blizzard" -> "weather";
         case "time_shift" -> "time";
         case "overdrive", "one_shot_bow", "sky_spear" -> "global_combat";
         case "mp_fever" -> "economy";
         default -> "personal";
      };
   }

   private long eventDuration(String type) {
      long fallback = switch (type) {
         case "rain", "snow", "time_shift" -> 90L;
         case "blizzard", "overdrive", "sky_spear" -> 45L;
         case "berserk", "iron_body", "one_shot_bow" -> 25L;
         case "speed" -> 20L;
         case "mp_fever" -> 120L;
         default -> 1L;
      };
      return Math.max(1L, this.plugin.getConfig().getLong("ffa.field-items.events." + type + ".duration-seconds", fallback));
   }

   private Material eventIcon(String type) {
      return switch (type) {
         case "rain" -> Material.HEART_OF_THE_SEA;
         case "snow" -> Material.SNOWBALL;
         case "blizzard" -> Material.BLUE_ICE;
         case "berserk" -> Material.BLAZE_POWDER;
         case "speed" -> Material.SUGAR;
         case "iron_body" -> Material.IRON_INGOT;
         case "overdrive" -> Material.NETHER_STAR;
         case "one_shot_bow" -> Material.ARROW;
         case "mp_fever" -> Material.EMERALD_BLOCK;
         case "sky_spear" -> Material.PHANTOM_MEMBRANE;
         case "time_shift" -> Material.CLOCK;
         case "heal_all" -> Material.GHAST_TEAR;
         default -> Material.GLISTERING_MELON_SLICE;
      };
   }

   private String eventDisplay(String type) {
      return switch (type) {
         case "rain" -> "恵みの雨";
         case "snow" -> "降雪";
         case "blizzard" -> "猛吹雪";
         case "berserk" -> "狂戦士";
         case "speed" -> "疾風";
         case "iron_body" -> "鋼鉄の肉体";
         case "overdrive" -> "オーバードライブ";
         case "one_shot_bow" -> "一撃必殺弓";
         case "mp_fever" -> "MPフィーバー";
         case "sky_spear" -> "天空槍撃戦";
         case "time_shift" -> "時空変動";
         case "heal_all" -> "全員即時回復";
         default -> "個人即時回復";
      };
   }

   private void heal(Player player, double amount) {
      double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
      player.setHealth(Math.min(max, player.getHealth() + amount));
   }

   private String serialize(Location location) {
      return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ();
   }

   private Location deserialize(String raw) {
      String[] parts = raw.split(",");
      if (parts.length < 4) {
         return null;
      }

      World world = Bukkit.getWorld(parts[0]);
      return world == null ? null : new Location(world, this.parseDouble(parts[1]), this.parseDouble(parts[2]), this.parseDouble(parts[3]));
   }

   private double parseDouble(String raw) {
      try {
         return Double.parseDouble(raw);
      } catch (NumberFormatException e) {
         return 0.0;
      }
   }

   private int parseInt(String raw, int fallback) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private void broadcast(String message) {
      for (Player player : this.ffaPlayers()) {
         player.sendMessage(message);
         player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.2F);
      }
   }

   private void ensureDefaults() {
      FileConfiguration config = this.plugin.getConfig();
      this.setIfMissing(config, "ffa.field-items.enabled", true);
      this.setIfMissing(config, "ffa.field-items.min-players", 2);
      this.setIfMissing(config, "ffa.field-items.event-despawn-seconds", 60);
      this.setIfMissing(config, "ffa.field-items.loot-despawn-seconds", 90);
      this.setIfMissing(config, "ffa.field-items.spawnpoints", List.of());
      this.plugin.saveConfig();
   }

   private void setIfMissing(FileConfiguration config, String path, Object value) {
      if (!config.contains(path)) {
         config.set(path, value);
      }
   }
}
