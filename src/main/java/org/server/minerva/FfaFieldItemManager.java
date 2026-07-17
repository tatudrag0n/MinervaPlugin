package org.server.minerva;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
        ensureDefaults();
        start();
    }

    void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        for (BukkitTask task : channelTasks.values()) {
            task.cancel();
        }
        channelTasks.clear();
        activeEvents.clear();
        removeEventItems("one_shot_bow");
        removeEventItems("sky_spear");
        removeFieldItems();
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
            case "spawnpoint" -> handleSpawnPoint(sender, args);
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                String type = args.length >= 4 ? args[3] : "random";
                spawnLoot(player.getLocation(), type);
                sender.sendMessage(ChatColor.GREEN + "フィールドアイテムを出現させました: " + type);
            }
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                String event = args.length >= 4 ? args[3] : "random";
                startEvent(event, player);
            }
            case "stop" -> {
                shutdown();
                start();
                sender.sendMessage(ChatColor.YELLOW + "FFAフィールドアイテムとイベントを停止しました。");
            }
            case "reload" -> {
                shutdown();
                load();
                sender.sendMessage(ChatColor.GREEN + "FFAフィールドアイテム設定を再読み込みしました。");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint|spawn|start|stop|reload");
        }
        return true;
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 3) {
            return List.of("spawnpoint", "spawn", "start", "stop", "reload");
        }
        if (args.length == 4 && "spawnpoint".equalsIgnoreCase(args[2])) {
            return List.of("add", "remove", "list");
        }
        if (args.length == 4 && "start".equalsIgnoreCase(args[2])) {
            return eventTypes();
        }
        if (args.length == 4 && "spawn".equalsIgnoreCase(args[2])) {
            return List.of("random", "common", "uncommon", "rare", "epic", "legendary");
        }
        return List.of();
    }

    boolean handlePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ffa.isPlaying(player)) {
            return false;
        }
        Item item = event.getItem();
        if (!isFieldItem(item)) {
            return false;
        }
        event.setCancelled(true);
        String eventType = item.getPersistentDataContainer().get(fieldEventKey, PersistentDataType.STRING);
        if (eventType != null && !eventType.isBlank()) {
            startEvent(eventType, player);
        } else {
            giveLoot(player, item.getPersistentDataContainer().get(fieldTypeKey, PersistentDataType.STRING));
        }
        fieldItems.remove(item.getUniqueId());
        item.remove();
        return true;
    }

    boolean handleEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && isFieldItem(item)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    boolean handleInventoryPickup(InventoryPickupItemEvent event) {
        if (isFieldItem(event.getItem())) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void handleSpawnPoint(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint add|remove|list");
            return;
        }
        String sub = args[3].toLowerCase(Locale.ROOT);
        List<String> points = new ArrayList<>(plugin.getConfig().getStringList("ffa.field-items.spawnpoints"));
        switch (sub) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only.");
                    return;
                }
                points.add(serialize(player.getLocation()));
                plugin.getConfig().set("ffa.field-items.spawnpoints", points);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "出現地点を追加しました: " + points.size());
            }
            case "remove" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "/mva ffa fielditem spawnpoint remove <番号>");
                    return;
                }
                int index = parseInt(args[4], -1) - 1;
                if (index < 0 || index >= points.size()) {
                    sender.sendMessage(ChatColor.RED + "番号が不正です。");
                    return;
                }
                points.remove(index);
                plugin.getConfig().set("ffa.field-items.spawnpoints", points);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "出現地点を削除しました。");
            }
            case "list" -> {
                if (points.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "出現地点は未登録です。");
                    return;
                }
                for (int i = 0; i < points.size(); i++) {
                    sender.sendMessage(ChatColor.GREEN + String.valueOf(i + 1) + ". " + points.get(i));
                }
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/mva ffa fielditem spawnpoint add|remove|list");
        }
    }

    private void start() {
        if (!plugin.getConfig().getBoolean("ffa.field-items.enabled", true)) {
            return;
        }
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        nextLootAt = System.currentTimeMillis() + 30_000L;
        nextEventAt = System.currentTimeMillis() + eventIntervalMillis();
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void tick() {
        List<Player> players = ffaPlayers();
        if (players.isEmpty()) {
            removeFieldItems();
            return;
        }
        long now = System.currentTimeMillis();
        if (players.size() >= plugin.getConfig().getInt("ffa.field-items.min-players", 2) && now >= nextEventAt && countEventItems() == 0) {
            spawnEventItem(randomSpawnPoint(), randomEvent());
            nextEventAt = now + eventIntervalMillis();
        }
        if (now >= nextLootAt && countLootItems() < maxLoot(players.size())) {
            spawnLoot(randomSpawnPoint(), "random");
            long[] range = lootInterval(players.size());
            nextLootAt = now + ThreadLocalRandom.current().nextLong(range[0], range[1] + 1L);
        }
    }

    private void spawnEventItem(Location location, String eventType) {
        if (location == null) {
            return;
        }
        ItemStack stack = named(eventIcon(eventType), "§dイベント: " + eventDisplay(eventType));
        Item item = location.getWorld().dropItem(location, stack);
        tagFieldItem(item, "event", eventType);
        scheduleRemove(item, plugin.getConfig().getLong("ffa.field-items.event-despawn-seconds", 60L));
        broadcast("§dイベントアイテムが出現しました: §f" + eventDisplay(eventType));
    }

    private void spawnLoot(Location location, String requestedType) {
        if (location == null) {
            return;
        }
        String rarity = "random".equalsIgnoreCase(requestedType) ? randomRarity() : requestedType.toLowerCase(Locale.ROOT);
        ItemStack stack = lootItem(rarity);
        Item item = location.getWorld().dropItem(location, stack);
        tagFieldItem(item, rarity, null);
        scheduleRemove(item, plugin.getConfig().getLong("ffa.field-items.loot-despawn-seconds", 90L));
        if ("legendary".equals(rarity)) {
            broadcast("§6レジェンダリーアイテムが出現しました。");
        }
    }

    private void giveLoot(Player player, String rarity) {
        List<ItemStack> items = lootItems(rarity == null ? "common" : rarity);
        if (ffa.currentKit(player) == FfaKit.GRAPPLER && items.stream().anyMatch(item -> isFieldEquipment(item.getType()))) {
            player.sendActionBar(Component.text("グラップラーはフィールド装備を取得できません", NamedTextColor.RED));
            return;
        }
        for (ItemStack item : items) {
            FfaKit.tagItem(plugin, FfaKit.SWORD, "field_" + (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)), item, Map.of());
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "ffa_field_owned"), PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(ffaOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
        }
        player.sendActionBar(Component.text("フィールドアイテム取得: " + rarity, NamedTextColor.GOLD));
    }

    private void startEvent(String rawType, Player activator) {
        String type = "random".equalsIgnoreCase(rawType) ? randomEvent() : normalizeEvent(rawType);
        String channel = eventChannel(type);
        BukkitTask old = channelTasks.remove(channel);
        if (old != null) {
            old.cancel();
            String oldType = activeEvents.remove(channel);
            if (oldType != null) {
                removeEventItems(oldType);
            }
        }
        long seconds = eventDuration(type);
        applyEvent(type, activator, (int) seconds);
        activeEvents.put(channel, type);
        if ("mp_fever".equals(type)) {
            plugin.data().set("ffa.events.mp-fever-until", System.currentTimeMillis() + seconds * 1000L);
            plugin.data().set("ffa.events.em-fever-until", null);
            plugin.saveData();
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> endEvent(type, channel), seconds * 20L);
        channelTasks.put(channel, task);
        broadcast("§d" + eventDisplay(type) + " が発動しました。");
    }

    private void applyEvent(String type, Player activator, int seconds) {
        World world = ffa.center() == null ? null : ffa.center().getWorld();
        switch (type) {
            case "rain" -> {
                if (world != null) {
                    int durationTicks = seconds * 20;
                    world.setStorm(true);
                    world.setWeatherDuration(durationTicks);
                    world.setThundering(false);
                    world.setThunderDuration(durationTicks);
                }
                for (Player player : ffaPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, false, true));
                    player.setFireTicks(0);
                }
            }
            case "snow", "blizzard" -> {
                for (Player player : ffaPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0, false, false, true));
                    if ("blizzard".equals(type)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 0, false, false, true));
                    }
                }
            }
            case "berserk" -> activator.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, false, true));
            case "speed" -> activator.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, false, false, true));
            case "iron_body" -> {
                activator.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0, false, false, true));
                activator.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 0, false, false, true));
            }
            case "overdrive" -> {
                for (Player player : ffaPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, false, true));
                }
            }
            case "one_shot_bow" -> ffaPlayers().forEach(this::giveOneShotBow);
            case "sky_spear" -> ffaPlayers().forEach(this::giveSkySpearGear);
            case "time_shift" -> {
                if (world != null) {
                    world.setTime(List.of(1000L, 6000L, 12000L, 18000L).get(ThreadLocalRandom.current().nextInt(4)));
                }
            }
            case "heal_self" -> heal(activator, 10.0D);
            case "heal_all" -> ffaPlayers().forEach(player -> heal(player, 8.0D));
            default -> {
            }
        }
    }

    private void endEvent(String type, String channel) {
        channelTasks.remove(channel);
        activeEvents.remove(channel);
        removeEventItems(type);
        if ("rain".equals(type)) {
            World world = ffa.center() == null ? null : ffa.center().getWorld();
            if (world != null) {
                world.setStorm(false);
                world.setWeatherDuration(0);
                world.setThundering(false);
                world.setThunderDuration(0);
                world.setClearWeatherDuration(20);
            }
        }
        if ("mp_fever".equals(type)) {
            plugin.data().set("ffa.events.mp-fever-until", null);
            plugin.data().set("ffa.events.em-fever-until", null);
            plugin.saveData();
        }
        broadcast("§7" + eventDisplay(type) + " が終了しました。");
    }

    void applyActiveEventGear(Player player) {
        if (activeEvents.containsValue("one_shot_bow")) {
            giveOneShotBow(player);
        }
        if (activeEvents.containsValue("sky_spear")) {
            giveSkySpearGear(player);
        }
    }

    private void giveOneShotBow(Player player) {
        if (hasKind(player, "event_one_shot_bow")) {
            return;
        }
        ItemStack bow = FfaKit.kitItem(plugin, FfaKit.BOW, "event_one_shot_bow", Material.BOW, "§c一撃必殺弓", 1,
                Map.of(Enchantment.INFINITY, 1));
        ItemStack arrow = FfaKit.kitItem(plugin, FfaKit.BOW, "event_one_shot_arrow", Material.ARROW, "§c一撃必殺の矢", 1, Map.of());
        tagOwner(bow, player.getUniqueId());
        tagOwner(arrow, player.getUniqueId());
        player.getInventory().addItem(bow, arrow);
        player.updateInventory();
    }

    private void giveSkySpearGear(Player player) {
        UUID uuid = player.getUniqueId();
        if (!skyChestplates.containsKey(uuid)) {
            ItemStack chestplate = player.getInventory().getChestplate();
            skyChestplates.put(uuid, chestplate == null ? null : chestplate.clone());
        }
        ItemStack elytra = FfaKit.kitItem(plugin, FfaKit.SPEAR, "event_sky_elytra", Material.ELYTRA, "§d天空槍撃戦のエリトラ", 1, Map.of());
        ItemStack spear = FfaKit.kitItem(plugin, FfaKit.SPEAR, "event_sky_spear", Material.IRON_SPEAR, "§d天空槍撃戦の鉄槍", 1, Map.of());
        ItemStack rockets = FfaKit.kitItem(plugin, FfaKit.SPEAR, "event_sky_firework", Material.FIREWORK_ROCKET, "§d天空槍撃戦のロケット花火", 12, Map.of());
        tagOwner(elytra, uuid);
        tagOwner(spear, uuid);
        tagOwner(rockets, uuid);
        player.getInventory().setChestplate(elytra);
        if (!hasKind(player, "event_sky_spear")) {
            player.getInventory().addItem(spear);
        }
        if (!hasKind(player, "event_sky_firework")) {
            player.getInventory().addItem(rockets);
        }
        player.updateInventory();
    }

    private void removeEventItems(String type) {
        if ("one_shot_bow".equals(type)) {
            for (Player player : ffaPlayers()) {
                removeKinds(player, Set.of("event_one_shot_bow", "event_one_shot_arrow"));
            }
            removeProjectiles("event_one_shot_arrow");
            return;
        }
        if ("sky_spear".equals(type)) {
            for (Player player : ffaPlayers()) {
                removeKinds(player, Set.of("event_sky_elytra", "event_sky_spear", "event_sky_firework"));
                ItemStack chestplate = skyChestplates.remove(player.getUniqueId());
                player.getInventory().setChestplate(chestplate == null ? null : chestplate.clone());
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, true));
                player.updateInventory();
            }
            skyChestplates.clear();
            removeProjectiles("event_sky_spear");
        }
    }

    private void removeProjectiles(String kind) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (kind.equals(entity.getPersistentDataContainer().get(projectileKindKey, PersistentDataType.STRING))) {
                    entity.remove();
                }
            }
        }
    }

    private boolean hasKind(Player player, String kind) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (kind.equals(itemKind(item))) {
                return true;
            }
        }
        return kind.equals(itemKind(player.getInventory().getChestplate()))
                || kind.equals(itemKind(player.getInventory().getItemInOffHand()));
    }

    private void removeKinds(Player player, Set<String> kinds) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (kinds.contains(itemKind(contents[i]))) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (kinds.contains(itemKind(armor[i]))) {
                armor[i] = null;
            }
        }
        player.getInventory().setArmorContents(armor);
        if (kinds.contains(itemKind(player.getInventory().getItemInOffHand()))) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void tagOwner(ItemStack item, UUID owner) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ffaOwnerKey, PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
    }

    private String itemKind(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return "";
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(ffaItemKindKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private void tagFieldItem(Item item, String type, String eventType) {
        UUID id = UUID.randomUUID();
        item.getPersistentDataContainer().set(fieldKey, PersistentDataType.BOOLEAN, true);
        item.getPersistentDataContainer().set(fieldTypeKey, PersistentDataType.STRING, type);
        item.getPersistentDataContainer().set(fieldIdKey, PersistentDataType.STRING, id.toString());
        if (eventType != null) {
            item.getPersistentDataContainer().set(fieldEventKey, PersistentDataType.STRING, eventType);
        }
        item.setGlowing(true);
        item.setCanMobPickup(false);
        item.setUnlimitedLifetime(false);
        fieldItems.add(item.getUniqueId());
    }

    private boolean isFieldItem(Item item) {
        return Boolean.TRUE.equals(item.getPersistentDataContainer().get(fieldKey, PersistentDataType.BOOLEAN));
    }

    private void scheduleRemove(Item item, long seconds) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            fieldItems.remove(item.getUniqueId());
            if (item.isValid()) {
                item.remove();
            }
        }, Math.max(1L, seconds) * 20L);
    }

    private void removeFieldItems() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && fieldItems.contains(item.getUniqueId())) {
                    item.remove();
                }
            }
        }
        fieldItems.clear();
    }

    private Location randomSpawnPoint() {
        List<Location> points = plugin.getConfig().getStringList("ffa.field-items.spawnpoints").stream()
                .map(this::deserialize)
                .filter(location -> location != null && location.getWorld() != null)
                .toList();
        if (!points.isEmpty()) {
            return points.get(ThreadLocalRandom.current().nextInt(points.size()));
        }
        Location center = ffa.center();
        return center == null ? null : center.clone().add(ThreadLocalRandom.current().nextInt(-8, 9), 1, ThreadLocalRandom.current().nextInt(-8, 9));
    }

    private List<Player> ffaPlayers() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ffa.isPlaying(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private int maxLoot(int players) {
        if (players >= 9) {
            return 6;
        }
        if (players >= 5) {
            return 4;
        }
        return 2;
    }

    private long[] lootInterval(int players) {
        if (players >= 9) {
            return new long[]{20_000L, 40_000L};
        }
        if (players >= 5) {
            return new long[]{30_000L, 50_000L};
        }
        return new long[]{45_000L, 70_000L};
    }

    private int countLootItems() {
        return (int) fieldItems.stream().map(this::entityById).filter(entity -> entity instanceof Item item && item.getPersistentDataContainer().get(fieldEventKey, PersistentDataType.STRING) == null).count();
    }

    private int countEventItems() {
        return (int) fieldItems.stream().map(this::entityById).filter(entity -> entity instanceof Item item && item.getPersistentDataContainer().get(fieldEventKey, PersistentDataType.STRING) != null).count();
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

    private ItemStack lootItem(String rarity) {
        return switch (rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT)) {
            case "legendary" -> enchanted(Material.NETHERITE_SWORD, "§6レジェンダリー武器", Map.of("sharpness", 1));
            case "epic" -> enchanted(Material.DIAMOND_SWORD, "§5エピック武器", Map.of("sharpness", 1));
            case "rare" -> named(Material.GOLDEN_APPLE, "§bレア金リンゴ");
            case "uncommon" -> named(Material.ENDER_PEARL, "§aアンコモン エンダーパール");
            default -> new ItemStack(Material.COOKED_BEEF, 2);
        };
    }

    private List<ItemStack> lootItems(String rarity) {
        String type = rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "legendary" -> legendaryLoot();
            case "epic" -> epicLoot();
            case "rare" -> rareLoot();
            case "uncommon" -> uncommonLoot();
            default -> commonLoot();
        };
    }

    private List<ItemStack> commonLoot() {
        return switch (ThreadLocalRandom.current().nextInt(8)) {
            case 0 -> List.of(new ItemStack(Material.COOKED_BEEF, 2));
            case 1 -> List.of(new ItemStack(Material.BREAD, 4));
            case 2 -> List.of(new ItemStack(Material.ARROW, 6));
            case 3 -> List.of(new ItemStack(Material.SNOWBALL, 8));
            case 4 -> List.of(new ItemStack(Material.WIND_CHARGE, 1));
            case 5 -> List.of(fieldPotion("heal", 1, 1, "§d即時回復ポーション"));
            case 6 -> List.of(enchanted(Material.STONE_SWORD, "§f石の剣", Map.of("sharpness", 1)));
            default -> List.of(enchanted(randomArmor("leather"), "§f革防具", Map.of("protection", 2)));
        };
    }

    private List<ItemStack> uncommonLoot() {
        return switch (ThreadLocalRandom.current().nextInt(11)) {
            case 0 -> List.of(new ItemStack(Material.ENDER_PEARL, 1));
            case 1 -> List.of(new ItemStack(Material.GOLDEN_CARROT, 3));
            case 2 -> List.of(fieldPotion("speed", 1, 20, "§a移動速度上昇ポーション"));
            case 3 -> List.of(fieldPotion("heal", 1, 1, "§d即時回復ポーション"), fieldPotion("heal", 1, 1, "§d即時回復ポーション"));
            case 4 -> List.of(enchanted(Material.IRON_SWORD, "§a鉄の剣", Map.of("sharpness", 1)));
            case 5 -> List.of(enchanted(Material.BOW, "§a強化弓", Map.of("power", 1)), new ItemStack(Material.ARROW, 5));
            case 6 -> List.of(new ItemStack(Material.CROSSBOW, 1), new ItemStack(Material.ARROW, 3));
            case 7 -> List.of(named(Material.SHIELD, "§a耐久制限付きの盾"));
            case 8 -> List.of(named(Material.TURTLE_HELMET, "§a亀の甲羅"));
            case 9 -> List.of(enchanted(randomArmor("iron"), "§a鉄防具", Map.of("protection", 1)));
            default -> List.of(enchanted(randomArmor("leather"), "§a革防具", Map.of("protection", 3)));
        };
    }

    private List<ItemStack> rareLoot() {
        return switch (ThreadLocalRandom.current().nextInt(11)) {
            case 0 -> List.of(named(Material.DIAMOND_SWORD, "§bダイヤモンドの剣"));
            case 1 -> List.of(named(Material.DIAMOND_AXE, "§bダイヤモンドの斧"));
            case 2 -> List.of(enchanted(Material.BOW, "§b強化弓", Map.of("power", 2)), new ItemStack(Material.ARROW, 6));
            case 3 -> List.of(enchanted(Material.CROSSBOW, "§b高速装填クロスボウ", Map.of("quick_charge", 1)), new ItemStack(Material.ARROW, 4));
            case 4 -> List.of(enchanted(material("IRON_SPEAR", Material.TRIDENT), "§b鉄の槍", Map.of("lunge", 1)));
            case 5 -> List.of(enchanted(Material.TRIDENT, "§bトライデント", Map.of("loyalty", 1)));
            case 6 -> List.of(enchanted(randomArmor("iron"), "§b鉄防具", Map.of("protection", 2)));
            case 7 -> List.of(named(Material.DIAMOND_BOOTS, "§bダイヤモンドのブーツ"));
            case 8 -> List.of(new ItemStack(Material.GOLDEN_APPLE, 1));
            case 9 -> List.of(fieldPotion("strength", 1, 20, "§b攻撃力上昇ポーション"));
            default -> List.of(fieldPotion("regeneration", 1, 20, "§b再生能力ポーション"));
        };
    }

    private List<ItemStack> epicLoot() {
        return switch (ThreadLocalRandom.current().nextInt(8)) {
            case 0 -> List.of(enchanted(Material.DIAMOND_SWORD, "§5ダイヤモンドの剣", Map.of("sharpness", 1)));
            case 1 -> List.of(enchanted(material("DIAMOND_SPEAR", material("IRON_SPEAR", Material.TRIDENT)), "§5ダイヤモンドの槍", Map.of("lunge", 1)));
            case 2 -> List.of(enchanted(Material.BOW, "§5強化弓", Map.of("power", 3)), new ItemStack(Material.ARROW, 6));
            case 3 -> List.of(enchanted(Material.MACE, "§5メイス", Map.of("density", 1)), new ItemStack(Material.WIND_CHARGE, 2));
            case 4 -> List.of(enchanted(Material.TRIDENT, "§5トライデント", Map.of("loyalty", 3)));
            case 5 -> List.of(named(Material.DIAMOND_CHESTPLATE, "§5ダイヤモンドのチェストプレート"));
            case 6 -> List.of(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            default -> List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        };
    }

    private List<ItemStack> legendaryLoot() {
        return switch (ThreadLocalRandom.current().nextInt(6)) {
            case 0 -> List.of(enchanted(Material.NETHERITE_SWORD, "§6ネザライトの剣", Map.of("sharpness", 1)));
            case 1 -> List.of(enchanted(material("NETHERITE_SPEAR", material("DIAMOND_SPEAR", material("IRON_SPEAR", Material.TRIDENT))), "§6ネザライトの槍", Map.of("lunge", 2)));
            case 2 -> List.of(enchanted(Material.DIAMOND_CHESTPLATE, "§6ダイヤモンドのチェストプレート", Map.of("protection", 2)));
            case 3 -> List.of(enchanted(Material.MACE, "§6メイス", Map.of("density", 2)), new ItemStack(Material.WIND_CHARGE, 3));
            case 4 -> List.of(enchanted(Material.BOW, "§6強化弓", Map.of("power", 4)), new ItemStack(Material.ARROW, 8));
            default -> List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
        };
    }

    private ItemStack fieldPotion(String effect, int level, int seconds, String name) {
        ItemStack item = FfaKit.potion(null, FfaKit.SWORD, Material.SPLASH_POTION, effect, level, seconds, 1, name);
        return item == null ? new ItemStack(Material.SPLASH_POTION) : item;
    }

    private Material randomArmor(String tier) {
        String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        return material(tier.toUpperCase(Locale.ROOT) + "_" + slots[ThreadLocalRandom.current().nextInt(slots.length)], Material.LEATHER_BOOTS);
    }

    private Material material(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack enchanted(Material material, String name, Map<String, Integer> enchants) {
        ItemStack item = named(material, name);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
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
        return weighted(Map.of(
                "common", plugin.getConfig().getInt("ffa.field-items.rarities.common", 50),
                "uncommon", plugin.getConfig().getInt("ffa.field-items.rarities.uncommon", 30),
                "rare", plugin.getConfig().getInt("ffa.field-items.rarities.rare", 14),
                "epic", plugin.getConfig().getInt("ffa.field-items.rarities.epic", 5),
                "legendary", plugin.getConfig().getInt("ffa.field-items.rarities.legendary", 1)), "common");
    }

    private String randomEvent() {
        Map<String, Integer> weights = new HashMap<>();
        for (String type : eventTypes()) {
            weights.put(type, plugin.getConfig().getInt("ffa.field-items.events." + type + ".weight", defaultEventWeight(type)));
        }
        return weighted(weights, "heal_self");
    }

    private String weighted(Map<String, Integer> weights, String fallback) {
        int total = weights.values().stream().mapToInt(value -> Math.max(0, value)).sum();
        if (total <= 0) {
            return fallback;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cursor += Math.max(0, entry.getValue());
            if (roll < cursor) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    private long eventIntervalMillis() {
        long min = Math.max(1L, plugin.getConfig().getLong("ffa.field-items.event-interval-min-seconds", 150L));
        long max = Math.max(min, plugin.getConfig().getLong("ffa.field-items.event-interval-max-seconds", 240L));
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
        return List.of("rain", "snow", "blizzard", "berserk", "speed", "iron_body", "overdrive", "one_shot_bow", "mp_fever", "sky_spear", "time_shift", "heal_self", "heal_all");
    }

    private String normalizeEvent(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        if ("em_fever".equals(normalized)) {
            return "mp_fever";
        }
        return eventTypes().contains(normalized) ? normalized : randomEvent();
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
        return Math.max(1L, plugin.getConfig().getLong("ffa.field-items.events." + type + ".duration-seconds", fallback));
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
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
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
        if (world == null) {
            return null;
        }
        return new Location(world, parseDouble(parts[1]), parseDouble(parts[2]), parseDouble(parts[3]));
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0D;
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
        for (Player player : ffaPlayers()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.2F);
        }
    }

    private void ensureDefaults() {
        FileConfiguration config = plugin.getConfig();
        setIfMissing(config, "ffa.field-items.enabled", true);
        setIfMissing(config, "ffa.field-items.min-players", 2);
        setIfMissing(config, "ffa.field-items.event-despawn-seconds", 60);
        setIfMissing(config, "ffa.field-items.loot-despawn-seconds", 90);
        setIfMissing(config, "ffa.field-items.spawnpoints", List.of());
        plugin.saveConfig();
    }

    private void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }
}
