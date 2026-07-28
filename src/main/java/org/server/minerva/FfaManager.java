package org.server.minerva;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class FfaManager {
    private static final String CENTER_MISSING = "§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。";
    private static final String KIT_SELECTOR_TITLE = "FFAキット選択";

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
    private final Map<UUID, FfaSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> revolverAmmo = new HashMap<>();
    private final Map<UUID, Integer> sniperAmmo = new HashMap<>();
    private final Map<UUID, BukkitTask> revolverReloadTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> sniperReloadTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> windChargeRefillTasks = new HashMap<>();
    private final Map<UUID, Long> wizardPotionCooldownUntil = new HashMap<>();
    private final Map<UUID, UUID> wizardPotionOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trackedTridents = new HashMap<>();
    private final Set<UUID> gamblerSelfDamage = new HashSet<>();
    private final Map<UUID, Double> vampireDamage = new HashMap<>();
    private final Map<UUID, KillRewardState> killRewardStates = new HashMap<>();
    private final Map<UUID, List<UUID>> summonedMobs = new HashMap<>();
    private final Map<UUID, UUID> summonOwners = new HashMap<>();
    private final Map<UUID, BukkitTask> summonExpiryTasks = new HashMap<>();
    private final Map<UUID, List<UUID>> bugMobs = new HashMap<>();
    private final Map<UUID, UUID> bugOwners = new HashMap<>();
    private final Map<UUID, BukkitTask> bugExpiryTasks = new HashMap<>();
    private final Map<UUID, Map<String, TrapState>> traps = new HashMap<>();
    private final Map<UUID, DamageCredit> damageCredits = new HashMap<>();
    private final Map<UUID, DeathLeaveRestore> deathLeaveRestores = new HashMap<>();
    private BukkitTask kitEffectTask;

    FfaManager(Minerva plugin) {
        this.plugin = plugin;
        this.config = new FfaConfig(plugin);
        this.stats = new FfaStatsManager(plugin);
        this.stands = new FfaKitStandManager(plugin, config);
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
    }

    void load() {
        config.ensureDefaults();
        stats.load();
        fieldItems.load();
        startKitEffectTask();
    }

    void shutdown() {
        fieldItems.shutdown();
        for (Player player : new ArrayList<>(plugin.getServer().getOnlinePlayers())) {
            if (isPlaying(player)) {
                leave(player, false);
            }
        }
        if (kitEffectTask != null) {
            kitEffectTask.cancel();
            kitEffectTask = null;
        }
        sessions.clear();
        revolverAmmo.clear();
        sniperAmmo.clear();
        cancelTasks(sniperReloadTasks);
        cancelTasks(windChargeRefillTasks);
        wizardPotionCooldownUntil.clear();
        wizardPotionOwners.clear();
        trackedTridents.clear();
        removeAllSummons();
        removeAllBugMobs();
        restoreAllTraps();
        damageCredits.clear();
        deathLeaveRestores.clear();
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
        return stands;
    }

    boolean isPlaying(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    boolean isPlaying(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    FfaKit currentKit(Player player) {
        FfaSession session = player == null ? null : sessions.get(player.getUniqueId());
        return session == null ? null : session.kit;
    }

    boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("fielditem".equals(action)) {
            return fieldItems.handleCommand(sender, args);
        }
        switch (action) {
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                leave(player, true);
            }
            case "stats" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                player.sendMessage(stats.summary(player));
            }
            case "setcenter" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                config.setCenter(player.getLocation());
                sender.sendMessage("§aFFA中央地点を設定しました。");
            }
            case "setkits" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                config.setKitSelection(player.getLocation());
                sender.sendMessage("§aFFAキット選択地点を設定しました。");
            }
            case "createkits" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                int count = stands.createKitStands();
                if (count < 0) {
                    sender.sendMessage(CENTER_MISSING);
                    return true;
                }
                sender.sendMessage("§aFFAキット選択用の防具立てを生成しました: " + count + "体");
            }
            case "removekits" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                int count = stands.removeKitStands();
                sender.sendMessage("§eFFAキット防具立てを削除しました: " + count + "体");
            }
            case "reload" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                config.reload();
                stats.load();
                fieldItems.shutdown();
                fieldItems.load();
                sender.sendMessage("§aFFA設定を再読み込みしました。");
            }
            default -> sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload|fielditem");
        }
        return true;
    }

    List<String> tabComplete(String[] args, CommandSender sender) {
        if (args.length == 2) {
            if (hasAdmin(sender)) {
                return List.of("leave", "stats", "setcenter", "setkits", "createkits", "removekits", "reload", "fielditem");
            }
            return List.of("leave", "stats");
        }
        if (args.length >= 2 && "fielditem".equalsIgnoreCase(args[1])) {
            return fieldItems.tabComplete(args);
        }
        return List.of();
    }

    void openKitSelector(Player player) {
        List<FfaKit> kits = FfaKit.activeKits(config);
        int size = Math.max(9, Math.min(54, ((kits.size() + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(player, size, Component.text(KIT_SELECTOR_TITLE));
        FfaKit selected = stands.selectedKit();
        int slot = 0;
        for (FfaKit kit : kits) {
            if (slot >= size) {
                break;
            }
            inventory.setItem(slot++, selectorItem(kit, kit == selected));
        }
        player.openInventory(inventory);
    }

    boolean handleKitSelectorClick(Player player, InventoryClickEvent event) {
        if (!KIT_SELECTOR_TITLE.equals(PlainTextComponentSerializer.plainText().serialize(event.getView().title()))) {
            return false;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }
        FfaKit kit = kitFromSelectorItem(event.getCurrentItem());
        if (kit == null) {
            return true;
        }
        kit = sanitizeKit(kit);
        stands.applySelectedKit(kit);
        player.closeInventory();
        join(player, kit);
        return true;
    }

    void join(Player player, FfaKit kit) {
        kit = sanitizeKit(kit);
        FfaKit selectedKit = kit;
        if (!config.enabled()) {
            player.sendMessage("§cFFAは現在無効です。");
            return;
        }
        Location arena = config.center();
        if (arena == null) {
            player.sendMessage(CENTER_MISSING);
            return;
        }
        if (selectedKit == FfaKit.SPEAR && selectedKit.spearMaterial(config, plugin, true) == null) {
            player.sendMessage("§c槍アイテムが現在の Paper API で見つかりません。Paper API / Minecraft バージョンを確認してください。");
            return;
        }
        sessions.computeIfAbsent(player.getUniqueId(), ignored -> new FfaSession(selectedKit, PlayerState.capture(player)));
        FfaSession session = sessions.get(player.getUniqueId());
        cleanupKitRuntime(player);
        session.kit = selectedKit;
        prepareForFight(player, selectedKit);
        updateScoreboard(player);
        player.teleport(arena);
        player.sendMessage("§aFFAに参加しました。キット: §f" + stripColor(selectedKit.displayName(config)));
    }

    void leave(Player player, boolean notify) {
        FfaSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (notify) {
                player.sendMessage("§eFFAには参加していません。");
            }
            return;
        }
        cleanupKitRuntime(player);
        clearTemporaryState(player);
        session.state.restore(player, leaveLocation(player.getWorld()));
        if (notify) {
            player.sendMessage("§eFFAから退出しました。");
        }
    }

    private Location leaveLocation(World world) {
        int y = world.getHighestBlockYAt(0, 0) + 1;
        return new Location(world, 0.5D, y, 0.5D);
    }

    void handleDeath(Player victim, Player killer) {
        FfaSession victimSession = sessions.remove(victim.getUniqueId());
        if (victimSession == null) {
            return;
        }
        if (killer == null) {
            killer = recentAttacker(victim);
        }
        damageCredits.remove(victim.getUniqueId());
        stats.recordDeath(victim);
        if (killer != null && isPlaying(killer) && !killer.getUniqueId().equals(victim.getUniqueId())) {
            stats.recordKill(killer);
            awardKillEmeralds(killer, victim);
            updateScoreboard(killer);
            killer.sendMessage("§a" + victim.getName() + " を倒しました！ 現在の連続キル: " + stats.currentStreak(killer.getUniqueId()));
        }
        cleanupKitRuntime(victim);
        clearTemporaryState(victim);
        Location leaveLocation = leaveLocation(victim.getWorld());
        deathLeaveRestores.put(victim.getUniqueId(), new DeathLeaveRestore(victimSession.state, leaveLocation));
        victim.sendMessage("§cあなたは倒されました。FFAから退出しました。");
    }

    Location handleDeathLeaveRespawn(Player player) {
        DeathLeaveRestore restore = deathLeaveRestores.remove(player.getUniqueId());
        if (restore == null) {
            return null;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                restore.state().restore(player, restore.location());
            }
        });
        return restore.location();
    }

    void respawn(Player player) {
        FfaSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location respawn = respawnLocation();
        if (respawn == null) {
            player.sendMessage(CENTER_MISSING);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            cleanupKitRuntime(player);
            prepareForFight(player, sanitizeKit(session.kit));
            updateScoreboard(player);
            player.teleport(respawn);
            player.sendMessage("§a再出撃しました。");
        }, config.respawnDelayTicks());
    }

    boolean commandAllowed(String commandLine) {
        if (!config.restrictCommands()) {
            return true;
        }
        String normalized = commandLine.toLowerCase(Locale.ROOT).trim();
        for (String allowed : config.allowedCommands()) {
            if (normalized.equals(allowed) || normalized.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    boolean restrictionAllows(String key) {
        return config.restriction(key);
    }

    boolean isFfaItem(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BOOLEAN));
    }

    boolean handleFieldItemPickup(EntityPickupItemEvent event) {
        return fieldItems.handlePickup(event);
    }

    boolean handleFieldItemDamage(EntityDamageEvent event) {
        return fieldItems.handleEntityDamage(event);
    }

    boolean handleFieldItemInventoryPickup(InventoryPickupItemEvent event) {
        return fieldItems.handleInventoryPickup(event);
    }

    boolean handlePotionUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().isRightClick()) {
            return false;
        }
        if (!isPlaying(player)) {
            return false;
        }
        ItemStack item = event.getItem();
        if (!isFfaItem(item)) {
            return false;
        }
        String kind = itemKind(item);
        FfaSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if ("food".equals(kind)) {
            handleReusableFood(player, item);
            return true;
        }
        if ("golden_apple".equals(kind) && session.kit == FfaKit.SWORD) {
            handleReusableGoldenApple(player, item);
            return true;
        }
        if (kind.startsWith("summon_") && session.kit == FfaKit.NECROMANCER) {
            String mob = kind.substring("summon_".length());
            long seconds = summonCooldownSeconds(mob);
            if (beginCooldown(player, "summon_" + mob, seconds, "召喚")) {
                summonNecromancerMob(player, mob);
            }
            return true;
        }
        if (kind.startsWith("trap_") && session.kit == FfaKit.TRAPPER) {
            String trap = kind.substring("trap_".length());
            long seconds = plugin.getConfig().getLong(config.kitPath(FfaKit.TRAPPER, "trap-cooldown-seconds"), 20L);
            if (beginCooldown(player, "trap_" + trap, seconds, "罠")) {
                placeTrap(player, trap, event);
            }
            return true;
        }
        if (kind.startsWith("chemist_potion_") && session.kit == FfaKit.WIZARD) {
            String ability = itemAbility(item).replaceFirst("^chemist_potion_", "");
            long seconds = plugin.getConfig().getLong(config.kitPath(FfaKit.WIZARD, "potion-cooldowns." + ability), 15L);
            if (!beginCooldown(player, "chemist_" + ability, seconds, "ポーション")) {
                return true;
            }
            restoreReusableItem(player, event.getHand(), item.clone());
            return false;
        }
        if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            scheduleWindChargeRefillCheck(player);
        }
        return false;
    }

    private void handleReusableFood(Player player, ItemStack item) {
        long seconds = plugin.getConfig().getLong("ffa.kits.food-cooldown-seconds", 15L);
        if (!beginCooldown(player, "kit_food", seconds, "食料")) {
            return;
        }
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
        player.setSaturation(Math.min(20.0F, player.getSaturation() + 7.2F));
        ItemStack restored = item.clone();
        restored.setAmount(1);
        setHeldItem(player, restored);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8F, 1.1F);
    }

    private void handleReusableGoldenApple(Player player, ItemStack item) {
        long seconds = plugin.getConfig().getLong(config.kitPath(FfaKit.SWORD, "golden-apple-cooldown-seconds"), 100L);
        if (!beginCooldown(player, "sword_golden_apple", seconds, "金リンゴ")) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, false, false, true));
        ItemStack restored = item.clone();
        restored.setAmount(1);
        setHeldItem(player, restored);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8F, 0.9F);
    }

    private boolean beginCooldown(Player player, String ability, long seconds, String label) {
        long now = System.currentTimeMillis();
        long until = cooldownUntil(player.getUniqueId(), ability);
        if (until > now) {
            long remaining = Math.max(1L, (long) Math.ceil((until - now) / 1000.0D));
            player.sendActionBar(Component.text(label + "再使用まで " + remaining + "秒", NamedTextColor.RED));
            return false;
        }
        setCooldownUntil(player.getUniqueId(), ability, now + Math.max(1L, seconds) * 1000L);
        return true;
    }

    private long cooldownUntil(UUID uuid, String ability) {
        return plugin.data().getLong("players." + uuid + ".ffa-cooldowns." + ability, 0L);
    }

    private void setCooldownUntil(UUID uuid, String ability, long until) {
        plugin.data().set("players." + uuid + ".ffa-cooldowns." + ability, until);
        plugin.saveData();
    }

    private void restoreReusableItem(Player player, EquipmentSlot hand, ItemStack item) {
        item.setAmount(1);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(item);
            } else {
                player.getInventory().setItemInMainHand(item);
            }
            player.updateInventory();
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            FfaSession session = sessions.get(player.getUniqueId());
            if (session == null || session.kit != FfaKit.MACE || countFfaItem(player, "wind_charge") > 0) {
                return;
            }
            startWindChargeRefill(player);
        }, 2L);
    }

    private void startWindChargeRefill(Player player) {
        UUID uuid = player.getUniqueId();
        if (windChargeRefillTasks.containsKey(uuid)) {
            return;
        }
        long delay = Math.max(1L, plugin.getConfig().getLong(config.kitPath(FfaKit.MACE, "wind-charge-refill-seconds"), 10L)) * 20L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            windChargeRefillTasks.remove(uuid);
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            FfaSession session = sessions.get(uuid);
            if (session == null || session.kit != FfaKit.MACE || countFfaItem(player, "wind_charge") > 0) {
                return;
            }
            ItemStack item = FfaKit.kitItem(plugin, FfaKit.MACE, "wind_charge", Material.WIND_CHARGE, "§f重戦士のウィンドチャージ",
                    plugin.getConfig().getInt(config.kitPath(FfaKit.MACE, "wind-charge"), 10), Map.of());
            tagOwner(item, uuid);
            player.getInventory().addItem(item);
            player.sendActionBar(Component.text("ウィンドチャージ補充", NamedTextColor.GREEN));
        }, delay);
        windChargeRefillTasks.put(uuid, task);
    }

    private int countFfaItem(Player player, String kind) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFfaItem(item) && kind.equals(itemKind(item))) {
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

    private void summonNecromancerMob(Player owner, String mob) {
        EntityType type = summonType(mob);
        if (type == null) {
            owner.sendMessage("§cこの召喚Mobは現在のAPIで使用できません: " + mob);
            return;
        }
        Location spawn = owner.getLocation().clone().add(owner.getLocation().getDirection().setY(0).normalize().multiply(1.5D));
        Entity entity = owner.getWorld().spawnEntity(spawn, type);
        entity.getPersistentDataContainer().set(entityKindKey, PersistentDataType.STRING, "summon");
        entity.getPersistentDataContainer().set(entityOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
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
        List<UUID> owned = summonedMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
        int maxOwned = Math.max(1, plugin.getConfig().getInt(config.kitPath(FfaKit.NECROMANCER, "max-summons"), 5));
        while (owned.size() >= maxOwned) {
            removeSummonEntity(owned.remove(0));
        }
        owned.add(entity.getUniqueId());
        summonOwners.put(entity.getUniqueId(), ownerId);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeSummonEntity(entity.getUniqueId()), 20L * 30L);
        summonExpiryTasks.put(entity.getUniqueId(), task);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.8F, 0.8F);
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
        Block target = trapTargetBlock(owner, event);
        if (target == null || !isFfaWorld(target.getWorld())) {
            owner.sendMessage("§cFFA範囲内でのみ罠を設置できます。");
            return;
        }
        if (plugin.isStructureProtectedLocation(target.getLocation())) {
            owner.sendMessage("§c保護区域には罠を設置できません。");
            return;
        }
        if (!target.isEmpty()) {
            owner.sendMessage("§cここには罠を設置できません。");
            return;
        }
        Material material = trapMaterial(type);
        if (material == null) {
            owner.sendMessage("§c不明な罠です。");
            return;
        }
        UUID ownerId = owner.getUniqueId();
        Map<String, TrapState> owned = traps.computeIfAbsent(ownerId, ignored -> new HashMap<>());
        TrapState old = owned.remove(type);
        if (old != null) {
            restoreTrap(old);
        }
        BlockState original = target.getState();
        target.setType(material, false);
        long duration = Math.max(1L, plugin.getConfig().getLong(config.kitPath(FfaKit.TRAPPER, "trap-duration-seconds"), 30L));
        owned.put(type, new TrapState(type, ownerId, target.getLocation().toBlockLocation(), original, System.currentTimeMillis() + duration * 1000L));
        owner.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_ON, 0.8F, 1.0F);
        owner.sendActionBar(Component.text("罠を設置しました", NamedTextColor.YELLOW));
    }

    private Block trapTargetBlock(Player owner, PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            return event.getClickedBlock().getRelative(event.getBlockFace());
        }
        return owner.getLocation().getBlock();
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
        for (UUID owner : new ArrayList<>(traps.keySet())) {
            Map<String, TrapState> owned = traps.get(owner);
            if (owned == null) {
                continue;
            }
            for (TrapState trap : new ArrayList<>(owned.values())) {
                if (now >= trap.expiresAt()) {
                    owned.remove(trap.type());
                    restoreTrap(trap);
                    continue;
                }
                Player target = trapTarget(trap);
                if (target != null) {
                    owned.remove(trap.type());
                    triggerTrap(trap, target);
                }
            }
            if (owned.isEmpty()) {
                traps.remove(owner);
            }
        }
    }

    private Player trapTarget(TrapState trap) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isPlaying(player) || trap.owner().equals(player.getUniqueId()) || !player.getWorld().equals(trap.location().getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(trap.location().clone().add(0.5D, 0.0D, 0.5D)) <= 1.2D) {
                return player;
            }
        }
        return null;
    }

    private void triggerTrap(TrapState trap, Player target) {
        Player owner = plugin.getServer().getPlayer(trap.owner());
        restoreTrap(trap);
        if (owner == null || !isPlaying(owner) || plugin.areFriends(owner.getUniqueId(), target.getUniqueId())) {
            return;
        }
        recordDamage(owner, target);
        World world = trap.location().getWorld();
        if ("explosion".equals(trap.type())) {
            target.damage(6.0D, owner);
            if (world != null) {
                world.playSound(trap.location(), Sound.ENTITY_GENERIC_EXPLODE, 0.9F, 1.1F);
            }
            return;
        }
        if ("poison".equals(trap.type())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, false, true));
            if (world != null) {
                world.playSound(trap.location(), Sound.ENTITY_SPIDER_AMBIENT, 0.8F, 1.0F);
            }
            return;
        }
        if ("web".equals(trap.type())) {
            placeTemporaryWebs(target.getLocation());
        }
    }

    private void placeTemporaryWebs(Location center) {
        List<BlockState> originals = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = center.clone().add(x, 0, z).getBlock();
                if (block.isEmpty()) {
                    originals.add(block.getState());
                    block.setType(Material.COBWEB, false);
                }
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> originals.forEach(state -> state.update(true, false)), 80L);
    }

    private void restoreTrap(TrapState trap) {
        trap.original().update(true, false);
    }

    private void restoreTraps(UUID owner) {
        Map<String, TrapState> owned = traps.remove(owner);
        if (owned == null) {
            return;
        }
        owned.values().forEach(this::restoreTrap);
    }

    private void restoreAllTraps() {
        for (UUID owner : new ArrayList<>(traps.keySet())) {
            restoreTraps(owner);
        }
        traps.clear();
    }

    private void tickSummons() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(summonOwners.entrySet())) {
            Entity entity = entityById(entry.getKey());
            Player owner = plugin.getServer().getPlayer(entry.getValue());
            if (!(entity instanceof Mob mob) || owner == null || !isPlaying(owner)) {
                removeSummonEntity(entry.getKey());
                continue;
            }
            Player target = nearestEnemy(owner, entity.getLocation(), false);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(bugOwners.entrySet())) {
            Entity entity = entityById(entry.getKey());
            Player owner = plugin.getServer().getPlayer(entry.getValue());
            if (!(entity instanceof Mob mob) || owner == null || !isPlaying(owner)) {
                removeBugEntity(entry.getKey());
                continue;
            }
            Player target = nearestEnemy(owner, entity.getLocation(), true);
            if (target != null) {
                mob.setTarget(target);
            }
        }
    }

    private Player nearestEnemy(Player owner, Location location, boolean avoidBugMania) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isPlaying(player) || player.getUniqueId().equals(owner.getUniqueId()) || !player.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (plugin.areFriends(owner.getUniqueId(), player.getUniqueId())) {
                continue;
            }
            FfaSession session = sessions.get(player.getUniqueId());
            if (avoidBugMania && session != null && session.kit == FfaKit.BUG_MANIA) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < bestDistance && distance <= 32.0D * 32.0D) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void spawnBugSilverfish(Player owner, Location location) {
        int globalMax = Math.max(1, plugin.getConfig().getInt(config.kitPath(FfaKit.BUG_MANIA, "max-global-silverfish"), 30));
        while (bugOwners.size() >= globalMax) {
            UUID first = bugOwners.keySet().stream().findFirst().orElse(null);
            if (first == null) {
                break;
            }
            removeBugEntity(first);
        }
        UUID ownerId = owner.getUniqueId();
        List<UUID> owned = bugMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
        int maxOwned = Math.max(1, plugin.getConfig().getInt(config.kitPath(FfaKit.BUG_MANIA, "max-owned-silverfish"), 6));
        while (owned.size() >= maxOwned) {
            removeBugEntity(owned.remove(0));
        }
        Entity entity = location.getWorld().spawnEntity(location, EntityType.SILVERFISH);
        entity.getPersistentDataContainer().set(entityKindKey, PersistentDataType.STRING, "bug_silverfish");
        entity.getPersistentDataContainer().set(entityOwnerKey, PersistentDataType.STRING, ownerId.toString());
        if (entity instanceof LivingEntity living) {
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(false);
        }
        owned.add(entity.getUniqueId());
        bugOwners.put(entity.getUniqueId(), ownerId);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeBugEntity(entity.getUniqueId()), 20L * 20L);
        bugExpiryTasks.put(entity.getUniqueId(), task);
    }

    Player ownerOfFfaEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        UUID owner = summonOwners.get(entity.getUniqueId());
        if (owner == null) {
            owner = bugOwners.get(entity.getUniqueId());
        }
        if (owner == null) {
            owner = parseUuid(entity.getPersistentDataContainer().get(entityOwnerKey, PersistentDataType.STRING));
        }
        if (owner == null) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(owner);
        return player != null && isPlaying(player) ? player : null;
    }

    void handleEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String kind = entity.getPersistentDataContainer().get(entityKindKey, PersistentDataType.STRING);
        if (kind == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        UUID entityId = entity.getUniqueId();
        UUID owner = parseUuid(entity.getPersistentDataContainer().get(entityOwnerKey, PersistentDataType.STRING));
        if ("summon".equals(kind)) {
            clearSummonEntityState(entityId);
            return;
        }
        if ("bug_silverfish".equals(kind)) {
            clearBugEntityState(entityId);
            Player killer = event.getEntity().getKiller();
            if (killer != null && (owner == null || !owner.equals(killer.getUniqueId())) && ThreadLocalRandom.current().nextInt(100) < 5) {
                killer.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 20 * 60, 0, false, false, true));
            }
            // バグマニアのシルバーフィッシュには討伐報酬を適用しない
            return;
        }
    }

    void handleEntityTarget(EntityTargetLivingEntityEvent event) {
        String kind = event.getEntity().getPersistentDataContainer().get(entityKindKey, PersistentDataType.STRING);
        if (kind == null) {
            return;
        }
        UUID ownerId = parseUuid(event.getEntity().getPersistentDataContainer().get(entityOwnerKey, PersistentDataType.STRING));
        if (ownerId == null) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getTarget() instanceof Player target)) {
            if (event.getTarget() != null && ownerId.equals(parseUuid(event.getTarget().getPersistentDataContainer().get(entityOwnerKey, PersistentDataType.STRING)))) {
                event.setCancelled(true);
            }
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (!isPlaying(target) || ownerId.equals(target.getUniqueId()) || (owner != null && plugin.areFriends(ownerId, target.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        FfaSession targetSession = sessions.get(target.getUniqueId());
        if ("bug_silverfish".equals(kind) && targetSession != null && targetSession.kit == FfaKit.BUG_MANIA) {
            event.setCancelled(true);
        }
    }

    private void removeSummons(UUID owner) {
        List<UUID> owned = summonedMobs.remove(owner);
        if (owned == null) {
            return;
        }
        for (UUID entityId : new ArrayList<>(owned)) {
            removeSummonEntity(entityId);
        }
    }

    private void removeAllSummons() {
        for (UUID owner : new ArrayList<>(summonedMobs.keySet())) {
            removeSummons(owner);
        }
        summonedMobs.clear();
        summonOwners.clear();
    }

    private void removeSummonEntity(UUID entityId) {
        clearSummonEntityState(entityId);
        Entity entity = entityById(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void clearSummonEntityState(UUID entityId) {
        UUID owner = summonOwners.remove(entityId);
        if (owner != null) {
            List<UUID> owned = summonedMobs.get(owner);
            if (owned != null) {
                owned.remove(entityId);
                if (owned.isEmpty()) {
                    summonedMobs.remove(owner);
                }
            }
        }
        BukkitTask task = summonExpiryTasks.remove(entityId);
        if (task != null) {
            task.cancel();
        }
    }

    private void removeBugMobs(UUID owner) {
        List<UUID> owned = bugMobs.remove(owner);
        if (owned == null) {
            return;
        }
        for (UUID entityId : new ArrayList<>(owned)) {
            removeBugEntity(entityId);
        }
    }

    private void removeAllBugMobs() {
        for (UUID owner : new ArrayList<>(bugMobs.keySet())) {
            removeBugMobs(owner);
        }
        bugMobs.clear();
        bugOwners.clear();
    }

    private void removeBugEntity(UUID entityId) {
        clearBugEntityState(entityId);
        Entity entity = entityById(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void clearBugEntityState(UUID entityId) {
        UUID owner = bugOwners.remove(entityId);
        if (owner != null) {
            List<UUID> owned = bugMobs.get(owner);
            if (owned != null) {
                owned.remove(entityId);
                if (owned.isEmpty()) {
                    bugMobs.remove(owner);
                }
            }
        }
        BukkitTask task = bugExpiryTasks.remove(entityId);
        if (task != null) {
            task.cancel();
        }
    }

    private Entity entityById(UUID uuid) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (uuid.equals(entity.getUniqueId())) {
                    return entity;
                }
            }
        }
        return null;
    }

    private void recordDamage(Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        damageCredits.put(victim.getUniqueId(), new DamageCredit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private Player recentAttacker(Player victim) {
        DamageCredit credit = damageCredits.get(victim.getUniqueId());
        if (credit == null || System.currentTimeMillis() - credit.at() > 10_000L) {
            return null;
        }
        Player attacker = plugin.getServer().getPlayer(credit.attacker());
        return attacker != null && isPlaying(attacker) ? attacker : null;
    }

    private void capFinalDamage(EntityDamageByEntityEvent event, double cap) {
        if (event.getFinalDamage() > cap && event.getDamage() > 0.0D) {
            event.setDamage(event.getDamage() * (cap / event.getFinalDamage()));
        }
    }

    private void refillOneShotArrowLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isPlaying(player) || findKitItem(player, "event_one_shot_bow") == null) {
                return;
            }
            if (countFfaItem(player, "event_one_shot_arrow") <= 0) {
                ItemStack arrow = FfaKit.kitItem(plugin, FfaKit.BOW, "event_one_shot_arrow", Material.ARROW, "§c一撃必殺の矢", 1, Map.of());
                tagOwner(arrow, player.getUniqueId());
                player.getInventory().addItem(arrow);
            }
        }, 100L);
    }

    private void updateSeaWarriorTrident(Player player) {
        ItemStack trident = findKitItem(player, "trident");
        if (trident == null) {
            return;
        }
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

    void handleProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player) || !isPlaying(player)) {
            return;
        }
        FfaKit kit = sessions.get(player.getUniqueId()).kit;
        if (projectile instanceof ThrownPotion potion && kit == FfaKit.WIZARD) {
            PotionEffectType type = potion.getEffects().stream().findFirst().map(PotionEffect::getType).orElse(null);
            String projectileKind = FfaKit.isNegative(type) ? "chemist_negative_potion" : "chemist_potion";
            if (type != null && FfaKit.isNegative(type)) {
                wizardPotionOwners.put(projectile.getUniqueId(), player.getUniqueId());
            }
            projectile.getPersistentDataContainer().set(projectileKindKey, PersistentDataType.STRING, projectileKind);
            projectile.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isFfaItem(mainHand) && "event_sky_spear".equals(itemKind(mainHand))) {
            projectile.getPersistentDataContainer().set(projectileKindKey, PersistentDataType.STRING, "event_sky_spear");
            projectile.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        }
        if (projectile instanceof Trident trident && kit == FfaKit.TRIDENT) {
            trident.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
            trident.getPersistentDataContainer().set(projectileKindKey, PersistentDataType.STRING, "trident");
            trident.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            trackedTridents.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(trident.getUniqueId());
        }
    }

    void handleProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Trident trident
                && "trident".equals(trident.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            trident.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        }
    }

    boolean handleArrowPickup(PlayerPickupArrowEvent event) {
        AbstractArrow arrow = event.getArrow();
        if (!(arrow instanceof Trident)
                || !"trident".equals(arrow.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            return false;
        }
        UUID owner = parseUuid(arrow.getPersistentDataContainer().get(projectileOwnerKey, PersistentDataType.STRING));
        if (owner == null) {
            event.setCancelled(true);
            arrow.remove();
            return true;
        }
        Player player = event.getPlayer();
        if (!owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return true;
        }
        FfaSession session = sessions.get(owner);
        if (session == null || session.kit != FfaKit.TRIDENT) {
            event.setCancelled(true);
            untrackTrident(owner, arrow.getUniqueId());
            arrow.remove();
            return true;
        }
        event.setCancelled(false);
        untrackTrident(owner, arrow.getUniqueId());
        return true;
    }

    void handlePotionSplash(PotionSplashEvent event) {
        UUID owner = wizardPotionOwners.remove(event.getEntity().getUniqueId());
        if (owner == null) {
            return;
        }
        event.getAffectedEntities().forEach(entity -> {
            if (entity instanceof Player player && owner.equals(player.getUniqueId())) {
                event.setIntensity(entity, 0.0D);
            }
        });
    }

    void handleBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isPlaying(player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (!isFfaItem(bow)) {
            return;
        }
        String kind = itemKind(bow);
        if ("event_one_shot_bow".equals(kind)) {
            if (event.getProjectile() instanceof Entity projectile) {
                projectile.getPersistentDataContainer().set(projectileKindKey, PersistentDataType.STRING, "event_one_shot_arrow");
                projectile.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            }
            refillOneShotArrowLater(player);
            return;
        }
        if ("sniper".equals(kind)) {
            handleAmmoCrossbow(event, player, FfaKit.SNIPER, sniperAmmo, sniperReloadTasks, 2, "スナイパー", "sniper");
            return;
        }
        if (!"revolver".equals(kind)) {
            return;
        }
        handleAmmoCrossbow(event, player, FfaKit.CROSSBOW, revolverAmmo, revolverReloadTasks, 6, "リボルバー", "revolver");
    }

    private void handleAmmoCrossbow(
            EntityShootBowEvent event,
            Player player,
            FfaKit expectedKit,
            Map<UUID, Integer> ammoMap,
            Map<UUID, BukkitTask> reloadTasks,
            int fallbackCapacity,
            String label,
            String projectileKind) {
        FfaSession session = sessions.get(player.getUniqueId());
        if (session == null || session.kit != expectedKit) {
            event.setCancelled(true);
            return;
        }
        if (reloadTasks.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("リロード中", NamedTextColor.RED));
            return;
        }
        int configuredCapacity = expectedKit == FfaKit.SNIPER ? session.kit.sniperCapacity(config) : session.kit.revolverCapacity(config);
        int capacity = Math.max(1, configuredCapacity <= 0 ? fallbackCapacity : configuredCapacity);
        int ammo = Math.max(0, ammoMap.getOrDefault(player.getUniqueId(), capacity));
        if (ammo <= 0) {
            event.setCancelled(true);
            startCrossbowReload(player, expectedKit, ammoMap, reloadTasks, capacity, label);
            return;
        }
        ammo--;
        ammoMap.put(player.getUniqueId(), ammo);
        if (event.getProjectile() instanceof Entity projectile) {
            projectile.getPersistentDataContainer().set(projectileKindKey, PersistentDataType.STRING, projectileKind);
            projectile.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        }
        playCrossbowShotSound(player, expectedKit);
        int remaining = ammo;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            updateAmmoCrossbowItem(player, expectedKit, label, remaining, capacity);
            player.sendActionBar(Component.text(label + " " + remaining + "/" + capacity, NamedTextColor.LIGHT_PURPLE));
            if (remaining > 0) {
                rechargeAmmoCrossbow(player, expectedKit);
            } else {
                startCrossbowReload(player, expectedKit, ammoMap, reloadTasks, capacity, label);
            }
        });
    }

    private void playCrossbowShotSound(Player player, FfaKit kit) {
        float blastVolume = kit == FfaKit.SNIPER ? 0.45F : 0.65F;
        float blastPitch = kit == FfaKit.SNIPER ? 0.85F : 1.45F;
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, blastVolume, blastPitch);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, kit == FfaKit.SNIPER ? 0.18F : 0.25F, 1.8F);
    }

    void adjustFfaDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (attacker == null || victim == null || !isPlaying(attacker) || !isPlaying(victim)) {
            return;
        }
        recordDamage(attacker, victim);
        Object damager = event.getDamager();
        if (damager instanceof Projectile projectile
                && "event_one_shot_arrow".equals(projectile.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            if (!plugin.isStructureProtectedLocation(victim.getLocation())) {
                event.setDamage(Math.max(event.getDamage(), victim.getHealth() + victim.getAbsorptionAmount() + 2.0D));
            }
            return;
        }
        if (damager instanceof Projectile projectile
                && "revolver".equals(projectile.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            double multiplier = plugin.getConfig().getDouble(config.kitPath(FfaKit.CROSSBOW, "damage-multiplier"), 0.70D);
            event.setDamage(event.getDamage() * Math.max(0.0D, multiplier));
        }
        if (damager instanceof Projectile projectile
                && "sniper".equals(projectile.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            double multiplier = plugin.getConfig().getDouble(config.kitPath(FfaKit.SNIPER, "damage-multiplier"), 5.0D);
            event.setDamage(event.getDamage() * Math.max(0.0D, multiplier));
        }
        if (damager instanceof Projectile projectile
                && "event_sky_spear".equals(projectile.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
            capFinalDamage(event, 12.0D);
        }
        FfaSession session = sessions.get(attacker.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (session.kit == FfaKit.GAMBLER && isFfaItem(mainHand) && "weapon".equals(itemKind(mainHand)) && event.getFinalDamage() > 0.0D) {
            double min = plugin.getConfig().getDouble(config.kitPath(FfaKit.GAMBLER, "min-damage-multiplier"), -3.0D);
            double max = plugin.getConfig().getDouble(config.kitPath(FfaKit.GAMBLER, "max-damage-multiplier"), 3.0D);
            double multiplier = ThreadLocalRandom.current().nextDouble(Math.min(min, max), Math.max(min, max) + 0.000001D);
            attacker.sendActionBar(Component.text("倍率 " + String.format(Locale.ROOT, "%.2f", multiplier), multiplier < 0 ? NamedTextColor.RED : NamedTextColor.GOLD));
            if (multiplier < 0.0D) {
                event.setCancelled(true);
                if (gamblerSelfDamage.add(attacker.getUniqueId())) {
                    try {
                        attacker.damage(event.getDamage() * Math.abs(multiplier), attacker);
                    } finally {
                        gamblerSelfDamage.remove(attacker.getUniqueId());
                    }
                }
                return;
            }
            event.setDamage(event.getDamage() * multiplier);
        }
        if (session.kit == FfaKit.ASSASSIN && isFfaItem(mainHand) && event.getFinalDamage() > 0.0D) {
            String kind = itemKind(mainHand);
            if ("fatal_sword".equals(kind)) {
                if (plugin.isStructureProtectedLocation(victim.getLocation())) {
                    return;
                }
                event.setDamage(0.0D);
                double target = Math.max(0.0D, victim.getHealth() - 1.0D);
                if (target > 0.0D) {
                    victim.setHealth(Math.max(1.0D, victim.getHealth() - target));
                    mainHand.setAmount(0);
                    attacker.sendActionBar(Component.text("致命の剣を使用しました", NamedTextColor.DARK_RED));
                }
                return;
            }
            if ("poison_sword".equals(kind)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1, false, false, true));
            }
        }
        if (session.kit == FfaKit.BUG_MANIA && isFfaItem(mainHand) && "bug_sword".equals(itemKind(mainHand))) {
            if (ThreadLocalRandom.current().nextInt(100) < 10) {
                spawnBugSilverfish(attacker, victim.getLocation());
            }
        }
        FfaSession victimSession = sessions.get(victim.getUniqueId());
        if (victimSession != null && victimSession.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 20 * 60, 0, false, false, true));
            attacker.sendActionBar(Component.text("虫食いを受けました", NamedTextColor.DARK_GREEN));
        }
        if (session != null
                && session.kit == FfaKit.MACE
                && isFfaItem(attacker.getInventory().getItemInMainHand())
                && "mace".equals(itemKind(attacker.getInventory().getItemInMainHand()))) {
            double cap = plugin.getConfig().getDouble(config.kitPath(FfaKit.MACE, "max-final-damage"), 12.0D);
            capFinalDamage(event, cap);
        }
        if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0D) {
            double dealt = Math.max(0.0D, event.getFinalDamage());
            double heal = dealt * Math.max(0.0D, plugin.getConfig().getDouble(config.kitPath(FfaKit.VAMPIRE, "lifesteal-percent"), 50.0D)) / 100.0D;
            AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
            double maxHealthValue = maxHealth == null ? 20.0D : maxHealth.getValue();
            attacker.setHealth(Math.min(maxHealthValue, attacker.getHealth() + heal));
            double total = vampireDamage.getOrDefault(attacker.getUniqueId(), 0.0D) + dealt;
            vampireDamage.put(attacker.getUniqueId(), total);
            int level = Math.min(3, (int) (total / Math.max(1.0D, plugin.getConfig().getDouble(config.kitPath(FfaKit.VAMPIRE, "damage-per-strength-level"), 100.0D))));
            if (level > 0) {
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, level - 1, false, false, true));
            }
            attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", heal) + " / 累計 " + (int) total, NamedTextColor.RED));
        }
    }

    boolean leaveOnWorldExit() {
        return config.leaveOnWorldExit();
    }

    boolean isFfaWorld(World world) {
        if (world == null) {
            return false;
        }
        Set<String> names = new HashSet<>();
        Location center = config.center();
        if (center != null && center.getWorld() != null) {
            names.add(center.getWorld().getName().toLowerCase(Locale.ROOT));
        }
        addWorldName(names, "minigame");
        addWorldName(names, plugin.getConfig().getString("servers.minigame.world"));
        for (String configuredName : plugin.getConfig().getStringList("world-rules.pvp.enabled-worlds")) {
            addWorldName(names, configuredName);
        }
        return names.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    Location center() {
        return config.center();
    }

    Location respawnLocation() {
        Location center = config.center();
        if (center == null) {
            return null;
        }
        return switch (config.respawnLocationMode()) {
            case "ffa-center", "center", "arena" -> center;
            case "kit-selection", "selection", "kits" -> {
                Location selection = config.kitSelection();
                yield selection == null ? center : selection;
            }
            default -> center.getWorld().getSpawnLocation();
        };
    }

    private void prepareForFight(Player player, FfaKit kit) {
        clearTemporaryState(player);
        player.setGameMode(GameMode.SURVIVAL);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0D);
        }
        player.setHealth(20.0D);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        kit.applyTo(inventory, config, plugin);
        tagOwnedKitItems(player);
        if (kit == FfaKit.CROSSBOW) {
            revolverAmmo.put(player.getUniqueId(), kit.revolverCapacity(config));
            updateRevolverItem(player);
        }
        if (kit == FfaKit.SNIPER) {
            sniperAmmo.put(player.getUniqueId(), kit.sniperCapacity(config));
            updateSniperItem(player);
        }
        applyKitEffects(player, kit);
        fieldItems.applyActiveEventGear(player);
        player.updateInventory();
    }

    private FfaKit sanitizeKit(FfaKit kit) {
        if (kit == null || !kit.isActive(config)) {
            return FfaKit.SWORD;
        }
        return kit;
    }

    private String itemKind(ItemStack item) {
        if (!isFfaItem(item)) {
            return "";
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(itemKindKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private String itemAbility(ItemStack item) {
        if (!isFfaItem(item)) {
            return "";
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        return value == null ? itemKind(item) : value;
    }

    private void tagOwnedKitItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            tagOwner(item, player.getUniqueId());
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            tagOwner(item, player.getUniqueId());
        }
        tagOwner(player.getInventory().getItemInOffHand(), player.getUniqueId());
    }

    private void tagOwner(ItemStack item, UUID owner) {
        if (!isFfaItem(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemOwnerKey, PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
    }

    private void cleanupKitRuntime(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask reload = revolverReloadTasks.remove(uuid);
        if (reload != null) {
            reload.cancel();
        }
        BukkitTask sniperReload = sniperReloadTasks.remove(uuid);
        if (sniperReload != null) {
            sniperReload.cancel();
        }
        BukkitTask windRefill = windChargeRefillTasks.remove(uuid);
        if (windRefill != null) {
            windRefill.cancel();
        }
        revolverAmmo.remove(uuid);
        sniperAmmo.remove(uuid);
        gamblerSelfDamage.remove(uuid);
        vampireDamage.remove(uuid);
        wizardPotionCooldownUntil.remove(uuid);
        wizardPotionOwners.entrySet().removeIf(entry -> uuid.equals(entry.getValue()));
        removeTrackedTridents(uuid);
        removeSummons(uuid);
        removeBugMobs(uuid);
        restoreTraps(uuid);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.INFESTED);
        player.setCooldown(Material.SPLASH_POTION, 0);
    }

    private void applyKitEffects(Player player, FfaKit kit) {
        if (kit == FfaKit.AXE) {
            int amplifier = Math.max(0, plugin.getConfig().getInt(config.kitPath(FfaKit.AXE, "slowness-amplifier"), 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, amplifier, false, false, true));
        }
        if (kit == FfaKit.SPEAR) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
        }
        if (kit == FfaKit.SNIPER) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, false, false, true));
        }
        if (kit == FfaKit.VAMPIRE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
        }
        if (kit == FfaKit.GRAPPLER) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 2, false, false, true));
        }
        if (kit == FfaKit.BUG_MANIA) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 100, 0, false, false, true));
        }
    }

    private void startKitEffectTask() {
        if (kitEffectTask != null) {
            kitEffectTask.cancel();
        }
        kitEffectTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new ArrayList<>(sessions.keySet())) {
                Player player = plugin.getServer().getPlayer(uuid);
                FfaSession session = sessions.get(uuid);
                if (player == null || !player.isOnline() || session == null) {
                    continue;
                }
                if (session.kit == FfaKit.AXE
                        || session.kit == FfaKit.SNIPER
                        || session.kit == FfaKit.SPEAR
                        || session.kit == FfaKit.GRAPPLER
                        || session.kit == FfaKit.VAMPIRE
                        || session.kit == FfaKit.BUG_MANIA) {
                    applyKitEffects(player, session.kit);
                }
                if (session.kit == FfaKit.VAMPIRE) {
                    applyVampireSunDamage(player);
                }
                if (session.kit == FfaKit.TRIDENT) {
                    updateSeaWarriorTrident(player);
                }
                pruneTrackedTridents(uuid);
            }
            tickSummons();
            tickTraps();
        }, 40L, 40L);
    }

    private void applyVampireSunDamage(Player player) {
        if (!isInDirectSunlight(player)) {
            return;
        }
        double damage = Math.max(0.0D, plugin.getConfig().getDouble(config.kitPath(FfaKit.VAMPIRE, "sun-damage"), 1.0D));
        if (damage > 0.0D) {
            player.damage(damage);
        }
    }

    private boolean isInDirectSunlight(Player player) {
        World world = player.getWorld();
        long time = world.getTime();
        if (time >= 12300L || world.hasStorm() || world.isThundering()) {
            return false;
        }
        Location location = player.getLocation();
        return location.getBlock().getLightFromSky() >= 15
                && location.getBlockY() >= world.getHighestBlockYAt(location);
    }

    private void startCrossbowReload(
            Player player,
            FfaKit kit,
            Map<UUID, Integer> ammoMap,
            Map<UUID, BukkitTask> reloadTasks,
            int capacity,
            String label) {
        UUID uuid = player.getUniqueId();
        if (reloadTasks.containsKey(uuid)) {
            return;
        }
        updateAmmoCrossbowItem(player, kit, label, 0, capacity);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, kit == FfaKit.SNIPER ? 0.25F : 0.8F, 0.8F);
        long reloadTicks = Math.max(1L, plugin.getConfig().getLong(config.kitPath(kit, "reload-ticks"), kit == FfaKit.SNIPER ? 125L : 75L));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            reloadTasks.remove(uuid);
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            FfaSession session = sessions.get(uuid);
            if (session == null || session.kit != kit) {
                return;
            }
            ammoMap.put(uuid, capacity);
            rechargeAmmoCrossbow(player, kit);
            updateAmmoCrossbowItem(player, kit, label, capacity, capacity);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 1.2F);
            player.sendActionBar(Component.text(label + " リロード完了 " + capacity + "/" + capacity, NamedTextColor.GREEN));
        }, reloadTicks);
        reloadTasks.put(uuid, task);
        player.sendActionBar(Component.text(label + " リロード中...", NamedTextColor.RED));
    }

    private void rechargeAmmoCrossbow(Player player, FfaKit kit) {
        ItemStack crossbow = findKitItem(player, kit == FfaKit.SNIPER ? "sniper" : "revolver");
        if (crossbow == null || !(crossbow.getItemMeta() instanceof CrossbowMeta meta)) {
            return;
        }
        meta.setChargedProjectiles(List.of(new ItemStack(Material.ARROW)));
        crossbow.setItemMeta(meta);
        player.updateInventory();
    }

    private void updateAmmoCrossbowItem(Player player, FfaKit kit, String label, int ammo, int capacity) {
        ItemStack crossbow = findKitItem(player, kit == FfaKit.SNIPER ? "sniper" : "revolver");
        if (crossbow == null) {
            return;
        }
        ItemMeta meta = crossbow.getItemMeta();
        meta.displayName(Component.text((kit == FfaKit.SNIPER ? "§8" : "§d") + label + " §7[" + ammo + "/" + capacity + "]"));
        if (meta instanceof CrossbowMeta crossbowMeta && ammo <= 0) {
            crossbowMeta.setChargedProjectiles(List.of());
            crossbow.setItemMeta(crossbowMeta);
        } else {
            crossbow.setItemMeta(meta);
        }
        player.updateInventory();
    }

    private void updateSniperItem(Player player) {
        int capacity = FfaKit.SNIPER.sniperCapacity(config);
        int ammo = Math.max(0, sniperAmmo.getOrDefault(player.getUniqueId(), capacity));
        updateAmmoCrossbowItem(player, FfaKit.SNIPER, "スナイパー", ammo, capacity);
    }

    private void startRevolverReload(Player player, int capacity) {
        UUID uuid = player.getUniqueId();
        if (revolverReloadTasks.containsKey(uuid)) {
            return;
        }
        updateRevolverItem(player);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.8F, 0.8F);
        long reloadTicks = Math.max(1L, plugin.getConfig().getLong(config.kitPath(FfaKit.CROSSBOW, "reload-ticks"), 75L));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            revolverReloadTasks.remove(uuid);
            if (!player.isOnline() || !isPlaying(player)) {
                return;
            }
            FfaSession session = sessions.get(uuid);
            if (session == null || session.kit != FfaKit.CROSSBOW) {
                return;
            }
            revolverAmmo.put(uuid, capacity);
            rechargeRevolver(player);
            updateRevolverItem(player);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 1.2F);
            player.sendActionBar(Component.text("リロード完了 " + capacity + "/" + capacity, NamedTextColor.GREEN));
        }, reloadTicks);
        revolverReloadTasks.put(uuid, task);
        player.sendActionBar(Component.text("リロード中...", NamedTextColor.RED));
    }

    private void rechargeRevolver(Player player) {
        ItemStack revolver = findRevolver(player);
        if (revolver == null || !(revolver.getItemMeta() instanceof CrossbowMeta meta)) {
            return;
        }
        meta.setChargedProjectiles(List.of(new ItemStack(Material.ARROW)));
        revolver.setItemMeta(meta);
        player.updateInventory();
    }

    private void updateRevolverItem(Player player) {
        ItemStack revolver = findRevolver(player);
        if (revolver == null) {
            return;
        }
        int capacity = FfaKit.CROSSBOW.revolverCapacity(config);
        int ammo = Math.max(0, revolverAmmo.getOrDefault(player.getUniqueId(), capacity));
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

    private ItemStack findRevolver(Player player) {
        return findKitItem(player, "revolver");
    }

    private ItemStack findKitItem(Player player, String kind) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isFfaItem(item) && kind.equals(itemKind(item))) {
                return item;
            }
        }
        return null;
    }

    private void removeTrackedTridents(UUID owner) {
        Set<UUID> tridents = trackedTridents.remove(owner);
        if (tridents == null || tridents.isEmpty()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (tridents.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
    }

    private void untrackTrident(UUID owner, UUID trident) {
        Set<UUID> tridents = trackedTridents.get(owner);
        if (tridents == null) {
            return;
        }
        tridents.remove(trident);
        if (tridents.isEmpty()) {
            trackedTridents.remove(owner);
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void pruneTrackedTridents(UUID owner) {
        Set<UUID> tridents = trackedTridents.get(owner);
        if (tridents == null || tridents.isEmpty()) {
            return;
        }
        Set<UUID> live = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (tridents.contains(entity.getUniqueId()) && entity.isValid()) {
                    live.add(entity.getUniqueId());
                }
            }
        }
        if (live.isEmpty()) {
            trackedTridents.remove(owner);
        } else {
            trackedTridents.put(owner, live);
        }
    }

    private ItemStack selectorItem(FfaKit kit, boolean selected) {
        Material icon = kit.icon(config);
        ItemStack item = new ItemStack(icon == null ? Material.BARRIER : icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((selected ? ChatColor.GREEN + "選択中: " : "") + kit.displayName(config)));
        List<Component> lore = new ArrayList<>(kit.details(config, plugin));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(selectorKitKey, PersistentDataType.STRING, kit.key());
        item.setItemMeta(meta);
        return item;
    }

    private FfaKit kitFromSelectorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        String key = item.getItemMeta().getPersistentDataContainer().get(selectorKitKey, PersistentDataType.STRING);
        return FfaKit.fromKey(key);
    }

    private void updateScoreboard(Player player) {
        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("minerva_ffa", "dummy", "§aFFA戦績");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        UUID uuid = player.getUniqueId();
        int kills = stats.kills(uuid);
        int deaths = stats.deaths(uuid);
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        objective.getScore("§fキット: §e" + stripColor(sessions.get(uuid).kit.displayName(config))).setScore(6);
        objective.getScore("§aキル: §f" + kills).setScore(5);
        objective.getScore("§cデス: §f" + deaths).setScore(4);
        objective.getScore("§eK/D: §f" + String.format(Locale.ROOT, "%.2f", kd)).setScore(3);
        objective.getScore("§b連続キル: §f" + stats.currentStreak(uuid)).setScore(2);
        objective.getScore("§d最高連続: §f" + stats.maxStreak(uuid)).setScore(1);
        player.setScoreboard(scoreboard);
    }

    private void awardKillEmeralds(Player killer, Player victim) {
        long now = System.currentTimeMillis();
        long resetMillis = Math.max(1L, plugin.getConfig().getLong("ffa.rewards.same-target-reset-seconds", 600L)) * 1000L;
        KillRewardState state = killRewardStates.get(killer.getUniqueId());
        int sameTargetRepeats = 0;
        if (state != null && victim.getUniqueId().equals(state.target()) && now - state.lastKillAt() <= resetMillis) {
            sameTargetRepeats = state.repeats() + 1;
        }
        killRewardStates.put(killer.getUniqueId(), new KillRewardState(victim.getUniqueId(), sameTargetRepeats, now));
        int base = Math.max(0, configInt("ffa.rewards.kill-mp", "ffa.rewards.kill-em", 50));
        int reward = sameTargetRepeats >= 7 ? 0 : (int) Math.floor(base * Math.pow(0.5D, sameTargetRepeats));
        FfaSession session = sessions.get(killer.getUniqueId());
        int gamblerDelta = 0;
        if (session != null && session.kit == FfaKit.GAMBLER) {
            int min = configInt(config.kitPath(FfaKit.GAMBLER, "mp-min"), config.kitPath(FfaKit.GAMBLER, "em-min"), -10);
            int max = configInt(config.kitPath(FfaKit.GAMBLER, "mp-max"), config.kitPath(FfaKit.GAMBLER, "em-max"), 10);
            gamblerDelta = ThreadLocalRandom.current().nextInt(Math.min(min, max), Math.max(min, max) + 1);
            reward += gamblerDelta;
        }
        boolean fever = plugin.data().getLong("ffa.events.mp-fever-until",
                plugin.data().getLong("ffa.events.em-fever-until", 0L)) > now;
        if (fever && reward > 0) {
            reward *= 2;
        }
        if (reward > 0) {
            plugin.depositEmeralds(killer.getUniqueId(), reward);
        } else if (reward < 0) {
            plugin.withdrawEmeralds(killer.getUniqueId(), Math.min(plugin.getEmeralds(killer.getUniqueId()), Math.abs(reward)));
        }
        killer.sendActionBar(Component.text("FFA報酬 " + (reward >= 0 ? "+" : "") + reward + "MP"
                + " / 同一減衰 " + sameTargetRepeats
                + (gamblerDelta == 0 ? "" : " / 運 " + gamblerDelta)
                + (fever && reward > 0 ? " / フィーバー" : ""), reward >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private int configInt(String path, String legacyPath, int fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getInt(path, fallback)
                : plugin.getConfig().getInt(legacyPath, fallback);
    }

    private void clearTemporaryState(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0D : Math.max(1.0D, maxHealth.getValue()));
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

    private static final class FfaSession {
        private FfaKit kit;
        private final PlayerState state;

        private FfaSession(FfaKit kit, PlayerState state) {
            this.kit = kit;
            this.state = state;
        }
    }

    private record KillRewardState(UUID target, int repeats, long lastKillAt) {
    }

    private record DamageCredit(UUID attacker, long at) {
    }

    private record TrapState(String type, UUID owner, Location location, BlockState original, long expiresAt) {
    }

    private record DeathLeaveRestore(PlayerState state, Location location) {
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
                Scoreboard scoreboard) {
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

        private static PlayerState capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            return new PlayerState(
                    cloneItems(inventory.getStorageContents()),
                    cloneItems(inventory.getArmorContents()),
                    inventory.getItemInOffHand().clone(),
                    player.getExp(),
                    player.getLevel(),
                    player.getTotalExperience(),
                    maxHealth == null ? 20.0D : maxHealth.getBaseValue(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getSaturation(),
                    player.getGameMode(),
                    player.getLocation().clone(),
                    new ArrayList<>(player.getActivePotionEffects()),
                    player.getFireTicks(),
                    player.getScoreboard());
        }

        private void restore(Player player) {
            restore(player, location);
        }

        private void restore(Player player, Location restoreLocation) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(cloneItems(storage));
            inventory.setArmorContents(cloneItems(armor));
            inventory.setItemInOffHand(offhand == null ? null : offhand.clone());
            player.setGameMode(gameMode);
            player.setExp(exp);
            player.setLevel(level);
            player.setTotalExperience(totalExperience);
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealthBase);
            }
            double max = maxHealth == null ? 20.0D : maxHealth.getValue();
            player.setHealth(Math.max(1.0D, Math.min(max, health)));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
            player.setFireTicks(fireTicks);
            player.setScoreboard(scoreboard);
            player.teleport(restoreLocation);
            player.updateInventory();
        }
    }
}
