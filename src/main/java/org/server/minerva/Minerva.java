package org.server.minerva;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.block.Shelf;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Minerva extends JavaPlugin implements Listener, TabExecutor {

    private static final String FRIEND_UI_TITLE = ChatColor.DARK_AQUA + "Minerva Friends";
    private static final String FRIEND_STATUS_UI_TITLE = ChatColor.DARK_GREEN + "Minerva Status";
    private static final String TELEPORT_UI_TITLE = ChatColor.DARK_PURPLE + "Minerva Teleporter";
    private static final String MERCHANT_UI_TITLE = ChatColor.GOLD + "Minerva Merchant";
    private static final long MERCHANT_REROLL_MILLIS = 60L * 60L * 1000L;
    private static final long MERCHANT_TRANSACTION_COOLDOWN_MILLIS = 150L;
    private static final long JUMP_PAD_COOLDOWN_MILLIS = 650L;
    private static final long JUMP_PAD_FALL_PROTECTION_MILLIS = 60L * 1000L;
    private static final int MAX_JUMP_PAD_POWER = 100;
    private static final int MAX_EMERALDS = 2_000_000_000;
    private static final int MAX_FRIEND_REQUESTS = 100;
    private static final int MAX_OFFLINE_MESSAGES = 50;
    private static final int MAX_FRIEND_MESSAGE_LENGTH = 256;
    private static final int MAX_FRIEND_FILTER_LENGTH = 32;
    private static final int MAX_SHOP_STACKS_PER_CLICK = 64;
    private static final int BARREL_SHOP_OFFER_SLOTS = 27;
    private static final int SHELF_SHOP_OFFER_SLOTS = 3;
    private static final int MOB_REWARD_FARM_THRESHOLD_PER_HOUR = 100;
    private static final Pattern SAFE_CONFIG_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
        private static final Map<String, TitleDefinition> TITLE_DEFINITIONS = Map.ofEntries(
            Map.entry("狙撃手", new TitleDefinition(Material.BOW, List.of("adventure/sniper_duel", "adventure/bullseye"))),
            Map.entry("狩人", new TitleDefinition(Material.CROSSBOW, List.of("adventure/two_birds_one_arrow", "adventure/arbalistic"))),
            Map.entry("重戦士", new TitleDefinition(Material.MACE, List.of("adventure/overoverkill|adventure/over_overkill", "adventure/blowback|adventure/reverse_wind"))),
            Map.entry("槍使い", new TitleDefinition(Material.TRIDENT, List.of("adventure/mob_kebab|adventure/throw_trident"))),
            Map.entry("海の戦士", new TitleDefinition(Material.PRISMARINE_SHARD, List.of("adventure/very_very_frightening"))),
            Map.entry("猫好き", new TitleDefinition(Material.COD, List.of("husbandry/complete_catalogue"))),
            Map.entry("犬好き", new TitleDefinition(Material.BONE, List.of("husbandry/whole_pack|husbandry/tame_an_animal"))),
            Map.entry("生物観察の鬼", new TitleDefinition(Material.FROGSPAWN, List.of("husbandry/froglights"))),
            Map.entry("友好的", new TitleDefinition(Material.CAKE, List.of("husbandry/allay_deliver_cake_to_note_block"))),
            Map.entry("猪突猛進", new TitleDefinition(Material.GOAT_HORN, List.of("husbandry/ride_a_boat_with_a_goat"))),
            Map.entry("癒し系", new TitleDefinition(Material.AXOLOTL_BUCKET, List.of("husbandry/kill_axolotl_target"))),
            Map.entry("歴史マニア", new TitleDefinition(Material.PITCHER_POD, List.of("husbandry/plant_any_sniffer_seed"))),
            Map.entry("闇の商人", new TitleDefinition(Material.LEAD, List.of("nether/uneasy_alliance"))),
            Map.entry("全能", new TitleDefinition(Material.NETHER_STAR, List.of("nether/all_effects"))),
            Map.entry("魔導師", new TitleDefinition(Material.BREWING_STAND, List.of("adventure/totem_of_undying", "nether/all_potions"))),
            Map.entry("冒険家", new TitleDefinition(Material.MAP, List.of("adventure/adventuring_time", "adventure/this_way_goes_on_forever|nether/ride_strider_in_overworld_lava|nether/fast_travel"))),
            Map.entry("農家", new TitleDefinition(Material.DIAMOND_HOE, List.of("husbandry/obtain_netherite_hoe", "husbandry/bred_all_animals"))),
            Map.entry("料理人", new TitleDefinition(Material.COOKED_BEEF, List.of("husbandry/balanced_diet", "nether/all_potions"))),
            Map.entry("英雄", new TitleDefinition(Material.DIAMOND_SWORD, List.of("adventure/kill_all_mobs"))),
            Map.entry("鍛冶師", new TitleDefinition(Material.SMITHING_TABLE, List.of("adventure/trim_with_all_exclusive_armor_patterns|adventure/trim_with_all_armor_patterns"))),
            Map.entry("黒き鎧", new TitleDefinition(Material.NETHERITE_HELMET, List.of("nether/netherite_armor"))),
            Map.entry("鉄は熱いうちに掘れ", new TitleDefinition(Material.IRON_PICKAXE, List.of("story/iron_tools"))),
            Map.entry("ダイヤの原石", new TitleDefinition(Material.DIAMOND, List.of("story/mine_diamond"))),
            Map.entry("まだ舞える", new TitleDefinition(Material.TOTEM_OF_UNDYING, List.of("adventure/totem_of_undying"))),
            Map.entry("村公認", new TitleDefinition(Material.EMERALD, List.of("adventure/hero_of_the_village"))),
            Map.entry("ハチ合わせ職人", new TitleDefinition(Material.HONEYCOMB, List.of("husbandry/safely_harvest_honey", "husbandry/silk_touch_nest"))),
            Map.entry("粉雪ソムリエ", new TitleDefinition(Material.LEATHER_BOOTS, List.of("adventure/walk_on_powder_snow_with_leather_boots"))),
            Map.entry("しーっ、セーフ", new TitleDefinition(Material.ECHO_SHARD, List.of("adventure/avoid_vibration"))),
            Map.entry("雷様のコンセント", new TitleDefinition(Material.LIGHTNING_ROD, List.of("adventure/lightning_rod_with_villager_no_fire"))),
            Map.entry("古代の落とし物", new TitleDefinition(Material.ANCIENT_DEBRIS, List.of("nether/obtain_ancient_debris"))),
            Map.entry("エンドロール係", new TitleDefinition(Material.DRAGON_HEAD, List.of("end/kill_dragon"))),
            Map.entry("花火で通勤", new TitleDefinition(Material.FIREWORK_ROCKET, List.of("end/elytra"))),
            Map.entry("照明係長", new TitleDefinition(Material.BEACON, List.of("nether/create_full_beacon"))),
            Map.entry("全知", new TitleDefinition(Material.KNOWLEDGE_BOOK, List.of())));
    private static final Set<Material> MERCHANT_EXCLUDED_ITEMS = Set.of(
            Material.AIR,
            Material.BARRIER,
            Material.BEDROCK,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.LIGHT,
            Material.DEBUG_STICK,
            Material.KNOWLEDGE_BOOK,
            Material.SPAWNER,
            Material.DRAGON_EGG);
    private static final Map<String, Integer> MOB_KILL_REWARDS = Map.ofEntries(
            Map.entry("BEE", 5),
            Map.entry("BLAZE", 25),
            Map.entry("BOGGED", 20),
            Map.entry("BREEZE", 25),
            Map.entry("CAVE_SPIDER", 15),
            Map.entry("CREAKING", 30),
            Map.entry("CREEPER", 12),
            Map.entry("DOLPHIN", 5),
            Map.entry("DROWNED", 10),
            Map.entry("ELDER_GUARDIAN", 80),
            Map.entry("ENDER_DRAGON", 900),
            Map.entry("ENDERMAN", 25),
            Map.entry("ENDERMITE", 4),
            Map.entry("EVOKER", 40),
            Map.entry("GHAST", 22),
            Map.entry("GOAT", 5),
            Map.entry("GUARDIAN", 18),
            Map.entry("HOGLIN", 25),
            Map.entry("HUSK", 10),
            Map.entry("LLAMA", 5),
            Map.entry("MAGMA_CUBE", 8),
            Map.entry("NAUTILUS", 40),
            Map.entry("PHANTOM", 15),
            Map.entry("PIGLIN", 15),
            Map.entry("PIGLIN_BRUTE", 45),
            Map.entry("PILLAGER", 20),
            Map.entry("POLAR_BEAR", 5),
            Map.entry("RAVAGER", 50),
            Map.entry("SHULKER", 35),
            Map.entry("SILVERFISH", 4),
            Map.entry("SKELETON", 10),
            Map.entry("SLIME", 6),
            Map.entry("SPIDER", 10),
            Map.entry("STRAY", 15),
            Map.entry("SULFUR_CUBE", 25),
            Map.entry("TRADER_LLAMA", 5),
            Map.entry("VEX", 30),
            Map.entry("VINDICATOR", 45),
            Map.entry("WARDEN", 250),
            Map.entry("WITCH", 25),
            Map.entry("WITHER", 650),
            Map.entry("WITHER_SKELETON", 30),
            Map.entry("ZOGLIN", 40),
            Map.entry("ZOMBIE", 10),
            Map.entry("ZOMBIE_VILLAGER", 10),
            Map.entry("ZOMBIFIED_PIGLIN", 8));

    private NamespacedKey minervaItemKey;
    private NamespacedKey merchantKey;
    private NamespacedKey merchantSpawnKey;
    private NamespacedKey merchantTradedKey;
    private NamespacedKey merchantTypeKey;
    private NamespacedKey merchantOfferKey;
    private NamespacedKey merchantOfferPriceKey;
    private NamespacedKey merchantOfferMaterialKey;
    private NamespacedKey merchantOfferAmountKey;
    private NamespacedKey merchantOfferMerchantKey;
    private NamespacedKey merchantOfferActionKey;
    private NamespacedKey merchantOfferRarityKey;
    private NamespacedKey barrelOfferPriceKey;
    private NamespacedKey barrelOfferRarityKey;
    private NamespacedKey ffaEntityKindKey;
    private NamespacedKey reincarnationStarKey;
    private NamespacedKey uiActionKey;
    private NamespacedKey uiTargetKey;
    private File dataFile;
    private FileConfiguration data;
    private final EconomyPriceTable economyPriceTable = new EconomyPriceTable(this);
    private final QuestService questService = new QuestService(this);
    private final ChunkProtectionFeature chunkProtectionFeature = new ChunkProtectionFeature(this);
    private final ProtectionService protectionService = new ProtectionService(this, chunkProtectionFeature);
    private final ServerPortalFeature serverPortalFeature = new ServerPortalFeature(this);
    private final CompassFeature compassFeature = new CompassFeature(this);
    private final WorldRulesFeature worldRulesFeature = new WorldRulesFeature(this);
    private final UtilityItemsFeature utilityItemsFeature = new UtilityItemsFeature(this);
    private final TextDisplayFeature textDisplayFeature = new TextDisplayFeature(this);
    private final AuctionFeature auctionFeature = new AuctionFeature(this, economyPriceTable);
    private final DiscordAuthManager discordAuthManager = new DiscordAuthManager(this);
    private final DiscordAuthListener discordAuthListener = new DiscordAuthListener(discordAuthManager);
    private final StructureManager structureManager = new StructureManager(this);
    private final ProposalManager proposalManager = new ProposalManager(this);
    private final QuestProgressListener questProgressListener = new QuestProgressListener(this, questService);
    private final ProtectedInteractionListener protectedInteractionListener = new ProtectedInteractionListener(this, protectionService);
    private final FfaManager ffaManager = new FfaManager(this);
    private final FfaListener ffaListener = new FfaListener(this, ffaManager);
    private final SlotMachineManager slotMachineManager = new SlotMachineManager(this);
    private final Random random = new Random();
    private final Map<String, Integer> shopSalePrices = new HashMap<>();
    private final Map<String, Integer> shopBuyPrices = new HashMap<>();
    private final Map<String, Integer> merchantBuyWeights = new HashMap<>();
    private final Map<String, Integer> merchantSellWeights = new HashMap<>();
    private final Map<String, BarrelShopConfig> barrelShopConfigs = new HashMap<>();
    private final Map<UUID, String> friendSearchFilters = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingFriendSearch = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingFriendChatInput = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeFriendChatTarget = new ConcurrentHashMap<>();
    private final Map<UUID, String> friendChatDrafts = new ConcurrentHashMap<>();
    private final Set<UUID> merchantTransactions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastMerchantTransaction = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeMerchantViews = new ConcurrentHashMap<>();
    private final Map<UUID, String> temporaryActionBarMessages = new ConcurrentHashMap<>();
    private final Set<UUID> activeTutorials = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> temporaryActionBarUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, KillRewardWindow>> mobRewardWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastJumpPadUse = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jumpPadFallProtectionUntil = new ConcurrentHashMap<>();
    // Auto-shutdown: schedule a server stop when no players are online
    private BukkitTask scheduledShutdownTask;
    private final Object shutdownLock = new Object();

    @Override
    public void onEnable() {
        minervaItemKey = new NamespacedKey(this, "item");
        merchantKey = new NamespacedKey(this, "merchant");
        merchantSpawnKey = new NamespacedKey(this, "merchant_spawned_at");
        merchantTradedKey = new NamespacedKey(this, "merchant_traded");
        merchantTypeKey = new NamespacedKey(this, "merchant_type");
        merchantOfferKey = new NamespacedKey(this, "merchant_offer");
        merchantOfferPriceKey = new NamespacedKey(this, "merchant_offer_price");
        merchantOfferMaterialKey = new NamespacedKey(this, "merchant_offer_material");
        merchantOfferAmountKey = new NamespacedKey(this, "merchant_offer_amount");
        merchantOfferMerchantKey = new NamespacedKey(this, "merchant_offer_merchant");
        merchantOfferActionKey = new NamespacedKey(this, "merchant_offer_action");
        merchantOfferRarityKey = new NamespacedKey(this, "merchant_offer_rarity");
        barrelOfferPriceKey = new NamespacedKey(this, "barrel_offer_price");
        barrelOfferRarityKey = new NamespacedKey(this, "barrel_offer_rarity");
        ffaEntityKindKey = new NamespacedKey(this, "ffa_entity_kind");
        reincarnationStarKey = new NamespacedKey(this, "reincarnation_star");
        uiActionKey = new NamespacedKey(this, "ui_action");
        uiTargetKey = new NamespacedKey(this, "ui_target");

        saveDefaultConfig();
        runStartupStep("migrate barrel shop offer slots", this::migrateBarrelShopOfferSlots);
        runStartupStep("migrate hub location", this::migrateDefaultHubLocation);
        runStartupStep("migrate minigame location", this::migrateDefaultMinigameLocation);
        runStartupStep("configure survival spawn location", this::configureSurvivalSpawnLocation);
        runStartupStep("normalize spawn locations to origin", this::normalizeSpawnLocationsToOrigin);
        runStartupStep("load economy price table", economyPriceTable::load);
        runStartupStep("load quest definitions", questService::load);
        runStartupStep("load shop prices", this::loadShopPrices);
        runStartupStep("apply economy price table", this::applyEconomyPriceTable);
        loadData();
        runStartupStep("sync shelf shop displays", this::syncShelfShopDisplays);
        runStartupStep("load Discord auth", discordAuthManager::load);
        runStartupStep("load structures", structureManager::load);
        runStartupStep("load proposals", proposalManager::load);
        runStartupStep("load text displays", textDisplayFeature::load);
        runStartupStep("load FFA", ffaManager::load);
        runStartupStep("register Minerva events", () -> Bukkit.getPluginManager().registerEvents(this, this));
        runStartupStep("register chunk protection events", () -> Bukkit.getPluginManager().registerEvents(chunkProtectionFeature, this));
        runStartupStep("register protected interaction events", () -> Bukkit.getPluginManager().registerEvents(protectedInteractionListener, this));
        runStartupStep("register quest progress events", () -> Bukkit.getPluginManager().registerEvents(questProgressListener, this));
        runStartupStep("register auction events", () -> Bukkit.getPluginManager().registerEvents(auctionFeature, this));
        runStartupStep("register Discord auth events", () -> Bukkit.getPluginManager().registerEvents(discordAuthListener, this));
        runStartupStep("register structure events", () -> Bukkit.getPluginManager().registerEvents(structureManager, this));
        runStartupStep("register FFA events", () -> Bukkit.getPluginManager().registerEvents(ffaListener, this));
        runStartupStep("register text display events", () -> Bukkit.getPluginManager().registerEvents(textDisplayFeature, this));
        runStartupStep("register server portal events", () -> Bukkit.getPluginManager().registerEvents(serverPortalFeature, this));
        runStartupStep("register slot machine events", () -> Bukkit.getPluginManager().registerEvents(slotMachineManager, this));
        runStartupStep("register compass events", () -> Bukkit.getPluginManager().registerEvents(compassFeature, this));
        runStartupStep("register utility item events", () -> Bukkit.getPluginManager().registerEvents(utilityItemsFeature, this));
        registerCommand("minerva");
        registerCommand("mva");
        registerCommand("friend");
        registerCommand("status");
        registerCommand("tutorial");

        runStartupStep("apply world rules", worldRulesFeature::apply);
        runStartupStep("apply world spawn locations", this::applyWorldSpawnLocations);
        runStartupStep("normalize merchants", this::normalizeMerchants);
        Bukkit.getScheduler().runTaskTimer(this, this::grantPlaytimeRewards, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickMerchants, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickShelfShopActionBars, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, discordAuthManager::tickExternalUpdates, 20L * 30L, 20L * 30L);
        getLogger().info("Minerva has been enabled.");
    }

    private void runStartupStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Throwable e) {
            getLogger().severe("Startup step failed: " + name);
            e.printStackTrace();
        }
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command is missing from plugin.yml: " + name);
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        try {
            ffaManager.shutdown();
        } catch (Throwable e) {
            getLogger().severe("Failed to disable FFA cleanly.");
            e.printStackTrace();
        }
        try {
            textDisplayFeature.disable();
        } catch (Throwable e) {
            getLogger().severe("Failed to disable text displays cleanly.");
            e.printStackTrace();
        }
        saveData();
        getLogger().info("Minerva has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("auto-shutdown.enabled", false)) return;
        synchronized (shutdownLock) {
            if (scheduledShutdownTask != null) {
                scheduledShutdownTask.cancel();
                scheduledShutdownTask = null;
                getLogger().info("Cancelled scheduled auto-shutdown because a player joined.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("auto-shutdown.enabled", false)) return;
        // If no players remain, schedule a delayed shutdown
        if (!Bukkit.getOnlinePlayers().iterator().hasNext()) {
            int delay = Math.max(1, getConfig().getInt("auto-shutdown.delay-seconds", 60));
            synchronized (shutdownLock) {
                if (scheduledShutdownTask != null) {
                    scheduledShutdownTask.cancel();
                }
                scheduledShutdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                    synchronized (shutdownLock) {
                        if (!Bukkit.getOnlinePlayers().iterator().hasNext()) {
                            getLogger().info("No players online for " + delay + "s; shutting down server.");
                            Bukkit.shutdown();
                        } else {
                            getLogger().info("Players returned before auto-shutdown; aborting.");
                        }
                        scheduledShutdownTask = null;
                    }
                }, 20L * delay);
                getLogger().info("Scheduled auto-shutdown in " + delay + " seconds.");
            }
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadShopPrices() {
        shopSalePrices.clear();
        shopBuyPrices.clear();
        merchantBuyWeights.clear();
        merchantSellWeights.clear();
        barrelShopConfigs.clear();
        try (InputStream input = getResource("shop-prices.yml")) {
            if (input == null) {
                getLogger().severe("Bundled shop-prices.yml is missing. Falling back to legacy prices.");
                return;
            }
            YamlConfiguration prices = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection section = prices.getConfigurationSection("prices");
            if (section == null) {
                getLogger().severe("shop-prices.yml does not contain a prices section.");
                return;
            }
            for (String materialName : section.getKeys(false)) {
                List<Integer> values = section.getIntegerList(materialName);
                if (values.size() < 2) {
                    continue;
                }
                String key = materialName.toUpperCase(Locale.ROOT);
                shopSalePrices.put(key, Math.max(0, values.get(0)));
                shopBuyPrices.put(key, Math.max(0, values.get(1)));
            }
            loadWeightedPool(prices.getConfigurationSection("merchant.buy"), merchantBuyWeights);
            loadWeightedPool(prices.getConfigurationSection("merchant.sell"), merchantSellWeights);
            loadBarrelShopPool(prices.getConfigurationSection("barrel"));
            ConfigurationSection aliases = prices.getConfigurationSection("aliases");
            if (aliases != null) {
                for (String targetName : aliases.getKeys(false)) {
                    String sourceName = aliases.getString(targetName, "").toUpperCase(Locale.ROOT);
                    Integer salePrice = shopSalePrices.get(sourceName);
                    Integer buyPrice = shopBuyPrices.get(sourceName);
                    if (salePrice != null && buyPrice != null) {
                        String targetKey = targetName.toUpperCase(Locale.ROOT);
                        shopSalePrices.putIfAbsent(targetKey, salePrice);
                        shopBuyPrices.putIfAbsent(targetKey, buyPrice);
                        copyWeightAlias(merchantBuyWeights, sourceName, targetKey);
                        copyWeightAlias(merchantSellWeights, sourceName, targetKey);
                        BarrelShopConfig barrelConfig = barrelShopConfigs.get(sourceName);
                        if (barrelConfig != null) {
                            barrelShopConfigs.putIfAbsent(targetKey, barrelConfig);
                        }
                    }
                }
            }
            getLogger().info("Loaded " + shopSalePrices.size() + " shop prices, "
                    + merchantSellWeights.size() + " merchant sell weights, "
                    + merchantBuyWeights.size() + " merchant buy weights, and "
                    + barrelShopConfigs.size() + " barrel shop entries from the 26.2 price table.");
        } catch (IOException e) {
            getLogger().severe("Could not load shop-prices.yml: " + e.getMessage());
        }
    }

    private void loadWeightedPool(ConfigurationSection section, Map<String, Integer> target) {
        if (section == null) {
            return;
        }
        for (String materialName : section.getKeys(false)) {
            int weight = section.getInt(materialName + ".weight", 0);
            if (weight > 0) {
                target.put(materialName.toUpperCase(Locale.ROOT), weight);
            }
        }
    }

    private void loadBarrelShopPool(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String materialName : section.getKeys(false)) {
            int weight = section.getInt(materialName + ".weight", 0);
            if (weight <= 0) {
                continue;
            }
            String tier = section.getString(materialName + ".tier", "junk").toLowerCase(Locale.ROOT);
            barrelShopConfigs.put(materialName.toUpperCase(Locale.ROOT), new BarrelShopConfig(tier, weight));
        }
    }

    private void copyWeightAlias(Map<String, Integer> weights, String sourceName, String targetName) {
        Integer weight = weights.get(sourceName);
        if (weight != null) {
            weights.putIfAbsent(targetName, weight);
        }
    }

    private void applyEconomyPriceTable() {
        int applied = 0;
        for (EconomyPriceTable.Entry entry : economyPriceTable.entries()) {
            String key = entry.material().name();
            if (entry.priceEm() > 0) {
                shopSalePrices.put(key, entry.priceEm());
            }
            if (entry.sellEm() > 0) {
                shopBuyPrices.put(key, entry.sellEm());
            }
            if (entry.merchantBuyPool() && entry.merchantBuyWeight() > 0) {
                merchantBuyWeights.put(key, entry.merchantBuyWeight());
            }
            if (entry.merchantSellPool() && entry.merchantSellWeight() > 0) {
                merchantSellWeights.put(key, entry.merchantSellWeight());
            }
            if (entry.barrelShopPool() && entry.barrelShopWeight() > 0) {
                barrelShopConfigs.put(key, new BarrelShopConfig(entry.barrelTierKey(), entry.barrelShopWeight()));
            }
            applied++;
        }
        if (applied > 0) {
            getLogger().info("Applied " + applied + " economy price table entries over shop-prices.yml.");
        }
    }

    void saveData() {
        if (data == null || dataFile == null) {
            return;
        }
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().severe("Could not create data folder: " + parent.getAbsolutePath());
            return;
        }
        File tempFile = new File(parent == null ? new File(".") : parent, dataFile.getName() + ".tmp");
        try {
            data.save(tempFile);
            try {
                Files.move(tempFile.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
            if (tempFile.exists() && !tempFile.delete()) {
                getLogger().warning("Could not delete temporary data file: " + tempFile.getAbsolutePath());
            }
        }
    }

    FileConfiguration data() {
        return data;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        startPlayerSession(player);
        giveInitialItems(player);
        compassFeature.updateCompassTarget(player);
        applyPendingAdvancementReset(player);
        handleLoginReward(player);
        routeByWarningLevel(player);
        refreshPlayerName(player);
        Bukkit.getScheduler().runTaskLater(this, () -> syncAdvancementState(player), 20L);
        if (isFirstJoin(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> startTutorial(player, false), 40L);
        }
    }

    private void startPlayerSession(Player player) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        section.set("session-minutes", 0);
        section.set("session-playtime-rewards", 0);
        section.set("total-play-count", safeAdd(section.getInt("total-play-count", 0), 1));
        saveData();
    }

    private boolean isFirstJoin(Player player) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        return section.getInt("total-play-count", 0) == 1 && !section.getBoolean("tutorial.started", false);
    }

    private void startTutorial(Player player, boolean manual) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!activeTutorials.add(player.getUniqueId())) {
            if (manual) {
                player.sendMessage(ChatColor.YELLOW + "チュートリアルはすでに進行中です。");
            }
            return;
        }
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        section.set("tutorial.started", true);
        section.set("tutorial.last-started-at", System.currentTimeMillis());
        saveData();

        List<String> steps = List.of(
                ChatColor.GOLD + "MinerVaへようこそ。まずは初期アイテムを確認しましょう。",
                ChatColor.YELLOW + "MPバンドル" + ChatColor.GRAY + ": MP残高の確認とショップ購入に使います。拾ったエメラルドは自動でMPに収納されます。",
                ChatColor.AQUA + "中央広場コンパス" + ChatColor.GRAY + ": 中央広場の方向を指します。",
                ChatColor.LIGHT_PURPLE + "テレポーター" + ChatColor.GRAY + ": 右クリックでサーバー移動UIを開けます。",
                ChatColor.GOLD + "フレンドブック" + ChatColor.GRAY + ": 右クリック、または /friend でフレンドUIを開けます。",
                ChatColor.GREEN + "保護したい拠点は /mva protect でチャンク保護ビーコンを受け取り、設置してください。",
                ChatColor.GREEN + "チュートリアル完了です。もう一度見たい場合は /tutorial を実行してください。");
        player.sendMessage(ChatColor.GOLD + "=== MinerVa Tutorial ===");
        for (int i = 0; i < steps.size(); i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) {
                    activeTutorials.remove(player.getUniqueId());
                    return;
                }
                player.sendMessage(steps.get(index));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f + (index * 0.08f));
                if (index == steps.size() - 1) {
                    getPlayerSection(player.getUniqueId()).set("tutorial.completed", true);
                    getPlayerSection(player.getUniqueId()).set("tutorial.completed-at", System.currentTimeMillis());
                    saveData();
                    activeTutorials.remove(player.getUniqueId());
                }
            }, 20L * i);
        }
    }

    private void giveInitialItems(Player player) {
        utilityItemsFeature.giveInitialItems(player);
    }

    private boolean hasMinervaItem(Player player, String id) {
        return utilityItemsFeature.hasMinervaItem(player, id);
    }

    private ItemStack createShopWand() {
        return utilityItemsFeature.createShopWand();
    }

    private ItemStack createShopWand(ShopWandType type) {
        return utilityItemsFeature.createShopWand(type);
    }

    private ItemStack createJumpPadWand(int verticalPower, int horizontalPower) {
        return utilityItemsFeature.createJumpPadWand(verticalPower, horizontalPower);
    }

    ItemStack createChunkProtectionBeacon() {
        return utilityItemsFeature.createChunkProtectionBeacon();
    }

    boolean isMinervaItem(ItemStack item, String id) {
        return utilityItemsFeature.isMinervaItem(item, id);
    }

    boolean isShopWand(ItemStack item) {
        return utilityItemsFeature.isShopWand(item);
    }

    boolean isLegacyShopWand(ItemStack item) {
        return utilityItemsFeature.isLegacyShopWand(item);
    }

    private ShopWandType shopWandType(ItemStack item) {
        return utilityItemsFeature.getShopWandType(item);
    }

    private boolean isReincarnationStar(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(reincarnationStarKey, PersistentDataType.BOOLEAN));
    }

    private void consumeOne(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND && !serverPortalFeature.isServerWand(item)) {
            return;
        }
        Player player = event.getPlayer();

        if (compassFeature.handleCompassClick(event)) {
            return;
        }

        if (isShopWand(item)) {
            handleShopWandClick(event);
            return;
        }

        if (isMinervaItem(item, "jump_pad_wand")) {
            handleJumpPadWandClick(event);
            return;
        }

        if (serverPortalFeature.isServerWand(item)) {
            serverPortalFeature.handleWandClick(event);
            return;
        }

        if (event.getAction().isRightClick() && event.getClickedBlock() != null
                && isShelf(event.getClickedBlock().getType()) && !player.hasPermission("minerva.admin")) {
            if (isMinervaItem(item, "emerald_bundle")) {
                tryShopPayment(player, event.getClickedBlock());
            }
            event.setCancelled(true);
            return;
        }

        if (event.getAction().isRightClick() && event.getClickedBlock() != null && isBarrelShop(event.getClickedBlock())) {
            if (event.getClickedBlock().getState() instanceof Barrel barrel) {
                player.openInventory(barrel.getInventory());
            }
            event.setCancelled(true);
            return;
        }

        if (item == null) {
            return;
        }

        if (isMinervaItem(item, "emerald_bundle")) {
            if (event.getAction().isLeftClick()) {
                player.sendMessage(ChatColor.GREEN + "所持MP: " + formatNumber(getEmeralds(player.getUniqueId())));
                event.setCancelled(true);
                return;
            }
            if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
                if (tryShopPayment(player, event.getClickedBlock())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (isMinervaItem(item, "friend_book") && event.getAction().isRightClick()) {
            openFriendUi(player);
            event.setCancelled(true);
            return;
        }

        if (isReincarnationStar(item) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            tryReincarnate(player, item);
            return;
        }

        if (isMinervaItem(item, "hub_compass")) {
            compassFeature.updateCompassTarget(player);
            return;
        }

        if (isMinervaItem(item, "teleporter") && event.getAction().isRightClick()) {
            openTeleportUi(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack.getType() != Material.EMERALD || !hasMinervaItem(player, "emerald_bundle")) {
            return;
        }
        depositEmeralds(player.getUniqueId(), stack.getAmount());
        event.getItem().remove();
        event.setCancelled(true);
        player.sendMessage(ChatColor.GREEN + "エメラルドを収納しました: +" + formatNumber(stack.getAmount()) + "MP");
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || entity instanceof Player || isMinervaMerchant(entity)) {
            return;
        }
        if (isFfaSummonedMob(entity)) {
            return;
        }
        if (!isDirectPlayerKill(entity, killer)) {
            return;
        }
        int baseReward = mobKillReward(entity.getType());
        if (baseReward <= 0) {
            return;
        }
        int reward = applyIncomeBonus(killer.getUniqueId(), adjustedMobKillReward(killer.getUniqueId(), entity.getType(), baseReward));
        addPlayerStat(killer.getUniqueId(), "total-mob-kills", 1);
        addKilledMob(killer.getUniqueId(), entity.getType());
        depositEmeralds(killer.getUniqueId(), reward);
        killer.sendMessage(ChatColor.GREEN + "討伐報酬: +" + formatNumber(reward) + "MP");
    }

    private int mobKillReward(EntityType type) {
        return type == null ? 0 : MOB_KILL_REWARDS.getOrDefault(type.name(), 0);
    }

    private boolean isFfaSummonedMob(LivingEntity entity) {
        String kind = entity.getPersistentDataContainer().get(ffaEntityKindKey, PersistentDataType.STRING);
        return "summon".equals(kind);
    }

    private int adjustedMobKillReward(UUID uuid, EntityType type, int baseReward) {
        if (baseReward <= 0 || type == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        Map<String, KillRewardWindow> playerWindows = mobRewardWindows.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        KillRewardWindow window = playerWindows.computeIfAbsent(type.name(), ignored -> new KillRewardWindow(now));
        if (now - window.startedAtMillis >= 60L * 60L * 1000L) {
            window.startedAtMillis = now;
            window.count = 0;
        }
        window.count++;
        if (window.count > MOB_REWARD_FARM_THRESHOLD_PER_HOUR && isFarmAdjustedMob(type)) {
            return Math.max(1, baseReward / 2);
        }
        return baseReward;
    }

    private boolean isFarmAdjustedMob(EntityType type) {
        return switch (type) {
            case BEE, CAVE_SPIDER, DOLPHIN, ENDERMITE, GOAT, GUARDIAN, LLAMA, MAGMA_CUBE,
                 POLAR_BEAR, SILVERFISH, SLIME, TRADER_LLAMA, ZOMBIFIED_PIGLIN -> true;
            default -> false;
        };
    }

    private boolean isDirectPlayerKill(LivingEntity entity, Player killer) {
        EntityDamageEvent damage = entity.getLastDamageCause();
        if (!(damage instanceof EntityDamageByEntityEvent entityDamage)) {
            return false;
        }
        Entity damager = entityDamage.getDamager();
        if (damager instanceof Player player) {
            return player.getUniqueId().equals(killer.getUniqueId());
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player && player.getUniqueId().equals(killer.getUniqueId());
        }
        return false;
    }

    private void addKilledMob(UUID uuid, EntityType type) {
        Set<String> killed = new HashSet<>(getPlayerSection(uuid).getStringList("killed-mobs"));
        if (killed.add(type.name())) {
            getPlayerSection(uuid).set("killed-mobs", new ArrayList<>(killed));
            saveData();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (ffaManager.isPlaying(player)) {
            return;
        }
        int current = getEmeralds(player.getUniqueId());
        int lost = current / 2;
        if (lost <= 0) {
            return;
        }
        withdrawEmeralds(player.getUniqueId(), lost);
        player.sendMessage(ChatColor.RED + "死亡により所持MPの50%を失いました: -" + formatNumber(lost) + "MP");
    }

    private boolean tryShopPayment(Player player, Block block) {
        ShelfShopOffer offer = readShelfShopOffer(player, block);
        if (offer == null) {
            return false;
        }
        if (offer.material() == null || offer.price() <= 0) {
            return false;
        }
        int discountedPrice = applyShopDiscount(player, offer.price());
        int currentEmeralds = getEmeralds(player.getUniqueId());
        if (currentEmeralds < discountedPrice) {
            showTemporaryActionBar(player, "MPが不足しています：" + formatNumber(discountedPrice - currentEmeralds) + "MP");
            return true;
        }
        if (inventorySpaceFor(player, offer.material()) < offer.amount()) {
            showTemporaryActionBar(player, "インベントリに空きがありません。");
            return true;
        }
        if (!withdrawEmeralds(player.getUniqueId(), discountedPrice)) {
            showTemporaryActionBar(player, "MPが不足しています：" + formatNumber(discountedPrice) + "MP");
            return true;
        }
        giveShopPurchasedItems(player, offer.material(), offer.amount());
        addPlayerStat(player.getUniqueId(), "total-trades", offer.amount());
        playPurchaseSound(player);
        sendItemMessage(player, NamedTextColor.GREEN, "購入しました: ", offer.material(),
                " x" + offer.amount() + " (" + formatNumber(discountedPrice) + "MP)");
        return true;
    }

    private void handleShopWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack wand = event.getItem();
        ShopWandType type = shopWandType(wand);
        
        // スロットワンドの処理
        if (type.isSlotWand()) {
            handleSlotWandClick(event, player, block, type);
            return;
        }
        
        if (type == ShopWandType.FRAME) {
            player.sendMessage(ChatColor.RED + "額縁ショップは未実装です。額縁は既存のオークション機能を使用してください。");
            event.setCancelled(true);
            return;
        }
        if (block == null || !isValidShopWandTarget(block, type)) {
            player.sendMessage(ChatColor.RED + shopWandTargetMessage(type));
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isRightClick() && !canCreateShop(player)) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isLeftClick() && !canManageShop(player, block)) {
            player.sendMessage(ChatColor.RED + "このショップを解除できるのは作成者または管理者のみです。");
            event.setCancelled(true);
            return;
        }
        if (block.getType() == Material.BARREL) {
            handleBarrelShopWandClick(player, block, event, type);
            return;
        }
        if (event.getAction().isRightClick()) {
            if (type == ShopWandType.SHELF) {
                List<Material> materials = randomShopMaterials(SHELF_SHOP_OFFER_SLOTS);
                if (materials.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "販売可能な価格設定済みアイテムがありません。");
                    event.setCancelled(true);
                    return;
                }
                setShelfShopRandomOffers(block, materials);
            }
            setShelfShop(block, true);
            setShopOwner(block, player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "棚をショップ化しました。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isLeftClick()) {
            if (setShelfShop(block, false)) {
                player.sendMessage(ChatColor.GREEN + "棚のショップ化を解除しました。");
            } else {
                player.sendMessage(ChatColor.YELLOW + "この棚はショップ化されていません。");
            }
            event.setCancelled(true);
        }
    }
    
    private void handleSlotWandClick(PlayerInteractEvent event, Player player, Block block, ShopWandType type) {
        if (block == null || !isShelf(block.getType())) {
            player.sendMessage(ChatColor.RED + "棚をクリックしてください。");
            event.setCancelled(true);
            return;
        }
        
        if (event.getAction().isRightClick()) {
            SlotMachineManager.Difficulty difficulty = type.getSlotDifficulty();
            if (difficulty == null) {
                player.sendMessage(ChatColor.RED + "無効なスロットワンドです。");
                event.setCancelled(true);
                return;
            }
            
            // スロットセッションを作成
            SlotMachineManager manager = slotMachineManager;
            if (manager == null) {
                player.sendMessage(ChatColor.RED + "スロットマシンシステムが初期化されていません。");
                event.setCancelled(true);
                return;
            }
            
            SlotMachineManager.SlotSession session = manager.createSession(player, block, difficulty);
            if (session == null) {
                player.sendMessage(ChatColor.RED + "スロットマシンの初期化に失敗しました。");
                event.setCancelled(true);
                return;
            }
            
            player.sendMessage(ChatColor.AQUA + "棚をスロットマシン化しました！");
            player.sendMessage(ChatColor.GRAY + "ウォレット（バンドル）を持って右クリックで回転します。");
            event.setCancelled(true);
        }
    }

    private boolean isValidShopWandTarget(Block block, ShopWandType type) {
        if (type == ShopWandType.SHELF) {
            return isShelf(block.getType());
        }
        if (type == ShopWandType.BARREL) {
            return block.getType() == Material.BARREL;
        }
        return isShelf(block.getType()) || block.getType() == Material.BARREL;
    }

    private String shopWandTargetMessage(ShopWandType type) {
        if (type == ShopWandType.SHELF) {
            return "棚をクリックしてください。";
        }
        if (type == ShopWandType.BARREL) {
            return "樽をクリックしてください。";
        }
        return "棚または樽をクリックしてください。";
    }

    private void handleBarrelShopWandClick(Player player, Block block, PlayerInteractEvent event, ShopWandType type) {
        if (event.getAction().isRightClick()) {
            if (!(block.getState() instanceof Barrel barrel)) {
                player.sendMessage(ChatColor.RED + "樽を読み込めませんでした。");
                event.setCancelled(true);
                return;
            }
            if (type == ShopWandType.BARREL) {
                populateBarrelShop(barrel);
                setBarrelShopMeta(block);
            } else {
                populateBarrelShop(barrel);
                clearBarrelShopMeta(block);
            }
            setBarrelShop(block, true);
            setShopOwner(block, player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "樽をショップ化し、商品を生成しました。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isLeftClick()) {
            if (setBarrelShop(block, false)) {
                if (block.getState() instanceof Barrel barrel) {
                    barrel.getInventory().clear();
                }
                clearBarrelShopMeta(block);
                player.sendMessage(ChatColor.GREEN + "樽のショップ化を解除しました。");
            } else {
                player.sendMessage(ChatColor.YELLOW + "この樽はショップ化されていません。");
            }
            event.setCancelled(true);
        }
    }

    private void tickShelfShopActionBars() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String temporaryMessage = temporaryActionBarMessages.get(player.getUniqueId());
            long temporaryUntil = temporaryActionBarUntil.getOrDefault(player.getUniqueId(), 0L);
            if (temporaryMessage != null && temporaryUntil > now) {
                player.sendActionBar(Component.text(temporaryMessage, NamedTextColor.RED));
                continue;
            }
            temporaryActionBarMessages.remove(player.getUniqueId());
            temporaryActionBarUntil.remove(player.getUniqueId());

            Block target = player.getTargetBlockExact(5);
            ShelfShopOffer offer = readShelfShopOffer(player, target);
            if (offer == null) {
                continue;
            }
            player.sendActionBar(Component.translatable(offer.material().translationKey()).color(rarityTextColor(merchantRarity(offer.material())))
                    .append(Component.text("：" + formatNumber(applyShopDiscount(player, offer.price())) + "MP", NamedTextColor.GOLD)));
        }
    }

    private void showTemporaryActionBar(Player player, String message) {
        temporaryActionBarMessages.put(player.getUniqueId(), message);
        temporaryActionBarUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private ShelfShopOffer readShelfShopOffer(Player player, Block block) {
        if (block == null || !isShelf(block.getType())) {
            return null;
        }
        if (!isShelfShop(block)) {
            return null;
        }
        List<Material> configuredMaterials = shelfShopRandomOffers(block);
        if (!configuredMaterials.isEmpty()) {
            Material configuredMaterial = materialForShelfSlot(configuredMaterials, selectedShelfSlot(player, block));
            int price = randomShopPrice(configuredMaterial);
            return price <= 0 ? null : new ShelfShopOffer(configuredMaterial, 1, price);
        }
        Material shelfMaterial = shelfShopMaterial(block, selectedShelfSlot(player, block));
        if (shelfMaterial == null || shelfMaterial == Material.AIR || !isPricedShopItem(shelfMaterial)) {
            return null;
        }
        return new ShelfShopOffer(shelfMaterial, 1, Math.max(1, materialPrice(shelfMaterial)));
    }

    private List<Material> shelfShopRandomOffers(Block block) {
        String path = shelfShopOfferPath(block);
        List<Material> materials = new ArrayList<>();
        for (String raw : data.getStringList(path + ".materials")) {
            Material material = Material.matchMaterial(raw);
            if (isRandomShopItem(material)) {
                materials.add(material);
            }
        }
        if (!materials.isEmpty()) {
            return materials;
        }
        String materialName = data.getString(path + ".material");
        if (materialName == null || materialName.isBlank()) {
            return List.of();
        }
        Material material = Material.matchMaterial(materialName);
        return material == null || !isRandomShopItem(material) ? List.of() : List.of(material);
    }

    private Material materialForShelfSlot(List<Material> materials, int selectedSlot) {
        if (materials.isEmpty()) {
            return null;
        }
        if (selectedSlot >= 0 && selectedSlot < materials.size()) {
            return materials.get(selectedSlot);
        }
        return materials.get(0);
    }

    private void setShelfShopRandomOffers(Block block, List<Material> materials) {
        String path = shelfShopOfferPath(block);
        data.set(path + ".type", ShopWandType.SHELF.key());
        data.set(path + ".material", materials.get(0).name());
        data.set(path + ".materials", materials.stream().map(Material::name).toList());
        data.set(path + ".created-at", System.currentTimeMillis());
        displayShelfShopOffers(block, materials);
        saveData();
    }

    private void clearShelfShopRandomOffer(Block block) {
        data.set(shelfShopOfferPath(block), null);
    }

    private void syncShelfShopDisplays() {
        ConfigurationSection worlds = data.getConfigurationSection("shelf-shop-offers");
        if (worlds == null) {
            return;
        }
        int synced = 0;
        for (String worldId : worlds.getKeys(false)) {
            World world = worldFromId(worldId);
            if (world == null) {
                continue;
            }
            ConfigurationSection offers = worlds.getConfigurationSection(worldId);
            if (offers == null) {
                continue;
            }
            for (String coordinates : offers.getKeys(false)) {
                Block block = blockFromCoordinates(world, coordinates);
                if (block == null || !isShelfShop(block)) {
                    continue;
                }
                List<Material> materials = shelfShopRandomOffers(block);
                if (materials.size() < SHELF_SHOP_OFFER_SLOTS) {
                    List<Material> refreshed = randomShopMaterials(SHELF_SHOP_OFFER_SLOTS);
                    if (refreshed.size() > materials.size()) {
                        setShelfShopRandomOffers(block, refreshed);
                        synced++;
                        continue;
                    }
                }
                if (!materials.isEmpty() && displayShelfShopOffers(block, materials)) {
                    synced++;
                }
            }
        }
        if (synced > 0) {
            getLogger().info("Synced " + synced + " shelf shop display items.");
        }
    }

    private World worldFromId(String worldId) {
        try {
            return Bukkit.getWorld(UUID.fromString(worldId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Block blockFromCoordinates(World world, String coordinates) {
        String[] parts = coordinates.split("_", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            return world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean displayShelfShopOffers(Block block, List<Material> materials) {
        if (block == null || materials.isEmpty() || !(block.getState() instanceof Shelf shelfState)) {
            return false;
        }
        Inventory inventory = shelfState.getInventory();
        inventory.clear();
        int slots = Math.min(SHELF_SHOP_OFFER_SLOTS, Math.min(inventory.getSize(), materials.size()));
        for (int slot = 0; slot < slots; slot++) {
            Material material = materials.get(slot);
            if (material != null && material != Material.AIR) {
                inventory.setItem(slot, new ItemStack(material));
            }
        }
        return true;
    }

    private void clearShelfShopDisplay(Block block) {
        if (block != null && block.getState() instanceof Shelf shelfState) {
            shelfState.getInventory().clear();
        }
    }

    private String shelfShopOfferPath(Block block) {
        return "shelf-shop-offers." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private int selectedShelfSlot(Player player, Block shelf) {
        if (player == null || shelf == null) {
            return 0;
        }
        org.bukkit.util.RayTraceResult result = player.rayTraceBlocks(5);
        if (result == null || result.getHitBlock() == null || !result.getHitBlock().equals(shelf)) {
            return 0;
        }
        double local = shelfSlotAxisPosition(shelf, result.getHitPosition().getX(), result.getHitPosition().getZ());
        if (local < 1.0 / 3.0) {
            return 2;
        }
        if (local < 2.0 / 3.0) {
            return 1;
        }
        return 0;
    }

    private double shelfSlotAxisPosition(Block shelf, double hitX, double hitZ) {
        BlockFace facing = shelfFacing(shelf);
        double local = switch (facing) {
            case NORTH -> hitX - shelf.getX();
            case SOUTH -> 1.0 - (hitX - shelf.getX());
            case EAST -> hitZ - shelf.getZ();
            case WEST -> 1.0 - (hitZ - shelf.getZ());
            default -> hitX - shelf.getX();
        };
        return Math.max(0.0, Math.min(0.999999, local));
    }

    private BlockFace shelfFacing(Block shelf) {
        if (shelf.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.NORTH;
    }

    private Material shelfShopMaterial(Block shelf, int selectedSlot) {
        if (shelf.getState() instanceof Shelf shelfState) {
            ItemStack[] contents = shelfState.getSnapshotInventory().getContents();
            if (selectedSlot >= 0 && selectedSlot < contents.length) {
                ItemStack selected = contents[selectedSlot];
                if (selected != null && selected.getType() != Material.AIR) {
                    return selected.getType();
                }
            }
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
                    return item.getType();
                }
            }
        }
        Location center = shelf.getLocation().add(0.5, 0.5, 0.5);
        return shelf.getWorld().getNearbyEntities(center, 1.25, 1.25, 1.25).stream()
                .filter(ItemFrame.class::isInstance)
                .map(ItemFrame.class::cast)
                .map(ItemFrame::getItem)
                .filter(item -> item != null && item.getType() != Material.AIR)
                .map(ItemStack::getType)
                .findFirst()
                .orElse(null);
    }

    private boolean isShelf(Material material) {
        String name = material.name();
        return name.endsWith("_SHELF") || name.equals("CHISELED_BOOKSHELF");
    }

    boolean isShelfShop(Block block) {
        return block != null && data.getBoolean(shelfShopPath(block), false);
    }

    boolean setShelfShop(Block block, boolean enabled) {
        String path = shelfShopPath(block);
        boolean existed = data.getBoolean(path, false);
        boolean randomOffer = !shelfShopRandomOffers(block).isEmpty();
        data.set(path, enabled ? true : null);
        if (!enabled) {
            if (randomOffer) {
                clearShelfShopDisplay(block);
            }
            clearShopOwner(block);
            clearShelfShopRandomOffer(block);
        }
        saveData();
        return existed;
    }

    private String shelfShopPath(Block block) {
        return "shelf-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private boolean canCreateShop(Player player) {
        return player.hasPermission("minerva.shop.admin") || player.hasPermission("minerva.admin");
    }

    private boolean canManageShop(Player player, Block block) {
        if (canCreateShop(player)) {
            return true;
        }
        String owner = data.getString(shopOwnerPath(block), "");
        return owner.equals(player.getUniqueId().toString());
    }

    private void setShopOwner(Block block, UUID owner) {
        data.set(shopOwnerPath(block), owner.toString());
        saveData();
    }

    private void clearShopOwner(Block block) {
        data.set(shopOwnerPath(block), null);
    }

    private String shopOwnerPath(Block block) {
        return "shop-owners." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    boolean isBarrelShop(Block block) {
        return block != null && block.getType() == Material.BARREL && data.getBoolean(barrelShopPath(block), false);
    }

    boolean isShopBlock(Block block) {
        return isShelfShop(block) || isBarrelShop(block);
    }

    boolean isAuctionFrame(Entity entity) {
        return auctionFeature.isAuctionFrame(entity);
    }

    boolean isAuctionInteractionItem(ItemStack item) {
        return auctionFeature.isAuctionInteractionItem(item);
    }

    void recordQuestProgress(Player player, String progressKey, int amount) {
        questService.addProgress(player, progressKey, amount);
    }

    boolean setBarrelShop(Block block, boolean enabled) {
        String path = barrelShopPath(block);
        boolean existed = data.getBoolean(path, false);
        data.set(path, enabled ? true : null);
        if (!enabled) {
            clearShopOwner(block);
            clearBarrelShopMeta(block);
        }
        saveData();
        return existed;
    }

    private String barrelShopPath(Block block) {
        return "barrel-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private void populateBarrelShop(Barrel barrel) {
        List<MerchantOffer> pool = barrelShopOffers();
        populateBarrelShop(barrel, pool);
    }

    private void populateBarrelShop(Barrel barrel, List<MerchantOffer> pool) {
        Set<Material> used = new HashSet<>();
        Inventory inventory = barrel.getInventory();
        inventory.clear();
        if (pool.isEmpty()) {
            getLogger().warning("Barrel shop pool is empty. No offers were generated.");
            return;
        }
        int offerSlots = Math.min(inventory.getSize(), Math.max(1,
                getConfig().getInt("barrel-shop.offer-slots", BARREL_SHOP_OFFER_SLOTS)));
        for (int slot = 0; slot < offerSlots; slot++) {
            MerchantOffer offer = randomBarrelOffer(pool, used);
            inventory.setItem(slot, createBarrelOfferItem(offer));
        }
    }

    private void setBarrelShopMeta(Block block) {
        String path = barrelShopMetaPath(block);
        data.set(path + ".type", ShopWandType.BARREL.key());
        data.set(path + ".category", null);
        data.set(path + ".created-at", System.currentTimeMillis());
        saveData();
    }

    private void clearBarrelShopMeta(Block block) {
        data.set(barrelShopMetaPath(block), null);
    }

    private String barrelShopMetaPath(Block block) {
        return "barrel-shop-meta." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private void handleJumpPadWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!player.hasPermission("minerva.admin")) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            event.setCancelled(true);
            return;
        }
        if (block == null) {
            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isRightClick()) {
            int verticalPower = utilityItemsFeature.getJumpPadVerticalPower(event.getItem());
            int horizontalPower = utilityItemsFeature.getJumpPadHorizontalPower(event.getItem());
            setJumpPad(block, verticalPower, horizontalPower);
            player.sendMessage(ChatColor.GREEN + "ジャンプパッドを設定しました。縦: " + verticalPower + " / 横: " + horizontalPower);
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isLeftClick()) {
            boolean existed = setJumpPad(block, false);
            player.sendMessage((existed ? ChatColor.GREEN : ChatColor.YELLOW)
                    + (existed ? "ジャンプパッドを解除しました。" : "このブロックはジャンプパッドではありません。"));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = to.clone().subtract(0.0, 1.0, 0.0).getBlock();
        JumpPadPower power = jumpPadPower(block);
        if (power == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastJumpPadUse.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < JUMP_PAD_COOLDOWN_MILLIS) {
            return;
        }
        lastJumpPadUse.put(player.getUniqueId(), now);
        jumpPadFallProtectionUntil.put(player.getUniqueId(), now + JUMP_PAD_FALL_PROTECTION_MILLIS);
        player.setFallDistance(0.0f);
        double horizontalVelocity = jumpPadHorizontalVelocity(power.horizontal());
        double verticalVelocity = jumpPadVerticalVelocity(power.vertical());
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() > 0.0) {
            direction.normalize().multiply(horizontalVelocity);
        }
        player.setVelocity(direction.setY(verticalVelocity));
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, Math.min(2.0f, 1.0f + power.vertical() * 0.01f));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJumpPadFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Long until = jumpPadFallProtectionUntil.get(playerId);
        if (until == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > until) {
            jumpPadFallProtectionUntil.remove(playerId);
            return;
        }
        jumpPadFallProtectionUntil.remove(playerId);
        player.setFallDistance(0.0f);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJumpPadBlockBreak(BlockBreakEvent event) {
        if (jumpPadPower(event.getBlock()) != null) {
            setJumpPad(event.getBlock(), false);
        }
    }

    private void setJumpPad(Block block, int verticalPower, int horizontalPower) {
        String path = jumpPadPath(block);
        data.set(path + ".verticalPower", clampJumpPadPower(verticalPower));
        data.set(path + ".horizontalPower", clampJumpPadPower(horizontalPower));
        data.set(path + ".material", block.getType().name());
        saveData();
    }

    private boolean setJumpPad(Block block, boolean enabled) {
        String path = jumpPadPath(block);
        boolean existed = jumpPadPower(block) != null;
        if (enabled) {
            setJumpPad(block, 5, 5);
            return existed;
        }
        data.set(path, null);
        saveData();
        return existed;
    }

    private JumpPadPower jumpPadPower(Block block) {
        if (block == null) {
            return null;
        }
        String path = jumpPadPath(block);
        Object raw = data.get(path);
        if (raw instanceof Boolean value) {
            if (value) {
                setJumpPad(block, 5, 5);
                return new JumpPadPower(5, 5);
            }
            return null;
        }
        if (raw instanceof Number value) {
            int power = oldJumpPadPowerToNewPower(value.intValue());
            setJumpPad(block, power, power);
            return new JumpPadPower(power, power);
        }
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        String material = section.getString("material", "");
        if (!block.getType().name().equals(material)) {
            return null;
        }
        if (section.contains("power")) {
            int power = oldJumpPadPowerToNewPower(section.getInt("power", 3));
            setJumpPad(block, power, power);
            return new JumpPadPower(power, power);
        }
        return new JumpPadPower(
                clampJumpPadPower(section.getInt("verticalPower", 5)),
                clampJumpPadPower(section.getInt("horizontalPower", 5)));
    }

    private String jumpPadPath(Block block) {
        return "jump-pads." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private void migrateBarrelShopOfferSlots() {
        String path = "barrel-shop.offer-slots";
        if (getConfig().getInt(path, BARREL_SHOP_OFFER_SLOTS) == 18) {
            getConfig().set(path, BARREL_SHOP_OFFER_SLOTS);
            saveConfig();
        }
    }

    private int clampJumpPadPower(int power) {
        return Math.max(1, Math.min(MAX_JUMP_PAD_POWER, power));
    }

    private int oldJumpPadPowerToNewPower(int power) {
        return clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2);
    }

    private double jumpPadHorizontalVelocity(int power) {
        int safePower = clampJumpPadPower(power);
        int basePower = Math.min(10, safePower);
        int extraPower = Math.max(0, safePower - 10);
        return 0.45D + basePower * 0.18D + extraPower * 0.04D;
    }

    private double jumpPadVerticalVelocity(int power) {
        int safePower = clampJumpPadPower(power);
        int basePower = Math.min(10, safePower);
        int extraPower = Math.max(0, safePower - 10);
        return 0.75D + basePower * 0.15D + extraPower * 0.05D;
    }

    private MerchantOffer randomBarrelOffer(List<MerchantOffer> pool, Set<Material> used) {
        String tier = random.nextInt(100) < 15 ? "bargain" : "junk";
        List<MerchantOffer> candidates = pool.stream()
                .filter(offer -> offer.rarity().equals(tier) && !used.contains(offer.material()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = pool.stream().filter(offer -> !used.contains(offer.material())).toList();
        }
        if (candidates.isEmpty()) {
            used.clear();
            candidates = pool;
        }
        MerchantOffer offer = randomWeightedBarrelOffer(candidates);
        used.add(offer.material());
        return offer;
    }

    private MerchantOffer randomWeightedBarrelOffer(List<MerchantOffer> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(offer -> Math.max(1, barrelShopWeight(offer.material())))
                .sum();
        int selected = random.nextInt(Math.max(1, totalWeight));
        for (MerchantOffer offer : candidates) {
            selected -= Math.max(1, barrelShopWeight(offer.material()));
            if (selected < 0) {
                return offer;
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private ItemStack createBarrelOfferItem(MerchantOffer offer) {
        ItemStack item = new ItemStack(offer.material());
        boolean equipment = item.getItemMeta() instanceof Damageable && offer.material().getMaxDurability() > 0;
        ItemMeta meta = item.getItemMeta();
        boolean damaged = equipment && random.nextInt(10) != 0;
        if (damaged && meta instanceof Damageable damageable) {
            int damagePercent = 20 + random.nextInt(61);
            int damage = Math.max(1, offer.material().getMaxDurability() * damagePercent / 100);
            damageable.setDamage(Math.min(offer.material().getMaxDurability() - 1, damage));
        }
        int price = barrelOfferPrice(offer);
        if (damaged) {
            int discountPercent = 10 + random.nextInt(61);
            price = discountedPrice(price, discountPercent);
        }
        meta.lore(List.of(
                Component.text("枠: ", NamedTextColor.GRAY)
                        .append(Component.text(barrelTierName(offer.rarity()), barrelTierColor(offer.rarity()))),
                Component.text("価格: " + formatNumber(price) + "MP", NamedTextColor.GOLD),
                Component.text("クリックで購入", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(barrelOfferPriceKey, PersistentDataType.INTEGER, price);
        meta.getPersistentDataContainer().set(barrelOfferRarityKey, PersistentDataType.STRING, offer.rarity());
        item.setItemMeta(meta);
        return item;
    }

    private int barrelOfferPrice(MerchantOffer offer) {
        int basePrice = Math.max(1, offer.price());
        if ("bargain".equals(offer.rarity())) {
            return (int) Math.max(1, Math.min(MAX_EMERALDS, (long) basePrice * (80 + random.nextInt(51)) / 100L));
        }
        return (int) Math.max(1, Math.min(MAX_EMERALDS, (long) basePrice * (60 + random.nextInt(41)) / 100L));
    }

    private String barrelTierName(String tier) {
        return "bargain".equals(tier) ? "掘り出し物" : "ジャンク";
    }

    private NamedTextColor barrelTierColor(String tier) {
        return "bargain".equals(tier) ? NamedTextColor.AQUA : NamedTextColor.GRAY;
    }

    private int discountedPrice(int price, int discountPercent) {
        return Math.max(1, (int) ((long) Math.max(1, price) * (100 - discountPercent) / 100L));
    }

    private void buyBarrelOffer(Player player, ItemStack displayed) {
        if (displayed == null || displayed.getType() == Material.AIR || !displayed.hasItemMeta()) {
            return;
        }
        Integer basePrice = displayed.getItemMeta().getPersistentDataContainer().get(barrelOfferPriceKey, PersistentDataType.INTEGER);
        if (basePrice == null || basePrice <= 0) {
            return;
        }
        int price = applyShopDiscount(player, basePrice);
        if (getEmeralds(player.getUniqueId()) < price) {
            showTemporaryActionBar(player, "MPが不足しています：" + formatNumber(price) + "MP");
            return;
        }
        ItemStack purchased = displayed.clone();
        purchased.setAmount(1);
        ItemMeta meta = purchased.getItemMeta();
        meta.lore(null);
        meta.getPersistentDataContainer().remove(barrelOfferPriceKey);
        meta.getPersistentDataContainer().remove(barrelOfferRarityKey);
        purchased.setItemMeta(meta);
        if (inventorySpaceFor(player, purchased.getType()) < 1) {
            showTemporaryActionBar(player, "インベントリに空きがありません。");
            return;
        }
        if (!withdrawEmeralds(player.getUniqueId(), price)) {
            showTemporaryActionBar(player, "MPが不足しています：" + formatNumber(price) + "MP");
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(purchased);
        if (!leftovers.isEmpty()) {
            depositEmeralds(player.getUniqueId(), price);
            showTemporaryActionBar(player, "インベントリに空きがありません。");
            return;
        }
        addPlayerStat(player.getUniqueId(), "total-trades", 1);
        playPurchaseSound(player);
        sendItemMessage(player, NamedTextColor.GREEN, "購入しました: ", purchased.getType(),
                " (" + formatNumber(price) + "MP)");
    }

    private void normalizeMerchants() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractVillager villager && isMinervaMerchant(entity)) {
                    villager.setAI(true);
                    villager.setInvulnerable(false);
                }
            }
        }
    }

    private ItemStack createShopPurchasedItem(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    private int applyShopDiscount(Player player, int price) {
        int discount = Math.max(0, Math.min(95, getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0)));
        long discounted = (long) Math.max(1, price) * (100L - discount) / 100L;
        return (int) Math.max(1, Math.min(MAX_EMERALDS, discounted));
    }

    private boolean spawnMerchant(Location location) {
        if (isCentralPlazaLocation(location)) {
            return false;
        }
        WanderingTrader trader = (WanderingTrader) location.getWorld().spawnEntity(location, EntityType.WANDERING_TRADER);
        String merchantType = randomMerchantType();
        trader.customName(Component.text(merchantTypeColor(merchantType) + merchantTypeName(merchantType) + "商人"));
        trader.setCustomNameVisible(true);
        trader.setDespawnDelay(Integer.MAX_VALUE);
        trader.setCanDrinkMilk(false);
        trader.setCanDrinkPotion(false);
        trader.setAI(true);
        trader.setInvulnerable(false);
        PersistentDataContainer container = trader.getPersistentDataContainer();
        container.set(merchantKey, PersistentDataType.BOOLEAN, true);
        container.set(merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
        container.set(merchantTradedKey, PersistentDataType.BOOLEAN, false);
        container.set(merchantTypeKey, PersistentDataType.STRING, merchantType);
        rerollMerchant(trader);
        return true;
    }

    private void rerollMerchant(AbstractVillager villager) {
        String merchantType = villager.getPersistentDataContainer().get(merchantTypeKey, PersistentDataType.STRING);
        if (merchantType == null || merchantType.isBlank()) {
            merchantType = randomMerchantType();
            villager.getPersistentDataContainer().set(merchantTypeKey, PersistentDataType.STRING, merchantType);
        }
        villager.customName(Component.text(merchantTypeColor(merchantType) + merchantTypeName(merchantType) + "商人"));
        villager.setRecipes(Collections.emptyList());
        List<MerchantOffer> sellOffers = randomMerchantOffers(8, merchantSellWeights, true, merchantType);
        List<MerchantOffer> buyOffers = randomMerchantOffers(8, merchantBuyWeights, false, merchantType);
        saveMerchantOffers(villager.getUniqueId(), sellOffers, buyOffers);
    }

    private String randomMerchantType() {
        int roll = random.nextInt(100);
        if (roll < 33) {
            return "red";
        }
        if (roll < 83) {
            return "blue";
        }
        if (roll < 98) {
            return "yellow";
        }
        return "purple";
    }

    private String merchantTypeName(String type) {
        return switch (type) {
            case "red" -> "赤";
            case "yellow" -> "黄";
            case "purple" -> "紫";
            default -> "青";
        };
    }

    private String merchantTypeColor(String type) {
        return switch (type) {
            case "red" -> ChatColor.RED;
            case "yellow" -> ChatColor.YELLOW;
            case "purple" -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.AQUA;
        };
    }

    private List<MerchantOffer> randomMerchantOffers(int count, Map<String, Integer> weights, boolean selling, String merchantType) {
        List<MerchantOffer> pool = merchantOffers(weights, selling).stream()
                .filter(offer -> merchantTypeAllows(merchantType, offer.material(), selling))
                .toList();
        if (pool.isEmpty()) {
            pool = (selling ? allMerchantOffers() : allMerchantOffers().stream()
                    .filter(offer -> materialBuyPrice(offer.material()) > 0)
                    .toList()).stream()
                    .filter(offer -> merchantTypeAllows(merchantType, offer.material(), selling))
                    .toList();
        }
        if (pool.isEmpty()) {
            pool = merchantOffers(weights, selling);
        }
        List<MerchantOffer> offers = new ArrayList<>();
        Set<Material> used = new HashSet<>();
        if ("purple".equals(merchantType)) {
            List<MerchantOffer> epicPool = pool.stream()
                    .filter(offer -> isMerchantRarityAtLeast(offer.rarity(), "epic"))
                    .toList();
            if (!epicPool.isEmpty()) {
                MerchantOffer epic = randomWeightedMerchantOffer(used, epicPool, weights);
                offers.add(new MerchantOffer(epic.material(), epic.amount(), epic.rarity(), randomMerchantPrice(epic.material(), selling)));
            }
            pool = pool.stream()
                    .filter(offer -> isMerchantRarityAtLeast(offer.rarity(), "rare"))
                    .toList();
            if (pool.isEmpty()) {
                pool = merchantOffers(weights, selling).stream()
                        .filter(offer -> isMerchantRarityAtLeast(offer.rarity(), "rare"))
                        .toList();
            }
        }
        for (int i = 0; i < count; i++) {
            MerchantOffer offer = randomWeightedMerchantOffer(used, pool, weights);
            int price = randomMerchantPrice(offer.material(), selling);
            offers.add(new MerchantOffer(offer.material(), offer.amount(), offer.rarity(), price));
        }
        return offers.stream().limit(count).toList();
    }

    private boolean merchantTypeAllows(String merchantType, Material material, boolean selling) {
        if ("purple".equals(merchantType)) {
            return true;
        }
        String name = material.name();
        if ("red".equals(merchantType)) {
            if (selling) {
                return material.getMaxDurability() > 0
                        || name.endsWith("_SWORD")
                        || name.endsWith("_AXE")
                        || name.endsWith("_PICKAXE")
                        || name.endsWith("_SHOVEL")
                        || name.endsWith("_HOE")
                        || name.endsWith("_HELMET")
                        || name.endsWith("_CHESTPLATE")
                        || name.endsWith("_LEGGINGS")
                        || name.endsWith("_BOOTS")
                        || name.contains("BOW")
                        || name.contains("ARROW")
                        || name.equals("SHIELD")
                        || name.equals("TRIDENT")
                        || name.equals("MACE");
            }
            return name.contains("STONE")
                    || name.contains("ORE")
                    || name.contains("INGOT")
                    || name.contains("COAL")
                    || name.contains("COPPER")
                    || name.contains("IRON")
                    || name.contains("GOLD")
                    || name.contains("REDSTONE")
                    || name.contains("LAPIS")
                    || name.contains("QUARTZ");
        }
        if ("yellow".equals(merchantType)) {
            return materialPrice(material) >= 100
                    && !name.endsWith("_SPAWN_EGG")
                    && !name.equals("SPAWNER")
                    && !name.equals("TRIAL_SPAWNER");
        }
        return !name.endsWith("_SPAWN_EGG")
                && !name.equals("SPAWNER")
                && !name.equals("TRIAL_SPAWNER")
                && materialPrice(material) < 1000;
    }

    private MerchantOffer randomWeightedMerchantOffer(Set<Material> used, List<MerchantOffer> sourcePool, Map<String, Integer> weights) {
        List<MerchantOffer> pool = sourcePool.stream()
                .filter(offer -> !used.contains(offer.material()))
                .toList();
        if (pool.isEmpty()) {
            used.clear();
            pool = sourcePool;
        }
        int totalWeight = pool.stream()
                .mapToInt(offer -> Math.max(1, weights.getOrDefault(offer.material().name(), 1)))
                .sum();
        int selected = random.nextInt(Math.max(1, totalWeight));
        for (MerchantOffer offer : pool) {
            selected -= Math.max(1, weights.getOrDefault(offer.material().name(), 1));
            if (selected < 0) {
                used.add(offer.material());
                return offer;
            }
        }
        MerchantOffer offer = pool.get(random.nextInt(pool.size()));
        used.add(offer.material());
        return offer;
    }

    private List<MerchantOffer> merchantOffers(Map<String, Integer> weights, boolean selling) {
        List<MerchantOffer> offers = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!isMerchantWeightedPoolItem(material, weights)) {
                continue;
            }
            int price = selling ? materialPrice(material) : materialBuyPrice(material);
            if (price > 0) {
                offers.add(new MerchantOffer(material, 1, merchantRarity(material), price));
            }
        }
        return offers;
    }

    private int randomMerchantPrice(Material material, boolean selling) {
        int basePrice = selling ? materialPrice(material) : materialBuyPrice(material);
        int multiplierPercent = 95 + random.nextInt(11);
        long price = (long) Math.max(1, basePrice) * multiplierPercent / 100L;
        return (int) Math.max(1, Math.min(MAX_EMERALDS, price));
    }

    private List<MerchantOffer> barrelShopOffers() {
        List<MerchantOffer> offers = new ArrayList<>();
        for (Material material : Material.values()) {
            BarrelShopConfig config = barrelShopConfigs.get(material.name());
            if (config == null || !isBarrelShopPoolItem(material)) {
                continue;
            }
            offers.add(new MerchantOffer(material, 1, config.tier(), materialPrice(material)));
        }
        return offers;
    }

    private List<Material> randomShopMaterials(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isRandomShopItem(material)) {
                materials.add(material);
            }
        }
        if (materials.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(materials, random);
        return materials.stream().limit(count).toList();
    }

    private boolean isRandomShopItem(Material material) {
        return material != null
                && material.isItem()
                && randomShopPrice(material) > 0
                && !MERCHANT_EXCLUDED_ITEMS.contains(material)
                && !material.name().startsWith("LEGACY_")
                && !material.name().endsWith("_SPAWN_EGG");
    }

    private int randomShopPrice(Material material) {
        if (material == null) {
            return 0;
        }
        int configuredPrice = shopSalePrices.getOrDefault(material.name(), 0);
        if (configuredPrice > 0) {
            return configuredPrice;
        }
        int economyPrice = economyPriceTable.price(material);
        if (economyPrice > 0) {
            return economyPrice;
        }
        Integer exact = exactMaterialPrice(material);
        return exact == null ? 0 : Math.max(1, exact);
    }

    private int barrelShopWeight(Material material) {
        BarrelShopConfig config = barrelShopConfigs.get(material.name());
        return config == null ? 0 : config.weight();
    }

    private List<MerchantOffer> allMerchantOffers() {
        Map<Material, MerchantOffer> configured = new HashMap<>();
        getConfig().getMapList("merchant-items").stream()
                .map(this::readMerchantOffer)
                .filter(Objects::nonNull)
                .forEach(offer -> configured.put(offer.material(), offer));
        List<MerchantOffer> offers = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isMerchantPoolItem(material)) {
                MerchantOffer override = configured.get(material);
                if (override != null) {
                    offers.add(override);
                } else {
                    String rarity = merchantRarity(material);
                    offers.add(new MerchantOffer(material, 1, rarity, materialPrice(material)));
                }
            }
        }
        return offers;
    }

    private void saveMerchantOffers(UUID merchantId, List<MerchantOffer> sellOffers, List<MerchantOffer> buyOffers) {
        data.set("merchants." + merchantId + ".sell", serializeMerchantOffers(sellOffers));
        data.set("merchants." + merchantId + ".buy", serializeMerchantOffers(buyOffers));
        saveData();
    }

    private List<String> serializeMerchantOffers(List<MerchantOffer> offers) {
        return offers.stream()
                .map(offer -> offer.material().name() + ":" + offer.rarity() + ":" + offer.price())
                .toList();
    }

    private List<MerchantOffer> readMerchantOffers(UUID merchantId, String key) {
        List<MerchantOffer> offers = new ArrayList<>();
        for (String raw : data.getStringList("merchants." + merchantId + "." + key)) {
            String[] parts = raw.split(":");
            if (parts.length < 3) {
                continue;
            }
            Material material = Material.matchMaterial(parts[0]);
            if (material != null && isMerchantPoolItem(material)) {
                String rarity = merchantRarity(material);
                int normalizedPrice = parsePositiveInt(parts[2], "buy".equals(key) ? materialBuyPrice(material) : materialPrice(material));
                if (normalizedPrice > 0) {
                    offers.add(new MerchantOffer(material, 1, rarity, normalizedPrice));
                }
            }
        }
        return offers;
    }

    private MerchantOffer readMerchantOffer(Map<?, ?> raw) {
        Object materialValue = raw.get("material");
        if (materialValue == null) {
            return null;
        }
        Material material = Material.matchMaterial(materialValue.toString());
        if (material == null || !isMerchantPoolItem(material)) {
            return null;
        }
        String rarity = merchantRarity(material);
        int configuredPrice = raw.containsKey("price") ? parsePositiveInt(String.valueOf(raw.get("price")), -1) : -1;
        int price = Math.max(materialPrice(material), configuredPrice);
        return new MerchantOffer(material, 1, rarity, price);
    }

    private boolean isMerchantPoolItem(Material material) {
        String name = material.name();
        return isPricedShopItem(material)
                && !name.startsWith("LEGACY_")
                && !name.startsWith("INFESTED_")
                && !name.endsWith("_COMMAND_BLOCK")
                && !Set.of(
                "AIR",
                "BARRIER",
                "BEDROCK",
                "COMMAND_BLOCK",
                "CHAIN_COMMAND_BLOCK",
                "REPEATING_COMMAND_BLOCK",
                "COMMAND_BLOCK_MINECART",
                "STRUCTURE_BLOCK",
                "STRUCTURE_VOID",
                "JIGSAW",
                "LIGHT",
                "DEBUG_STICK",
                "KNOWLEDGE_BOOK").contains(name);
    }

    private boolean isMerchantWeightedPoolItem(Material material, Map<String, Integer> weights) {
        return isMerchantPoolItem(material) && weights.getOrDefault(material.name(), 0) > 0;
    }

    private boolean isBarrelShopPoolItem(Material material) {
        String name = material.name();
        return isPricedShopItem(material)
                && barrelShopConfigs.containsKey(name)
                && !name.endsWith("_SPAWN_EGG")
                && !MERCHANT_EXCLUDED_ITEMS.contains(material);
    }

    private boolean isPricedShopItem(Material material) {
        return material != null && material.isItem() && shopSalePrices.getOrDefault(material.name(), 0) > 0;
    }

    private String merchantRarity(Material material) {
        int price = materialPrice(material);
        String name = material.name();
        if (price >= 1000 || name.contains("NETHERITE") || name.equals("ELYTRA") || name.equals("ENCHANTED_GOLDEN_APPLE")
                || name.endsWith("_TEMPLATE") || name.endsWith("_HEAD") || name.endsWith("_SKULL")) {
            return "epic";
        }
        if (price >= 100 || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("GOLDEN")
                || name.contains("TOTMP") || name.contains("HEART_OF_THE_SEA")
                || name.contains("TRIDENT") || name.endsWith("_SPEAR") || name.contains("SHULKER_BOX")) {
            return "rare";
        }
        if (price >= 10 || name.contains("IRON") || name.contains("GOLD") || name.contains("COPPER")
                || name.contains("REDSTONE") || name.contains("LAPIS") || name.contains("QUARTZ")
                || name.contains("AMETHYST") || name.contains("ENDER") || name.contains("BLAZE")) {
            return "uncommon";
        }
        return "common";
    }

    private boolean isMerchantRarityAtLeast(String rarity, String minimum) {
        return merchantRarityRank(rarity) >= merchantRarityRank(minimum);
    }

    private int merchantRarityRank(String rarity) {
        return switch ((rarity == null ? "" : rarity).toLowerCase(Locale.ROOT)) {
            case "epic" -> 3;
            case "rare" -> 2;
            case "uncommon" -> 1;
            default -> 0;
        };
    }

    private int materialPrice(Material material) {
        String name = material.name();
        Integer configuredPrice = shopSalePrices.get(name);
        if (configuredPrice != null && configuredPrice > 0) {
            return configuredPrice;
        }
        Integer exact = exactMaterialPrice(material);
        if (exact != null) {
            return exact;
        }
        if (name.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) {
            return 1000;
        }
        if (name.endsWith("_SMITHING_TEMPLATE")) {
            return 150;
        }
        if (name.equals("IRON_DOOR") || name.equals("IRON_TRAPDOOR")) {
            return 40;
        }
        if (name.equals("HEAVY_WEIGHTED_PRESSURE_PLATE")) {
            return 20;
        }
        if (name.equals("LIGHT_WEIGHTED_PRESSURE_PLATE")) {
            return 50;
        }
        int baseFromStorage = storageMaterialPrice(name);
        if (baseFromStorage > 0) {
            return baseFromStorage;
        }
        int equipment = equipmentPrice(name);
        if (equipment > 0) {
            return equipment;
        }
        if (name.endsWith("_ORE")) {
            return Math.max(8, priceByContainedResource(name));
        }
        if (name.startsWith("RAW_") && !name.endsWith("_BLOCK")) {
            return switch (name) {
                case "RAW_IRON" -> 8;
                case "RAW_GOLD" -> 20;
                case "RAW_COPPER" -> 3;
                default -> 4;
            };
        }
        if (name.endsWith("_LOG") || name.endsWith("_STMP") || name.endsWith("_HYPHAE")) {
            return 2;
        }
        if (name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING")
                || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")) {
            return 1;
        }
        if (name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_FENCE") || name.endsWith("_WALL")) {
            return 2;
        }
        if (name.endsWith("_WOOL") || name.endsWith("_CARPET") || name.endsWith("_TERRACOTTA")
                || name.endsWith("_CONCRETE") || name.endsWith("_CONCRETE_POWDER")) {
            return 2;
        }
        if (name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE")) {
            return 2;
        }
        if (name.endsWith("_SHULKER_BOX")) {
            return 150;
        }
        if (name.endsWith("_BANNER") || name.endsWith("_BED")) {
            return 5;
        }
        if (name.endsWith("_BOAT") || name.endsWith("_SIGN") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")) {
            return 4;
        }
        if (name.contains("POTION") || name.contains("ENCHANTED_BOOK")) {
            return 100;
        }
        if (name.contains("MUSIC_DISC") || name.contains("POTTERY_SHERD") || name.contains("ARMOR_TRIM")) {
            return 100;
        }
        if (name.contains("SPONGE") || name.contains("SEA_LANTERN") || name.contains("PRISMARINE")
                || name.contains("END_ROD") || name.contains("PURPUR")) {
            return 20;
        }
        if (name.contains("SCULK") || name.contains("ECHO_SHARD") || name.contains("RECOVERY_COMPASS")) {
            return 50;
        }
        if (name.contains("DEEPSLATE") || name.contains("TUFF") || name.contains("CALCITE")
                || name.contains("DRIPSTONE") || name.contains("BLACKSTONE") || name.contains("BASALT")) {
            return 2;
        }
        if (name.contains("NETHER") || name.contains("END_")) {
            return 8;
        }
        if (name.contains("FLOWER") || name.contains("CORAL") || name.contains("FUNGUS") || name.contains("MUSHROOM")
                || name.contains("ROOTS") || name.contains("VINES")) {
            return 3;
        }
        if (name.contains("SUSPICIOUS") || name.contains("TRIAL") || name.contains("OMINOUS")) {
            return 50;
        }
        return material.isBlock() ? 1 : 3;
    }

    private int materialBuyPrice(Material material) {
        int configuredBuyPrice = shopBuyPrices.getOrDefault(material.name(), 0);
        if (configuredBuyPrice <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_EMERALDS, configuredBuyPrice));
    }

    private Integer exactMaterialPrice(Material material) {
        return switch (material) {
            case DIRT, GRASS_BLOCK, SAND, GRAVEL, COBBLESTONE, STONE, NETHERRACK, END_STONE -> 1;
            case CLAY, BRICK, BRICKS -> 4;
            case COAL, CHARCOAL -> 4;
            case COAL_BLOCK -> 36;
            case COPPER_INGOT -> 4;
            case COPPER_BLOCK, WAXED_COPPER_BLOCK -> 36;
            case IRON_NUGGET -> 2;
            case IRON_INGOT -> 10;
            case IRON_BLOCK -> 90;
            case GOLD_NUGGET -> 3;
            case GOLD_INGOT -> 25;
            case GOLD_BLOCK -> 225;
            case REDSTONE -> 5;
            case REDSTONE_BLOCK -> 45;
            case LAPIS_LAZULI -> 8;
            case LAPIS_BLOCK -> 72;
            case QUARTZ -> 8;
            case QUARTZ_BLOCK -> 32;
            case AMETHYST_SHARD -> 8;
            case AMETHYST_BLOCK -> 32;
            case DIAMOND -> 100;
            case DIAMOND_BLOCK -> 900;
            case EMERALD -> 100;
            case EMERALD_BLOCK -> 900;
            case NETHERITE_SCRAP -> 250;
            case NETHERITE_INGOT -> 1100;
            case NETHERITE_BLOCK -> 9900;
            case OBSIDIAN -> 20;
            case CRYING_OBSIDIAN -> 35;
            case ANCIENT_DEBRIS -> 300;
            case BLAZE_ROD -> 20;
            case BLAZE_POWDER -> 10;
            case ENDER_PEARL -> 20;
            case ENDER_EYE -> 35;
            case GHAST_TEAR -> 50;
            case MAGMA_CREAM -> 12;
            case SLIME_BALL -> 10;
            case PHANTOM_MEMBRANE -> 30;
            case SHULKER_SHELL -> 75;
            case TOTEM_OF_UNDYING -> 250;
            case HEART_OF_THE_SEA -> 250;
            case NAUTILUS_SHELL -> 30;
            case TRIDENT -> 300;
            case ELYTRA -> 1500;
            case DRAGON_BREATH -> 100;
            case NETHER_STAR -> 2000;
            case EXPERIENCE_BOTTLE -> 20;
            case GOLDEN_APPLE -> 80;
            case ENCHANTED_GOLDEN_APPLE -> 1500;
            case APPLE, BREAD, CARROT, POTATO, BEETROOT, WHEAT, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> 2;
            case COOKED_BEEF, COOKED_PORKCHOP, COOKED_MUTTON, COOKED_CHICKEN, COOKED_COD, COOKED_SALMON -> 5;
            case BEEF, PORKCHOP, MUTTON, CHICKEN, COD, SALMON -> 3;
            case TORCH -> 1;
            case LANTERN, SOUL_LANTERN -> 12;
            case BOOK -> 6;
            case BOOKSHELF -> 20;
            case PAPER -> 1;
            case LEATHER -> 8;
            case STRING -> 3;
            case FEATHER -> 3;
            case GUNPOWDER -> 8;
            case BONE -> 3;
            case ROTTEN_FLESH -> 1;
            case SPIDER_EYE -> 4;
            case NAME_TAG -> 80;
            case SADDLE -> 80;
            case LEAD -> 15;
            case BEACON -> 2500;
            case CONDUIT -> 600;
            default -> null;
        };
    }

    private int storageMaterialPrice(String name) {
        if (name.endsWith("_BLOCK")) {
            String base = name.substring(0, name.length() - "_BLOCK".length());
            return switch (base) {
                case "RAW_IRON" -> 72;
                case "RAW_GOLD" -> 180;
                case "RAW_COPPER" -> 27;
                case "HONEY" -> 36;
                case "SLIME" -> 90;
                default -> 0;
            };
        }
        if (name.endsWith("_BUNDLE") || name.endsWith("_BOX")) {
            return 20;
        }
        return 0;
    }

    private int equipmentPrice(String name) {
        int material = equipmentMaterialBase(name);
        if (material <= 0) {
            return 0;
        }
        int units = equipmentUnits(name);
        if (units <= 0) {
            return 0;
        }
        int enchantablePremium = name.contains("NETHERITE") ? 200 : 0;
        return material * units + enchantablePremium;
    }

    private int equipmentMaterialBase(String name) {
        if (name.startsWith("WOODEN_")) {
            return 2;
        }
        if (name.startsWith("STONE_")) {
            return 1;
        }
        if (name.startsWith("LEATHER_")) {
            return 8;
        }
        if (name.startsWith("CHAINMAIL_")) {
            return 12;
        }
        if (name.startsWith("IRON_")) {
            return 10;
        }
        if (name.startsWith("GOLDEN_")) {
            return 25;
        }
        if (name.startsWith("COPPER_")) {
            return 4;
        }
        if (name.startsWith("DIAMOND_")) {
            return 100;
        }
        if (name.startsWith("NETHERITE_")) {
            return 1100;
        }
        return 0;
    }

    private int equipmentUnits(String name) {
        if (name.endsWith("_HELMET")) {
            return 5;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return 8;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 7;
        }
        if (name.endsWith("_BOOTS")) {
            return 4;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_HOE") || name.endsWith("_SPEAR")) {
            return 2;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) {
            return 3;
        }
        if (name.endsWith("_SHOVEL")) {
            return 1;
        }
        if (name.endsWith("_HORSE_ARMOR")) {
            return 6;
        }
        return 0;
    }

    private int priceByContainedResource(String name) {
        if (name.contains("DIAMOND")) {
            return 100;
        }
        if (name.contains("EMERALD")) {
            return 100;
        }
        if (name.contains("GOLD")) {
            return 25;
        }
        if (name.contains("IRON")) {
            return 10;
        }
        if (name.contains("LAPIS")) {
            return 8;
        }
        if (name.contains("REDSTONE")) {
            return 5;
        }
        if (name.contains("COPPER")) {
            return 4;
        }
        if (name.contains("COAL")) {
            return 4;
        }
        if (name.contains("QUARTZ")) {
            return 8;
        }
        return 4;
    }

    private void tickMerchants() {
        long now = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof AbstractVillager villager) || !isMinervaMerchant(entity)) {
                    continue;
                }
                villager.setInvulnerable(false);
                villager.setAI(activeMerchantViews.containsValue(villager.getUniqueId()) ? false : true);
                PersistentDataContainer container = entity.getPersistentDataContainer();
                long spawnedAt = container.getOrDefault(merchantSpawnKey, PersistentDataType.LONG, now);
                if (now - spawnedAt < MERCHANT_REROLL_MILLIS) {
                    continue;
                }
                boolean traded = Boolean.TRUE.equals(container.get(merchantTradedKey, PersistentDataType.BOOLEAN));
                if (!traded) {
                    entity.remove();
                    continue;
                }
                rerollMerchant(villager);
                container.set(merchantSpawnKey, PersistentDataType.LONG, now);
                container.set(merchantTradedKey, PersistentDataType.BOOLEAN, false);
            }
            trySpawnRandomMerchant(world);
        }
    }

    private void trySpawnRandomMerchant(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL
                || world.getName().equalsIgnoreCase("elysion")
                || world.getName().equalsIgnoreCase("ginnungagap")
                || random.nextDouble() >= 0.005D
                || world.getPlayers().isEmpty()) {
            return;
        }
        Player anchor = world.getPlayers().get(random.nextInt(world.getPlayers().size()));
        Location base = anchor.getLocation().clone().add(random.nextInt(33) - 16, 0, random.nextInt(33) - 16);
        int y = world.getHighestBlockYAt(base);
        Location spawn = new Location(world, base.getBlockX() + 0.5, y + 1.0, base.getBlockZ() + 0.5);
        if (isCentralPlazaLocation(spawn)) {
            return;
        }
        spawnMerchant(spawn);
    }

    private boolean isCentralPlazaLocation(Location location) {
        return location != null && protectionService.isSpawnProtected(location);
    }

    boolean isStructureProtectedLocation(Location location) {
        return protectionService.isProtected(location);
    }

    private boolean isMinervaMerchant(Entity entity) {
        return Boolean.TRUE.equals(entity.getPersistentDataContainer().get(merchantKey, PersistentDataType.BOOLEAN));
    }

    @EventHandler
    public void onMerchantDamage(EntityDamageEvent event) {
        if (!isMinervaMerchant(event.getEntity())) {
            return;
        }
        event.getEntity().setInvulnerable(false);
        if (isPlayerCausedDamage(event)) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
    }

    private boolean isPlayerCausedDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent entityDamage)) {
            return false;
        }
        Entity damager = entityDamage.getDamager();
        if (damager instanceof Player) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Player;
        }
        return false;
    }

    @EventHandler
    public void onPlayerTrade(PlayerTradeEvent event) {
        addPlayerStat(event.getPlayer().getUniqueId(), "total-trades", 1);
        if (isMinervaMerchant(event.getVillager())) {
            event.getVillager().getPersistentDataContainer().set(merchantTradedKey, PersistentDataType.BOOLEAN, true);
        }
    }

    @EventHandler
    public void onMerchantInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractVillager villager) || !isMinervaMerchant(villager)) {
            return;
        }
        event.setCancelled(true);
        villager.setAI(false);
        villager.setInvulnerable(false);
        openMerchantUi(event.getPlayer(), villager);
    }

    private void openMerchantUi(Player player, AbstractVillager villager) {
        activeMerchantViews.put(player.getUniqueId(), villager.getUniqueId());
        Inventory inventory = Bukkit.createInventory(player, 18, Component.text(MERCHANT_UI_TITLE));
        List<MerchantOffer> sellOffers = readMerchantOffers(villager.getUniqueId(), "sell");
        List<MerchantOffer> buyOffers = readMerchantOffers(villager.getUniqueId(), "buy");
        if (sellOffers.isEmpty() || buyOffers.isEmpty()) {
            rerollMerchant(villager);
            sellOffers = readMerchantOffers(villager.getUniqueId(), "sell");
            buyOffers = readMerchantOffers(villager.getUniqueId(), "buy");
        }
        inventory.setItem(0, named(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + "購入",
                List.of(ChatColor.GRAY + "右側の商品をクリックで購入")));
        inventory.setItem(9, named(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "売却",
                List.of(ChatColor.GRAY + "右側の商品をクリックで売却")));
        for (int i = 0; i < Math.min(8, sellOffers.size()); i++) {
            inventory.setItem(i + 1, createMerchantOfferIcon(villager, sellOffers.get(i), "sell"));
        }
        for (int i = 0; i < Math.min(8, buyOffers.size()); i++) {
            inventory.setItem(i + 10, createMerchantOfferIcon(villager, buyOffers.get(i), "buy"));
        }
        player.openInventory(inventory);
    }

    private ItemStack createMerchantOfferIcon(AbstractVillager villager, MerchantOffer offer, String action) {
        ItemStack item = new ItemStack(offer.material(), 1);
        ItemMeta meta = item.getItemMeta();
        boolean selling = "sell".equals(action);
        meta.displayName(Component.translatable(offer.material().translationKey())
                .color(rarityTextColor(offer.rarity())));
        meta.lore(List.of(
                Component.text(ChatColor.GRAY + "レア度: " + rarityLabel(offer.rarity())),
                Component.text(ChatColor.GRAY + (selling ? "価格: " : "買取額: ") + formatNumber(offer.price()) + "MP"),
                Component.text(ChatColor.GRAY + (selling ? "クリックでMP残高から購入" : "クリックで1個売却")),
                Component.text(ChatColor.GRAY + "Shift+クリック: まとめて取引")));
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(merchantOfferKey, PersistentDataType.BOOLEAN, true);
        container.set(merchantOfferPriceKey, PersistentDataType.INTEGER, offer.price());
        container.set(merchantOfferMaterialKey, PersistentDataType.STRING, offer.material().name());
        container.set(merchantOfferAmountKey, PersistentDataType.INTEGER, 1);
        container.set(merchantOfferMerchantKey, PersistentDataType.STRING, villager.getUniqueId().toString());
        container.set(merchantOfferActionKey, PersistentDataType.STRING, action);
        container.set(merchantOfferRarityKey, PersistentDataType.STRING, offer.rarity());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMerchantOffer(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(merchantOfferKey, PersistentDataType.BOOLEAN));
    }

    private void buyMerchantOffer(Player player, ItemStack clicked, boolean bulk) {
        long now = System.currentTimeMillis();
        long last = lastMerchantTransaction.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < MERCHANT_TRANSACTION_COOLDOWN_MILLIS) {
            return;
        }
        if (!merchantTransactions.add(player.getUniqueId())) {
            return;
        }
        lastMerchantTransaction.put(player.getUniqueId(), now);
        try {
            buyMerchantOfferNow(player, clicked, bulk);
        } finally {
            Bukkit.getScheduler().runTaskLater(this, () -> merchantTransactions.remove(player.getUniqueId()), 3L);
        }
    }

    private void buyMerchantOfferNow(Player player, ItemStack clicked, boolean bulk) {
        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer price = container.get(merchantOfferPriceKey, PersistentDataType.INTEGER);
        Integer amount = container.get(merchantOfferAmountKey, PersistentDataType.INTEGER);
        String materialName = container.get(merchantOfferMaterialKey, PersistentDataType.STRING);
        String action = container.get(merchantOfferActionKey, PersistentDataType.STRING);
        if (price == null || amount == null || materialName == null || action == null) {
            player.sendMessage(ChatColor.RED + "商人の商品データが不正です。");
            return;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            player.sendMessage(ChatColor.RED + "商人の商品が不正です。");
            return;
        }
        price = Math.min(MAX_EMERALDS, Math.max(1, price));
        amount = Math.min(MAX_SHOP_STACKS_PER_CLICK, Math.max(1, amount));
        if ("buy".equals(action)) {
            int quantity = bulk ? maxMerchantSaleQuantity(player, material) : 1;
            if (bulk && quantity <= 0) {
                player.sendMessage(ChatColor.RED + "このアイテムは一括取引できません。");
                return;
            }
            MerchantSale sale = quantity <= 0 ? null : removeItemsForMerchantSale(player, material, quantity, price);
            if (sale == null) {
                sendItemMessage(player, NamedTextColor.RED, "", material, "を1個持っていません。");
                return;
            }
            depositEmeralds(player.getUniqueId(), sale.totalPrice());
            addPlayerStat(player.getUniqueId(), "total-trades", sale.quantity());
            markMerchantTraded(container.get(merchantOfferMerchantKey, PersistentDataType.STRING));
            playPurchaseSound(player);
            sendItemMessage(player, NamedTextColor.GREEN, "売却しました: ", material,
                    " x" + sale.quantity() + " (+" + formatNumber(sale.totalPrice()) + "MP)");
            return;
        }
        int quantity = bulk ? maxMerchantPurchaseQuantity(player, material, price) : 1;
        if (bulk && material.getMaxStackSize() <= 1) {
            player.sendMessage(ChatColor.RED + "このアイテムは一括購入できません。");
            return;
        }
        if (quantity <= 0) {
            if (getEmeralds(player.getUniqueId()) < price) {
                player.sendMessage(ChatColor.RED + "MPが足りません。必要MP: " + formatNumber(price));
            } else {
                player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
            }
            return;
        }
        if (inventorySpaceFor(player, material) < quantity) {
            player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
            return;
        }
        int total = safeMultiply(price, quantity);
        if (!withdrawEmeralds(player.getUniqueId(), total)) {
            player.sendMessage(ChatColor.RED + "MPが足りません。必要MP: " + formatNumber(total));
            return;
        }
        giveShopPurchasedItems(player, material, quantity);
        addPlayerStat(player.getUniqueId(), "total-trades", quantity);
        markMerchantTraded(container.get(merchantOfferMerchantKey, PersistentDataType.STRING));
        playPurchaseSound(player);
        sendItemMessage(player, NamedTextColor.GREEN, "購入しました: ", material,
                " x" + quantity + " (" + formatNumber(total) + "MP)");
    }

    private void sendItemMessage(Player player, NamedTextColor color, String prefix, Material material, String suffix) {
        player.sendMessage(Component.text(prefix, color)
                .append(Component.translatable(material.translationKey()).color(color))
                .append(Component.text(suffix, color)));
    }

    private void tryReincarnate(Player player, ItemStack star) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        if (!section.getBoolean("all-advancements-rewarded", false)) {
            player.sendMessage(ChatColor.RED + "全進捗達成後に転生できます。");
            return;
        }
        int next = section.getInt("reincarnations", 0) + 1;
        int requiredEmeralds = safeMultiply(10000, next);
        int requiredLevel = Math.min(1000, 20 + Math.max(0, next) * 10);
        int currentEmeralds = getEmeralds(player.getUniqueId());
        int currentLevel = player.getLevel();
        if (currentEmeralds < requiredEmeralds || currentLevel < requiredLevel) {
            player.sendMessage(ChatColor.RED + "転生条件を満たしていません。必要: "
                    + formatNumber(requiredEmeralds) + "MP / Lv" + requiredLevel);
            return;
        }
        int bonus = Math.max(0, currentEmeralds / 1000 + currentLevel);
        if (star != null) {
            consumeOne(star);
        }
        section.set("emeralds", 0);
        section.set("advancement-bonus-percent", 0);
        section.set("income-bonus-percent", null);
        section.set("reincarnations", next);
        section.set("reincarnation-bonus-percent", getReincarnationBonus(player.getUniqueId()) + bonus);
        player.setLevel(0);
        player.setExp(0);
        saveData();
        recordQuestProgress(player, "reincarnations", next);
        playReincarnationSound(player);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "転生しました: " + next + "回目 / 転生ボーナス+" + bonus + "%");
    }

    private void resetAdvancements(Player player) {
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (!shouldTrackAdvancement(advancement)) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    private void applyPendingAdvancementReset(Player player) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        if (!section.getBoolean("pending-advancement-reset", false)) {
            return;
        }
        resetAdvancements(player);
        section.set("pending-advancement-reset", null);
        saveData();
    }

    private void playPurchaseSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.1f);
    }

    private void playUiClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.25f);
    }

    private void playTeleportSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
    }

    private void playReincarnationSound(Player player) {
        Location location = player.getLocation();
        player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private MerchantSale removeItemsForMerchantSale(Player player, Material material, int amount, int basePrice) {
        int available = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMerchantSellableStack(item, material)) {
                available += item.getAmount();
            }
        }
        if (available < amount) {
            return null;
        }
        int remaining = amount;
        int totalPrice = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isMerchantSellableStack(item, material)) {
                continue;
            }
            int removed = Math.min(item.getAmount(), remaining);
            totalPrice = safeAdd(totalPrice, safeMultiply(merchantSaleUnitPrice(basePrice, item), removed));
            item.setAmount(item.getAmount() - removed);
            remaining -= removed;
            if (item.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        return new MerchantSale(amount, totalPrice);
    }

    private int countItemsByType(Player player, Material material) {
        int available = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMerchantSellableStack(item, material)) {
                available += item.getAmount();
            }
        }
        return available;
    }

    private boolean isMerchantSellableStack(ItemStack item, Material material) {
        return item != null
                && item.getType() == material
                && utilityItemsFeature.getMinervaItemId(item) == null
                && !isMerchantOffer(item);
    }

    private int merchantSaleUnitPrice(int basePrice, ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return Math.max(1, basePrice);
        }
        int maxDurability = item.getType().getMaxDurability();
        int remainingDurability = Math.max(1, maxDurability - damageable.getDamage());
        return Math.max(1, (int) ((long) Math.max(1, basePrice) * remainingDurability / maxDurability));
    }

    private int maxMerchantPurchaseQuantity(Player player, Material material, int price) {
        int maxStack = material.getMaxStackSize();
        if (maxStack <= 1 || price <= 0) {
            return 0;
        }
        int affordable = getEmeralds(player.getUniqueId()) / price;
        int space = inventorySpaceFor(player, material);
        return Math.max(0, Math.min(Math.min(affordable, space), maxStack));
    }

    private int maxMerchantSaleQuantity(Player player, Material material) {
        int maxStack = material.getMaxStackSize();
        if (maxStack <= 1) {
            return 0;
        }
        return Math.min(countItemsByType(player, material), maxStack);
    }

    private int inventorySpaceFor(Player player, Material material) {
        int space = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += maxStack;
            } else if (item.getType() == material && item.getAmount() < maxStack) {
                space += maxStack - item.getAmount();
            }
        }
        return space;
    }

    private void giveShopPurchasedItems(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStack = material.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createShopPurchasedItem(material, stackAmount));
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            remaining -= stackAmount;
        }
    }

    private String rarityLabel(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "epic" -> ChatColor.LIGHT_PURPLE + "エピック";
            case "rare" -> ChatColor.BLUE + "レア";
            case "uncommon" -> ChatColor.GREEN + "アンコモン";
            default -> ChatColor.WHITE + "コモン";
        };
    }

    private String rarityName(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "epic" -> "エピック";
            case "rare" -> "レア";
            case "uncommon" -> "アンコモン";
            default -> "コモン";
        };
    }

    private String rarityChatColor(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "epic" -> ChatColor.LIGHT_PURPLE;
            case "rare" -> ChatColor.BLUE;
            case "uncommon" -> ChatColor.GREEN;
            default -> ChatColor.WHITE;
        };
    }

    private NamedTextColor rarityTextColor(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "epic" -> NamedTextColor.LIGHT_PURPLE;
            case "rare" -> NamedTextColor.BLUE;
            case "uncommon" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    private String japaneseItemName(Material material) {
        return switch (material) {
            case OAK_LOG -> "オークの原木";
            case COBBLESTONE -> "丸石";
            case BREAD -> "パン";
            case TORCH -> "松明";
            case COAL -> "石炭";
            case GLASS -> "ガラス";
            case BRICKS -> "レンガブロック";
            case IRON_INGOT -> "鉄インゴット";
            case GOLD_INGOT -> "金インゴット";
            case REDSTONE -> "レッドストーン";
            case LAPIS_LAZULI -> "ラピスラズリ";
            case QUARTZ -> "ネザークォーツ";
            case AMETHYST_SHARD -> "アメジストの欠片";
            case DIAMOND -> "ダイヤモンド";
            case EMERALD -> "エメラルド";
            case GOLDEN_APPLE -> "金のリンゴ";
            case ENCHANTED_GOLDEN_APPLE -> "エンチャントされた金のリンゴ";
            case NETHERITE_UPGRADE_SMITHING_TEMPLATE -> "ネザライト強化の鍛冶型";
            default -> toReadableMaterialName(material);
        };
    }

    private String toReadableMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
            }
        }
        return String.join(" ", words);
    }

    private void markMerchantTraded(String rawUuid) {
        if (rawUuid == null) {
            return;
        }
        try {
            UUID merchantId = UUID.fromString(rawUuid);
            for (World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(merchantId);
                if (entity != null && isMinervaMerchant(entity)) {
                    entity.getPersistentDataContainer().set(merchantTradedKey, PersistentDataType.BOOLEAN, true);
                    return;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore stale offer data.
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.isEmpty() || normalized.length() > 10) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed > Integer.MAX_VALUE) {
                return fallback;
            }
            return (int) Math.max(0L, parsed);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String sanitizeTextInput(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", "").trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private void openFriendUi(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, Component.text(FRIEND_UI_TITLE));
        String filter = friendSearchFilters.getOrDefault(player.getUniqueId(), "");
        inventory.setItem(0, actionItem(Material.OAK_SIGN, ChatColor.YELLOW + "検索バー",
                List.of(ChatColor.GRAY + (filter.isBlank() ? "クリックして検索語を入力" : "検索中: " + filter),
                        ChatColor.GRAY + "空入力で検索解除"), "friend_search", null));
        fillFriendTopTabs(inventory);

        fillFriendRows(player, inventory, filter);
        fillNotificationOrChatBox(player, inventory);
        fillStatusBox(player, inventory);
        player.openInventory(inventory);
    }

    private void fillFriendTopTabs(Inventory inventory) {
        inventory.setItem(1, actionItem(Material.BOOK, ChatColor.LIGHT_PURPLE + "進捗",
                List.of(ChatColor.GRAY + "進捗一覧"), "status_tab_progress", null));
        inventory.setItem(2, actionItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "転生",
                List.of(ChatColor.GRAY + "転生回数、条件、転生ボーナス"), "status_tab_reincarnation", null));
        inventory.setItem(3, actionItem(Material.NAME_TAG, ChatColor.GOLD + "称号",
                List.of(ChatColor.GRAY + "称号一覧と選択"), "status_tab_titles", null));
        inventory.setItem(4, actionItem(Material.ZOMBIE_SPAWN_EGG, ChatColor.RED + "討伐",
                List.of(ChatColor.GRAY + "Mob討伐状況"), "status_tab_kills", null));
        inventory.setItem(5, actionItem(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "クエスト",
                List.of(ChatColor.GRAY + "デイリー、ウィークリー、マンスリー、スペシャル"), "status_tab_quests", null));
    }

    private void fillFriendRows(Player player, Inventory inventory, String filter) {
        Set<UUID> friends = getUuidSet(player.getUniqueId(), "friends");
        Set<UUID> listed = new HashSet<>(friends);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                listed.add(online.getUniqueId());
            }
        }

        List<OfflinePlayer> users = listed.stream()
                .map(Bukkit::getOfflinePlayer)
                .filter(user -> matchesFriendFilter(user, filter))
                .sorted((first, second) -> {
                    if (first.isOnline() != second.isOnline()) {
                        return first.isOnline() ? -1 : 1;
                    }
                    return safePlayerName(first).compareToIgnoreCase(safePlayerName(second));
                })
                .toList();

        int row = 1;
        for (OfflinePlayer user : users) {
            if (row > 3) {
                break;
            }
            boolean friend = friends.contains(user.getUniqueId());
            boolean online = user.isOnline();
            int base = row * 9;
            inventory.setItem(base, actionItem(Material.PLAYER_HEAD, ChatColor.WHITE + safePlayerName(user),
                    List.of(ChatColor.GRAY + "プレイヤー"), "friend_profile", user.getUniqueId().toString()));
            inventory.setItem(base + 1, actionItem(Material.NAME_TAG, ChatColor.WHITE + safePlayerName(user),
                    List.of(ChatColor.GRAY + "ユーザーネーム"), "friend_profile", user.getUniqueId().toString()));
            inventory.setItem(base + 2, actionItem(online ? Material.LIME_DYE : Material.GRAY_DYE,
                    (online ? ChatColor.GREEN : ChatColor.DARK_GRAY) + (online ? "オンライン" : "オフライン"),
                    List.of(ChatColor.GRAY + "オンライン状態"), "friend_profile", user.getUniqueId().toString()));
            inventory.setItem(base + 3, actionItem(friend ? Material.RED_DYE : Material.EMERALD,
                    friend ? ChatColor.RED + "フレンド解除" : ChatColor.GREEN + "フレンド申請",
                    List.of(friend ? ChatColor.GRAY + "クリックで解除" : ChatColor.GRAY + "クリックで申請を送信"),
                    friend ? "friend_remove" : "friend_request", user.getUniqueId().toString()));
            inventory.setItem(base + 4, actionItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "チャット",
                    List.of(friend ? ChatColor.GRAY + "クリックでチャット欄を開く" : ChatColor.RED + "フレンドのみチャット可能"),
                    friend ? "friend_chat_open" : "friend_request", user.getUniqueId().toString()));
            row++;
        }
        if (row == 1) {
            inventory.setItem(9, named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "該当プレイヤーなし",
                    List.of(ChatColor.GRAY + "検索条件を変更してください")));
        }
    }

    private boolean matchesFriendFilter(OfflinePlayer player, String filter) {
        return filter == null || filter.isBlank() || safePlayerName(player).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private void fillNotificationOrChatBox(Player player, Inventory inventory) {
        UUID chatTarget = activeFriendChatTarget.get(player.getUniqueId());
        if (chatTarget != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(chatTarget);
            String draft = friendChatDrafts.getOrDefault(player.getUniqueId(), "");
            inventory.setItem(6, named(Material.BOOK, ChatColor.AQUA + "チャット欄: " + safePlayerName(target),
                    List.of(ChatColor.GRAY + "下の羽ペンで本文を入力")));
            inventory.setItem(7, actionItem(Material.WRITABLE_BOOK, ChatColor.YELLOW + "本文入力",
                    List.of(ChatColor.GRAY + (draft.isBlank() ? "未入力" : draft)), "friend_chat_input", chatTarget.toString()));
            inventory.setItem(8, actionItem(Material.LIME_CONCRETE, ChatColor.GREEN + "送信",
                    List.of(ChatColor.GRAY + (draft.isBlank() ? "本文を入力してください" : "クリックで送信")), "friend_chat_send", chatTarget.toString()));
            inventory.setItem(15, actionItem(Material.BARRIER, ChatColor.RED + "チャット欄を閉じる",
                    List.of(ChatColor.GRAY + "通知ボックスに戻る"), "friend_chat_close", chatTarget.toString()));
            return;
        }

        List<ItemStack> notifications = notificationItems(player);
        int[] slots = {6, 7, 8, 15, 16, 17, 24, 25, 26};
        inventory.setItem(6, named(Material.PAPER, ChatColor.AQUA + "通知ボックス", List.of(ChatColor.GRAY + "申請とチャット通知")));
        if (notifications.isEmpty()) {
            inventory.setItem(15, named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "通知はありません", List.of()));
            return;
        }
        for (int i = 0; i < Math.min(notifications.size(), slots.length - 1); i++) {
            inventory.setItem(slots[i + 1], notifications.get(i));
        }
    }

    private List<ItemStack> notificationItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (UUID requesterId : getUuidSet(player.getUniqueId(), "requests")) {
            OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
            items.add(actionItem(Material.EMERALD, ChatColor.YELLOW + safePlayerName(requester) + " から申請",
                    List.of(ChatColor.GRAY + "クリックで承認"), "friend_accept", requesterId.toString()));
        }
        for (String message : getPlayerSection(player.getUniqueId()).getStringList("offline-messages")) {
            items.add(named(Material.MAP, ChatColor.AQUA + "フレンドチャット",
                    List.of(ChatColor.GRAY + message)));
        }
        return items;
    }

    private void fillStatusBox(Player player, Inventory inventory) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        inventory.setItem(36, named(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "MVL / MVLランク",
                List.of(ChatColor.GRAY + "MVL: " + getMvl(player.getUniqueId()),
                        ChatColor.GRAY + "ランク: " + getMvlRank(player.getUniqueId()))));
        inventory.setItem(37, named(Material.EMERALD, ChatColor.GREEN + "所持MP",
                List.of(ChatColor.GRAY + formatNumber(getEmeralds(player.getUniqueId())) + "MP")));
        inventory.setItem(38, named(Material.EMERALD_BLOCK, ChatColor.GREEN + "総獲得MP",
                List.of(ChatColor.GRAY + formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP")));
        inventory.setItem(39, named(Material.CLOCK, ChatColor.YELLOW + "総プレイ時間",
                List.of(ChatColor.GRAY + formatPlayTime(section.getInt("total-minutes", 0)))));
        inventory.setItem(40, named(Material.CAMPFIRE, ChatColor.GOLD + "総プレイ回数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-play-count", 0))));
        inventory.setItem(41, named(Material.TRADER_LLAMA_SPAWN_EGG, ChatColor.YELLOW + "総取引回数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-trades", 0))));
        inventory.setItem(42, named(Material.IRON_PICKAXE, ChatColor.BLUE + "破壊 / 設置",
                List.of(ChatColor.GRAY + "破壊: " + section.getInt("total-blocks-broken", 0),
                        ChatColor.GRAY + "設置: " + section.getInt("total-blocks-placed", 0))));
        inventory.setItem(43, named(Material.IRON_SWORD, ChatColor.RED + "総モブ討伐数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-mob-kills", 0))));
        inventory.setItem(44, named(Material.COMPASS, ChatColor.AQUA + "現在地",
                List.of(ChatColor.GRAY + player.getWorld().getName(),
                        ChatColor.GRAY + "X " + player.getLocation().getBlockX()
                                + " Y " + player.getLocation().getBlockY()
                                + " Z " + player.getLocation().getBlockZ())));
    }

    private void openDetailedStatusUi(Player player) {
        openStatusUi(player, "progress:0");
    }

    private void openStatusUi(Player player, String tab) {
        syncAdvancementState(player);
        Inventory inventory = Bukkit.createInventory(player, 54, Component.text(FRIEND_STATUS_UI_TITLE));
        fillStatusTabs(inventory, tab.startsWith("quests") ? "quests" : tab);
        if (tab.startsWith("progress")) {
            fillProgressTab(player, inventory, tabPage(tab, "progress"));
        } else if (tab.startsWith("kills")) {
            fillKillsTab(player, inventory, tabPage(tab, "kills"));
        } else if (tab.startsWith("titles")) {
            fillTitlesTab(player, inventory, tabPage(tab, "titles"));
        } else if ("reincarnation".equals(tab)) {
            fillReincarnationTab(player, inventory);
        } else if ("quests".equals(tab)) {
            fillQuestCategoryTab(inventory);
        } else if (tab.startsWith("quests:")) {
            fillQuestListTab(player, inventory, questTypeFromTab(tab));
        } else {
            fillProgressTab(player, inventory, 0);
        }
        inventory.setItem(53, actionItem(Material.ARROW, ChatColor.WHITE + "戻る",
                List.of(ChatColor.GRAY + "フレンド画面に戻る"), "friend_status_back", null));
        player.openInventory(inventory);
    }

    private void fillStatusTabs(Inventory inventory, String activeTab) {
        String normalizedTab = activeStatusTab(activeTab);
        inventory.setItem(0, switch (normalizedTab) {
            case "reincarnation" -> named(Material.NETHER_STAR, ChatColor.GOLD + "転生", List.of());
            case "titles" -> named(Material.NAME_TAG, ChatColor.GOLD + "称号", List.of());
            case "kills" -> named(Material.ZOMBIE_SPAWN_EGG, ChatColor.GOLD + "討伐", List.of());
            case "quests" -> named(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "クエスト", List.of());
            default -> named(Material.BOOK, ChatColor.GOLD + "進捗", List.of());
        });
    }

    private String activeStatusTab(String activeTab) {
        if (activeTab == null) {
            return "progress";
        }
        if (activeTab.startsWith("progress")) {
            return "progress";
        }
        if (activeTab.startsWith("titles")) {
            return "titles";
        }
        if (activeTab.startsWith("kills")) {
            return "kills";
        }
        if (activeTab.startsWith("quests")) {
            return "quests";
        }
        if ("reincarnation".equals(activeTab)) {
            return "reincarnation";
        }
        return "progress";
    }

    private void fillQuestCategoryTab(Inventory inventory) {
        inventory.setItem(20, actionItem(Material.CLOCK, ChatColor.AQUA + "デイリー",
                List.of(ChatColor.GRAY + "毎日5件抽選 + 完全達成"), "quest_category", QuestType.DAILY.key()));
        inventory.setItem(21, actionItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "ウィークリー",
                List.of(ChatColor.GRAY + "毎週5件抽選 + 完全達成"), "quest_category", QuestType.WEEKLY.key()));
        inventory.setItem(23, actionItem(Material.MAP, ChatColor.AQUA + "マンスリー",
                List.of(ChatColor.GRAY + "10件固定表示"), "quest_category", QuestType.MONTHLY.key()));
        inventory.setItem(24, actionItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "スペシャル",
                List.of(ChatColor.GRAY + "条件達成で解放"), "quest_category", QuestType.SPECIAL.key()));
    }

    private void fillQuestListTab(Player player, Inventory inventory, QuestType type) {
        questService.ensurePeriods(player);
        inventory.setItem(45, named(Material.PAPER, ChatColor.YELLOW + type.label() + "クエスト",
                List.of(ChatColor.GRAY + "残り時間: " + questService.remainingTime(type))));
        inventory.setItem(48, actionItem(Material.ARROW, ChatColor.WHITE + "カテゴリに戻る",
                List.of(ChatColor.GRAY + "クエストカテゴリ"), "status_tab_quests", null));
        int slot = 18;
        for (QuestDefinition definition : questService.visibleQuests(player, type)) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, questItem(player, definition));
        }
    }

    private ItemStack questItem(Player player, QuestDefinition definition) {
        boolean unlocked = questService.isUnlocked(player, definition);
        if (!unlocked) {
            return actionItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "？？？",
                    List.of(ChatColor.GRAY + "未解放スペシャル"), "locked_quest", definition.id());
        }
        int progress = questService.progress(player, definition);
        boolean completed = questService.isCompleted(player, definition);
        boolean claimed = questService.isClaimed(player, definition);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "条件: " + definition.condition());
        lore.add(ChatColor.GRAY + "現在進捗: " + Math.min(progress, definition.required()));
        lore.add(ChatColor.GRAY + "必要数: " + definition.required());
        lore.add(ChatColor.GRAY + "基礎報酬MP: " + formatNumber(definition.baseReward()));
        lore.add(ChatColor.GRAY + "転生補正後MP: " + formatNumber(questService.effectiveReward(player, definition)));
        lore.add(ChatColor.GRAY + "残り時間: " + questService.remainingTime(definition.type()));
        lore.add((completed ? ChatColor.GREEN : ChatColor.YELLOW) + "達成状態: " + (completed ? "達成済み" : "未達成"));
        lore.add((claimed ? ChatColor.GREEN : ChatColor.GOLD) + "受取状態: " + (claimed ? "受取済み" : "未受取"));
        if (completed && !claimed) {
            lore.add(ChatColor.YELLOW + "クリックで受取");
        }
        String color = claimed ? ChatColor.DARK_GRAY : completed ? ChatColor.GOLD : ChatColor.WHITE;
        ItemStack item = actionItem(claimed ? Material.LIME_STAINED_GLASS_PANE : definition.icon(),
                color + definition.id() + " " + definition.name(), lore, "quest_claim", definition.id());
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(completed && !claimed);
        item.setItemMeta(meta);
        return item;
    }

    private QuestType questTypeFromTab(String tab) {
        String key = tab.substring("quests:".length()).toLowerCase(Locale.ROOT);
        for (QuestType type : QuestType.values()) {
            if (type.key().equals(key)) {
                return type;
            }
        }
        return QuestType.DAILY;
    }

    private void fillStatusTab(Player player, Inventory inventory) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        inventory.setItem(10, named(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "MVL / MVLランク",
                List.of(ChatColor.GRAY + "MVL: " + getMvl(player.getUniqueId()),
                        ChatColor.GRAY + "ランク: " + getMvlRank(player.getUniqueId()))));
        inventory.setItem(11, named(Material.EMERALD, ChatColor.GREEN + "所持MP",
                List.of(ChatColor.GRAY + formatNumber(getEmeralds(player.getUniqueId())) + "MP")));
        inventory.setItem(12, named(Material.EMERALD_BLOCK, ChatColor.GREEN + "総獲得MP",
                List.of(ChatColor.GRAY + formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP")));
        inventory.setItem(13, named(Material.CLOCK, ChatColor.YELLOW + "総プレイ時間",
                List.of(ChatColor.GRAY + formatPlayTime(section.getInt("total-minutes", 0)))));
        inventory.setItem(14, named(Material.CAMPFIRE, ChatColor.GOLD + "総プレイ回数 / 連続ログイン",
                List.of(ChatColor.GRAY + "総プレイ回数: " + section.getInt("total-play-count", 0),
                        ChatColor.GRAY + "連続ログイン: " + section.getInt("login-streak", 0) + "日")));
        inventory.setItem(19, named(Material.IRON_SWORD, ChatColor.RED + "総モブ討伐数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-mob-kills", 0))));
        inventory.setItem(20, named(Material.IRON_PICKAXE, ChatColor.BLUE + "総ブロック破壊数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-blocks-broken", 0))));
        inventory.setItem(21, named(Material.GRASS_BLOCK, ChatColor.GREEN + "総ブロック設置数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-blocks-placed", 0))));
        inventory.setItem(22, named(Material.TRADER_LLAMA_SPAWN_EGG, ChatColor.YELLOW + "総取引回数",
                List.of(ChatColor.GRAY.toString() + section.getInt("total-trades", 0))));
    }

    private void fillProgressTab(Player player, Inventory inventory, int page) {
        Set<String> completed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
        List<Advancement> advancements = trackableAdvancements();
        int pageSize = 27;
        int maxPage = Math.max(0, (advancements.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));
        inventory.setItem(45, named(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "達成進捗数",
                List.of(ChatColor.GRAY.toString() + countCompletedAdvancements(player) + "/" + advancements.size())));
        inventory.setItem(49, named(Material.PAPER, ChatColor.YELLOW + "ページ",
                List.of(ChatColor.GRAY.toString() + (safePage + 1) + "/" + (maxPage + 1))));
        if (safePage > 0) {
            inventory.setItem(48, actionItem(Material.ARROW, ChatColor.WHITE + "前のページ",
                    List.of(ChatColor.GRAY + "ページ " + safePage + " へ"), "progress_page", String.valueOf(safePage - 1)));
        }
        if (safePage < maxPage) {
            inventory.setItem(50, actionItem(Material.ARROW, ChatColor.WHITE + "次のページ",
                    List.of(ChatColor.GRAY + "ページ " + (safePage + 2) + " へ"), "progress_page", String.valueOf(safePage + 1)));
        }
        int slot = 9;
        int from = safePage * pageSize;
        int to = Math.min(advancements.size(), from + pageSize);
        for (Advancement advancement : advancements.subList(from, to)) {
            AdvancementDisplay display = advancement.getDisplay();
            boolean done = completed.contains(advancement.getKey().toString());
            Material icon = display == null || display.icon() == null ? Material.PAPER : display.icon().getType();
            inventory.setItem(slot++, advancementItem(advancement, icon, done));
        }
    }

    private int tabPage(String tab, String prefix) {
        String marker = prefix + ":";
        if (!tab.startsWith(marker)) {
            return 0;
        }
        return parsePositiveInt(tab.substring(marker.length()), 0);
    }

    private void fillReincarnationTab(Player player, Inventory inventory) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        int next = section.getInt("reincarnations", 0) + 1;
        int requiredEmeralds = safeMultiply(10000, next);
        int requiredLevel = Math.min(1000, 20 + Math.max(0, next) * 10);
        inventory.setItem(20, named(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "転生回数",
                List.of(ChatColor.GRAY.toString() + section.getInt("reincarnations", 0))));
        inventory.setItem(22, actionItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "転生する",
                List.of(ChatColor.GRAY + "必要: " + formatNumber(requiredEmeralds) + "MP / Lv" + requiredLevel,
                        ChatColor.GRAY + "現在: " + formatNumber(getEmeralds(player.getUniqueId())) + "MP / Lv" + player.getLevel(),
                        ChatColor.YELLOW + "クリックで転生"), "reincarnate_now", null));
        inventory.setItem(24, named(Material.GOLD_INGOT, ChatColor.GOLD + "転生ボーナス",
                List.of(ChatColor.GRAY + "+" + getReincarnationBonus(player.getUniqueId()) + "%")));
    }

    private void fillTitlesTab(Player player, Inventory inventory, int page) {
        Set<String> completed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
        String selected = selectedTitle(player);
        List<Map.Entry<String, TitleDefinition>> titles = titleDefinitions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        int pageSize = 27;
        int maxPage = Math.max(0, (titles.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));
        long unlockedCount = titles.stream().filter(entry -> hasTitle(completed, entry.getValue())).count();
        inventory.setItem(45, named(Material.NAME_TAG, ChatColor.GOLD + "取得済み称号",
                List.of(ChatColor.GRAY.toString() + unlockedCount + "/" + titles.size())));
        inventory.setItem(46, actionItem(Material.BARRIER, ChatColor.WHITE + "称号なし",
                List.of(selected.isBlank() ? ChatColor.GOLD + "選択中" : ChatColor.GRAY + "クリックで称号を外す"),
                "clear_title", null));
        inventory.setItem(49, named(Material.PAPER, ChatColor.YELLOW + "ページ",
                List.of(ChatColor.GRAY.toString() + (safePage + 1) + "/" + (maxPage + 1))));
        if (safePage > 0) {
            inventory.setItem(48, actionItem(Material.ARROW, ChatColor.WHITE + "前のページ",
                    List.of(ChatColor.GRAY + "ページ " + safePage + " へ"), "titles_page", String.valueOf(safePage - 1)));
        }
        if (safePage < maxPage) {
            inventory.setItem(50, actionItem(Material.ARROW, ChatColor.WHITE + "次のページ",
                    List.of(ChatColor.GRAY + "ページ " + (safePage + 2) + " へ"), "titles_page", String.valueOf(safePage + 1)));
        }
        int slot = 9;
        int from = safePage * pageSize;
        int to = Math.min(titles.size(), from + pageSize);
        for (Map.Entry<String, TitleDefinition> entry : titles.subList(from, to)) {
            boolean unlocked = hasTitle(completed, entry.getValue());
            String title = unlocked ? entry.getKey() : "???";
            inventory.setItem(slot++, actionItem(entry.getValue().icon(), (unlocked ? ChatColor.GREEN : ChatColor.DARK_GRAY) + title,
                    List.of(unlocked ? ChatColor.GREEN + "取得済" : ChatColor.GRAY + "未取得",
                            entry.getKey().equals(selected) ? ChatColor.GOLD + "選択中" : ChatColor.GRAY + "クリックで選択"),
                    unlocked ? "select_title" : "locked_title", entry.getKey()));
        }
    }

    private boolean hasTitle(Set<String> completed, TitleDefinition definition) {
        if (definition.requiredAdvancements().isEmpty()) {
            return completed.size() >= countTrackableAdvancements() && countTrackableAdvancements() > 0;
        }
        return definition.requiredAdvancements().stream().allMatch(requirement -> matchesTitleRequirement(completed, requirement));
    }

    private boolean matchesTitleRequirement(Set<String> completed, String requirement) {
        for (String option : requirement.split("\\|")) {
            String key = option.trim();
            if (key.isBlank()) {
                continue;
            }
            if (completed.contains(key) || completed.contains("minecraft:" + key)) {
                return true;
            }
        }
        return false;
    }

    private void notifyUnlockedTitles(Player player, Set<String> completed) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
        List<String> newlyUnlocked = new ArrayList<>();
        for (Map.Entry<String, TitleDefinition> entry : titleDefinitions().entrySet()) {
            String title = entry.getKey();
            if (!notified.contains(title) && hasTitle(completed, entry.getValue())) {
                notified.add(title);
                newlyUnlocked.add(title);
            }
        }
        if (newlyUnlocked.isEmpty()) {
            return;
        }
        section.set("unlocked-titles", new ArrayList<>(notified));
        saveData();
        newlyUnlocked.stream().sorted().forEach(title ->
                player.sendMessage(ChatColor.GOLD + "称号を獲得しました: " + ChatColor.YELLOW + title));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    public void unlockTitle(Player player, String title) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
        if (!notified.contains(title)) {
            notified.add(title);
            section.set("unlocked-titles", new ArrayList<>(notified));
            saveData();
            player.sendMessage(ChatColor.GOLD + "称号を獲得しました：" + ChatColor.YELLOW + title);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }


    private void fillKillsTab(Player player, Inventory inventory, int page) {
        Set<String> killed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("killed-mobs"));
        List<EntityType> mobs = killableMobTypes();
        int pageSize = 27;
        int maxPage = Math.max(0, (mobs.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));
        long killedCount = mobs.stream().filter(type -> killed.contains(type.name())).count();
        inventory.setItem(45, named(Material.IRON_SWORD, ChatColor.RED + "討伐済みMob",
                List.of(ChatColor.GRAY.toString() + killedCount + "/" + mobs.size())));
        inventory.setItem(49, named(Material.PAPER, ChatColor.YELLOW + "ページ",
                List.of(ChatColor.GRAY.toString() + (safePage + 1) + "/" + (maxPage + 1))));
        if (safePage > 0) {
            inventory.setItem(48, actionItem(Material.ARROW, ChatColor.WHITE + "前のページ",
                    List.of(ChatColor.GRAY + "ページ " + safePage + " へ"), "kills_page", String.valueOf(safePage - 1)));
        }
        if (safePage < maxPage) {
            inventory.setItem(50, actionItem(Material.ARROW, ChatColor.WHITE + "次のページ",
                    List.of(ChatColor.GRAY + "ページ " + (safePage + 2) + " へ"), "kills_page", String.valueOf(safePage + 1)));
        }
        int slot = 9;
        int from = safePage * pageSize;
        int to = Math.min(mobs.size(), from + pageSize);
        for (EntityType type : mobs.subList(from, to)) {
            Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            boolean done = killed.contains(type.name());
            inventory.setItem(slot++, statusItem(done ? egg : Material.GRAY_STAINED_GLASS_PANE,
                    (done ? ChatColor.GREEN : ChatColor.DARK_GRAY) + mobDisplayName(type),
                    List.of(done ? ChatColor.GREEN + "討伐済" : ChatColor.GRAY + "未討伐"), done));
        }
    }

    private List<EntityType> killableMobTypes() {
        List<EntityType> mobs = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive() || type == EntityType.PLAYER || type == EntityType.UNKNOWN) {
                continue;
            }
            if (Material.matchMaterial(type.name() + "_SPAWN_EGG") != null) {
                mobs.add(type);
            }
        }
        mobs.sort((first, second) -> {
            int difficulty = Integer.compare(killDifficulty(first), killDifficulty(second));
            if (difficulty != 0) {
                return difficulty;
            }
            return first.name().compareToIgnoreCase(second.name());
        });
        return mobs;
    }

    private int killDifficulty(EntityType type) {
        return switch (type) {
            case CHICKEN, COW, PIG, SHEEP, RABBIT, COD, SALMON, TROPICAL_FISH, PUFFERFISH, SQUID, GLOW_SQUID -> 1;
            case BAT, FOX, FROG, GOAT, OCELOT, PARROT, TURTLE, WOLF, CAT, PANDA, DOLPHIN, CAMEL, HORSE, DONKEY, MULE, LLAMA, TRADER_LLAMA, SNIFFER -> 2;
            case ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER, DROWNED, HUSK, STRAY, SLIME, SILVERFISH, ENDERMITE, PHANTOM -> 3;
            case CREEPER, WITCH, PILLAGER, VINDICATOR, EVOKER, VEX, BLAZE, MAGMA_CUBE, GUARDIAN, BOGGED, BREEZE -> 4;
            case ENDERMAN, ZOMBIFIED_PIGLIN, PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN, WITHER_SKELETON, ELDER_GUARDIAN, RAVAGER, SHULKER -> 5;
            case WARDEN, WITHER, ENDER_DRAGON -> 6;
            default -> 3;
        };
    }

    private String mobDisplayName(EntityType type) {
        return switch (type) {
            case ALLAY -> "アレイ";
            case ARMADILLO -> "アルマジロ";
            case AXOLOTL -> "ウーパールーパー";
            case BAT -> "コウモリ";
            case BEE -> "ミツバチ";
            case BLAZE -> "ブレイズ";
            case BOGGED -> "ボグド";
            case BREEZE -> "ブリーズ";
            case CAMEL -> "ラクダ";
            case CAT -> "ネコ";
            case CAVE_SPIDER -> "洞窟グモ";
            case CHICKEN -> "ニワトリ";
            case COD -> "タラ";
            case COW -> "ウシ";
            case CREEPER -> "クリーパー";
            case DOLPHIN -> "イルカ";
            case DONKEY -> "ロバ";
            case DROWNED -> "ドラウンド";
            case ELDER_GUARDIAN -> "エルダーガーディアン";
            case ENDER_DRAGON -> "エンダードラゴン";
            case ENDERMAN -> "エンダーマン";
            case ENDERMITE -> "エンダーマイト";
            case EVOKER -> "エヴォーカー";
            case FOX -> "キツネ";
            case FROG -> "カエル";
            case GHAST -> "ガスト";
            case GLOW_SQUID -> "ヒカリイカ";
            case GOAT -> "ヤギ";
            case GUARDIAN -> "ガーディアン";
            case HOGLIN -> "ホグリン";
            case HORSE -> "ウマ";
            case HUSK -> "ハスク";
            case ILLUSIONER -> "イリュージョナー";
            case IRON_GOLEM -> "アイアンゴーレム";
            case LLAMA -> "ラマ";
            case MAGMA_CUBE -> "マグマキューブ";
            case MOOSHROOM -> "ムーシュルーム";
            case MULE -> "ラバ";
            case OCELOT -> "ヤマネコ";
            case PANDA -> "パンダ";
            case PARROT -> "オウム";
            case PHANTOM -> "ファントム";
            case PIG -> "ブタ";
            case PIGLIN -> "ピグリン";
            case PIGLIN_BRUTE -> "ピグリンブルート";
            case PILLAGER -> "ピリジャー";
            case POLAR_BEAR -> "シロクマ";
            case PUFFERFISH -> "フグ";
            case RABBIT -> "ウサギ";
            case RAVAGER -> "ラヴェジャー";
            case SALMON -> "サケ";
            case SHEEP -> "ヒツジ";
            case SHULKER -> "シュルカー";
            case SILVERFISH -> "シルバーフィッシュ";
            case SKELETON -> "スケルトン";
            case SKELETON_HORSE -> "スケルトンホース";
            case SLIME -> "スライム";
            case SNIFFER -> "スニッファー";
            case SNOW_GOLEM -> "スノウゴーレム";
            case SPIDER -> "クモ";
            case SQUID -> "イカ";
            case STRAY -> "ストレイ";
            case STRIDER -> "ストライダー";
            case TADPOLE -> "オタマジャクシ";
            case TRADER_LLAMA -> "行商人のラマ";
            case TROPICAL_FISH -> "熱帯魚";
            case TURTLE -> "カメ";
            case VEX -> "ヴェックス";
            case VILLAGER -> "村人";
            case VINDICATOR -> "ヴィンディケーター";
            case WANDERING_TRADER -> "行商人";
            case WARDEN -> "ウォーデン";
            case WITCH -> "ウィッチ";
            case WITHER -> "ウィザー";
            case WITHER_SKELETON -> "ウィザースケルトン";
            case WOLF -> "オオカミ";
            case ZOGLIN -> "ゾグリン";
            case ZOMBIE -> "ゾンビ";
            case ZOMBIE_HORSE -> "ゾンビホース";
            case ZOMBIE_VILLAGER -> "村人ゾンビ";
            case ZOMBIFIED_PIGLIN -> "ゾンビピグリン";
            default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private ItemStack statusItem(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = named(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(glint);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack advancementItem(Advancement advancement, Material icon, boolean done) {
        AdvancementDisplay display = advancement.getDisplay();
        Frame frame = display == null ? Frame.TASK : display.frame();
        ItemStack item = new ItemStack(done ? icon : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        Component title = display == null
                ? Component.text(advancement.getKey().getKey())
                : display.title();
        meta.displayName(title.color(done ? advancementFrameColor(frame) : NamedTextColor.DARK_GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(done ? "達成済" : "未達成", done ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        if (display != null && display.description() != null) {
            lore.add(display.description().color(done ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(done);
        item.setItemMeta(meta);
        return item;
    }

    private NamedTextColor advancementFrameColor(Frame frame) {
        return switch (frame) {
            case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
            case GOAL -> NamedTextColor.GOLD;
            default -> NamedTextColor.GREEN;
        };
    }

    private String formatPlayTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours + "時間" + minutes + "分";
    }

    private int countCompletedAdvancements(Player player) {
        Set<String> completed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
        int count = 0;
        for (Advancement advancement : trackableAdvancements()) {
            if (shouldTrackAdvancement(advancement) && completed.contains(advancement.getKey().toString())) {
                count++;
            }
        }
        return count;
    }

    private int countTrackableAdvancements() {
        return trackableAdvancements().size();
    }

    private List<Advancement> trackableAdvancements() {
        List<Advancement> advancements = new ArrayList<>();
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (shouldTrackAdvancement(advancement)) {
                advancements.add(advancement);
            }
        }
        advancements.sort((first, second) -> {
            int difficulty = Integer.compare(advancementDifficulty(first), advancementDifficulty(second));
            if (difficulty != 0) {
                return difficulty;
            }
            int category = Integer.compare(advancementCategoryOrder(first), advancementCategoryOrder(second));
            if (category != 0) {
                return category;
            }
            int path = Integer.compare(advancementPathOrder(first), advancementPathOrder(second));
            if (path != 0) {
                return path;
            }
            int frame = Integer.compare(advancementFrameOrder(first), advancementFrameOrder(second));
            if (frame != 0) {
                return frame;
            }
            return first.getKey().toString().compareToIgnoreCase(second.getKey().toString());
        });
        return advancements;
    }

    private int advancementFrameOrder(Advancement advancement) {
        AdvancementDisplay display = advancement.getDisplay();
        Frame frame = display == null ? Frame.TASK : display.frame();
        return switch (frame) {
            case TASK -> 0;
            case GOAL -> 1;
            case CHALLENGE -> 2;
        };
    }

    private int advancementCategoryOrder(Advancement advancement) {
        String key = advancement.getKey().getKey();
        String category = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
        return switch (category) {
            case "story" -> 0;
            case "nether" -> 1;
            case "end" -> 2;
            case "adventure" -> 3;
            case "husbandry" -> 4;
            default -> 9;
        };
    }

    private int advancementPathOrder(Advancement advancement) {
        String key = advancement.getKey().getKey();
        String category = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
        String path = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
        List<String> ordered = switch (category) {
            case "story" -> List.of(
                    "root", "mine_stone", "upgrade_tools", "smelt_iron", "obtain_armor", "lava_bucket",
                    "iron_tools", "deflect_arrow", "form_obsidian", "mine_diamond", "enter_the_nether",
                    "shiny_gear", "enchant_item", "cure_zombie_villager", "follow_ender_eye", "enter_the_end");
            case "nether" -> List.of(
                    "root", "return_to_sender", "find_bastion", "obtain_ancient_debris", "fast_travel",
                    "find_fortress", "obtain_crying_obsidian", "distract_piglin", "ride_strider",
                    "uneasy_alliance", "loot_bastion", "use_lodestone", "netherite_armor",
                    "get_wither_skull", "obtain_blaze_rod", "charge_respawn_anchor",
                    "ride_strider_in_overworld_lava", "explore_nether", "summon_wither", "brew_potion",
                    "create_beacon", "all_potions", "create_full_beacon", "all_effects");
            case "end" -> List.of(
                    "root", "kill_dragon", "dragon_egg", "enter_end_gateway", "respawn_dragon",
                    "dragon_breath", "find_end_city", "elytra", "levitate");
            case "adventure" -> List.of(
                    "root", "voluntary_exile", "spyglass_at_parrot", "kill_a_mob", "trade",
                    "trim_with_any_armor_pattern", "honey_block_slide", "ol_betsy",
                    "lightning_rod_with_villager_no_fire", "fall_from_world_height", "avoid_vibration",
                    "sleep_in_bed", "hero_of_the_village", "spyglass_at_ghast", "throw_trident",
                    "shoot_arrow", "kill_all_mobs", "totem_of_undying", "summon_iron_golem",
                    "trade_at_world_height", "two_birds_one_arrow", "whos_the_pillager_now",
                    "arbalistic", "adventuring_time", "play_jukebox_in_meadows",
                    "walk_on_powder_snow_with_leather_boots", "spyglass_at_dragon",
                    "very_very_frightening", "sniper_duel", "bullseye");
            case "husbandry" -> List.of(
                    "root", "safely_harvest_honey", "breed_an_animal", "ride_a_boat_with_a_goat",
                    "tame_an_animal", "make_a_sign_glow", "fishy_business", "silk_touch_nest",
                    "plant_seed", "wax_on", "bred_all_animals", "allay_deliver_item_to_player",
                    "complete_catalogue", "tactical_fishing", "balanced_diet", "obtain_netherite_hoe",
                    "axolotl_in_a_bucket", "wax_off", "kill_axolotl_target", "frogspawn",
                    "froglights", "allay_deliver_cake_to_note_block", "leash_all_frog_variants",
                    "feed_snifflet", "plant_any_sniffer_seed");
            default -> List.of("root");
        };
        int index = ordered.indexOf(path);
        if (index >= 0) {
            return index;
        }
        return path.equals("root") ? 0 : 1000 + advancementDifficulty(advancement);
    }

    private int advancementDifficulty(Advancement advancement) {
        String key = advancement.getKey().getKey();
        String path = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
        List<String> ordered = List.of(
                "root", "mine_stone", "upgrade_tools", "smelt_iron", "obtain_armor", "iron_tools",
                "deflect_arrow", "lava_bucket", "form_obsidian", "mine_diamond", "shiny_gear",
                "enchant_item", "enter_the_nether", "follow_ender_eye", "enter_the_end",
                "kill_a_mob", "shoot_arrow", "ol_betsy", "trade", "sleep_in_bed", "plant_seed",
                "breed_an_animal", "tame_an_animal", "fishy_business", "safely_harvest_honey",
                "wax_on", "wax_off", "silk_touch_nest", "make_a_sign_glow", "ride_a_boat_with_a_goat",
                "voluntary_exile", "honey_block_slide", "throw_trident", "totem_of_undying",
                "summon_iron_golem", "cure_zombie_villager", "return_to_sender", "find_fortress",
                "obtain_blaze_rod", "brew_potion", "distract_piglin", "obtain_crying_obsidian",
                "ride_strider", "use_lodestone", "find_bastion", "loot_bastion", "obtain_ancient_debris",
                "kill_dragon", "dragon_egg", "enter_end_gateway", "dragon_breath", "find_end_city",
                "elytra", "respawn_dragon", "spyglass_at_parrot", "spyglass_at_ghast",
                "lightning_rod_with_villager_no_fire", "walk_on_powder_snow_with_leather_boots",
                "play_jukebox_in_meadows", "avoid_vibration", "fall_from_world_height",
                "trade_at_world_height", "hero_of_the_village", "whos_the_pillager_now",
                "two_birds_one_arrow", "sniper_duel", "bullseye", "arbalistic",
                "very_very_frightening", "kill_all_mobs", "adventuring_time",
                "tactical_fishing", "axolotl_in_a_bucket", "kill_axolotl_target", "frogspawn",
                "froglights", "allay_deliver_item_to_player", "allay_deliver_cake_to_note_block",
                "complete_catalogue", "bred_all_animals", "balanced_diet", "obtain_netherite_hoe",
                "feed_snifflet", "plant_any_sniffer_seed", "leash_all_frog_variants",
                "fast_travel", "uneasy_alliance", "get_wither_skull", "summon_wither",
                "create_beacon", "create_full_beacon", "netherite_armor", "charge_respawn_anchor",
                "ride_strider_in_overworld_lava", "explore_nether", "all_potions", "all_effects",
                "levitate");
        int explicit = ordered.indexOf(path);
        if (explicit >= 0) {
            return explicit;
        }
        int score = key.split("/").length * 10 + key.length();
        if (key.contains("root")) {
            score -= 100;
        }
        for (String token : List.of("obtain", "mine", "smelt", "breed", "tame", "trade", "kill", "summon", "all", "complete", "netherite", "elytra")) {
            if (key.contains(token)) {
                score += switch (token) {
                    case "obtain", "mine", "smelt" -> 5;
                    case "breed", "tame", "trade" -> 15;
                    case "kill", "summon" -> 25;
                    case "all", "complete" -> 60;
                    case "netherite", "elytra" -> 80;
                    default -> 0;
                };
            }
        }
        return score;
    }

    String formatNumber(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String formatDateTime(long millis) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(millis));
    }

    private String safePlayerName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    private boolean isSafeConfigKey(String key) {
        return key != null && SAFE_CONFIG_KEY_PATTERN.matcher(key).matches();
    }

    private void sendInvalidConfigKeyMessage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + label + " は英数字、ハイフン、アンダースコアのみで1-32文字にしてください。");
    }

    private OfflinePlayer resolveKnownPlayer(CommandSender sender, String name) {
        if (name == null || name.isBlank() || name.length() > 16) {
            sender.sendMessage(ChatColor.RED + "プレイヤー名が不正です。");
            return null;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.isOnline() && !player.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + name);
            return null;
        }
        return player;
    }

    void openTeleportUi(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 9, Component.text(TELEPORT_UI_TITLE));
        inventory.setItem(0, actionItem(Material.GRASS_BLOCK, ChatColor.GREEN + "中央広場",
                List.of(ChatColor.GRAY + "初期スポーンへ移動"), "teleport", "hub"));
        ConfigurationSection servers = getConfig().getConfigurationSection("servers");
        if (servers != null) {
            int slot = 1;
            for (String key : servers.getKeys(false)) {
                if (!isSafeConfigKey(key)) {
                    getLogger().warning("Ignoring unsafe server key in config.yml: " + key);
                    continue;
                }
                if (slot >= 9) {
                    break;
                }
                inventory.setItem(slot++, actionItem(serverIconMaterial("servers." + key), ChatColor.LIGHT_PURPLE + key,
                        List.of(ChatColor.GRAY + "クリックで移動"), "teleport", "servers." + key));
            }
        }
        player.openInventory(inventory);
    }

    void openServerPortalTargetUi(Player player, String portalKey) {
        Inventory inventory = Bukkit.createInventory(player, 9, Component.text(TELEPORT_UI_TITLE));
        inventory.setItem(0, named(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "ポータル移動先設定",
                List.of(ChatColor.GRAY + "このポータルに触れた時の移動先を選択")));
        ConfigurationSection servers = getConfig().getConfigurationSection("servers");
        if (servers != null) {
            int slot = 1;
            for (String key : servers.getKeys(false)) {
                if (!isSafeConfigKey(key)) {
                    getLogger().warning("Ignoring unsafe server key in config.yml: " + key);
                    continue;
                }
                if (slot >= 9) {
                    break;
                }
                inventory.setItem(slot++, actionItem(serverIconMaterial("servers." + key), ChatColor.LIGHT_PURPLE + key,
                        List.of(ChatColor.GRAY + "クリックでこのポータルの移動先に設定"),
                        "server_portal_bind", portalKey + "|servers." + key));
            }
        }
        player.openInventory(inventory);
    }

    private Material serverIconMaterial(String path) {
        String configured = getConfig().getString(path + ".icon", "ender_pearl");
        Material material = configured == null ? null : Material.matchMaterial(configured.toUpperCase(Locale.ROOT));
        if (material == null || material == Material.AIR || !material.isItem()) {
            return Material.ENDER_PEARL;
        }
        return material;
    }

    private Material parseServerIcon(CommandSender sender, String raw) {
        Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material == null || material == Material.AIR || !material.isItem()) {
            sender.sendMessage(ChatColor.RED + "アイコン素材が見つからないか、アイテムとして使えません: " + raw);
            sender.sendMessage(ChatColor.GRAY + "例: /mva setserver minigame diamond_sword");
            return null;
        }
        return material;
    }

    private List<String> serverIconSuggestions(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> preferred = List.of(
                "diamond_sword", "bow", "crossbow", "trident", "mace", "iron_spear",
                "grass_block", "compass", "ender_pearl", "ender_eye", "nether_star",
                "emerald", "diamond", "gold_ingot", "iron_pickaxe", "elytra");
        List<String> suggestions = new ArrayList<>();
        for (String value : preferred) {
            if (value.startsWith(normalized)) {
                suggestions.add(value);
            }
        }
        if (suggestions.size() >= 20) {
            return suggestions;
        }
        for (Material material : Material.values()) {
            if (material == Material.AIR || !material.isItem()) {
                continue;
            }
            String key = material.name().toLowerCase(Locale.ROOT);
            if (key.startsWith(normalized) && !suggestions.contains(key)) {
                suggestions.add(key);
                if (suggestions.size() >= 20) {
                    break;
                }
            }
        }
        return suggestions;
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(Component::text).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action, String target) {
        ItemStack item = named(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(uiActionKey, PersistentDataType.STRING, action);
        if (target != null) {
            container.set(uiTargetKey, PersistentDataType.STRING, target);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String getUiAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(uiActionKey, PersistentDataType.STRING);
    }

    private UUID getUiTarget(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = getUiTargetString(item);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getUiTargetString(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(uiTargetKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isBarrelShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                buyBarrelOffer(player, event.getCurrentItem());
            }
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        String title = inventoryTitle(event.getView().title());
        if (FRIEND_UI_TITLE.equals(title)) {
            event.setCancelled(true);
            handleFriendUiClick(player, event.getCurrentItem());
            return;
        }
        if (FRIEND_STATUS_UI_TITLE.equals(title)) {
            event.setCancelled(true);
            handleStatusUiClick(player, event.getCurrentItem());
            return;
        }
        if (TELEPORT_UI_TITLE.equals(title)) {
            event.setCancelled(true);
            String action = getUiAction(event.getCurrentItem());
            String target = getUiTargetString(event.getCurrentItem());
            if ("teleport".equals(action) && target != null) {
                playUiClickSound(player);
                teleportToConfigLocation(player, target);
                player.closeInventory();
            } else if ("server_portal_bind".equals(action) && target != null) {
                String[] parts = target.split("\\|", 2);
                if (parts.length == 2) {
                    serverPortalFeature.setServerPortalTarget(parts[0], parts[1]);
                    playUiClickSound(player);
                    player.sendMessage(ChatColor.GREEN + "サーバーポータルの移動先を設定しました。");
                    player.closeInventory();
                }
            }
            return;
        }
        if (MERCHANT_UI_TITLE.equals(title)) {
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            event.setCancelled(true);
            if (isMerchantOffer(event.getCurrentItem())) {
                buyMerchantOffer(player, event.getCurrentItem(), event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isBarrelShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            return;
        }
        if (!MERCHANT_UI_TITLE.equals(inventoryTitle(event.getView().title()))) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isBarrelShopInventory(event.getSource()) || isBarrelShopInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isBarrelShopInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Barrel barrel && isBarrelShop(barrel.getBlock());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !MERCHANT_UI_TITLE.equals(inventoryTitle(event.getView().title()))) {
            return;
        }
        UUID merchantId = activeMerchantViews.remove(player.getUniqueId());
        if (merchantId == null || activeMerchantViews.containsValue(merchantId)) {
            return;
        }
        Entity entity = findEntity(merchantId);
        if (entity instanceof AbstractVillager villager && isMinervaMerchant(entity)) {
            villager.setAI(true);
            villager.setInvulnerable(false);
        }
    }

    private String inventoryTitle(Component title) {
        return PlainTextComponentSerializer.plainText().serialize(title);
    }

    private Entity findEntity(UUID entityId) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void handleFriendUiClick(Player player, ItemStack clicked) {
        String action = getUiAction(clicked);
        if (action == null) {
            return;
        }
        playUiClickSound(player);
        UUID targetId = getUiTarget(clicked);
        switch (action) {
            case "friend_search" -> {
                pendingFriendSearch.put(player.getUniqueId(), "");
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "検索するユーザー名をチャットに入力してください。空入力の代わりに clear で解除します。");
            }
            case "friend_request" -> {
                if (targetId != null) {
                    sendFriendRequest(player, Bukkit.getOfflinePlayer(targetId));
                    openFriendUi(player);
                }
            }
            case "friend_accept" -> {
                if (targetId != null) {
                    acceptFriendRequest(player, Bukkit.getOfflinePlayer(targetId));
                    openFriendUi(player);
                }
            }
            case "friend_remove" -> {
                if (targetId != null) {
                    removeFriend(player, Bukkit.getOfflinePlayer(targetId));
                    openFriendUi(player);
                }
            }
            case "friend_chat_open" -> {
                if (targetId != null) {
                    activeFriendChatTarget.put(player.getUniqueId(), targetId);
                    openFriendUi(player);
                }
            }
            case "friend_chat_input" -> {
                if (targetId != null) {
                    pendingFriendChatInput.put(player.getUniqueId(), targetId);
                    player.closeInventory();
                    player.sendMessage(ChatColor.AQUA + safePlayerName(Bukkit.getOfflinePlayer(targetId)) + " へ送る本文をチャットに入力してください。");
                }
            }
            case "friend_chat_send" -> {
                if (targetId != null) {
                    sendFriendChatDraft(player, Bukkit.getOfflinePlayer(targetId));
                    openFriendUi(player);
                }
            }
            case "friend_chat_close" -> {
                activeFriendChatTarget.remove(player.getUniqueId());
                friendChatDrafts.remove(player.getUniqueId());
                openFriendUi(player);
            }
            case "status_tab_progress" -> openStatusUi(player, "progress:0");
            case "status_tab_reincarnation" -> openStatusUi(player, "reincarnation");
            case "status_tab_titles" -> openStatusUi(player, "titles:0");
            case "status_tab_kills" -> openStatusUi(player, "kills:0");
            case "status_tab_quests" -> openStatusUi(player, "quests");
            case "friend_status_detail" -> openDetailedStatusUi(player);
            default -> {
            }
        }
    }

    private void handleStatusUiClick(Player player, ItemStack clicked) {
        String action = getUiAction(clicked);
        if (action == null) {
            return;
        }
        playUiClickSound(player);
        switch (action) {
            case "friend_status_back" -> openFriendUi(player);
            case "status_tab_progress" -> openStatusUi(player, "progress:0");
            case "status_tab_reincarnation" -> openStatusUi(player, "reincarnation");
            case "status_tab_titles" -> openStatusUi(player, "titles:0");
            case "status_tab_kills" -> openStatusUi(player, "kills:0");
            case "status_tab_quests" -> openStatusUi(player, "quests");
            case "quest_category" -> openStatusUi(player, "quests:" + getUiTargetString(clicked));
            case "quest_claim" -> {
                String questId = getUiTargetString(clicked);
                if (questId != null && questService.claim(player, questId)) {
                    QuestDefinition definition = questService.definition(questId);
                    openStatusUi(player, definition == null ? "quests" : "quests:" + definition.type().key());
                }
            }
            case "progress_page" -> openStatusUi(player, "progress:" + parsePositiveInt(getUiTargetString(clicked), 0));
            case "titles_page" -> openStatusUi(player, "titles:" + parsePositiveInt(getUiTargetString(clicked), 0));
            case "kills_page" -> openStatusUi(player, "kills:" + parsePositiveInt(getUiTargetString(clicked), 0));
            case "reincarnate_now" -> {
                tryReincarnate(player, null);
                openStatusUi(player, "reincarnation");
            }
            case "select_title" -> {
                String title = getUiTargetString(clicked);
                if (title != null && canUseTitle(player, title)) {
                    getPlayerSection(player.getUniqueId()).set("selected-title", title);
                    saveData();
                    refreshPlayerName(player);
                    player.sendMessage(ChatColor.GREEN + "称号を選択しました: " + title);
                    openStatusUi(player, "titles:0");
                }
            }
            case "clear_title" -> {
                getPlayerSection(player.getUniqueId()).set("selected-title", null);
                saveData();
                refreshPlayerName(player);
                player.sendMessage(ChatColor.GREEN + "称号を外しました。");
                openStatusUi(player, "titles:0");
            }
            default -> {
            }
        }
    }

    void teleportToConfigLocation(Player player, String path) {
        Location location = readLocation(path);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "移動先が未設定です: " + path);
            return;
        }
        player.teleport(location);
        playTeleportSound(player);
        player.sendMessage(ChatColor.GREEN + "移動しました。");
    }

    Location readLocation(String path) {
        String worldName = getConfig().getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                getConfig().getDouble(path + ".x"),
                getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"),
                (float) getConfig().getDouble(path + ".yaw"),
                (float) getConfig().getDouble(path + ".pitch"));
    }

    private void applyWorldSpawnLocations() {
        ConfigurationSection spawns = getConfig().getConfigurationSection("world-rules.spawn");
        if (spawns == null) {
            return;
        }
        for (String key : spawns.getKeys(false)) {
            String path = "world-rules.spawn." + key;
            String worldName = getConfig().getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            Location spawn = new Location(world,
                    getConfig().getDouble(path + ".x", 0.0D),
                    getConfig().getDouble(path + ".y", 0.0D),
                    getConfig().getDouble(path + ".z", 0.0D),
                    (float) getConfig().getDouble(path + ".yaw", 0.0D),
                    (float) getConfig().getDouble(path + ".pitch", 0.0D));
            world.setSpawnLocation(spawn);
        }
    }

    private void normalizeSpawnLocationsToOrigin() {
        setLocationCoordinatesToOrigin("hub");
        normalizeLocationSection("world-rules.spawn");
        normalizeLocationSection("servers");
        normalizeLocationSection("warning-servers");
        saveConfig();
    }

    private void normalizeLocationSection(String sectionPath) {
        ConfigurationSection section = getConfig().getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = sectionPath + "." + key;
            if ("world-rules.spawn".equals(sectionPath)
                    && "survival".equalsIgnoreCase(getConfig().getString(path + ".world"))) {
                continue;
            }
            setLocationCoordinatesToOrigin(path);
        }
    }

    private void setLocationCoordinatesToOrigin(String path) {
        if (!getConfig().contains(path + ".world")) {
            return;
        }
        getConfig().set(path + ".x", 0.0D);
        getConfig().set(path + ".y", 0.0D);
        getConfig().set(path + ".z", 0.0D);
        getConfig().set(path + ".yaw", 0.0D);
        getConfig().set(path + ".pitch", 0.0D);
    }

    private void applyFixedSpawnLocation(String worldName, double x, double y, double z) {
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world != null) {
            world.setSpawnLocation(new Location(world, x, y, z, 0.0F, 0.0F));
        }
    }

    private void configureSurvivalSpawnLocation() {
        getConfig().set("world-rules.spawn.survival.world", "survival");
        getConfig().set("world-rules.spawn.survival.x", 0.0D);
        getConfig().set("world-rules.spawn.survival.y", 101.0D);
        getConfig().set("world-rules.spawn.survival.z", 0.0D);
        getConfig().set("world-rules.spawn.survival.yaw", 0.0D);
        getConfig().set("world-rules.spawn.survival.pitch", 0.0D);
        saveConfig();
    }

    private void applyConfiguredSpawnLocation(String primaryPath, String fallbackPath, String defaultWorld, double defaultX, double defaultY, double defaultZ) {
        String path = getConfig().contains(primaryPath + ".world") ? primaryPath : fallbackPath;
        String worldName = getConfig().getString(path + ".world", defaultWorld);
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        Location spawn = new Location(world,
                getConfig().getDouble(path + ".x", defaultX),
                getConfig().getDouble(path + ".y", defaultY),
                getConfig().getDouble(path + ".z", defaultZ),
                (float) getConfig().getDouble(path + ".yaw", 0.0D),
                (float) getConfig().getDouble(path + ".pitch", 0.0D));
        world.setSpawnLocation(spawn);
    }

    private void migrateDefaultHubLocation() {
        if (!"world".equalsIgnoreCase(getConfig().getString("hub.world", "world"))) {
            return;
        }
        boolean oldDefault = Math.abs(getConfig().getDouble("hub.x") - 0.5D) < 0.0001D
                && Math.abs(getConfig().getDouble("hub.y") - 64.0D) < 0.0001D
                && Math.abs(getConfig().getDouble("hub.z") - 0.5D) < 0.0001D;
        boolean lowOriginSpawn = Math.abs(getConfig().getDouble("hub.x")) < 0.0001D
                && Math.abs(getConfig().getDouble("hub.z")) < 0.0001D
                && (Math.abs(getConfig().getDouble("hub.y") - 60.0D) < 0.0001D
                || Math.abs(getConfig().getDouble("hub.y") - 64.0D) < 0.0001D);
        boolean highOriginSpawn = Math.abs(getConfig().getDouble("hub.x")) < 0.0001D
                && Math.abs(getConfig().getDouble("hub.y") - 300.0D) < 0.0001D
                && Math.abs(getConfig().getDouble("hub.z")) < 0.0001D;
        boolean missing = !getConfig().contains("hub.x")
                || !getConfig().contains("hub.y")
                || !getConfig().contains("hub.z");
        if (!oldDefault && !lowOriginSpawn && !highOriginSpawn && !missing) {
            return;
        }
        getConfig().set("hub.world", "world");
        getConfig().set("hub.x", 0.0D);
        getConfig().set("hub.y", 0.0D);
        getConfig().set("hub.z", 0.0D);
        getConfig().set("hub.yaw", 0.0D);
        getConfig().set("hub.pitch", 0.0D);
        setIfMissing("world-rules.spawn.main.world", "world");
        setIfMissing("world-rules.spawn.main.x", 0.0D);
        setIfMissing("world-rules.spawn.main.y", 0.0D);
        setIfMissing("world-rules.spawn.main.z", 0.0D);
        setIfMissing("world-rules.spawn.main.yaw", 0.0D);
        setIfMissing("world-rules.spawn.main.pitch", 0.0D);
        saveConfig();
    }

    private void migrateDefaultMinigameLocation() {
        if (!"minigame".equalsIgnoreCase(getConfig().getString("servers.minigame.world", "minigame"))) {
            return;
        }
        boolean oldDefault = Math.abs(getConfig().getDouble("servers.minigame.x") - 0.5D) < 0.0001D
                && Math.abs(getConfig().getDouble("servers.minigame.y") - 64.0D) < 0.0001D
                && Math.abs(getConfig().getDouble("servers.minigame.z") - 0.5D) < 0.0001D;
        boolean missing = !getConfig().contains("servers.minigame.x")
                || !getConfig().contains("servers.minigame.y")
                || !getConfig().contains("servers.minigame.z");
        if (!oldDefault && !missing) {
            return;
        }
        getConfig().set("servers.minigame.world", "minigame");
        getConfig().set("servers.minigame.x", 0.0D);
        getConfig().set("servers.minigame.y", 0.0D);
        getConfig().set("servers.minigame.z", 0.0D);
        getConfig().set("servers.minigame.yaw", 0.0D);
        getConfig().set("servers.minigame.pitch", 0.0D);
        setIfMissing("world-rules.spawn.minigame.world", "minigame");
        setIfMissing("world-rules.spawn.minigame.x", 0.0D);
        setIfMissing("world-rules.spawn.minigame.y", 0.0D);
        setIfMissing("world-rules.spawn.minigame.z", 0.0D);
        setIfMissing("world-rules.spawn.minigame.yaw", 0.0D);
        setIfMissing("world-rules.spawn.minigame.pitch", 0.0D);
        saveConfig();
    }

    private void setIfMissing(String path, Object value) {
        if (!getConfig().contains(path)) {
            getConfig().set(path, value);
        }
    }

    private void writeLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
        saveConfig();
    }

        @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (pendingFriendSearch.containsKey(sender.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> {
                String plainMessage = sanitizeTextInput(rawMessage, MAX_FRIEND_FILTER_LENGTH);
                pendingFriendSearch.remove(sender.getUniqueId());
                if (plainMessage.equalsIgnoreCase("clear") || plainMessage.isBlank()) {
                    friendSearchFilters.remove(sender.getUniqueId());
                } else {
                    friendSearchFilters.put(sender.getUniqueId(), plainMessage);
                }
                openFriendUi(sender);
            });
            return;
        }
        if (pendingFriendChatInput.containsKey(sender.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> {
                UUID chatTarget = pendingFriendChatInput.remove(sender.getUniqueId());
                if (chatTarget == null) {
                    return;
                }
                String plainMessage = sanitizeTextInput(rawMessage, MAX_FRIEND_MESSAGE_LENGTH);
                if (!plainMessage.isBlank()) {
                    activeFriendChatTarget.put(sender.getUniqueId(), chatTarget);
                    friendChatDrafts.put(sender.getUniqueId(), plainMessage);
                }
                openFriendUi(sender);
            });
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) ->
                titlePrefix(source).append(sourceDisplayName).append(Component.text(": ")).append(message));
        double radius = getConfig().getDouble("local-chat-radius", 50.0);
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player receiver)) {
                return false;
            }
            return !receiver.getWorld().equals(sender.getWorld())
                    || receiver.getLocation().distanceSquared(sender.getLocation()) > radius * radius;
        });
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        if (!shouldTrackAdvancement(advancement)) {
            return;
        }
        String fullKey = advancement.getKey().toString();
        Player player = event.getPlayer();
        Set<String> completed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
        completed.add(fullKey);
        getPlayerSection(player.getUniqueId()).set("completed-advancements", new ArrayList<>(completed));
        notifyUnlockedTitles(player, completed);
        Set<String> rewarded = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("rewarded-advancements"));
        if (!rewarded.add(fullKey)) {
            saveData();
            return;
        }
        getPlayerSection(player.getUniqueId()).set("rewarded-advancements", new ArrayList<>(rewarded));
        String key = advancement.getKey().getKey();
        AdvancementDisplay display = advancement.getDisplay();
        Frame frame = display == null ? Frame.TASK : display.frame();
        int reward = advancementReward(frame, "emeralds");
        int bonus = advancementBonus(frame);
        ConfigurationSection special = getConfig().getConfigurationSection("advancement-unlocks." + key);
        if (special != null) {
            reward = special.getInt("emeralds", reward);
            applyUnlocks(player, special);
        }
        int paidReward = applyIncomeBonus(player.getUniqueId(), reward);
        depositEmeralds(player.getUniqueId(), paidReward);
        updateAdvancementBonus(player.getUniqueId(), completed);
        player.sendMessage(ChatColor.GREEN + "進捗報酬: +" + formatNumber(paidReward) + "MP");
        checkAllAdvancementsCompleted(player);
        refreshPlayerName(player);
    }

    private int advancementReward(Frame frame, String field) {
        String path = switch (frame) {
            case CHALLENGE -> "advancement-rewards.challenge.";
            case GOAL -> "advancement-rewards.goal.";
            default -> "advancement-rewards.default.";
        };
        int fallback = switch (frame) {
            case CHALLENGE -> 100;
            case GOAL -> 50;
            default -> 10;
        };
        return getConfig().getInt(path + field, fallback);
    }

    private int advancementBonus(Frame frame) {
        return 0;
    }

    private void syncAdvancementState(Player player) {
        if (!player.isOnline()) {
            return;
        }
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
        Set<String> rewarded = new HashSet<>(section.getStringList("rewarded-advancements"));
        boolean changed = false;
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (!shouldTrackAdvancement(advancement)) {
                continue;
            }
            String key = advancement.getKey().toString();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) {
                changed |= completed.add(key);
                changed |= rewarded.add(key);
                continue;
            }
            if (completed.contains(key)) {
                changed |= rewarded.add(key);
                for (String criterion : new ArrayList<>(progress.getRemainingCriteria())) {
                    progress.awardCriteria(criterion);
                }
                changed = true;
            }
        }
        if (changed) {
            section.set("completed-advancements", new ArrayList<>(completed));
            section.set("rewarded-advancements", new ArrayList<>(rewarded));
        }
        updateAdvancementBonus(player.getUniqueId(), completed);
        saveData();
        checkAllAdvancementsCompleted(player);
        notifyUnlockedTitles(player, completed);
        refreshPlayerName(player);
    }

    private boolean shouldTrackAdvancement(Advancement advancement) {
        String key = advancement.getKey().getKey();
        return !key.startsWith("recipes/");
    }

    private void checkAllAdvancementsCompleted(Player player) {
        int total = countTrackableAdvancements();
        if (total <= 0 || countCompletedAdvancements(player) < total) {
            return;
        }
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        if (section.getBoolean("all-advancements-rewarded", false)) {
            return;
        }
        int paidReward = applyIncomeBonus(player.getUniqueId(), 10000);
        depositEmeralds(player.getUniqueId(), paidReward);
        updateAdvancementBonus(player.getUniqueId(), new HashSet<>(section.getStringList("completed-advancements")));
        section.set("all-advancements-rewarded", true);
        saveData();
        player.sendMessage(ChatColor.GOLD + "全進捗達成報酬: +" + formatNumber(paidReward) + "MP / 転生タブから転生できます。");
    }

    private void applyUnlocks(Player player, ConfigurationSection section) {
        if (section.contains("shop-discount")) {
            getPlayerSection(player.getUniqueId()).set("shop-discount", Math.max(
                    getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0),
                    section.getInt("shop-discount")));
        }
        for (String listKey : List.of("unlocks", "skins", "pets", "ffa-classes")) {
            List<String> values = section.getStringList(listKey);
            if (!values.isEmpty()) {
                Set<String> current = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList(listKey));
                current.addAll(values);
                getPlayerSection(player.getUniqueId()).set(listKey, new ArrayList<>(current));
            }
        }
        saveData();
    }

    private void handleLoginReward(Player player) {
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        if (today.equals(section.getString("last-login-reward"))) {
            return;
        }
        LocalDate last = null;
        String lastValue = section.getString("last-login-reward");
        if (lastValue != null) {
            last = LocalDate.parse(lastValue);
        }
        int streak = last != null && last.plusDays(1).toString().equals(today)
                ? section.getInt("login-streak", 0) + 1
                : 1;
        int total = section.getInt("total-logins", 0) + 1;
        section.set("last-login-reward", today);
        section.set("login-streak", streak);
        section.set("total-logins", total);

        int reward = applyIncomeBonus(player.getUniqueId(), 10 + streak);
        if (total % 10 == 0) {
            reward += 100 * (total / 10);
        }
        depositEmeralds(player.getUniqueId(), reward);
        player.sendMessage(ChatColor.GREEN + "ログイン報酬: +" + formatNumber(reward) + "MP");
        saveData();
    }

    private void grantPlaytimeRewards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ConfigurationSection section = getPlayerSection(player.getUniqueId());
            int minutes = section.getInt("session-minutes", 0) + 1;
            section.set("session-minutes", minutes);
            int totalMinutes = section.getInt("total-minutes", 0) + 1;
            section.set("total-minutes", totalMinutes);
            if (minutes % 10 == 0) {
                int sessionRewards = section.getInt("session-playtime-rewards", 0);
                if (sessionRewards >= 20) {
                    continue;
                }
                int reward = applyIncomeBonus(player.getUniqueId(), 10);
                if (totalMinutes % 6000 == 0) {
                    reward += 100 * (totalMinutes / 6000);
                }
                section.set("session-playtime-rewards", sessionRewards + 1);
                depositEmeralds(player.getUniqueId(), reward);
                player.sendMessage(ChatColor.GREEN + "滞在報酬: +" + formatNumber(reward) + "MP");
            }
        }
        saveData();
    }

    private void routeByWarningLevel(Player player) {
        int warning = getPlayerSection(player.getUniqueId()).getInt("warning-level", 0);
        if (warning >= 4) {
            player.kick(Component.text("BAN: 警戒値4に到達しています。"));
            return;
        }
        String path = switch (warning) {
            case 1 -> "warning-servers.caution";
            case 2 -> "warning-servers.detention";
            case 3 -> "warning-servers.imprisonment";
            default -> "warning-servers.basic";
        };
        Location location = readLocation(path);
        if (location != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> player.teleport(location), 5L);
        }
    }

    int getEmeralds(UUID uuid) {
        int emeralds = getPlayerSection(uuid).getInt("emeralds", 0);
        return Math.max(0, Math.min(MAX_EMERALDS, emeralds));
    }

    void depositEmeralds(UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }
        ConfigurationSection section = getPlayerSection(uuid);
        int added = Math.min(amount, MAX_EMERALDS);
        section.set("emeralds", safeAdd(section.getInt("emeralds", 0), added));
        section.set("total-earned-emeralds", safeAdd(section.getInt("total-earned-emeralds", 0), added));
        saveData();
    }

    boolean withdrawEmeralds(UUID uuid, int amount) {
        if (amount <= 0) {
            return false;
        }
        ConfigurationSection section = getPlayerSection(uuid);
        int current = getEmeralds(uuid);
        if (current < amount) {
            return false;
        }
        section.set("emeralds", current - amount);
        saveData();
        return true;
    }

    private int safeAdd(int current, int amount) {
        long result = (long) Math.max(0, current) + Math.max(0, amount);
        return (int) Math.min(MAX_EMERALDS, result);
    }

    private int safeMultiply(int left, int right) {
        long result = (long) Math.max(0, left) * Math.max(0, right);
        return (int) Math.min(MAX_EMERALDS, result);
    }

    private int applyIncomeBonus(UUID uuid, int base) {
        if (base <= 0) {
            return 0;
        }
        int bonus = getTotalIncomeBonus(uuid);
        long reward = (long) base + ((long) base * Math.max(0, bonus) / 100L);
        return (int) Math.min(MAX_EMERALDS, reward);
    }

    private int getMvl(UUID uuid) {
        ConfigurationSection section = getPlayerSection(uuid);
        int score = section.getInt("total-blocks-broken", 0)
                + section.getInt("total-blocks-placed", 0)
                + section.getInt("total-trades", 0) * 5
                + section.getInt("total-minutes", 0)
                + section.getInt("total-play-count", 0) * 10
                + section.getInt("total-mob-kills", 0) * 3
                + section.getStringList("completed-advancements").size() * 50;
        return Math.max(1, score / 100 + 1);
    }

    private String getMvlRank(UUID uuid) {
        int mvl = getMvl(uuid);
        if (mvl >= 100) {
            return "S";
        }
        if (mvl >= 60) {
            return "A";
        }
        if (mvl >= 30) {
            return "B";
        }
        if (mvl >= 10) {
            return "C";
        }
        return "D";
    }

    private int getAdvancementBonus(UUID uuid) {
        ConfigurationSection section = getPlayerSection(uuid);
        Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
        if (!completed.isEmpty()) {
            return calculateAdvancementBonus(completed);
        }
        return 0;
    }

    int getReincarnationBonus(UUID uuid) {
        return getPlayerSection(uuid).getInt("reincarnation-bonus-percent", 0);
    }

    private int getTotalIncomeBonus(UUID uuid) {
        return getReincarnationBonus(uuid);
    }

    private int updateAdvancementBonus(UUID uuid, Set<String> completed) {
        ConfigurationSection section = getPlayerSection(uuid);
        int bonus = calculateAdvancementBonus(completed);
        section.set("advancement-bonus-percent", bonus);
        section.set("income-bonus-percent", null);
        saveData();
        return bonus;
    }

    private int calculateAdvancementBonus(Set<String> completed) {
        return 0;
    }

    private void addAdvancementBonus(UUID uuid, int percent) {
        updateAdvancementBonus(uuid, new HashSet<>(getPlayerSection(uuid).getStringList("completed-advancements")));
    }

    private void addReincarnationBonus(UUID uuid, int percent) {
        ConfigurationSection section = getPlayerSection(uuid);
        section.set("reincarnation-bonus-percent", getReincarnationBonus(uuid) + Math.max(0, percent));
        saveData();
    }

    void addPlayerStat(UUID uuid, String key, int amount) {
        if (amount <= 0) {
            return;
        }
        ConfigurationSection section = getPlayerSection(uuid);
        section.set(key, safeAdd(section.getInt(key, 0), amount));
        saveData();
        questService.recordStat(uuid, key, amount);
    }

    private void refreshPlayerName(Player player) {
        String title = selectedTitle(player);
        Component name = titlePrefix(player).append(Component.text(player.getName()));
        player.displayName(name);
        player.playerListName(name);
        player.customName(name);
        player.setCustomNameVisible(!title.isBlank());
    }

    private Component titlePrefix(Player player) {
        String title = selectedTitle(player);
        if (title.isBlank()) {
            return Component.empty();
        }
        return Component.text("[" + title + "] ", NamedTextColor.GOLD);
    }

    private String selectedTitle(Player player) {
        String title = getPlayerSection(player.getUniqueId()).getString("selected-title", "");
        if (title == null || title.isBlank() || !canUseTitle(player, title)) {
            if (title != null && !title.isBlank()) {
                getPlayerSection(player.getUniqueId()).set("selected-title", null);
                saveData();
            }
            return "";
        }
        return title;
    }

    private boolean canUseTitle(Player player, String title) {
        TitleDefinition definition = titleDefinitions().get(title);
        if (definition == null) {
            return false;
        }
        Set<String> completed = new HashSet<>(getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
        return hasTitle(completed, definition);
    }

    private Map<String, TitleDefinition> titleDefinitions() {
        Map<String, TitleDefinition> definitions = new LinkedHashMap<>(TITLE_DEFINITIONS);
        ConfigurationSection section = getConfig().getConfigurationSection("titles");
        if (section == null) {
            return definitions;
        }
        for (String key : section.getKeys(false)) {
            String displayName = section.getString(key + ".display-name", key);
            if (displayName == null || displayName.isBlank()) {
                continue;
            }
            Material icon = Material.matchMaterial(section.getString(key + ".icon", "name_tag"));
            List<String> requirements = section.getStringList(key + ".required-advancements");
            definitions.put(displayName, new TitleDefinition(icon == null ? Material.NAME_TAG : icon, requirements));
        }
        return definitions;
    }

    private void resetStatusData(UUID uuid) {
        ConfigurationSection section = getPlayerSection(uuid);
        for (String key : List.of(
                "emeralds",
                "total-earned-emeralds",
                "income-bonus-percent",
                "advancement-bonus-percent",
                "reincarnation-bonus-percent",
                "reincarnations",
                "pending-advancement-reset",
                "session-minutes",
                "session-playtime-rewards",
                "total-minutes",
                "total-play-count",
                "login-streak",
                "total-logins",
                "last-login-reward",
                "completed-advancements",
                "rewarded-advancements",
                "all-advancements-rewarded",
                "shop-discount",
                "unlocks",
                "skins",
                "pets",
                "ffa-classes",
                "total-mob-kills",
                "killed-mobs",
                "unlocked-titles",
                "selected-title",
                "total-trades",
                "total-blocks-broken",
                "total-blocks-placed")) {
            section.set(key, null);
        }
        section.set("pending-advancement-reset", true);
        saveData();
    }

    private ConfigurationSection getPlayerSection(UUID uuid) {
        String path = "players." + uuid;
        ConfigurationSection section = data.getConfigurationSection(path);
        return section != null ? section : data.createSection(path);
    }

    private Set<UUID> getUuidSet(UUID owner, String key) {
        List<String> raw = getPlayerSection(owner).getStringList(key);
        Set<UUID> result = new HashSet<>();
        for (String value : raw) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale data.
            }
        }
        return result;
    }

    boolean areFriends(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        return getUuidSet(first, "friends").contains(second)
                || getUuidSet(second, "friends").contains(first);
    }

    private void setUuidSet(UUID owner, String key, Set<UUID> values) {
        getPlayerSection(owner).set(key, values.stream().map(UUID::toString).toList());
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if ("friend".equalsIgnoreCase(command.getName())) {
                return handleFriendCommand(sender, args);
            }
            if ("status".equalsIgnoreCase(command.getName())) {
                return handleStatusCommand(sender, args);
            }
            if ("tutorial".equalsIgnoreCase(command.getName())) {
                return handleTutorialCommand(sender);
            }
            return handleMinervaCommand(sender, args);
        } catch (Throwable e) {
            getLogger().severe("Command failed: /" + label + " " + String.join(" ", args));
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "コマンド実行中にエラーが発生しました。詳細はサーバーコンソールを確認してください。");
            return true;
        }
    }

    private boolean handleMinervaCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/minerva check|list|tp|text|ffa|auth|structure|proposal|gamerules|info|reload|kit|balance|pay|merchant|minigame|athletic|quest|mp|regen|chunk|protect|status|tutorial|shopwand|jumppadwand|serverwand|sethub|setserver|delserver|warning");
            return true;
        }
        if (!(sender instanceof Player player) && !List.of("warning", "mp", "em", "emerald", "regen", "reload", "info", "list", "gamerules", "text", "ffa", "auth", "structure", "proposal").contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("Player only.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> {
                if (hasPermission(sender, "minerva.command.chunk")) {
                    handleChunkCommand((Player) sender);
                }
            }
            case "list" -> handleListCommand(sender);
            case "tp" -> handleWorldTpCommand((Player) sender, args);
            case "text" -> textDisplayFeature.handleCommand(sender, args);
            case "ffa" -> ffaManager.handleCommand(sender, args);
            case "auth" -> discordAuthManager.handleCommand(sender, args);
            case "structure" -> structureManager.handleCommand(sender, args);
            case "proposal" -> proposalManager.handleCommand(sender, args);
            case "gamerules" -> handleGamerulesCommand(sender, args);
            case "info" -> handleInfoCommand(sender);
            case "reload" -> handleReloadCommand(sender);
            case "kit" -> {
                giveInitialItems((Player) sender);
                sender.sendMessage(ChatColor.GREEN + "初期配布物を確認しました。");
            }
            case "balance" -> sender.sendMessage(ChatColor.GREEN + "所持MP: " + formatNumber(getEmeralds(((Player) sender).getUniqueId())));
            case "pay" -> handlePayCommand((Player) sender, args);
            case "merchant", "marchant" -> handleMerchantCommand((Player) sender, args);
            case "minigame" -> handleMinigameCommand((Player) sender, args);
            case "athletic" -> handleAthleticCommand((Player) sender, args);
            case "quest" -> handleQuestCommand(sender, args);
            case "mp", "em", "emerald" -> handleEmeraldCommand(sender, args);
            case "regen" -> handleRegenCommand(sender, args);
            case "chunk" -> {
                if (hasPermission(sender, "minerva.command.chunk")) {
                    handleChunkCommand((Player) sender);
                }
            }
            case "protect" -> {
                if (hasPermission(sender, "minerva.command.protect")) {
                    handleProtectCommand((Player) sender);
                }
            }
            case "status" -> {
                if (hasPermission(sender, "minerva.command.status")) {
                    handleMinervaStatusCommand((Player) sender, args);
                }
            }
            case "tutorial" -> handleTutorialCommand(sender);
            case "shopwand" -> {
                if (!sender.hasPermission("minerva.shop.admin") && !sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                ItemStack wand;
                if (args.length == 1) {
                    wand = createShopWand();
                } else {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "/mva shopwand <shelf|barrel|frame>");
                        return true;
                    }
                    ShopWandType type = ShopWandType.fromKey(args[1]);
                    if (type == null) {
                        sender.sendMessage(ChatColor.RED + "type は shelf / barrel / frame のいずれかです。");
                        return true;
                    }
                    if (type == ShopWandType.FRAME) {
                        sender.sendMessage(ChatColor.RED + "frame ショップは未実装です。額縁は既存のオークション専用です。");
                        return true;
                    }
                    wand = createShopWand(type);
                }
                Map<Integer, ItemStack> leftovers = ((Player) sender).getInventory().addItem(wand);
                if (!leftovers.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "ショップワンドを入手しました。");
            }
            case "jumppadwand" -> {
                if (!sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                int verticalPower = args.length >= 2 ? parsePositiveInt(args[1], -1) : 5;
                int horizontalPower = args.length >= 3 ? parsePositiveInt(args[2], -1) : verticalPower;
                if (verticalPower < 1 || verticalPower > MAX_JUMP_PAD_POWER || horizontalPower < 1 || horizontalPower > MAX_JUMP_PAD_POWER) {
                    sender.sendMessage(ChatColor.RED + "/mva jumppadwand <縦1-100> [横1-100]");
                    return true;
                }
                Map<Integer, ItemStack> leftovers = ((Player) sender).getInventory().addItem(createJumpPadWand(verticalPower, horizontalPower));
                if (!leftovers.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "ジャンプパッドワンドを入手しました。縦: " + verticalPower + " / 横: " + horizontalPower);
            }
            case "serverwand" -> {
                if (!sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                Map<Integer, ItemStack> leftovers = ((Player) sender).getInventory().addItem(serverPortalFeature.createServerWand());
                if (!leftovers.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "サーバーワンドを入手しました。");
            }
            case "sethub" -> {
                if (!sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                writeLocation("hub", ((Player) sender).getLocation());
                sender.sendMessage(ChatColor.GREEN + "中央広場を設定しました。");
            }
            case "setserver" -> {
                if (!sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/minerva setserver <name> [icon]");
                    return true;
                }
                if (!isSafeConfigKey(args[1])) {
                    sendInvalidConfigKeyMessage(sender, "サーバー名");
                    return true;
                }
                Material icon = null;
                if (args.length >= 3) {
                    icon = parseServerIcon(sender, args[2]);
                    if (icon == null) {
                        return true;
                    }
                }
                writeLocation("servers." + args[1], ((Player) sender).getLocation());
                if (icon != null) {
                    getConfig().set("servers." + args[1] + ".icon", icon.name().toLowerCase(Locale.ROOT));
                    saveConfig();
                }
                sender.sendMessage(ChatColor.GREEN + "サーバー移動先を設定しました: " + args[1]
                        + (icon == null ? "" : ChatColor.GRAY + " / icon: " + icon.name().toLowerCase(Locale.ROOT)));
            }
            case "delserver", "removeserver" -> {
                if (!sender.hasPermission("minerva.admin")) {
                    sender.sendMessage(ChatColor.RED + "権限がありません。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/minerva delserver <name>");
                    return true;
                }
                if (!isSafeConfigKey(args[1])) {
                    sendInvalidConfigKeyMessage(sender, "サーバー名");
                    return true;
                }
                String path = "servers." + args[1];
                if (!getConfig().contains(path)) {
                    sender.sendMessage(ChatColor.RED + "サーバー移動先が見つかりません: " + args[1]);
                    return true;
                }
                getConfig().set(path, null);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "サーバー移動先を削除しました: " + args[1]);
            }
            case "warning" -> handleWarningCommand(sender, args);
            default -> sender.sendMessage(ChatColor.YELLOW + "/minerva check|list|tp|text|ffa|auth|structure|proposal|gamerules|info|reload|kit|balance|pay|merchant|minigame|athletic|quest|mp|regen|chunk|protect|status|tutorial|shopwand|jumppadwand|serverwand|sethub|setserver|delserver|warning");
        }
        return true;
    }

    private boolean handleTutorialCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        startTutorial(player, true);
        return true;
    }

    private void handleRegenCommand(CommandSender sender, String[] args) {
        chunkProtectionFeature.handleRegenCommand(sender, args);
    }

    boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("minerva.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "権限がありません。");
        return false;
    }

    private void handleListCommand(CommandSender sender) {
        List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).sorted().toList();
        ConfigurationSection servers = getConfig().getConfigurationSection("servers");
        List<String> serverKeys = servers == null ? Collections.emptyList() : servers.getKeys(false).stream().sorted().toList();
        sender.sendMessage(ChatColor.GREEN + "Worlds: " + String.join(", ", worlds));
        sender.sendMessage(ChatColor.GREEN + "Configured servers: " + (serverKeys.isEmpty() ? "(none)" : String.join(", ", serverKeys)));
    }

    private void handleWorldTpCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/minerva tp <worldKey>");
            return;
        }
        String key = args[1];
        if (getConfig().contains("servers." + key)) {
            teleportToConfigLocation(player, "servers." + key);
            return;
        }
        World world = Bukkit.getWorld(key);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "移動先が見つかりません: " + key);
            return;
        }
        player.teleport(world.getSpawnLocation());
        playTeleportSound(player);
        player.sendMessage(ChatColor.GREEN + world.getName() + " のスポーンへ移動しました。");
    }

    private void handleGamerulesCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        worldRulesFeature.apply();
        if (args.length >= 2) {
            World world = Bukkit.getWorld(args[1]);
            sender.sendMessage(world == null
                    ? ChatColor.RED + "ワールドが見つかりません: " + args[1]
                    : ChatColor.GREEN + "ゲームルールを適用しました: " + world.getName());
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "全ワールドへMinerVaゲームルールを適用しました。");
    }

    private void handleInfoCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "MinerVa " + getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Commands: /minerva, /mva");
        sender.sendMessage(ChatColor.GRAY + "/mv はMultiverse-Core専用です。MinerVaは登録しません。");
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        reloadConfig();
        economyPriceTable.load();
        questService.load();
        loadShopPrices();
        applyEconomyPriceTable();
        syncShelfShopDisplays();
        discordAuthManager.reload();
        structureManager.load();
        proposalManager.load();
        ffaManager.load();
        sender.sendMessage(ChatColor.GREEN + "MinerVa設定、価格表、クエスト定義を再読込しました。");
    }

    private void handleChunkCommand(Player player) {
        chunkProtectionFeature.handleChunkCommand(player);
    }

    private void handleProtectCommand(Player player) {
        chunkProtectionFeature.handleProtectCommand(player);
    }

    private void handleMinervaStatusCommand(Player player, String[] args) {
        if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission("minerva.admin")) {
                player.sendMessage(ChatColor.RED + "ステータスリセットは管理者のみ実行できます。");
                return;
            }
            resetStatusData(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "自分のMinerVaステータスをリセットしました。");
            return;
        }
        ConfigurationSection section = getPlayerSection(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "MinerVaステータス");
        player.sendMessage(ChatColor.GRAY + "MVL: " + getMvl(player.getUniqueId()) + " / ランク: " + getMvlRank(player.getUniqueId()));
        player.sendMessage(ChatColor.GRAY + "所持MP: " + formatNumber(getEmeralds(player.getUniqueId())) + "MP");
        player.sendMessage(ChatColor.GRAY + "転生ボーナス: +" + getReincarnationBonus(player.getUniqueId()) + "%");
        player.sendMessage(ChatColor.GRAY + "総プレイ時間: " + formatPlayTime(section.getInt("total-minutes", 0)));
        player.sendMessage(ChatColor.YELLOW + "リセット: /minerva status reset");
    }

    private void handleQuestCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (args.length < 5 || !"progress".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.RED + "/minerva quest progress <player> <questId> <amount>");
            return;
        }
        OfflinePlayer target = resolveKnownPlayer(sender, args[2]);
        if (target == null || !target.isOnline() || target.getPlayer() == null) {
            sender.sendMessage(ChatColor.RED + "オンラインのプレイヤーを指定してください。");
            return;
        }
        int amount = parsePositiveInt(args[4], -1);
        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "amount は0以上の数字にしてください。");
            return;
        }
        questService.setQuestProgress(target.getPlayer(), args[3].toUpperCase(Locale.ROOT), amount);
    }

    private void handleAthleticCommand(Player player, String[] args) {
        if (!hasPermission(player, "minerva.reward.grant")) {
            return;
        }
        if (args.length < 3 || !"complete".equalsIgnoreCase(args[1])) {
            player.sendMessage(ChatColor.RED + "/minerva athletic complete <easy|normal|hard|hardcore> [misses]");
            return;
        }
        String difficulty = args[2].toLowerCase(Locale.ROOT);
        int base = switch (difficulty) {
            case "easy", "イージー" -> 5;
            case "normal", "ノーマル" -> 10;
            case "hard", "ハード" -> 50;
            case "hardcore", "ハードコア" -> 100;
            default -> -1;
        };
        if (base < 0) {
            player.sendMessage(ChatColor.RED + "難易度は easy, normal, hard, hardcore のいずれかです。");
            return;
        }
        int misses = args.length >= 4 ? parsePositiveInt(args[3], 0) : 0;
        int reward = applyIncomeBonus(player.getUniqueId(), Math.max(0, base - misses));
        depositEmeralds(player.getUniqueId(), reward);
        addPlayerStat(player.getUniqueId(), "athletic-clears", 1);
        player.sendMessage(ChatColor.GREEN + "アスレチック報酬: +" + formatNumber(reward) + "MP");
    }

    private void handleMinigameCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/minerva minigame play|win|unlock <name> <amount>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "play" -> {
                if (!hasPermission(player, "minerva.reward.grant")) {
                    return;
                }
                int reward = applyIncomeBonus(player.getUniqueId(), 10);
                depositEmeralds(player.getUniqueId(), reward);
                addPlayerStat(player.getUniqueId(), "minigame-plays", 1);
                player.sendMessage(ChatColor.GREEN + "ミニゲーム参加報酬: +" + formatNumber(reward) + "MP");
            }
            case "win" -> {
                if (!hasPermission(player, "minerva.reward.grant")) {
                    return;
                }
                int reward = applyIncomeBonus(player.getUniqueId(), 10);
                depositEmeralds(player.getUniqueId(), reward);
                addPlayerStat(player.getUniqueId(), "minigame-wins", 1);
                player.sendMessage(ChatColor.GREEN + "ミニゲーム勝利報酬: +" + formatNumber(reward) + "MP");
            }
            case "unlock" -> {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "/minerva minigame unlock <name> <amount>");
                    return;
                }
                int amount = parsePositiveInt(args[3], -1);
                String key = args[2].toLowerCase(Locale.ROOT);
                if (!isSafeConfigKey(key)) {
                    sendInvalidConfigKeyMessage(player, "ミニゲーム名");
                    return;
                }
                if (amount <= 0 || !withdrawEmeralds(player.getUniqueId(), amount)) {
                    player.sendMessage(ChatColor.RED + "納品できません。");
                    return;
                }
                String path = "minigames." + key + ".donated";
                int donated = safeAdd(data.getInt(path, 0), amount);
                data.set(path, donated);
                recordQuestProgress(player, "community_donations", amount);
                recordQuestProgress(player, "server_unlock_contribution", amount);
                int required = getConfig().getInt("minigame-unlocks." + key + ".required-emeralds", 0);
                if (required > 0 && donated >= required) {
                    data.set("minigames." + key + ".unlocked", true);
                }
                saveData();
                String suffix = required > 0 ? " / 必要: " + formatNumber(required) : "";
                String unlocked = data.getBoolean("minigames." + key + ".unlocked", false) ? " / 解放済" : "";
                player.sendMessage(ChatColor.GREEN + key + " に " + formatNumber(amount) + "MP 納品しました。累計: " + formatNumber(donated) + suffix + unlocked);
            }
            default -> player.sendMessage(ChatColor.RED + "/minerva minigame play|win|unlock <name> <amount>");
        }
    }

    private void handleEmeraldCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (args.length < 4 || !List.of("give", "grant", "add").contains(args[1].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(ChatColor.RED + "/minerva mp give <player> <amount>");
            return;
        }
        OfflinePlayer target = resolveKnownPlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        int amount = parsePositiveInt(args[3], -1);
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "配布MPは1以上の数字にしてください。");
            return;
        }
        depositEmeralds(target.getUniqueId(), amount);
        sender.sendMessage(ChatColor.GREEN + safePlayerName(target) + " に " + formatNumber(amount) + "MP を配布しました。");
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.GREEN + "管理者から " + formatNumber(amount) + "MP が配布されました。");
        }
    }

    private void handlePayCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "/minerva pay <player> <amount>");
            return;
        }
        OfflinePlayer target = resolveKnownPlayer(player, args[1]);
        if (target == null) {
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "自分には支払えません。");
            return;
        }
        int amount = parsePositiveInt(args[2], -1);
        if (amount <= 0 || !withdrawEmeralds(player.getUniqueId(), amount)) {
            player.sendMessage(ChatColor.RED + "支払いできません。");
            return;
        }
        depositEmeralds(target.getUniqueId(), amount);
        player.sendMessage(ChatColor.GREEN + safePlayerName(target) + " に " + formatNumber(amount) + "MP 支払いました。");
    }

    private void handleMerchantCommand(Player player, String[] args) {
        if (!player.hasPermission("minerva.admin")) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/minerva merchant spawn|reroll|clear");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (spawnMerchant(player.getLocation())) {
                    player.sendMessage(ChatColor.GREEN + "Minerva商人をスポーンしました。");
                } else {
                    player.sendMessage(ChatColor.RED + "中央広場にはMinerva商人をスポーンできません。");
                }
            }
            case "reroll" -> {
                int count = 0;
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof AbstractVillager villager && isMinervaMerchant(entity)) {
                            rerollMerchant(villager);
                            entity.getPersistentDataContainer().set(merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
                            entity.getPersistentDataContainer().set(merchantTradedKey, PersistentDataType.BOOLEAN, false);
                            count++;
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + "商人の販売品を再抽選しました: " + count + "体");
            }
            case "clear" -> {
                int count = 0;
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (isMinervaMerchant(entity)) {
                            entity.remove();
                            count++;
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Minerva商人を削除しました: " + count + "体");
            }
            default -> player.sendMessage(ChatColor.RED + "/minerva merchant spawn|reroll|clear");
        }
    }

    private void handleWarningCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "/minerva warning <player> <0-4>");
            return;
        }
        OfflinePlayer target = resolveKnownPlayer(sender, args[1]);
        if (target == null) {
            return;
        }
        int level = Math.min(4, parsePositiveInt(args[2], 0));
        getPlayerSection(target.getUniqueId()).set("warning-level", level);
        saveData();
        sender.sendMessage(ChatColor.GREEN + safePlayerName(target) + " の警戒値を " + level + " にしました。");
        if (target.isOnline()) {
            routeByWarningLevel(target.getPlayer());
        }
    }

    private void sendFriendRequest(Player player, OfflinePlayer target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "自分には申請できません。");
            return;
        }
        if (getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "すでにフレンドです。");
            return;
        }
        Set<UUID> requests = getUuidSet(target.getUniqueId(), "requests");
        if (requests.size() >= MAX_FRIEND_REQUESTS) {
            player.sendMessage(ChatColor.RED + "相手のフレンド申請が上限に達しています。");
            return;
        }
        if (!requests.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "すでに申請済みです。");
            return;
        }
        setUuidSet(target.getUniqueId(), "requests", requests);
        player.sendMessage(ChatColor.GREEN + "フレンド申請を送信しました: " + safePlayerName(target));
        if (target.isOnline()) {
            target.getPlayer().sendMessage(ChatColor.YELLOW + player.getName() + " からフレンド申請が届きました。");
        }
    }

    private void acceptFriendRequest(Player player, OfflinePlayer requester) {
        Set<UUID> requests = getUuidSet(player.getUniqueId(), "requests");
        if (!requests.remove(requester.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "申請が見つかりません。");
            return;
        }
        setUuidSet(player.getUniqueId(), "requests", requests);
        Set<UUID> playerFriends = getUuidSet(player.getUniqueId(), "friends");
        Set<UUID> requesterFriends = getUuidSet(requester.getUniqueId(), "friends");
        playerFriends.add(requester.getUniqueId());
        requesterFriends.add(player.getUniqueId());
        setUuidSet(player.getUniqueId(), "friends", playerFriends);
        setUuidSet(requester.getUniqueId(), "friends", requesterFriends);
        player.sendMessage(ChatColor.GREEN + safePlayerName(requester) + " とフレンドになりました。");
        if (requester.isOnline()) {
            requester.getPlayer().sendMessage(ChatColor.GREEN + player.getName() + " がフレンド申請を承認しました。");
        }
    }

    private void removeFriend(Player player, OfflinePlayer target) {
        Set<UUID> playerFriends = getUuidSet(player.getUniqueId(), "friends");
        Set<UUID> targetFriends = getUuidSet(target.getUniqueId(), "friends");
        playerFriends.remove(target.getUniqueId());
        targetFriends.remove(player.getUniqueId());
        setUuidSet(player.getUniqueId(), "friends", playerFriends);
        setUuidSet(target.getUniqueId(), "friends", targetFriends);
        player.sendMessage(ChatColor.GREEN + "フレンドを解除しました: " + safePlayerName(target));
    }

    private void sendFriendChatDraft(Player player, OfflinePlayer target) {
        String message = friendChatDrafts.getOrDefault(player.getUniqueId(), "").trim();
        if (message.isBlank()) {
            player.sendMessage(ChatColor.RED + "本文が未入力です。");
            return;
        }
        sendFriendChat(player, target, message);
        friendChatDrafts.remove(player.getUniqueId());
    }

    private void sendFriendChat(Player player, OfflinePlayer target, String message) {
        message = sanitizeTextInput(message, MAX_FRIEND_MESSAGE_LENGTH);
        if (message.isBlank()) {
            player.sendMessage(ChatColor.RED + "本文が未入力です。");
            return;
        }
        if (!getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "フレンドではありません。");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "[Friend -> " + safePlayerName(target) + "] " + message);
        if (target.isOnline()) {
            target.getPlayer().sendMessage(ChatColor.AQUA + "[Friend <- " + player.getName() + "] " + message);
            return;
        }
        List<String> notifications = getPlayerSection(target.getUniqueId()).getStringList("offline-messages");
        notifications.add(safePlayerName(player) + ": " + message);
        while (notifications.size() > MAX_OFFLINE_MESSAGES) {
            notifications.remove(0);
        }
        getPlayerSection(target.getUniqueId()).set("offline-messages", notifications);
        saveData();
    }

    private boolean handleFriendCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0) {
            openFriendUi(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "/friend add <player>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(player, args[1]);
                if (target != null) {
                    sendFriendRequest(player, target);
                }
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "/friend accept <player>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(player, args[1]);
                if (target != null) {
                    acceptFriendRequest(player, target);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "/friend remove <player>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(player, args[1]);
                if (target != null) {
                    removeFriend(player, target);
                }
            }
            case "chat" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "/friend chat <player> <message>");
                    return true;
                }
                OfflinePlayer target = resolveKnownPlayer(player, args[1]);
                if (target != null) {
                    String message = sanitizeTextInput(String.join(" ", List.of(args).subList(2, args.length)), MAX_FRIEND_MESSAGE_LENGTH);
                    sendFriendChat(player, target, message);
                }
            }
            default -> player.sendMessage(ChatColor.YELLOW + "/friend add|accept|remove|chat");
        }
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }
        if (args.length < 2 || !"reset".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.RED + "/status <player> reset");
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(sender, args[0]);
        if (target == null) {
            return true;
        }
        resetStatusData(target.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + safePlayerName(target) + " のステータスをリセットしました。");
        if (target.isOnline() && target.getPlayer() != null) {
            resetAdvancements(target.getPlayer());
            getPlayerSection(target.getUniqueId()).set("pending-advancement-reset", null);
            saveData();
            target.getPlayer().sendMessage(ChatColor.YELLOW + "ステータスがリセットされました。");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && isMinervaRootCommand(command)) {
            return List.of("check", "list", "tp", "text", "ffa", "auth", "structure", "proposal", "gamerules", "info", "reload", "kit", "balance", "pay", "merchant", "marchant", "minigame", "athletic", "quest", "mp", "regen", "chunk", "protect", "status", "tutorial", "shopwand", "jumppadwand", "serverwand", "sethub", "setserver", "delserver", "warning");
        }
        if (args.length >= 2 && isMinervaRootCommand(command) && "text".equalsIgnoreCase(args[0])) {
            return textDisplayFeature.tabComplete(args);
        }
        if (args.length >= 2 && isMinervaRootCommand(command) && "ffa".equalsIgnoreCase(args[0])) {
            return ffaManager.tabComplete(args, sender);
        }
        if (args.length >= 2 && isMinervaRootCommand(command) && "auth".equalsIgnoreCase(args[0])) {
            return discordAuthManager.tabComplete(args, sender);
        }
        if (args.length >= 2 && isMinervaRootCommand(command) && "structure".equalsIgnoreCase(args[0])) {
            return structureManager.tabComplete(args);
        }
        if (args.length >= 2 && isMinervaRootCommand(command) && "proposal".equalsIgnoreCase(args[0])) {
            return proposalManager.tabComplete(args);
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "shopwand".equalsIgnoreCase(args[0])) {
            return List.of("shelf", "barrel", "frame");
        }
        if ((args.length == 2 || args.length == 3) && isMinervaRootCommand(command) && "jumppadwand".equalsIgnoreCase(args[0])) {
            return List.of("1", "5", "10", "25", "50", "75", "100");
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "tp".equalsIgnoreCase(args[0])) {
            ConfigurationSection servers = getConfig().getConfigurationSection("servers");
            List<String> values = new ArrayList<>();
            if (servers != null) {
                values.addAll(servers.getKeys(false));
            }
            values.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            return values;
        }
        if (args.length == 3 && isMinervaRootCommand(command) && "setserver".equalsIgnoreCase(args[0])) {
            return serverIconSuggestions(args[2]);
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "gamerules".equalsIgnoreCase(args[0])) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "regen".equalsIgnoreCase(args[0])) {
            return List.of("0", "1", "2", "4", "force");
        }
        if (args.length == 3 && isMinervaRootCommand(command)
                && "regen".equalsIgnoreCase(args[0]) && "force".equalsIgnoreCase(args[1])) {
            return List.of("0", "1", "2", "4");
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "status".equalsIgnoreCase(args[0])) {
            return List.of("reset");
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "minigame".equalsIgnoreCase(args[0])) {
            return List.of("play", "win", "unlock");
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "athletic".equalsIgnoreCase(args[0])) {
            return List.of("complete");
        }
        if (args.length == 2 && isMinervaRootCommand(command) && "quest".equalsIgnoreCase(args[0])) {
            return List.of("progress");
        }
        if (args.length == 3 && isMinervaRootCommand(command) && "athletic".equalsIgnoreCase(args[0])) {
            return List.of("easy", "normal", "hard", "hardcore");
        }
        if (args.length == 2 && isMinervaRootCommand(command)
                && ("merchant".equalsIgnoreCase(args[0]) || "marchant".equalsIgnoreCase(args[0]))) {
            return List.of("spawn", "reroll", "clear");
        }
        if (args.length == 2 && isMinervaRootCommand(command)
                && ("mp".equalsIgnoreCase(args[0]) || "em".equalsIgnoreCase(args[0]) || "emerald".equalsIgnoreCase(args[0]))) {
            return List.of("give");
        }
        if (args.length == 2 && isMinervaRootCommand(command)
                && ("delserver".equalsIgnoreCase(args[0]) || "removeserver".equalsIgnoreCase(args[0]))) {
            ConfigurationSection servers = getConfig().getConfigurationSection("servers");
            return servers == null ? Collections.emptyList() : new ArrayList<>(servers.getKeys(false));
        }
        if (args.length == 1 && "friend".equalsIgnoreCase(command.getName())) {
            return List.of("add", "accept", "remove", "chat");
        }
        if (args.length == 2 && "status".equalsIgnoreCase(command.getName())) {
            return List.of("reset");
        }
        return Collections.emptyList();
    }

    private boolean isMinervaRootCommand(Command command) {
        return "minerva".equalsIgnoreCase(command.getName()) || "mva".equalsIgnoreCase(command.getName());
    }

    private record MerchantOffer(Material material, int amount, String rarity, int price) {
    }

    private record ShelfShopOffer(Material material, int amount, int price) {
    }

    private record BarrelShopConfig(String tier, int weight) {
    }

    private record JumpPadPower(int vertical, int horizontal) {
    }

    private record MerchantSale(int quantity, int totalPrice) {
    }

    private record TitleDefinition(Material icon, List<String> requiredAdvancements) {
    }

    private static final class KillRewardWindow {
        private long startedAtMillis;
        private int count;

        private KillRewardWindow(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }

    static final class ChatColor {
        static final String DARK_AQUA = "\u00A73";
        static final String DARK_GREEN = "\u00A72";
        static final String DARK_PURPLE = "\u00A75";
        static final String GOLD = "\u00A76";
        static final String GREEN = "\u00A7a";
        static final String GRAY = "\u00A77";
        static final String AQUA = "\u00A7b";
        static final String LIGHT_PURPLE = "\u00A7d";
        static final String YELLOW = "\u00A7e";
        static final String RED = "\u00A7c";
        static final String BLUE = "\u00A79";
        static final String WHITE = "\u00A7f";
        static final String DARK_GRAY = "\u00A78";

        private ChatColor() {
        }

        private static String stripColor(String input) {
            return input == null ? null : input.replaceAll("(?i)\u00A7[0-9A-FK-ORX]", "");
        }
    }
}





