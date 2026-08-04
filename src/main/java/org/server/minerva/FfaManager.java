package org.server.minerva;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

final class FfaManager {
   private static final String CENTER_MISSING = "§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。";
   private static final String KIT_SELECTOR_TITLE = "FFAキット選択";
   private static final List<String> NECROMANCER_MOBS = List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton", "phantom");
   private final Minerva plugin;
   private final FfaConfig config;
   private final FfaStatsManager stats;
   private final FfaKitStandManager stands;
   private final FfaFieldItemManager fieldItems;
   private final NamespacedKey selectorKitKey;
   private final NamespacedKey itemKey;
   private final NamespacedKey itemKindKey;
   private final NamespacedKey itemOwnerKey;
   private final NamespacedKey abilityKey;
   private final NamespacedKey projectileKindKey;
   private final NamespacedKey projectileOwnerKey;
   private final NamespacedKey entityKindKey;
   private final NamespacedKey entityOwnerKey;
   private final NamespacedKey armorBonusKey;
   private final Map<UUID, FfaManager.FfaSession> sessions = new HashMap<>();
   private final Map<UUID, Integer> revolverAmmo = new HashMap<>();
   private final Map<UUID, Integer> sniperAmmo = new HashMap<>();
   private final Map<UUID, Long> sniperShotCooldownUntil = new HashMap<>();
   private final Map<UUID, BukkitTask> revolverReloadTasks = new HashMap<>();
   private final Map<UUID, BukkitTask> sniperReloadTasks = new HashMap<>();
   private final Map<UUID, BukkitTask> windChargeRefillTasks = new HashMap<>();
   private final Map<UUID, Long> wizardPotionCooldownUntil = new HashMap<>();
   private final Map<UUID, UUID> wizardPotionOwners = new ConcurrentHashMap<>();
   private final Map<UUID, Set<UUID>> trackedTridents = new HashMap<>();
   private final Set<UUID> gamblerSelfDamage = new HashSet<>();
   private final Set<UUID> crusherExplosionDamage = new HashSet<>();
   private final Map<UUID, Double> vampireDamage = new HashMap<>();
   private final Map<UUID, FfaManager.KillRewardState> killRewardStates = new HashMap<>();
   private final Map<UUID, List<UUID>> summonedMobs = new HashMap<>();
   private final Map<UUID, UUID> summonOwners = new HashMap<>();
   private final Map<UUID, BukkitTask> summonExpiryTasks = new HashMap<>();
   private final Map<UUID, Map<String, BukkitTask>> summonEggRefillTasks = new HashMap<>();
   private final Map<UUID, List<UUID>> bugMobs = new HashMap<>();
   private final Map<UUID, UUID> bugOwners = new HashMap<>();
   private final Map<UUID, BukkitTask> bugExpiryTasks = new HashMap<>();
   private final Map<UUID, Map<String, FfaManager.TrapState>> traps = new HashMap<>();
   private final Map<UUID, FfaManager.DamageCredit> damageCredits = new HashMap<>();
   private final Map<UUID, FfaManager.DeathLeaveRestore> deathLeaveRestores = new HashMap<>();
   private BukkitTask kitEffectTask;
   private Map<UUID, Long> lastCrossbowShotTick;

   FfaManager(Minerva plugin) {
      this.plugin = plugin;
      this.config = new FfaConfig(plugin);
      this.stats = new FfaStatsManager(plugin);
      this.stands = new FfaKitStandManager(plugin, this.config);
      this.fieldItems = new FfaFieldItemManager(plugin, this);
      this.selectorKitKey = new NamespacedKey(plugin, "ffa_selector_kit");
      this.itemKey = new NamespacedKey(plugin, "ffa_item");
      this.itemKindKey = new NamespacedKey(plugin, "ffa_item_kind");
      this.itemOwnerKey = new NamespacedKey(plugin, "ffa_item_owner");
      this.abilityKey = new NamespacedKey(plugin, "ffa_ability");
      this.projectileKindKey = new NamespacedKey(plugin, "ffa_projectile_kind");
      this.projectileOwnerKey = new NamespacedKey(plugin, "ffa_projectile_owner");
      this.entityKindKey = new NamespacedKey(plugin, "ffa_entity_kind");
      this.entityOwnerKey = new NamespacedKey(plugin, "ffa_entity_owner");
      this.armorBonusKey = new NamespacedKey(plugin, "ffa_armor_bonus");
   }

   void load() {
      this.config.ensureDefaults();
      this.stats.load();
      this.fieldItems.load();
      this.startKitEffectTask();
   }

   void shutdown() {
      this.fieldItems.shutdown();

      for (Player player : new ArrayList<Player>(this.plugin.getServer().getOnlinePlayers())) {
         if (this.isPlaying(player)) {
            this.leave(player, false);
         }
      }

      if (this.kitEffectTask != null) {
         this.kitEffectTask.cancel();
         this.kitEffectTask = null;
      }

      this.sessions.clear();
      this.revolverAmmo.clear();
      this.sniperAmmo.clear();
      this.sniperShotCooldownUntil.clear();
      this.cancelTasks(this.sniperReloadTasks);
      this.cancelTasks(this.windChargeRefillTasks);
      this.wizardPotionCooldownUntil.clear();
      this.wizardPotionOwners.clear();
      this.trackedTridents.clear();
      this.removeAllSummons();
      this.removeAllBugMobs();
      this.restoreAllTraps();
      this.damageCredits.clear();
      this.deathLeaveRestores.clear();
   }

   private void cancelTasks(Map<UUID, BukkitTask> tasks) {
      for (BukkitTask task : tasks.values()) {
         if (task != null) {
            task.cancel();
         }
      }

      tasks.clear();
   }

   FfaKitStandManager stands() {
      return this.stands;
   }

   boolean isPlaying(Player player) {
      return player != null && this.sessions.containsKey(player.getUniqueId());
   }

   boolean isPlaying(UUID uuid) {
      return this.sessions.containsKey(uuid);
   }

   FfaKit currentKit(Player player) {
      FfaManager.FfaSession session = player == null ? null : this.sessions.get(player.getUniqueId());
      return session == null ? null : session.kit;
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload");
         return true;
      }

      String action = args[1].toLowerCase(Locale.ROOT);
      if ("fielditem".equals(action)) {
         return this.fieldItems.handleCommand(sender, args);
      }

      switch (action) {
         case "leave":
            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            this.leave(player, true);
            break;
         case "stats":
            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            player.sendMessage(this.stats.summary(player));
            break;
         case "setcenter":
            if (!this.hasAdmin(sender)) {
               sender.sendMessage("§c権限がありません。");
               return true;
            }

            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            this.config.setCenter(player.getLocation());
            sender.sendMessage("§aFFA中央地点を設定しました。");
            break;
         case "setkits":
            if (!this.hasAdmin(sender)) {
               sender.sendMessage("§c権限がありません。");
               return true;
            }

            if (!(sender instanceof Player player)) {
               sender.sendMessage("Player only.");
               return true;
            }

            this.config.setKitSelection(player.getLocation());
            sender.sendMessage("§aFFAキット選択地点を設定しました。");
            break;
         case "createkits":
            if (!this.hasAdmin(sender)) {
               sender.sendMessage("§c権限がありません。");
               return true;
            }

            int count = this.stands.createKitStands();
            if (count < 0) {
               sender.sendMessage("§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。");
               return true;
            }

            sender.sendMessage("§aFFAキット選択用の防具立てを生成しました: " + count + "体");
            break;
         case "removekits":
            if (!this.hasAdmin(sender)) {
               sender.sendMessage("§c権限がありません。");
               return true;
            }

            int removedCount = this.stands.removeKitStands();
            sender.sendMessage("§eFFAキット防具立てを削除しました: " + removedCount + "体");
            break;
         case "reload":
            if (!this.hasAdmin(sender)) {
               sender.sendMessage("§c権限がありません。");
               return true;
            }

            this.config.reload();
            this.stats.load();
            this.fieldItems.shutdown();
            this.fieldItems.load();
            sender.sendMessage("§aFFA設定を再読み込みしました。");
            break;
         default:
            sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload|fielditem");
      }

      return true;
   }

   List<String> tabComplete(String[] args, CommandSender sender) {
      if (args.length == 2) {
         return this.hasAdmin(sender)
            ? List.of("leave", "stats", "setcenter", "setkits", "createkits", "removekits", "reload", "fielditem")
            : List.of("leave", "stats");
      } else {
         return args.length >= 2 && "fielditem".equalsIgnoreCase(args[1]) ? this.fieldItems.tabComplete(args) : List.of();
      }
   }

   void openKitSelector(Player player) {
      List<FfaKit> kits = FfaKit.activeKits(this.config);
      int size = Math.max(9, Math.min(54, (kits.size() + 8) / 9 * 9));
      Inventory inventory = Bukkit.createInventory(player, size, Component.text("FFAキット選択"));
      FfaKit selected = this.stands.selectedKit();
      int slot = 0;

      for (FfaKit kit : kits) {
         if (slot >= size) {
            break;
         }

         inventory.setItem(slot++, this.selectorItem(kit, kit == selected));
      }

      this.fillEmptyKitSelectorSlots(inventory);
      player.openInventory(inventory);
   }

   private void fillEmptyKitSelectorSlots(Inventory inventory) {
      ItemStack filler = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = filler.getItemMeta();
      meta.displayName(Component.text(" "));
      filler.setItemMeta(meta);

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {
            inventory.setItem(slot, filler.clone());
         }
      }
   }

   boolean handleKitSelectorClick(Player player, InventoryClickEvent event) {
      if (!"FFAキット選択".equals(PlainTextComponentSerializer.plainText().serialize(event.getView().title()))) {
         return false;
      }

      if (event.getClickedInventory() != event.getView().getTopInventory()) {
         return true;
      }

      FfaKit kit = this.kitFromSelectorItem(event.getCurrentItem());
      if (kit == null) {
         return true;
      }

      kit = this.sanitizeKit(kit);
      this.stands.applySelectedKit(kit);
      player.closeInventory();
      this.join(player, kit);
      return true;
   }

   void join(Player player, FfaKit kit) {
      kit = this.sanitizeKit(kit);
      FfaKit selectedKit = kit;
      if (!this.config.enabled()) {
         player.sendMessage("§cFFAは現在無効です。");
      } else {
         Location arena = this.config.center();
         if (arena == null) {
            player.sendMessage("§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。");
         } else if (selectedKit == FfaKit.SPEAR && selectedKit.spearMaterial(this.config, this.plugin, true) == null) {
            player.sendMessage("§c槍アイテムが現在の Paper API で見つかりません。Paper API / Minecraft バージョンを確認してください。");
         } else {
            this.sessions.computeIfAbsent(player.getUniqueId(), ignored -> new FfaManager.FfaSession(selectedKit, FfaManager.PlayerState.capture(player)));
            FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
            this.cleanupKitRuntime(player);
            session.kit = selectedKit;
            this.prepareForFight(player, selectedKit);
            this.updateScoreboard(player);
            player.teleport(arena);
            player.sendMessage("§aFFAに参加しました。キット: §f" + this.stripColor(selectedKit.displayName(this.config)));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.2F);
         }
      }
   }

   void leave(Player player, boolean notify) {
      FfaManager.FfaSession session = this.sessions.remove(player.getUniqueId());
      if (session == null) {
         if (notify) {
            player.sendMessage("§eFFAには参加していません。");
         }
      } else {
         this.cleanupKitRuntime(player);
         this.clearTemporaryState(player);
         session.state.restore(player, this.leaveLocation(player.getWorld()));
         if (notify) {
            player.sendMessage("§eFFAから退出しました。");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 0.8F);
         }
      }
   }

   private void awardAssassinKillReward(Player killer) {
      FfaManager.FfaSession session = this.sessions.get(killer.getUniqueId());
      if (session != null && session.kit == FfaKit.ASSASSIN) {
         String path = "players." + killer.getUniqueId() + ".ffa.assassin-kills";
         int kills = this.plugin.data().getInt(path, 0) + 1;
         this.plugin.data().set(path, kills);
         this.plugin.saveData();
         if (kills % 2 == 0) {
            ItemStack dagger = FfaKit.kitItem(this.plugin, FfaKit.ASSASSIN, "fatal_dagger", Material.GOLDEN_SWORD, "§5致命の短剣", 1, Map.of());
            this.tagOwner(dagger, killer.getUniqueId());
            killer.getInventory().addItem(new ItemStack[]{dagger});
            killer.sendMessage("§5プレイヤー2キル報酬: 致命の短剣を獲得しました。");
            killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 0.7F);
         }
      }
   }

   private Location leaveLocation(World world) {
      int y = world.getHighestBlockYAt(0, 0) + 1;
      return new Location(world, 0.5, y, 0.5);
   }

   void handleDeath(Player victim, Player killer) {
      FfaManager.FfaSession victimSession = this.sessions.get(victim.getUniqueId());
      if (victimSession != null) {
         if (killer == null) {
            killer = this.recentAttacker(victim);
         }

         this.damageCredits.remove(victim.getUniqueId());
         this.stats.recordDeath(victim);
         if (killer != null && this.isPlaying(killer) && !killer.getUniqueId().equals(victim.getUniqueId())) {
            this.stats.recordKill(killer);
            this.awardAssassinKillReward(killer);
            this.awardKillEmeralds(killer, victim);
            this.updateScoreboard(killer);
            killer.sendMessage("§a" + victim.getName() + " を倒しました！ 現在の連続キル: " + this.stats.currentStreak(killer.getUniqueId()));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
         }

         this.cleanupKitRuntime(victim);
         this.clearTemporaryState(victim);
         this.deathLeaveRestores.remove(victim.getUniqueId());
         victim.sendMessage("§cあなたは倒されました。再出撃を準備しています。");
      }
   }

   Location handleDeathLeaveRespawn(Player player) {
      FfaManager.DeathLeaveRestore restore = this.deathLeaveRestores.remove(player.getUniqueId());
      if (restore == null) {
         return null;
      }

      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline()) {
            restore.state().restore(player, restore.location());
         }
      });
      return restore.location();
   }

   void respawn(Player player) {
      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      if (session != null) {
         Location respawn = this.respawnLocation();
         if (respawn == null) {
            player.sendMessage("§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。");
         } else {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && this.isPlaying(player)) {
                  this.cleanupKitRuntime(player);
                  this.prepareForFight(player, this.sanitizeKit(session.kit));
                  this.updateScoreboard(player);
                  player.teleport(respawn);
                  player.sendMessage("§a再出撃しました。");
                  player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.3F);
               }
            }, this.config.respawnDelayTicks());
         }
      }
   }

   boolean commandAllowed(String commandLine) {
      if (!this.config.restrictCommands()) {
         return true;
      }

      String normalized = commandLine.toLowerCase(Locale.ROOT).trim();

      for (String allowed : this.config.allowedCommands()) {
         if (normalized.equals(allowed) || normalized.startsWith(allowed + " ")) {
            return true;
         }
      }

      return false;
   }

   boolean restrictionAllows(String key) {
      return this.config.restriction(key);
   }

   boolean isFfaItem(ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.BOOLEAN));
   }

   boolean handleFieldItemPickup(EntityPickupItemEvent event) {
      return this.fieldItems.handlePickup(event);
   }

   boolean handleFieldItemDamage(EntityDamageEvent event) {
      return this.fieldItems.handleEntityDamage(event);
   }

   boolean handleFieldItemInventoryPickup(InventoryPickupItemEvent event) {
      return this.fieldItems.handleInventoryPickup(event);
   }

   boolean handlePotionUse(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (!event.getAction().isRightClick()) {
         return false;
      }

      if (!this.isPlaying(player)) {
         return false;
      }

      ItemStack item = event.getItem();
      if (!this.isFfaItem(item)) {
         return false;
      }

      String kind = this.itemKind(item);
      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      if (session == null) {
         return false;
      }

      if ("food".equals(kind)) {
         this.handleReusableFood(player, item);
         return true;
      }

      if ("golden_apple".equals(kind) && session.kit == FfaKit.SWORD) {
         this.handleReusableGoldenApple(player, item);
         return true;
      }

      if ("invisibility_potion".equals(kind) && session.kit == FfaKit.ASSASSIN) {
         if (this.beginCooldown(player, "assassin_invisibility", 180L, "透明化")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0, false, false, true));
            this.restoreReusableItem(player, event.getHand(), item.clone());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.6F);
         }

         return true;
      } else if ("sniper".equals(kind) && session.kit == FfaKit.SNIPER) {
         int capacity = Math.max(1, session.kit.sniperCapacity(this.config));
         if (!this.sniperReloadTasks.containsKey(player.getUniqueId())) {
            this.startCrossbowReload(player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, capacity, "スナイパー");
         }

         return true;
      } else if (kind.startsWith("summon_") && session.kit == FfaKit.NECROMANCER) {
         String mob = kind.substring("summon_".length());
         long seconds = this.summonCooldownSeconds(mob);
         if (this.beginCooldown(player, "summon_" + mob, seconds, "召喚")) {
            ItemStack egg = item.clone();
            egg.setAmount(1);
            if (this.summonNecromancerMob(player, mob)) {
               this.startSummonEggRefill(player, mob, egg);
            }
         }

         return true;
      } else if (kind.startsWith("trap_") && session.kit == FfaKit.TRAPPER) {
         String trap = kind.substring("trap_".length());
         long seconds = this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.TRAPPER, "trap-cooldown-seconds"), 20L);
         if (this.beginCooldown(player, "trap_" + trap, seconds, "罠")) {
            this.placeTrap(player, trap, event);
         }

         return true;
      } else if (kind.startsWith("chemist_potion_") && session.kit == FfaKit.WIZARD) {
         String ability = this.itemAbility(item).replaceFirst("^chemist_potion_", "");
         long seconds = this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.WIZARD, "potion-cooldowns." + ability), 15L);
         if (!this.beginCooldown(player, "chemist_" + ability, seconds, "ポーション")) {
            return true;
         }

         this.restoreReusableItem(player, event.getHand(), item.clone());
         return false;
      } else {
         if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            this.scheduleWindChargeRefillCheck(player);
         }

         return false;
      }
   }

   private void handleReusableFood(Player player, ItemStack item) {
      long seconds = this.plugin.getConfig().getLong("ffa.kits.food-cooldown-seconds", 15L);
      if (this.beginCooldown(player, "kit_food", seconds, "食料")) {
         player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
         player.setSaturation(Math.min(20.0F, player.getSaturation() + 7.2F));
         ItemStack restored = item.clone();
         restored.setAmount(1);
         this.setHeldItem(player, restored);
         player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8F, 1.1F);
      }
   }

   private void handleReusableGoldenApple(Player player, ItemStack item) {
      long seconds = this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.SWORD, "golden-apple-cooldown-seconds"), 100L);
      if (this.beginCooldown(player, "sword_golden_apple", seconds, "金リンゴ")) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false, true));
         player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, false, false, true));
         ItemStack restored = item.clone();
         restored.setAmount(1);
         this.setHeldItem(player, restored);
         player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8F, 0.9F);
      }
   }

   private boolean beginCooldown(Player player, String ability, long seconds, String label) {
      long now = System.currentTimeMillis();
      long until = this.cooldownUntil(player.getUniqueId(), ability);
      if (until > now) {
         long remaining = Math.max(1L, (long)Math.ceil((until - now) / 1000.0));
         player.sendActionBar(Component.text(label + "再使用まで " + remaining + "秒", NamedTextColor.RED));
         return false;
      } else {
         this.setCooldownUntil(player.getUniqueId(), ability, now + Math.max(1L, seconds) * 1000L);
         return true;
      }
   }

   private long cooldownUntil(UUID uuid, String ability) {
      return this.plugin.data().getLong("players." + uuid + ".ffa-cooldowns." + ability, 0L);
   }

   private void setCooldownUntil(UUID uuid, String ability, long until) {
      this.plugin.data().set("players." + uuid + ".ffa-cooldowns." + ability, until);
      this.plugin.saveData();
   }

   private void restoreReusableItem(Player player, EquipmentSlot hand, ItemStack item) {
      item.setAmount(1);
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline() && this.isPlaying(player)) {
            if (hand == EquipmentSlot.OFF_HAND) {
               player.getInventory().setItemInOffHand(item);
            } else {
               player.getInventory().setItemInMainHand(item);
            }

            player.updateInventory();
         }
      });
   }

   private void setHeldItem(Player player, ItemStack item) {
      if (player.getInventory().getItemInOffHand().isSimilar(item)) {
         player.getInventory().setItemInOffHand(item);
      } else {
         player.getInventory().setItemInMainHand(item);
      }

      player.updateInventory();
   }

   private void scheduleWindChargeRefillCheck(Player player) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline() && this.isPlaying(player)) {
            FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
            if (session != null && session.kit == FfaKit.MACE && this.countFfaItem(player, "wind_charge") <= 0) {
               this.startWindChargeRefill(player);
            }
         }
      }, 2L);
   }

   private void startWindChargeRefill(Player player) {
      UUID uuid = player.getUniqueId();
      if (!this.windChargeRefillTasks.containsKey(uuid)) {
         long delay = Math.max(1L, this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.MACE, "wind-charge-refill-seconds"), 10L)) * 20L;
         BukkitTask task = this.plugin
            .getServer()
            .getScheduler()
            .runTaskLater(
               this.plugin,
               () -> {
                  this.windChargeRefillTasks.remove(uuid);
                  if (player.isOnline() && this.isPlaying(player)) {
                     FfaManager.FfaSession session = this.sessions.get(uuid);
                     if (session != null && session.kit == FfaKit.MACE && this.countFfaItem(player, "wind_charge") <= 0) {
                        ItemStack item = FfaKit.kitItem(
                           this.plugin,
                           FfaKit.MACE,
                           "wind_charge",
                           Material.WIND_CHARGE,
                           "§f重戦士のウィンドチャージ",
                           this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.MACE, "wind-charge"), 10),
                           Map.of()
                        );
                        this.tagOwner(item, uuid);
                        player.getInventory().addItem(new ItemStack[]{item});
                        player.sendActionBar(Component.text("ウィンドチャージ補充", NamedTextColor.GREEN));
                     }
                  }
               },
               delay
            );
         this.windChargeRefillTasks.put(uuid, task);
      }
   }

   private int countFfaItem(Player player, String kind) {
      int count = 0;

      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isFfaItem(item) && kind.equals(this.itemKind(item))) {
            count += item.getAmount();
         }
      }

      return count;
   }

   private long summonCooldownSeconds(String mob) {
      return switch (mob) {
         case "zombie" -> 5L;
         case "husk", "stray" -> 15L;
         case "drowned", "phantom" -> 30L;
         case "skeleton" -> 10L;
         case "bogged" -> 20L;
         case "wither_skeleton" -> 60L;
         default -> 15L;
      };
   }

   private boolean summonNecromancerMob(Player owner, String mob) {
      EntityType type = this.summonType(mob);
      if (type == null) {
         owner.sendMessage("§cこの召喚Mobは現在のAPIで使用できません: " + mob);
         return false;
      }

      Location spawn = owner.getLocation().clone().add(owner.getLocation().getDirection().setY(0).normalize().multiply(1.5));
      Entity entity = owner.getWorld().spawnEntity(spawn, type);
      entity.getPersistentDataContainer().set(this.entityKindKey, PersistentDataType.STRING, "summon");
      entity.getPersistentDataContainer().set(this.entityOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
      entity.setCustomName("§5" + owner.getName() + "の召喚");
      entity.setCustomNameVisible(false);
      if (entity instanceof LivingEntity living) {
         living.setCanPickupItems(false);
         living.setRemoveWhenFarAway(false);
         if ("drowned".equals(mob) && living.getEquipment() != null) {
            living.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
         }
      }

      UUID ownerId = owner.getUniqueId();
      List<UUID> owned = this.summonedMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
      int maxOwned = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.NECROMANCER, "max-summons"), 5));

      while (owned.size() >= maxOwned) {
         this.removeSummonEntity(owned.remove(0));
      }

      owned.add(entity.getUniqueId());
      this.summonOwners.put(entity.getUniqueId(), ownerId);
      BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.removeSummonEntity(entity.getUniqueId()), 600L);
      this.summonExpiryTasks.put(entity.getUniqueId(), task);
      owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.8F, 0.8F);
      return true;
   }

   private void startSummonEggRefill(Player player, String mob, ItemStack egg) {
      String kind = "summon_" + mob;
      this.removeFfaItems(player, kind);
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
         if (player.isOnline() && session != null && session.kit == FfaKit.NECROMANCER) {
            this.removeFfaItems(player, kind);
            player.updateInventory();
         }
      });
      Map<String, BukkitTask> tasks = this.summonEggRefillTasks.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
      BukkitTask old = tasks.remove(kind);
      if (old != null) {
         old.cancel();
      }

      long remainingMillis = Math.max(1L, this.cooldownUntil(player.getUniqueId(), kind) - System.currentTimeMillis());
      long delay = Math.max(1L, (remainingMillis + 49L) / 50L);
      BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         Map<String, BukkitTask> currentTasks = this.summonEggRefillTasks.get(player.getUniqueId());
         if (currentTasks != null) {
            currentTasks.remove(kind);
            if (currentTasks.isEmpty()) {
               this.summonEggRefillTasks.remove(player.getUniqueId());
            }
         }

         FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
         if (player.isOnline() && session != null && session.kit == FfaKit.NECROMANCER && this.countFfaItem(player, kind) <= 0) {
            this.restoreSummonEggToFixedSlot(player, mob, egg.clone());
            player.updateInventory();
            player.sendActionBar(Component.text("召喚エッグを再配布しました", NamedTextColor.GREEN));
         }
      }, delay);
      tasks.put(kind, task);
   }

   private void restoreSummonEggToFixedSlot(Player player, String mob, ItemStack egg) {
      int slot = switch (mob) {
         case "zombie" -> 1;
         case "husk" -> 2;
         case "drowned" -> 3;
         case "skeleton" -> 4;
         case "stray" -> 5;
         case "bogged" -> 6;
         case "wither_skeleton" -> 7;
         case "phantom" -> 8;
         default -> -1;
      };
      if (slot < 0) {
         player.getInventory().addItem(new ItemStack[]{egg});
      } else {
         ItemStack displaced = player.getInventory().getItem(slot);
         player.getInventory().setItem(slot, egg);
         if (displaced != null && displaced.getType() != Material.AIR) {
            player.getInventory().addItem(new ItemStack[]{displaced});
         }
      }
   }

   private void restoreNecromancerEggCooldowns(Player player) {
      long now = System.currentTimeMillis();

      for (String mob : NECROMANCER_MOBS) {
         String kind = "summon_" + mob;
         if (this.cooldownUntil(player.getUniqueId(), kind) > now) {
            ItemStack egg = this.findKitItem(player, kind);
            if (egg != null) {
               this.startSummonEggRefill(player, mob, egg.clone());
            }
         }
      }
   }

   private void removeFfaItems(Player player, String kind) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isFfaItem(item) && kind.equals(this.itemKind(item))) {
            item.setAmount(0);
         }
      }
   }

   private EntityType summonType(String mob) {
      return switch (mob) {
         case "zombie" -> EntityType.ZOMBIE;
         case "husk" -> EntityType.HUSK;
         case "drowned" -> EntityType.DROWNED;
         case "skeleton" -> EntityType.SKELETON;
         case "stray" -> EntityType.STRAY;
         case "bogged" -> EntityType.BOGGED;
         case "wither_skeleton" -> EntityType.WITHER_SKELETON;
         case "phantom" -> EntityType.PHANTOM;
         default -> null;
      };
   }

   private void placeTrap(Player owner, String type, PlayerInteractEvent event) {
      Block target = this.trapTargetBlock(owner, event);
      if (target != null && this.isFfaWorld(target.getWorld())) {
         if (this.plugin.isStructureProtectedLocation(target.getLocation())) {
            owner.sendMessage("§c保護区域には罠を設置できません。");
         } else if (!target.isEmpty()) {
            owner.sendMessage("§cここには罠を設置できません。");
         } else {
            Material material = this.trapMaterial(type);
            if (material == null) {
               owner.sendMessage("§c不明な罠です。");
            } else {
               UUID ownerId = owner.getUniqueId();
               Map<String, FfaManager.TrapState> owned = this.traps.computeIfAbsent(ownerId, ignored -> new HashMap<>());
               FfaManager.TrapState old = owned.remove(type);
               if (old != null) {
                  this.restoreTrap(old);
               }

               BlockState original = target.getState();
               target.setType(material, false);
               long duration = Math.max(1L, this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.TRAPPER, "trap-duration-seconds"), 30L));
               owned.put(
                  type,
                  new FfaManager.TrapState(type, ownerId, target.getLocation().toBlockLocation(), original, System.currentTimeMillis() + duration * 1000L)
               );
               owner.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_ON, 0.8F, 1.0F);
               owner.sendActionBar(Component.text("罠を設置しました", NamedTextColor.YELLOW));
            }
         }
      } else {
         owner.sendMessage("§cFFA範囲内でのみ罠を設置できます。");
      }
   }

   private Block trapTargetBlock(Player owner, PlayerInteractEvent event) {
      return event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
         ? event.getClickedBlock().getRelative(event.getBlockFace())
         : owner.getLocation().getBlock();
   }

   private Material trapMaterial(String type) {
      return switch (type) {
         case "explosion" -> Material.STONE_PRESSURE_PLATE;
         case "web" -> Material.OAK_PRESSURE_PLATE;
         case "poison" -> Material.SPRUCE_PRESSURE_PLATE;
         default -> null;
      };
   }

   private void tickTraps() {
      long now = System.currentTimeMillis();

      for (UUID owner : new ArrayList<>(this.traps.keySet())) {
         Map<String, FfaManager.TrapState> owned = this.traps.get(owner);
         if (owned != null) {
            for (FfaManager.TrapState trap : new ArrayList<>(owned.values())) {
               if (now >= trap.expiresAt()) {
                  owned.remove(trap.type());
                  this.restoreTrap(trap);
               } else {
                  Player target = this.trapTarget(trap);
                  if (target != null) {
                     owned.remove(trap.type());
                     this.triggerTrap(trap, target);
                  }
               }
            }

            if (owned.isEmpty()) {
               this.traps.remove(owner);
            }
         }
      }
   }

   private Player trapTarget(FfaManager.TrapState trap) {
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (this.isPlaying(player)
            && player.getWorld().equals(trap.location().getWorld())
            && !trap.owner().equals(player.getUniqueId())
            && player.getLocation().distanceSquared(trap.location().clone().add(0.5, 0.0, 0.5)) <= 1.2) {
            return player;
         }
      }

      return null;
   }

   private void triggerTrap(FfaManager.TrapState trap, Player target) {
      Player owner = this.plugin.getServer().getPlayer(trap.owner());
      this.restoreTrap(trap);
      if (owner != null && this.isPlaying(owner)) {
         this.recordDamage(owner, target);
         World world = trap.location().getWorld();
         if ("explosion".equals(trap.type())) {
            target.damage(6.0, owner);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 128, false, false, true));
            if (world != null) {
               world.spawnParticle(Particle.EXPLOSION, trap.location().clone().add(0.5, 0.5, 0.5), 1);
               world.playSound(trap.location(), Sound.ENTITY_GENERIC_EXPLODE, 0.9F, 1.1F);
            }
         } else if ("poison".equals(trap.type())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, false, true));
            if (world != null) {
               AreaEffectCloud cloud = (AreaEffectCloud)world.spawn(trap.location().clone().add(0.5, 0.2, 0.5), AreaEffectCloud.class);
               cloud.setRadius(2.0F);
               cloud.setDuration(100);
               cloud.setRadiusOnUse(0.0F);
               cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 1), true);
               world.playSound(trap.location(), Sound.ENTITY_SPIDER_AMBIENT, 0.8F, 1.0F);
            }
         } else {
            if ("web".equals(trap.type())) {
               this.placeTemporaryWebs(target.getLocation());
            }
         }
      }
   }

   private void placeTemporaryWebs(Location center) {
      List<BlockState> originals = new ArrayList<>();

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            Block block = center.clone().add(x, 0.0, z).getBlock();
            if (block.isEmpty()) {
               originals.add(block.getState());
               block.setType(Material.COBWEB, false);
            }
         }
      }

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> originals.forEach(state -> state.update(true, false)), 80L);
   }

   private void restoreTrap(FfaManager.TrapState trap) {
      trap.original().update(true, false);
   }

   private void restoreTraps(UUID owner) {
      Map<String, FfaManager.TrapState> owned = this.traps.remove(owner);
      if (owned != null) {
         owned.values().forEach(this::restoreTrap);
      }
   }

   private void restoreAllTraps() {
      for (UUID owner : new ArrayList<>(this.traps.keySet())) {
         this.restoreTraps(owner);
      }

      this.traps.clear();
   }

   private void tickSummons() {
      for (Entry<UUID, UUID> entry : new ArrayList<>(this.summonOwners.entrySet())) {
         Entity entity = this.entityById(entry.getKey());
         Player owner = this.plugin.getServer().getPlayer(entry.getValue());
         if (entity instanceof Mob mob && owner != null && this.isPlaying(owner)) {
            Player target = this.nearestEnemy(owner, entity.getLocation(), false);
            if (target != null) {
               mob.setTarget(target);
            }
         } else {
            this.removeSummonEntity(entry.getKey());
         }
      }

      for (Entry<UUID, UUID> entry : new ArrayList<>(this.bugOwners.entrySet())) {
         Entity entity = this.entityById(entry.getKey());
         Player owner = this.plugin.getServer().getPlayer(entry.getValue());
         if (entity instanceof Mob mob && owner != null && this.isPlaying(owner)) {
            Player target = this.nearestEnemy(owner, entity.getLocation(), true);
            if (target != null) {
               mob.setTarget(target);
            }
         } else {
            this.removeBugEntity(entry.getKey());
         }
      }
   }

   private Player nearestEnemy(Player owner, Location location, boolean avoidBugMania) {
      Player best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (this.isPlaying(player) && !player.getUniqueId().equals(owner.getUniqueId()) && player.getWorld().equals(location.getWorld())) {
            FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
            if (!avoidBugMania || session == null || session.kit != FfaKit.BUG_MANIA) {
               double distance = player.getLocation().distanceSquared(location);
               if (distance < bestDistance && distance <= 1024.0) {
                  best = player;
                  bestDistance = distance;
               }
            }
         }
      }

      return best;
   }

   private void spawnBugSilverfish(Player owner, Location location) {
      int globalMax = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.BUG_MANIA, "max-global-silverfish"), 30));

      while (this.bugOwners.size() >= globalMax) {
         UUID first = this.bugOwners.keySet().stream().findFirst().orElse(null);
         if (first == null) {
            break;
         }

         this.removeBugEntity(first);
      }

      UUID ownerId = owner.getUniqueId();
      List<UUID> owned = this.bugMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
      int maxOwned = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.BUG_MANIA, "max-owned-silverfish"), 6));

      while (owned.size() >= maxOwned) {
         this.removeBugEntity(owned.remove(0));
      }

      Entity entity = location.getWorld().spawnEntity(location, EntityType.SILVERFISH);
      entity.getPersistentDataContainer().set(this.entityKindKey, PersistentDataType.STRING, "bug_silverfish");
      entity.getPersistentDataContainer().set(this.entityOwnerKey, PersistentDataType.STRING, ownerId.toString());
      if (entity instanceof LivingEntity living) {
         living.setCanPickupItems(false);
         living.setRemoveWhenFarAway(false);
      }

      owned.add(entity.getUniqueId());
      this.bugOwners.put(entity.getUniqueId(), ownerId);
      BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.removeBugEntity(entity.getUniqueId()), 400L);
      this.bugExpiryTasks.put(entity.getUniqueId(), task);
   }

   Player ownerOfFfaEntity(Entity entity) {
      if (entity == null) {
         return null;
      }

      UUID owner = this.summonOwners.get(entity.getUniqueId());
      if (owner == null) {
         owner = this.bugOwners.get(entity.getUniqueId());
      }

      if (owner == null) {
         owner = this.parseUuid((String)entity.getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING));
      }

      if (owner == null) {
         return null;
      }

      Player player = this.plugin.getServer().getPlayer(owner);
      return player != null && this.isPlaying(player) ? player : null;
   }

   UUID bugOwnerOf(Entity entity) {
      if (entity == null) {
         return null;
      }

      UUID owner = this.bugOwners.get(entity.getUniqueId());
      if (owner == null) {
         owner = this.parseUuid((String)entity.getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING));
      }

      return owner;
   }

   boolean isBugMania(Player player) {
      FfaManager.FfaSession session = player == null ? null : this.sessions.get(player.getUniqueId());
      return session != null && session.kit == FfaKit.BUG_MANIA;
   }

   NamespacedKey entityKindKey() {
      return this.entityKindKey;
   }

   void handleEntityDeath(EntityDeathEvent event) {
      Entity entity = event.getEntity();
      String kind = (String)entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if (kind != null) {
         event.getDrops().clear();
         event.setDroppedExp(0);
         UUID entityId = entity.getUniqueId();
         UUID owner = this.parseUuid((String)entity.getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING));
         if ("summon".equals(kind)) {
            this.clearSummonEntityState(entityId);
         } else if ("bug_silverfish".equals(kind)) {
            this.clearBugEntityState(entityId);
            Player killer = event.getEntity().getKiller();
            if (killer != null && (owner == null || !owner.equals(killer.getUniqueId())) && ThreadLocalRandom.current().nextInt(100) < 5) {
               killer.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 1200, 0, false, false, true));
            }
         }
      }
   }

   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
      String kind = (String)event.getEntity().getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if (kind != null) {
         UUID ownerId = this.parseUuid((String)event.getEntity().getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING));
         if (ownerId == null) {
            event.setCancelled(true);
         } else if (event.getTarget() instanceof Player target) {
            if (this.isPlaying(target) && !ownerId.equals(target.getUniqueId())) {
               FfaManager.FfaSession targetSession = this.sessions.get(target.getUniqueId());
               if ("bug_silverfish".equals(kind) && targetSession != null && targetSession.kit == FfaKit.BUG_MANIA) {
                  event.setCancelled(true);
               }
            } else {
               event.setCancelled(true);
            }
         } else {
            if (event.getTarget() != null
               && ownerId.equals(this.parseUuid((String)event.getTarget().getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING)))) {
               event.setCancelled(true);
            }
         }
      }
   }

   private void removeSummons(UUID owner) {
      List<UUID> owned = this.summonedMobs.remove(owner);
      if (owned != null) {
         for (UUID entityId : new ArrayList<>(owned)) {
            this.removeSummonEntity(entityId);
         }
      }
   }

   private void removeAllSummons() {
      for (UUID owner : new ArrayList<>(this.summonedMobs.keySet())) {
         this.removeSummons(owner);
      }

      this.summonedMobs.clear();
      this.summonOwners.clear();
   }

   private void removeSummonEntity(UUID entityId) {
      this.clearSummonEntityState(entityId);
      Entity entity = this.entityById(entityId);
      if (entity != null) {
         entity.remove();
      }
   }

   private void clearSummonEntityState(UUID entityId) {
      UUID owner = this.summonOwners.remove(entityId);
      if (owner != null) {
         List<UUID> owned = this.summonedMobs.get(owner);
         if (owned != null) {
            owned.remove(entityId);
            if (owned.isEmpty()) {
               this.summonedMobs.remove(owner);
            }
         }
      }

      BukkitTask task = this.summonExpiryTasks.remove(entityId);
      if (task != null) {
         task.cancel();
      }
   }

   private void removeBugMobs(UUID owner) {
      List<UUID> owned = this.bugMobs.remove(owner);
      if (owned != null) {
         for (UUID entityId : new ArrayList<>(owned)) {
            this.removeBugEntity(entityId);
         }
      }
   }

   private void removeAllBugMobs() {
      for (UUID owner : new ArrayList<>(this.bugMobs.keySet())) {
         this.removeBugMobs(owner);
      }

      this.bugMobs.clear();
      this.bugOwners.clear();
   }

   private void removeBugEntity(UUID entityId) {
      this.clearBugEntityState(entityId);
      Entity entity = this.entityById(entityId);
      if (entity != null) {
         entity.remove();
      }
   }

   private void clearBugEntityState(UUID entityId) {
      UUID owner = this.bugOwners.remove(entityId);
      if (owner != null) {
         List<UUID> owned = this.bugMobs.get(owner);
         if (owned != null) {
            owned.remove(entityId);
            if (owned.isEmpty()) {
               this.bugMobs.remove(owner);
            }
         }
      }

      BukkitTask task = this.bugExpiryTasks.remove(entityId);
      if (task != null) {
         task.cancel();
      }
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

   private void recordDamage(Player attacker, Player victim) {
      if (attacker != null && victim != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
         this.damageCredits.put(victim.getUniqueId(), new FfaManager.DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
      }
   }

   private Player recentAttacker(Player victim) {
      FfaManager.DamageCredit credit = this.damageCredits.get(victim.getUniqueId());
      if (credit != null && System.currentTimeMillis() - credit.at() <= 10000L) {
         Player attacker = this.plugin.getServer().getPlayer(credit.attacker());
         return attacker != null && this.isPlaying(attacker) ? attacker : null;
      } else {
         return null;
      }
   }

   private void triggerCrusherExplosion(Player owner, Player target, boolean attacking) {
      if (!this.crusherExplosionDamage.contains(owner.getUniqueId())) {
         double roll = ThreadLocalRandom.current().nextDouble();
         double scale = attacking ? 0.5 : 1.0;
         double damage = 0.0;
         double radius = 0.0;
         if (roll < 0.0625 * scale) {
            damage = 32.0;
            radius = 16.0;
         } else if (roll < 0.125 * scale) {
            damage = 16.0;
            radius = 8.0;
         } else if (roll < 0.25 * scale) {
            damage = 8.0;
            radius = 4.0;
         } else if (roll < 0.5 * scale) {
            damage = 4.0;
            radius = 2.0;
         }

         if (!(damage <= 0.0) && !(radius <= 0.0)) {
            Location origin = owner.getLocation().clone().add(0.0, 1.0, 0.0);
            this.crusherExplosionDamage.add(owner.getUniqueId());

            try {
               for (Player nearby : owner.getWorld().getPlayers()) {
                  if (this.isPlaying(nearby) && !nearby.getUniqueId().equals(owner.getUniqueId())) {
                     double distance = origin.distance(nearby.getLocation().clone().add(0.0, 1.0, 0.0));
                     if (!(distance > radius)) {
                        double distanceFactor = Math.max(0.0, 1.0 - distance / radius);
                        double coverFactor = owner.hasLineOfSight(nearby) ? 1.0 : 0.5;
                        double dealt = damage * distanceFactor * coverFactor;
                        if (dealt > 0.0) {
                           this.recordDamage(owner, nearby);
                           nearby.damage(dealt * 0.8, owner);
                        }
                     }
                  }
               }
            } finally {
               this.crusherExplosionDamage.remove(owner.getUniqueId());
            }

            owner.getWorld()
               .spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, Math.min(2.0, radius / 4.0), 0.5, Math.min(2.0, radius / 4.0), 0.05);
            owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
         }
      }
   }

   private void capFinalDamage(EntityDamageByEntityEvent event, double cap) {
      if (event.getFinalDamage() > cap && event.getDamage() > 0.0) {
         event.setDamage(event.getDamage() * (cap / event.getFinalDamage()));
      }
   }

   private void refillOneShotArrowLater(Player player) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline() && this.isPlaying(player) && this.findKitItem(player, "event_one_shot_bow") != null) {
            if (this.countFfaItem(player, "event_one_shot_arrow") <= 0) {
               ItemStack arrow = FfaKit.kitItem(this.plugin, FfaKit.BOW, "event_one_shot_arrow", Material.ARROW, "§c一撃必殺の矢", 1, Map.of());
               this.tagOwner(arrow, player.getUniqueId());
               player.getInventory().addItem(new ItemStack[]{arrow});
            }
         }
      }, 100L);
   }

   private void updateSeaWarriorTrident(Player player) {
      ItemStack trident = this.findKitItem(player, "trident");
      if (trident != null) {
         World world = player.getWorld();
         trident.removeEnchantment(Enchantment.LOYALTY);
         trident.removeEnchantment(Enchantment.CHANNELING);
         trident.removeEnchantment(Enchantment.RIPTIDE);
         if (world.isThundering()) {
            trident.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
         } else if (world.hasStorm()) {
            trident.addUnsafeEnchantment(Enchantment.RIPTIDE, 1);
         } else {
            trident.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
         }
      }
   }

   void handleProjectileLaunch(ProjectileLaunchEvent event) {
      Projectile projectile = event.getEntity();
      if (projectile.getShooter() instanceof Player player && this.isPlaying(player)) {
         FfaKit kit = this.sessions.get(player.getUniqueId()).kit;
         if (projectile instanceof ThrownPotion potion && kit == FfaKit.WIZARD) {
            PotionEffectType type = potion.getEffects().stream().findFirst().<PotionEffectType>map(PotionEffect::getType).orElse(null);
            String projectileKind = FfaKit.isNegative(type) ? "chemist_negative_potion" : "chemist_potion";
            if (type != null && FfaKit.isNegative(type)) {
               this.wizardPotionOwners.put(projectile.getUniqueId(), player.getUniqueId());
            }

            projectile.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, projectileKind);
            projectile.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         } else {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (this.isFfaItem(mainHand) && "event_sky_spear".equals(this.itemKind(mainHand))) {
               projectile.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, "event_sky_spear");
               projectile.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            }

            if (projectile instanceof Trident trident && kit == FfaKit.TRIDENT) {
               trident.setPickupStatus(PickupStatus.ALLOWED);
               trident.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, "trident");
               trident.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
               this.trackedTridents.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(trident.getUniqueId());
            }
         }
      }
   }

   void handleProjectileHit(ProjectileHitEvent event) {
      if (event.getEntity() instanceof Trident trident
         && "trident".equals(trident.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
         trident.setPickupStatus(PickupStatus.ALLOWED);
      }
   }

   boolean handleArrowPickup(PlayerPickupArrowEvent event) {
      AbstractArrow arrow = event.getArrow();
      if (arrow instanceof Trident && "trident".equals(arrow.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
         UUID owner = this.parseUuid((String)arrow.getPersistentDataContainer().get(this.projectileOwnerKey, PersistentDataType.STRING));
         if (owner == null) {
            event.setCancelled(true);
            arrow.remove();
            return true;
         } else {
            Player player = event.getPlayer();
            if (!owner.equals(player.getUniqueId())) {
               event.setCancelled(true);
               return true;
            } else {
               FfaManager.FfaSession session = this.sessions.get(owner);
               if (session != null && session.kit == FfaKit.TRIDENT) {
                  event.setCancelled(false);
                  this.untrackTrident(owner, arrow.getUniqueId());
                  return true;
               } else {
                  event.setCancelled(true);
                  this.untrackTrident(owner, arrow.getUniqueId());
                  arrow.remove();
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   void handlePotionSplash(PotionSplashEvent event) {
      UUID owner = this.wizardPotionOwners.remove(event.getEntity().getUniqueId());
      if (owner != null) {
         event.getAffectedEntities().forEach(entity -> {
            if (entity instanceof Player player && owner.equals(player.getUniqueId())) {
               event.setIntensity(entity, 0.0);
            }
         });
      }
   }

   void handleBowShoot(EntityShootBowEvent event) {
      if (event.getEntity() instanceof Player player && this.isPlaying(player)) {
         ItemStack bow = event.getBow();
         if (this.isFfaItem(bow)) {
            String kind = this.itemKind(bow);
            if ("event_one_shot_bow".equals(kind)) {
               if (event.getProjectile() instanceof Entity projectile) {
                  projectile.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, "event_one_shot_arrow");
                  projectile.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
               }

               this.refillOneShotArrowLater(player);
            } else if ("sniper".equals(kind)) {
               this.handleAmmoCrossbow(event, player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, 2, "スナイパー", "sniper");
            } else if ("revolver".equals(kind)) {
               this.handleAmmoCrossbow(event, player, FfaKit.CROSSBOW, this.revolverAmmo, this.revolverReloadTasks, 6, "リボルバー", "revolver");
            }
         }
      }
   }

   private void handleAmmoCrossbow(
      EntityShootBowEvent event,
      Player player,
      FfaKit expectedKit,
      Map<UUID, Integer> ammoMap,
      Map<UUID, BukkitTask> reloadTasks,
      int fallbackCapacity,
      String label,
      String projectileKind
   ) {
      if (this.isDuplicateCrossbowShot(player)) {
         event.setCancelled(true);
         return;
      }

      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
         if (session == null || session.kit != expectedKit) {
            event.setCancelled(true);
         } else if (reloadTasks.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("リロード中", NamedTextColor.RED));
         } else if (expectedKit == FfaKit.SNIPER && System.currentTimeMillis() < this.sniperShotCooldownUntil.getOrDefault(player.getUniqueId(), 0L)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("スナイパーのクールダウン中", NamedTextColor.RED));
         } else {
            int configuredCapacity = expectedKit == FfaKit.SNIPER ? session.kit.sniperCapacity(this.config) : session.kit.revolverCapacity(this.config);
            int capacity = Math.max(1, configuredCapacity <= 0 ? fallbackCapacity : configuredCapacity);
            int ammo = Math.max(0, ammoMap.getOrDefault(player.getUniqueId(), capacity));
            if (ammo <= 0) {
               event.setCancelled(true);
               if (expectedKit == FfaKit.SNIPER) {
                  player.sendActionBar(Component.text("右クリックでリロード", NamedTextColor.YELLOW));
               } else {
                  this.startCrossbowReload(player, expectedKit, ammoMap, reloadTasks, capacity, label);
               }
            } else {
               ammoMap.put(player.getUniqueId(), --ammo);
               if (event.getProjectile() instanceof Entity projectile) {
                  projectile.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, projectileKind);
                  projectile.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
               }

               this.playCrossbowShotSound(player, expectedKit);
               int remaining = ammo;
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                  if (player.isOnline() && this.isPlaying(player)) {
                     this.updateAmmoCrossbowItem(player, expectedKit, label, remaining, capacity);
                     player.sendActionBar(Component.text(label + " " + remaining + "/" + capacity, NamedTextColor.LIGHT_PURPLE));
                     if (remaining > 0) {
                        this.rechargeAmmoCrossbow(player, expectedKit);
                     } else if (expectedKit == FfaKit.SNIPER) {
                        player.sendActionBar(Component.text("右クリックでリロード", NamedTextColor.YELLOW));
                     }
                  }
               });
               if (expectedKit == FfaKit.SNIPER) {
                  this.sniperShotCooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
               }
            }
         }
   }

   private void playCrossbowShotSound(Player player, FfaKit kit) {
      float blastVolume = kit == FfaKit.SNIPER ? 0.45F : 0.65F;
      float blastPitch = kit == FfaKit.SNIPER ? 0.85F : 1.45F;
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, blastVolume, blastPitch);
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, kit == FfaKit.SNIPER ? 0.18F : 0.25F, 1.8F);
   }

   void adjustFfaDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
      if (attacker != null && victim != null && this.isPlaying(attacker) && this.isPlaying(victim)) {
         if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
            this.recordDamage(attacker, victim);
            Object damager = event.getDamager();
            if (damager instanceof Projectile projectile
               && "event_one_shot_arrow".equals(projectile.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
               if (!this.plugin.isStructureProtectedLocation(victim.getLocation())) {
                  event.setDamage(Math.max(event.getDamage(), victim.getHealth() + victim.getAbsorptionAmount() + 2.0));
               }
            } else {
               if (damager instanceof Projectile projectile
                  && "revolver".equals(projectile.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
                  double multiplier = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.CROSSBOW, "damage-multiplier"), 0.7);
                  event.setDamage(event.getDamage() * Math.max(0.0, multiplier));
               }

               if (damager instanceof Projectile projectile
                  && "sniper".equals(projectile.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
                  double multiplier = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.SNIPER, "damage-multiplier"), 3.0);
                  event.setDamage(event.getDamage() * Math.max(0.0, multiplier));
               }

               if (damager instanceof Projectile projectile
                  && "event_sky_spear".equals(projectile.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING))) {
                  this.capFinalDamage(event, 12.0);
               }

               FfaManager.FfaSession victimSessionForCrusher = this.sessions.get(victim.getUniqueId());
               if (victimSessionForCrusher != null && victimSessionForCrusher.kit == FfaKit.CRUSHER) {
                  this.triggerCrusherExplosion(victim, attacker, false);
               }

               this.applyGamblerIncoming(event, victim);
               FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());
               if (session != null) {
                  if (session.kit == FfaKit.CRUSHER) {
                     this.triggerCrusherExplosion(attacker, victim, true);
                  }

                  ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                  if (!this.applyGamblerOutgoing(event, attacker, victim, session, mainHand)
                     && session.kit == FfaKit.GAMBLER
                     && this.isFfaItem(mainHand)
                     && "weapon".equals(this.itemKind(mainHand))
                     && event.getFinalDamage() > 0.0) {
                     double min = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.GAMBLER, "min-damage-multiplier"), -10.0);
                     double max = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.GAMBLER, "max-damage-multiplier"), 15.0);
                     int steps = (int)Math.round((Math.max(min, max) - Math.min(min, max)) / 0.5);
                     double multiplier = Math.min(min, max) + ThreadLocalRandom.current().nextInt(steps + 1) * 0.5;
                     attacker.sendMessage("§6ギャンブラー倍率: §e" + String.format(Locale.ROOT, "%.1f", multiplier) + "倍");
                     attacker.playSound(
                        attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55F, multiplier < 0.0 ? 0.6F : Math.min(2.0F, 0.9F + (float)multiplier / 20.0F)
                     );
                     if (multiplier == 15.0) {
                        attacker.sendMessage("§6§l✦✦✦ LUCKY PUNCH! 15.0倍! ✦✦✦");
                        attacker.playSound(attacker.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
                        this.plugin.unlockTitle(attacker, "ラッキーパンチ");
                     }

                     if (multiplier < 0.0) {
                        event.setCancelled(true);
                        if (this.gamblerSelfDamage.add(attacker.getUniqueId())) {
                           try {
                              attacker.damage(event.getDamage() * Math.abs(multiplier), attacker);
                           } finally {
                              this.gamblerSelfDamage.remove(attacker.getUniqueId());
                           }
                        }

                        return;
                     }

                     event.setDamage(event.getDamage() * multiplier);
                  }

                  if (session.kit == FfaKit.ASSASSIN && this.isFfaItem(mainHand) && event.getFinalDamage() > 0.0) {
                     String kind = this.itemKind(mainHand);
                     if ("fatal_sword".equals(kind) || "fatal_dagger".equals(kind)) {
                        if (this.plugin.isStructureProtectedLocation(victim.getLocation())) {
                           return;
                        }

                        event.setDamage(0.0);
                        double target = Math.max(0.0, victim.getHealth() - 0.5);
                        if (target > 0.0) {
                           victim.setHealth(Math.max(0.5, victim.getHealth() - target));
                           mainHand.setAmount(0);
                           attacker.sendActionBar(Component.text("致命の短剣を使用しました", NamedTextColor.DARK_RED));
                        }

                        return;
                     }

                     if ("poison_sword".equals(kind)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1, false, false, true));
                     }
                  }

                  if (session.kit == FfaKit.BUG_MANIA
                     && this.isFfaItem(mainHand)
                     && "bug_sword".equals(this.itemKind(mainHand))
                     && ThreadLocalRandom.current().nextInt(100) < 10) {
                     this.spawnBugSilverfish(attacker, victim.getLocation());
                  }

                  FfaManager.FfaSession victimSession = this.sessions.get(victim.getUniqueId());
                  if (victimSession != null && victimSession.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10) {
                     attacker.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 1200, 0, false, false, true));
                     attacker.sendActionBar(Component.text("虫食いを受けました", NamedTextColor.DARK_GREEN));
                  }

                  if (session != null
                     && session.kit == FfaKit.MACE
                     && this.isFfaItem(attacker.getInventory().getItemInMainHand())
                     && "mace".equals(this.itemKind(attacker.getInventory().getItemInMainHand()))) {
                     double cap = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.MACE, "max-final-damage"), 12.0);
                     this.capFinalDamage(event, cap);
                  }

                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double dealt = Math.max(0.0, event.getFinalDamage());
                     double heal = dealt
                        * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "lifesteal-percent"), 50.0))
                        / 100.0;
                     AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
                     double maxHealthValue = maxHealth == null ? 20.0 : maxHealth.getValue();
                     attacker.setHealth(Math.min(maxHealthValue, attacker.getHealth() + heal));
                     double total = this.vampireDamage.getOrDefault(attacker.getUniqueId(), 0.0) + dealt;
                     this.vampireDamage.put(attacker.getUniqueId(), total);
                     int level = Math.min(
                        3,
                        (int)(total / Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-per-strength-level"), 100.0)))
                     );
                     if (level > 0) {
                        attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, level - 1, false, false, true));
                     }

                     attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", heal) + " / 累計 " + (int)total, NamedTextColor.RED));
                  }
               }
            }
         }
      }
   }

   boolean leaveOnWorldExit() {
      return this.config.leaveOnWorldExit();
   }

   boolean isFfaWorld(World world) {
      if (world == null) {
         return false;
      }

      Set<String> names = new HashSet<>();
      Location center = this.config.center();
      if (center != null && center.getWorld() != null) {
         names.add(center.getWorld().getName().toLowerCase(Locale.ROOT));
      }

      this.addWorldName(names, "minigame");
      this.addWorldName(names, this.plugin.getConfig().getString("servers.minigame.world"));

      for (String configuredName : this.plugin.getConfig().getStringList("world-rules.pvp.enabled-worlds")) {
         this.addWorldName(names, configuredName);
      }

      return names.contains(world.getName().toLowerCase(Locale.ROOT));
   }

   Location center() {
      return this.config.center();
   }

   Location respawnLocation() {
      Location center = this.config.center();
      if (center == null) {
         return null;
      }

      return switch (this.config.respawnLocationMode()) {
         case "ffa-center", "center", "arena" -> center;
         case "kit-selection", "selection", "kits" -> {
            Location selection = this.config.kitSelection();
            yield selection == null ? center : selection;
         }
         default -> center.getWorld().getSpawnLocation();
      };
   }

   private void prepareForFight(Player player, FfaKit kit) {
      this.clearTemporaryState(player);
      player.setGameMode(GameMode.SURVIVAL);
      AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealth != null) {
         maxHealth.setBaseValue(20.0);
      }

      player.setHealth(20.0);
      PlayerInventory inventory = player.getInventory();
      inventory.clear();
      inventory.setArmorContents(null);
      inventory.setItemInOffHand(null);
      kit.applyTo(inventory, this.config, this.plugin);
      this.applyArmorBonus(player);
      this.tagOwnedKitItems(player);
      if (kit == FfaKit.NECROMANCER) {
         this.restoreNecromancerEggCooldowns(player);
      }

      if (kit == FfaKit.CROSSBOW) {
         this.revolverAmmo.put(player.getUniqueId(), kit.revolverCapacity(this.config));
         this.updateRevolverItem(player);
      }

      if (kit == FfaKit.SNIPER) {
         this.sniperAmmo.put(player.getUniqueId(), kit.sniperCapacity(this.config));
         this.updateSniperItem(player);
      }

      this.applyKitEffects(player, kit);
      this.fieldItems.applyActiveEventGear(player);
      player.updateInventory();
   }

   private FfaKit sanitizeKit(FfaKit kit) {
      return kit != null && kit.isActive(this.config) ? kit : FfaKit.SWORD;
   }

   private String itemKind(ItemStack item) {
      if (!this.isFfaItem(item)) {
         return "";
      }

      String value = (String)item.getItemMeta().getPersistentDataContainer().get(this.itemKindKey, PersistentDataType.STRING);
      return value == null ? "" : value;
   }

   private String itemAbility(ItemStack item) {
      if (!this.isFfaItem(item)) {
         return "";
      }

      String value = (String)item.getItemMeta().getPersistentDataContainer().get(this.abilityKey, PersistentDataType.STRING);
      return value == null ? this.itemKind(item) : value;
   }

   private void tagOwnedKitItems(Player player) {
      for (ItemStack item : player.getInventory().getContents()) {
         this.tagOwner(item, player.getUniqueId());
      }

      for (ItemStack item : player.getInventory().getArmorContents()) {
         this.tagOwner(item, player.getUniqueId());
      }

      this.tagOwner(player.getInventory().getItemInOffHand(), player.getUniqueId());
   }

   private void tagOwner(ItemStack item, UUID owner) {
      if (this.isFfaItem(item)) {
         ItemMeta meta = item.getItemMeta();
         meta.getPersistentDataContainer().set(this.itemOwnerKey, PersistentDataType.STRING, owner.toString());
         item.setItemMeta(meta);
      }
   }

   private void cleanupKitRuntime(Player player) {
      UUID uuid = player.getUniqueId();
      BukkitTask reload = this.revolverReloadTasks.remove(uuid);
      if (reload != null) {
         reload.cancel();
      }

      BukkitTask sniperReload = this.sniperReloadTasks.remove(uuid);
      if (sniperReload != null) {
         sniperReload.cancel();
      }

      BukkitTask windRefill = this.windChargeRefillTasks.remove(uuid);
      if (windRefill != null) {
         windRefill.cancel();
      }

      Map<String, BukkitTask> summonRefills = this.summonEggRefillTasks.remove(uuid);
      if (summonRefills != null) {
         summonRefills.values().forEach(BukkitTask::cancel);
      }

      this.revolverAmmo.remove(uuid);
      this.sniperAmmo.remove(uuid);
      this.sniperShotCooldownUntil.remove(uuid);
      this.gamblerSelfDamage.remove(uuid);
      this.vampireDamage.remove(uuid);
      this.wizardPotionCooldownUntil.remove(uuid);
      this.wizardPotionOwners.entrySet().removeIf(entry -> uuid.equals(entry.getValue()));
      this.removeTrackedTridents(uuid);
      this.removeSummons(uuid);
      this.removeBugMobs(uuid);
      this.restoreTraps(uuid);
      player.removePotionEffect(PotionEffectType.SLOWNESS);
      player.removePotionEffect(PotionEffectType.SPEED);
      player.removePotionEffect(PotionEffectType.JUMP_BOOST);
      player.removePotionEffect(PotionEffectType.WEAKNESS);
      player.removePotionEffect(PotionEffectType.INFESTED);
      player.setCooldown(Material.SPLASH_POTION, 0);
      AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
      if (armor != null) {
         armor.removeModifier(this.armorBonusKey);
      }

      this.clearCrossbowShotTick(uuid);
   }

   private void applyArmorBonus(Player player) {
      boolean wearsArmor = Arrays.stream(player.getInventory().getArmorContents()).anyMatch(item -> item != null && item.getType() != Material.AIR);
      AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
      if (wearsArmor && armor != null) {
         armor.removeModifier(this.armorBonusKey);
         double bonus = Math.max(0.0, this.plugin.getConfig().getDouble("ffa.kits.armor-points-bonus", 3.0));
         if (bonus > 0.0) {
            armor.addTransientModifier(new AttributeModifier(this.armorBonusKey, bonus, Operation.ADD_NUMBER));
         }
      }
   }

   private void applyKitEffects(Player player, FfaKit kit) {
      if (kit == FfaKit.AXE) {
         int amplifier = Math.max(0, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.AXE, "slowness-amplifier"), 0));
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, amplifier, false, false, true));
      }

      if (kit == FfaKit.SPEAR) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
      }

      if (kit == FfaKit.VAMPIRE) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
      }

      if (kit == FfaKit.GRAPPLER) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
         player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, false, true));
         player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 0, false, false, true));
      }

      if (kit == FfaKit.BUG_MANIA) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 100, 0, false, false, true));
      }

      if (kit == FfaKit.ASSASSIN) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
      }
   }

   private void startKitEffectTask() {
      if (this.kitEffectTask != null) {
         this.kitEffectTask.cancel();
      }

      this.kitEffectTask = this.plugin
         .getServer()
         .getScheduler()
         .runTaskTimer(
            this.plugin,
            () -> {
               for (UUID uuid : new ArrayList<>(this.sessions.keySet())) {
                  Player player = this.plugin.getServer().getPlayer(uuid);
                  FfaManager.FfaSession session = this.sessions.get(uuid);
                  if (player != null && player.isOnline() && session != null) {
                     if (session.kit == FfaKit.AXE
                        || session.kit == FfaKit.SPEAR
                        || session.kit == FfaKit.GRAPPLER
                        || session.kit == FfaKit.VAMPIRE
                        || session.kit == FfaKit.BUG_MANIA
                        || session.kit == FfaKit.ASSASSIN) {
                        this.applyKitEffects(player, session.kit);
                     }

                     if (session.kit == FfaKit.VAMPIRE) {
                        this.applyVampireSunDamage(player);
                     }

                     if (session.kit == FfaKit.TRIDENT) {
                        this.updateSeaWarriorTrident(player);
                     }

                     this.pruneTrackedTridents(uuid);
                  }
               }

               this.tickSummons();
               this.tickTraps();
            },
            40L,
            40L
         );
   }

   private void applyVampireSunDamage(Player player) {
      if (this.isInDirectSunlight(player)) {
         double damage = Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "sun-damage"), 1.0));
         if (damage > 0.0) {
            player.damage(damage);
         }
      }
   }

   private boolean isInDirectSunlight(Player player) {
      World world = player.getWorld();
      long time = world.getTime();
      if (time < 12300L && !world.hasStorm() && !world.isThundering()) {
         Location location = player.getLocation();
         return location.getBlock().getLightFromSky() >= 15 && location.getBlockY() >= world.getHighestBlockYAt(location);
      } else {
         return false;
      }
   }

   private void startCrossbowReload(Player player, FfaKit kit, Map<UUID, Integer> ammoMap, Map<UUID, BukkitTask> reloadTasks, int capacity, String label) {
      UUID uuid = player.getUniqueId();
      if (!reloadTasks.containsKey(uuid)) {
         this.updateAmmoCrossbowItem(player, kit, label, 0, capacity);
         player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, kit == FfaKit.SNIPER ? 0.45F : 0.7F, kit == FfaKit.SNIPER ? 0.55F : 0.65F);
         long reloadTicks = Math.max(1L, this.plugin.getConfig().getLong(this.config.kitPath(kit, "reload-ticks"), kit == FfaKit.SNIPER ? 60L : 75L));
         BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            reloadTasks.remove(uuid);
            if (player.isOnline() && this.isPlaying(player)) {
               FfaManager.FfaSession session = this.sessions.get(uuid);
               if (session != null && session.kit == kit) {
                  ammoMap.put(uuid, capacity);
                  this.rechargeAmmoCrossbow(player, kit);
                  this.updateAmmoCrossbowItem(player, kit, label, capacity, capacity);
                  if (kit == FfaKit.SNIPER) {
                     player.removePotionEffect(PotionEffectType.SLOWNESS);
                  }

                  player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 1.2F);
                  player.sendActionBar(Component.text(label + " リロード完了 " + capacity + "/" + capacity, NamedTextColor.GREEN));
               }
            }
         }, reloadTicks);
         reloadTasks.put(uuid, task);
         this.playReloadProgressSound(player, kit, reloadTasks, reloadTicks, 10L);
         if (kit == FfaKit.SNIPER) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)Math.min(2147483647L, reloadTicks + 5L), 3, false, false, true));
         }

         player.sendActionBar(Component.text(label + " リロード中...", NamedTextColor.RED));
      }
   }

   private void playReloadProgressSound(Player player, FfaKit kit, Map<UUID, BukkitTask> reloadTasks, long totalTicks, long elapsedTicks) {
      UUID uuid = player.getUniqueId();
      if (elapsedTicks >= totalTicks) {
         return;
      }

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         FfaManager.FfaSession session = this.sessions.get(uuid);
         if (player.isOnline() && session != null && session.kit == kit && reloadTasks.containsKey(uuid)) {
            float progress = Math.min(1.0F, elapsedTicks / (float)Math.max(1L, totalTicks));
            float pitch = (kit == FfaKit.SNIPER ? 0.5F : 0.62F) + progress * 0.28F;
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, kit == FfaKit.SNIPER ? 0.35F : 0.55F, pitch);
            this.playReloadProgressSound(player, kit, reloadTasks, totalTicks, elapsedTicks + 10L);
         }
      }, 10L);
   }

   private void rechargeAmmoCrossbow(Player player, FfaKit kit) {
      ItemStack crossbow = this.findKitItem(player, kit == FfaKit.SNIPER ? "sniper" : "revolver");
      if (crossbow != null && crossbow.getItemMeta() instanceof CrossbowMeta meta) {
         meta.removeEnchant(Enchantment.MULTISHOT);
         meta.setChargedProjectiles(List.of(new ItemStack(Material.ARROW)));
         crossbow.setItemMeta(meta);
         player.updateInventory();
      }
   }

   private void updateAmmoCrossbowItem(Player player, FfaKit kit, String label, int ammo, int capacity) {
      ItemStack crossbow = this.findKitItem(player, kit == FfaKit.SNIPER ? "sniper" : "revolver");
      if (crossbow != null) {
         ItemMeta meta = crossbow.getItemMeta();
         meta.displayName(Component.text((kit == FfaKit.SNIPER ? "§8" : "§d") + label + " §7[" + ammo + "/" + capacity + "]"));
         if (meta instanceof CrossbowMeta crossbowMeta) {
            if (kit == FfaKit.SNIPER) {
               crossbowMeta.removeEnchant(Enchantment.MULTISHOT);
            }
            if (ammo <= 0) {
               crossbowMeta.setChargedProjectiles(List.of());
            }
            crossbow.setItemMeta(crossbowMeta);
         } else {
            crossbow.setItemMeta(meta);
         }

         player.updateInventory();
      }
   }

   private void updateSniperItem(Player player) {
      int capacity = FfaKit.SNIPER.sniperCapacity(this.config);
      int ammo = Math.max(0, this.sniperAmmo.getOrDefault(player.getUniqueId(), capacity));
      this.updateAmmoCrossbowItem(player, FfaKit.SNIPER, "スナイパー", ammo, capacity);
   }

   private void startRevolverReload(Player player, int capacity) {
      UUID uuid = player.getUniqueId();
      if (!this.revolverReloadTasks.containsKey(uuid)) {
         this.updateRevolverItem(player);
         player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.8F, 0.8F);
         long reloadTicks = Math.max(1L, this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.CROSSBOW, "reload-ticks"), 75L));
         BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            this.revolverReloadTasks.remove(uuid);
            if (player.isOnline() && this.isPlaying(player)) {
               FfaManager.FfaSession session = this.sessions.get(uuid);
               if (session != null && session.kit == FfaKit.CROSSBOW) {
                  this.revolverAmmo.put(uuid, capacity);
                  this.rechargeRevolver(player);
                  this.updateRevolverItem(player);
                  player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 1.2F);
                  player.sendActionBar(Component.text("リロード完了 " + capacity + "/" + capacity, NamedTextColor.GREEN));
               }
            }
         }, reloadTicks);
         this.revolverReloadTasks.put(uuid, task);
         player.sendActionBar(Component.text("リロード中...", NamedTextColor.RED));
      }
   }

   private void rechargeRevolver(Player player) {
      ItemStack revolver = this.findRevolver(player);
      if (revolver != null && revolver.getItemMeta() instanceof CrossbowMeta meta) {
         meta.setChargedProjectiles(List.of(new ItemStack(Material.ARROW)));
         revolver.setItemMeta(meta);
         player.updateInventory();
      }
   }

   private void updateRevolverItem(Player player) {
      ItemStack revolver = this.findRevolver(player);
      if (revolver != null) {
         int capacity = FfaKit.CROSSBOW.revolverCapacity(this.config);
         int ammo = Math.max(0, this.revolverAmmo.getOrDefault(player.getUniqueId(), capacity));
         ItemMeta meta = revolver.getItemMeta();
         meta.displayName(Component.text("§dリボルバー §7[" + ammo + "/" + capacity + "]"));
         if (meta instanceof CrossbowMeta crossbowMeta && ammo <= 0) {
            crossbowMeta.setChargedProjectiles(List.of());
            revolver.setItemMeta(crossbowMeta);
         } else {
            revolver.setItemMeta(meta);
         }

         player.updateInventory();
      }
   }

   private ItemStack findRevolver(Player player) {
      return this.findKitItem(player, "revolver");
   }

   private ItemStack findKitItem(Player player, String kind) {
      PlayerInventory inventory = player.getInventory();

      for (ItemStack item : inventory.getContents()) {
         if (this.isFfaItem(item) && kind.equals(this.itemKind(item))) {
            return item;
         }
      }

      return null;
   }

   private void removeTrackedTridents(UUID owner) {
      Set<UUID> tridents = this.trackedTridents.remove(owner);
      if (tridents != null && !tridents.isEmpty()) {
         for (World world : this.plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
               if (tridents.contains(entity.getUniqueId())) {
                  entity.remove();
               }
            }
         }
      }
   }

   private void untrackTrident(UUID owner, UUID trident) {
      Set<UUID> tridents = this.trackedTridents.get(owner);
      if (tridents != null) {
         tridents.remove(trident);
         if (tridents.isEmpty()) {
            this.trackedTridents.remove(owner);
         }
      }
   }

   private UUID parseUuid(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return UUID.fromString(raw);
         } catch (IllegalArgumentException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void pruneTrackedTridents(UUID owner) {
      Set<UUID> tridents = this.trackedTridents.get(owner);
      if (tridents != null && !tridents.isEmpty()) {
         Set<UUID> live = new HashSet<>();

         for (World world : this.plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
               if (tridents.contains(entity.getUniqueId()) && entity.isValid()) {
                  live.add(entity.getUniqueId());
               }
            }
         }

         if (live.isEmpty()) {
            this.trackedTridents.remove(owner);
         } else {
            this.trackedTridents.put(owner, live);
         }
      }
   }

   private ItemStack selectorItem(FfaKit kit, boolean selected) {
      Material icon = kit.icon(this.config);
      ItemStack item = new ItemStack(icon == null ? Material.BARRIER : icon);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text((selected ? ChatColor.GREEN + "選択中: " : "") + kit.displayName(this.config)));
      List<Component> lore = new ArrayList<>(kit.details(this.config, this.plugin));
      meta.lore(lore);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
      meta.getPersistentDataContainer().set(this.selectorKitKey, PersistentDataType.STRING, kit.key());
      item.setItemMeta(meta);
      return item;
   }

   private FfaKit kitFromSelectorItem(ItemStack item) {
      if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
         String key = (String)item.getItemMeta().getPersistentDataContainer().get(this.selectorKitKey, PersistentDataType.STRING);
         return FfaKit.fromKey(key);
      } else {
         return null;
      }
   }

   private void updateScoreboard(Player player) {
      Scoreboard scoreboard = this.plugin.getServer().getScoreboardManager().getNewScoreboard();
      Objective objective = scoreboard.registerNewObjective("minerva_ffa", "dummy", "§aFFA戦績");
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      UUID uuid = player.getUniqueId();
      int kills = this.stats.kills(uuid);
      int deaths = this.stats.deaths(uuid);
      double kd = deaths == 0 ? kills : (double)kills / deaths;
      objective.getScore("§fキット: §e" + this.stripColor(this.sessions.get(uuid).kit.displayName(this.config))).setScore(6);
      objective.getScore("§aキル: §f" + kills).setScore(5);
      objective.getScore("§cデス: §f" + deaths).setScore(4);
      objective.getScore("§eK/D: §f" + String.format(Locale.ROOT, "%.2f", kd)).setScore(3);
      objective.getScore("§b連続キル: §f" + this.stats.currentStreak(uuid)).setScore(2);
      objective.getScore("§d最高連続: §f" + this.stats.maxStreak(uuid)).setScore(1);
      player.setScoreboard(scoreboard);
   }

   private void awardKillEmeralds(Player killer, Player victim) {
      long now = System.currentTimeMillis();
      long resetMillis = Math.max(1L, this.plugin.getConfig().getLong("ffa.rewards.same-target-reset-seconds", 600L)) * 1000L;
      FfaManager.KillRewardState state = this.killRewardStates.get(killer.getUniqueId());
      int sameTargetRepeats = 0;
      if (state != null && victim.getUniqueId().equals(state.target()) && now - state.lastKillAt() <= resetMillis) {
         sameTargetRepeats = state.repeats() + 1;
      }

      this.killRewardStates.put(killer.getUniqueId(), new FfaManager.KillRewardState(victim.getUniqueId(), sameTargetRepeats, now));
      int base = Math.max(0, this.configInt("ffa.rewards.kill-mp", "ffa.rewards.kill-em", 50));
      int reward = sameTargetRepeats >= 7 ? 0 : (int)Math.floor(base * Math.pow(0.5, sameTargetRepeats));
      FfaManager.FfaSession session = this.sessions.get(killer.getUniqueId());
      int gamblerDelta = 0;
      if (session != null && session.kit == FfaKit.GAMBLER) {
         int min = this.configInt(this.config.kitPath(FfaKit.GAMBLER, "mp-min"), this.config.kitPath(FfaKit.GAMBLER, "em-min"), -10);
         int max = this.configInt(this.config.kitPath(FfaKit.GAMBLER, "mp-max"), this.config.kitPath(FfaKit.GAMBLER, "em-max"), 10);
         gamblerDelta = ThreadLocalRandom.current().nextInt(Math.min(min, max), Math.max(min, max) + 1);
         reward += gamblerDelta;
      }

      boolean fever = this.plugin.data().getLong("ffa.events.mp-fever-until", this.plugin.data().getLong("ffa.events.em-fever-until", 0L)) > now;
      if (fever && reward > 0) {
         reward *= 2;
      }

      if (reward > 0) {
         this.plugin.depositEmeralds(killer.getUniqueId(), reward);
      } else if (reward < 0) {
         this.plugin.withdrawEmeralds(killer.getUniqueId(), Math.min(this.plugin.getEmeralds(killer.getUniqueId()), Math.abs(reward)));
      }

      killer.sendActionBar(
         Component.text(
            "FFA報酬 "
               + (reward >= 0 ? "+" : "")
               + reward
               + "MP / 同一減衰 "
               + sameTargetRepeats
               + (gamblerDelta == 0 ? "" : " / 運 " + gamblerDelta)
               + (fever && reward > 0 ? " / フィーバー" : ""),
            reward >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED
         )
      );
   }

   private int configInt(String path, String legacyPath, int fallback) {
      return this.plugin.getConfig().contains(path) ? this.plugin.getConfig().getInt(path, fallback) : this.plugin.getConfig().getInt(legacyPath, fallback);
   }

   private void clearTemporaryState(Player player) {
      for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
         player.removePotionEffect(effect.getType());
      }

      player.setFireTicks(0);
      player.setFoodLevel(20);
      player.setSaturation(5.0F);
      AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
      player.setHealth(maxHealth == null ? 20.0 : Math.max(1.0, maxHealth.getValue()));
      player.setFallDistance(0.0F);
   }

   private boolean hasAdmin(CommandSender sender) {
      return sender.hasPermission("minerva.admin") || sender.hasPermission("minerva.ffa.admin");
   }

   private void addWorldName(Set<String> names, String worldName) {
      if (worldName != null && !worldName.isBlank()) {
         names.add(worldName.toLowerCase(Locale.ROOT));
      }
   }

   private String stripColor(String value) {
      return value.replaceAll("§.", "");
   }

   private static ItemStack[] cloneItems(ItemStack[] items) {
      ItemStack[] copy = new ItemStack[items.length];

      for (int i = 0; i < items.length; i++) {
         copy[i] = items[i] == null ? null : items[i].clone();
      }

      return copy;
   }

   private boolean isDuplicateCrossbowShot(Player var1) {
      if (this.lastCrossbowShotTick == null) {
         this.lastCrossbowShotTick = new HashMap<>();
      }

      UUID var2 = var1.getUniqueId();
      long var3 = this.plugin.getServer().getCurrentTick();
      Long var5 = this.lastCrossbowShotTick.put(var2, var3);
      return var5 != null && var5 == var3;
   }

   private void clearCrossbowShotTick(UUID var1) {
      if (this.lastCrossbowShotTick != null) {
         this.lastCrossbowShotTick.remove(var1);
      }
   }

   private void applyGamblerIncoming(EntityDamageByEntityEvent var1, Player var2) {
      FfaManager.FfaSession var3 = this.sessions.get(var2.getUniqueId());
      if (var3 != null && var3.kit == FfaKit.GAMBLER && !(var1.getFinalDamage() <= 0.0)) {
         int var4 = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "min-incoming-reduction"), -5);
         int var5 = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "max-incoming-reduction"), 5);
         int var6 = Math.min(var4, var5);
         int var7 = Math.max(var4, var5);
         int var8 = ThreadLocalRandom.current().nextInt(var6, var7 + 1);
         var1.setDamage(Math.max(0.0, var1.getDamage() - var8));
         var2.sendMessage("§6ギャンブラー防御抽選: §e" + var8 + " ダメージ補正");
      }
   }

   private boolean applyGamblerOutgoing(EntityDamageByEntityEvent var1, Player var2, Player var3, FfaManager.FfaSession var4, ItemStack var5) {
      if (var4 != null && var4.kit == FfaKit.GAMBLER && this.isFfaItem(var5) && "weapon".equals(this.itemKind(var5)) && !(var1.getFinalDamage() <= 0.0)) {
         int var6 = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "min-random-damage"), -10);
         int var7 = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "max-random-damage"), 20);
         int var8 = Math.min(var6, var7);
         int var9 = Math.max(var6, var7);
         int var10 = ThreadLocalRandom.current().nextInt(var8, var9 + 1);
         var2.sendMessage("§6ギャンブラー攻撃抽選: §e" + var10 + " ダメージ");
         if (var10 < 0) {
            var1.setCancelled(true);
            double var11 = Math.min(Math.abs(var10), var3.getMaxHealth() - var3.getHealth());
            if (var11 > 0.0) {
               var3.setHealth(var3.getHealth() + var11);
            }

            var3.sendMessage("§aギャンブラーの攻撃で " + String.format(Locale.ROOT, "%.1f", var11) + " 回復しました。");
            return true;
         } else {
            if (var10 == 0) {
               var1.setCancelled(true);
               return true;
            }

            var1.setDamage(var10);
            if (var10 == 20) {
               var2.sendMessage("§6§l✦✦✦ JACKPOT DAMAGE! 20ダメージ! ✦✦✦");
               var2.playSound(var2.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
               this.plugin.unlockTitle(var2, "ラッキーパンチ");
            }

            return true;
         }
      } else {
         return false;
      }
   }

   boolean handleEmptyCrossbowInteract(PlayerInteractEvent var1) {
      if (!var1.getAction().isRightClick()) {
         return false;
      }

      Player var2 = var1.getPlayer();
      FfaManager.FfaSession var3 = this.sessions.get(var2.getUniqueId());
      if (var3 != null && (var3.kit == FfaKit.CROSSBOW || var3.kit == FfaKit.SNIPER)) {
         ItemStack var4 = var1.getItem();
         if (!this.isFfaItem(var4)) {
            return false;
         }

         String var5 = this.itemKind(var4);
         if (var3.kit == FfaKit.CROSSBOW && "revolver".equals(var5)) {
            int var6 = var3.kit.revolverCapacity(this.config);
            if (this.revolverAmmo.getOrDefault(var2.getUniqueId(), var6) <= 0) {
               var1.setCancelled(true);
               this.startCrossbowReload(var2, FfaKit.CROSSBOW, this.revolverAmmo, this.revolverReloadTasks, var6, "リボルバー");
               return true;
            }
         }

         if (var3.kit == FfaKit.SNIPER && "sniper".equals(var5)) {
            int var7 = var3.kit.sniperCapacity(this.config);
            if (this.sniperAmmo.getOrDefault(var2.getUniqueId(), var7) <= 0) {
               var1.setCancelled(true);
               this.startCrossbowReload(var2, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, var7, "スナイパー");
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   void handleTrapStep(Player var1) {
      if (var1 != null && this.isPlaying(var1)) {
         for (UUID var3 : new ArrayList<>(this.traps.keySet())) {
            Map<String, FfaManager.TrapState> var4 = this.traps.get(var3);
            if (var4 != null) {
               for (FfaManager.TrapState var6 : new ArrayList<FfaManager.TrapState>(var4.values())) {
                  if (var1.getWorld().equals(var6.location().getWorld())
                     && var1.getLocation().distanceSquared(var6.location().clone().add(0.5, 0.0, 0.5)) <= 1.2) {
                     var4.remove(var6.type());
                     this.triggerTrap(var6, var1);
                     if (var4.isEmpty()) {
                        this.traps.remove(var3);
                     }

                     return;
                  }
               }
            }
         }
      }
   }

   private record DamageCredit(UUID attacker, long at) {
   }

   private record DeathLeaveRestore(FfaManager.PlayerState state, Location location) {
   }

   private static final class FfaSession {
      private FfaKit kit;
      private final FfaManager.PlayerState state;

      private FfaSession(FfaKit kit, FfaManager.PlayerState state) {
         this.kit = kit;
         this.state = state;
      }
   }

   private record KillRewardState(UUID target, int repeats, long lastKillAt) {
   }

   private static final class PlayerState {
      private final ItemStack[] storage;
      private final ItemStack[] armor;
      private final ItemStack offhand;
      private final float exp;
      private final int level;
      private final int totalExperience;
      private final double maxHealthBase;
      private final double health;
      private final int food;
      private final float saturation;
      private final GameMode gameMode;
      private final Location location;
      private final Collection<PotionEffect> effects;
      private final int fireTicks;
      private final Scoreboard scoreboard;

      private PlayerState(
         ItemStack[] storage,
         ItemStack[] armor,
         ItemStack offhand,
         float exp,
         int level,
         int totalExperience,
         double maxHealthBase,
         double health,
         int food,
         float saturation,
         GameMode gameMode,
         Location location,
         Collection<PotionEffect> effects,
         int fireTicks,
         Scoreboard scoreboard
      ) {
         this.storage = storage;
         this.armor = armor;
         this.offhand = offhand;
         this.exp = exp;
         this.level = level;
         this.totalExperience = totalExperience;
         this.maxHealthBase = maxHealthBase;
         this.health = health;
         this.food = food;
         this.saturation = saturation;
         this.gameMode = gameMode;
         this.location = location;
         this.effects = effects;
         this.fireTicks = fireTicks;
         this.scoreboard = scoreboard;
      }

      private static FfaManager.PlayerState capture(Player player) {
         PlayerInventory inventory = player.getInventory();
         AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
         return new FfaManager.PlayerState(
            FfaManager.cloneItems(inventory.getStorageContents()),
            FfaManager.cloneItems(inventory.getArmorContents()),
            inventory.getItemInOffHand().clone(),
            player.getExp(),
            player.getLevel(),
            player.getTotalExperience(),
            maxHealth == null ? 20.0 : maxHealth.getBaseValue(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getGameMode(),
            player.getLocation().clone(),
            new ArrayList<>(player.getActivePotionEffects()),
            player.getFireTicks(),
            player.getScoreboard()
         );
      }

      private void restore(Player player) {
         this.restore(player, this.location);
      }

      private void restore(Player player, Location restoreLocation) {
         PlayerInventory inventory = player.getInventory();
         inventory.clear();
         inventory.setStorageContents(FfaManager.cloneItems(this.storage));
         inventory.setArmorContents(FfaManager.cloneItems(this.armor));
         inventory.setItemInOffHand(this.offhand == null ? null : this.offhand.clone());
         player.setGameMode(this.gameMode);
         player.setExp(this.exp);
         player.setLevel(this.level);
         player.setTotalExperience(this.totalExperience);
         AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
         if (maxHealth != null) {
            maxHealth.setBaseValue(this.maxHealthBase);
         }

         double max = maxHealth == null ? 20.0 : maxHealth.getValue();
         player.setHealth(Math.max(1.0, Math.min(max, this.health)));
         player.setFoodLevel(this.food);
         player.setSaturation(this.saturation);

         for (PotionEffect effect : this.effects) {
            player.addPotionEffect(effect);
         }

         player.setFireTicks(this.fireTicks);
         player.setScoreboard(this.scoreboard);
         player.teleport(restoreLocation);
         player.updateInventory();
      }
   }

   private record TrapState(String type, UUID owner, Location location, BlockState original, long expiresAt) {
   }
}
