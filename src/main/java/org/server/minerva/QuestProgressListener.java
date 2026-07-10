package org.server.minerva;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class QuestProgressListener implements Listener {
    private static final long DAMAGE_PARTICIPATION_MILLIS = 30_000L;
    private static final double EFFECTIVE_DAMAGE_RATIO = 0.20D;

    private final Minerva plugin;
    private final QuestService quests;
    private final Map<UUID, Map<UUID, DamageParticipation>> damageByEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> recentPlacedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, RepeatedBlockAction> lastBlockActions = new ConcurrentHashMap<>();

    QuestProgressListener(Minerva plugin, QuestService quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        quests.ensurePeriods(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        rememberPlacedBlock(player, block);
        if (!shouldCountBlockAction(player, block)) {
            return;
        }
        if (isQuestBuildingBlock(block.getType())) {
            quests.addProgress(player, "building_blocks", 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!shouldCountBlockAction(player, block) || wasRecentlyPlacedBy(player, block)) {
            return;
        }
        if (isMiningBlock(block.getType())) {
            quests.addProgress(player, "mining_blocks", 1);
        }
        if (isCropOrNaturalCollection(block.getType())) {
            quests.addProgress(player, "life_actions", 1);
            quests.addProgress(player, "fishing_collecting", 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }
        Player player = event.getPlayer();
        String key = chunkKey(event.getTo());
        String path = "players." + player.getUniqueId() + ".quests.seen-chunks";
        Set<String> seen = new HashSet<>(plugin.data().getStringList(path));
        if (!seen.add(key)) {
            return;
        }
        plugin.data().set(path, new ArrayList<>(seen));
        plugin.saveData();
        quests.addProgress(player, "exploration_chunks", 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        quests.addProgress(event.getPlayer(), "fishing_collecting", 1);
        quests.addProgress(event.getPlayer(), "life_actions", 17);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            quests.addProgress(player, "life_actions", 8);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player player = damagingPlayer(event.getDamager());
        if (player == null || victim instanceof Player) {
            return;
        }
        damageByEntity
                .computeIfAbsent(victim.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .compute(player.getUniqueId(), (ignored, current) -> {
                    double damage = event.getFinalDamage();
                    long now = System.currentTimeMillis();
                    return current == null
                            ? new DamageParticipation(damage, now)
                            : new DamageParticipation(current.damage() + damage, now);
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Map<UUID, DamageParticipation> participation = damageByEntity.remove(entity.getUniqueId());
        Player killer = entity.getKiller();
        if (killer != null && isHostile(entity.getType()) && hasRecentEffectiveParticipation(entity, killer.getUniqueId(), participation)) {
            quests.addProgress(killer, "hostile_kills", 1);
        }
        if (entity.getType() == EntityType.ENDER_DRAGON || entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ELDER_GUARDIAN) {
            grantBossParticipation(entity, participation, killer);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().getKey();
        if (key.startsWith("recipes/")) {
            return;
        }
        Player player = event.getPlayer();
        quests.addProgress(player, "advancements", 1);
        if ("end/elytra".equals(key) || "end/find_end_city".equals(key)) {
            quests.addProgress(player, "elytra_obtained", 1);
        }
    }

    private void grantBossParticipation(LivingEntity entity, Map<UUID, DamageParticipation> participation, Player killer) {
        Set<UUID> rewarded = new HashSet<>();
        if (killer != null && hasRecentEffectiveParticipation(entity, killer.getUniqueId(), participation)) {
            rewarded.add(killer.getUniqueId());
            grantBossProgress(killer, entity.getType());
        }
        if (participation == null) {
            return;
        }
        for (UUID uuid : participation.keySet()) {
            if (!rewarded.add(uuid) || !hasRecentEffectiveParticipation(entity, uuid, participation)) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                grantBossProgress(player, entity.getType());
            }
        }
    }

    private void grantBossProgress(Player player, EntityType type) {
        if (type == EntityType.ENDER_DRAGON) {
            quests.addProgress(player, "dragon_participation", 1);
        } else if (type == EntityType.WITHER) {
            quests.addProgress(player, "wither_participation", 1);
        } else if (type == EntityType.ELDER_GUARDIAN) {
            quests.addProgress(player, "elder_guardian_kills", 1);
        }
    }

    private boolean hasRecentEffectiveParticipation(LivingEntity entity, UUID playerId, Map<UUID, DamageParticipation> participation) {
        if (participation == null) {
            return entity.getKiller() != null && entity.getKiller().getUniqueId().equals(playerId);
        }
        DamageParticipation damage = participation.get(playerId);
        if (damage == null || System.currentTimeMillis() - damage.lastDamageAt() > DAMAGE_PARTICIPATION_MILLIS) {
            return false;
        }
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double required = maxHealth == null ? 1.0D : Math.max(1.0D, maxHealth.getValue() * EFFECTIVE_DAMAGE_RATIO);
        return damage.damage() >= required || (entity.getKiller() != null && entity.getKiller().getUniqueId().equals(playerId));
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void rememberPlacedBlock(Player player, Block block) {
        String signature = blockSignature(block);
        recentPlacedBlocks
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(signature, System.currentTimeMillis());
    }

    private boolean wasRecentlyPlacedBy(Player player, Block block) {
        Map<String, Long> placed = recentPlacedBlocks.get(player.getUniqueId());
        if (placed == null) {
            return false;
        }
        Long placedAt = placed.get(blockSignature(block));
        if (placedAt == null) {
            return false;
        }
        long window = Math.max(1, plugin.getConfig().getInt("quests.anti-abuse.repeated-block-window-seconds", 30)) * 1000L;
        return System.currentTimeMillis() - placedAt <= window;
    }

    private boolean shouldCountBlockAction(Player player, Block block) {
        String signature = blockSignature(block);
        long now = System.currentTimeMillis();
        long window = Math.max(1, plugin.getConfig().getInt("quests.anti-abuse.repeated-block-window-seconds", 30)) * 1000L;
        int maxCount = Math.max(1, plugin.getConfig().getInt("quests.anti-abuse.repeated-block-max-count", 2));
        RepeatedBlockAction previous = lastBlockActions.get(player.getUniqueId());
        if (previous == null || !previous.signature().equals(signature) || now - previous.lastAt() > window) {
            lastBlockActions.put(player.getUniqueId(), new RepeatedBlockAction(signature, now, 1));
            return true;
        }
        int nextCount = previous.count() + 1;
        lastBlockActions.put(player.getUniqueId(), new RepeatedBlockAction(signature, now, nextCount));
        return nextCount <= maxCount;
    }

    private boolean isMiningBlock(Material material) {
        String name = material.name();
        return name.endsWith("_ORE")
                || name.equals("ANCIENT_DEBRIS")
                || name.equals("STONE")
                || name.equals("DEEPSLATE")
                || name.equals("COBBLESTONE")
                || name.equals("COBBLED_DEEPSLATE")
                || name.equals("NETHERRACK")
                || name.equals("END_STONE")
                || name.equals("TUFF")
                || name.equals("BLACKSTONE")
                || name.equals("BASALT");
    }

    private boolean isQuestBuildingBlock(Material material) {
        if (!material.isBlock()) {
            return false;
        }
        String name = material.name();
        return !name.endsWith("_ORE")
                && !name.endsWith("_SPAWN_EGG")
                && !name.equals("AIR")
                && !name.equals("CAVE_AIR")
                && !name.equals("VOID_AIR");
    }

    private boolean isCropOrNaturalCollection(Material material) {
        String name = material.name();
        return Set.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
                        "MELON", "PUMPKIN", "SUGAR_CANE", "BAMBOO", "CACTUS", "SWEET_BERRY_BUSH", "TORCHFLOWER", "PITCHER_CROP")
                .contains(name)
                || name.endsWith("_LEAVES")
                || name.endsWith("_FLOWER")
                || name.endsWith("_MUSHROOM");
    }

    private boolean isHostile(EntityType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Set.of(
                "blaze", "bogged", "breeze", "cave_spider", "creaking", "creeper", "drowned",
                "elder_guardian", "ender_dragon", "enderman", "endermite", "evoker", "ghast",
                "guardian", "hoglin", "husk", "magma_cube", "phantom", "piglin", "piglin_brute",
                "pillager", "ravager", "shulker", "silverfish", "skeleton", "slime", "spider",
                "stray", "vex", "vindicator", "warden", "witch", "wither", "wither_skeleton",
                "zoglin", "zombie", "zombie_villager", "zombified_piglin").contains(name);
    }

    private String chunkKey(Location location) {
        return location.getWorld().getName() + "," + location.getChunk().getX() + "," + location.getChunk().getZ();
    }

    private String blockSignature(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ() + ":" + block.getType().name();
    }

    private record DamageParticipation(double damage, long lastDamageAt) {
    }

    private record RepeatedBlockAction(String signature, long lastAt, int count) {
    }
}
