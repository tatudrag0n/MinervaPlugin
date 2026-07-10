package org.server.minerva;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
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

final class FfaManager {
    private static final String CENTER_MISSING = "§cFFA中央地点が設定されていません。管理者に /mva ffa setcenter を実行してもらってください。";
    private static final String KIT_SELECTOR_TITLE = "FFAキット選択";

    private final Minerva plugin;
    private final FfaConfig config;
    private final FfaStatsManager stats;
    private final FfaKitStandManager stands;
    private final NamespacedKey selectorKitKey;
    private final Map<UUID, FfaSession> sessions = new HashMap<>();

    FfaManager(Minerva plugin) {
        this.plugin = plugin;
        this.config = new FfaConfig(plugin);
        this.stats = new FfaStatsManager(plugin);
        this.stands = new FfaKitStandManager(plugin, config);
        this.selectorKitKey = new NamespacedKey(plugin, "ffa_selector_kit");
    }

    void load() {
        config.ensureDefaults();
        stats.load();
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

    boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
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
                sender.sendMessage("§aFFA設定を再読み込みしました。");
            }
            default -> sender.sendMessage("§e/mva ffa leave|stats|setcenter|setkits|createkits|removekits|reload");
        }
        return true;
    }

    List<String> tabComplete(String[] args, CommandSender sender) {
        if (args.length == 2) {
            if (hasAdmin(sender)) {
                return List.of("leave", "stats", "setcenter", "setkits", "createkits", "removekits", "reload");
            }
            return List.of("leave", "stats");
        }
        return List.of();
    }

    void openKitSelector(Player player) {
        int size = Math.max(9, Math.min(54, ((FfaKit.values().length + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(player, size, Component.text(KIT_SELECTOR_TITLE));
        FfaKit selected = stands.selectedKit();
        int slot = 0;
        for (FfaKit kit : FfaKit.values()) {
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
        stands.applySelectedKit(kit);
        player.closeInventory();
        join(player, kit);
        return true;
    }

    void join(Player player, FfaKit kit) {
        if (!config.enabled()) {
            player.sendMessage("§cFFAは現在無効です。");
            return;
        }
        Location arena = config.center();
        if (arena == null) {
            player.sendMessage(CENTER_MISSING);
            return;
        }
        if (kit == FfaKit.SPEAR && kit.spearMaterial(config, plugin, true) == null) {
            player.sendMessage("§c槍アイテムが現在の Paper API で見つかりません。Paper API / Minecraft バージョンを確認してください。");
            return;
        }
        sessions.computeIfAbsent(player.getUniqueId(), ignored -> new FfaSession(kit, PlayerState.capture(player)));
        FfaSession session = sessions.get(player.getUniqueId());
        session.kit = kit;
        prepareForFight(player, kit);
        updateScoreboard(player);
        player.teleport(arena);
        player.sendMessage("§aFFAに参加しました。キット: §f" + stripColor(kit.displayName(config)));
    }

    void leave(Player player, boolean notify) {
        FfaSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (notify) {
                player.sendMessage("§eFFAには参加していません。");
            }
            return;
        }
        clearTemporaryState(player);
        session.state.restore(player);
        if (notify) {
            player.sendMessage("§eFFAから退出しました。");
        }
    }

    void handleDeath(Player victim, Player killer) {
        FfaSession victimSession = sessions.get(victim.getUniqueId());
        if (victimSession == null) {
            return;
        }
        stats.recordDeath(victim);
        if (killer != null && isPlaying(killer) && !killer.getUniqueId().equals(victim.getUniqueId())) {
            stats.recordKill(killer);
            updateScoreboard(killer);
            killer.sendMessage("§a" + victim.getName() + " を倒しました！ 現在の連続キル: " + stats.currentStreak(killer.getUniqueId()));
        }
        victim.sendMessage("§cあなたは倒されました。FFAから退出します。");
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
            sessions.remove(player.getUniqueId());
            clearTemporaryState(player);
            session.state.restore(player, respawn);
            player.sendMessage("§eFFAから退出しました。再参加する場合はキットを選択してください。");
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
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        kit.applyTo(inventory, config, plugin);
        player.updateInventory();
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

    private static final class PlayerState {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final float exp;
        private final int level;
        private final int totalExperience;
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
            return new PlayerState(
                    cloneItems(inventory.getStorageContents()),
                    cloneItems(inventory.getArmorContents()),
                    inventory.getItemInOffHand().clone(),
                    player.getExp(),
                    player.getLevel(),
                    player.getTotalExperience(),
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
