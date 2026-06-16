package org.server.minerva;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ChunkProtectionFeature implements Listener {
    private final Minerva plugin;
    private final Map<UUID, String> lastChunkWarning = new ConcurrentHashMap<>();

    ChunkProtectionFeature(Minerva plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtected(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
            return;
        }
        if (plugin.isShelfShop(event.getBlock())) {
            plugin.setShelfShop(event.getBlock(), false);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "棚ショップ設定を解除しました。");
        }
        if (plugin.isBarrelShop(event.getBlock())) {
            plugin.setBarrelShop(event.getBlock(), false);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "樽ショップ設定を解除しました。");
        }
        plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-broken", 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtected(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
            return;
        }
        if (event.getBlockPlaced().getType() == Material.BEACON) {
            claimChunk(event.getPlayer(), event.getBlockPlaced().getChunk());
            plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-placed", 1);
            event.getPlayer().sendMessage(ChatColor.GREEN + "このチャンクを保護しました。");
            return;
        }
        if (isWarningPlacement(event.getBlockPlaced().getType()) && !isChunkRegenerationSafe(event.getBlockPlaced().getChunk())) {
            sendChunkWarning(event.getPlayer(), event.getBlockPlaced().getChunk());
        }
        plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-placed", 1);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isBuildProtectedChunk(block.getChunk()));
        event.blockList().forEach(this::clearShopSettings);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isBuildProtectedChunk(block.getChunk()));
        event.blockList().forEach(this::clearShopSettings);
    }

    private void clearShopSettings(Block block) {
        if (plugin.isShelfShop(block)) {
            plugin.setShelfShop(block, false);
        }
        if (plugin.isBarrelShop(block)) {
            plugin.setBarrelShop(block, false);
        }
    }

    void handleRegenCommand(CommandSender sender, String[] args) {
        if (!plugin.hasPermission(sender, "minerva.admin.regen.force") || !(sender instanceof Player player)) {
            return;
        }

        int radiusArgIndex = args.length >= 2 && "force".equalsIgnoreCase(args[1]) ? 2 : 1;
        if (args.length > radiusArgIndex && !args[radiusArgIndex].matches("\\d+")) {
            sender.sendMessage(ChatColor.RED + "/minerva regen [radius]");
            sender.sendMessage(ChatColor.GRAY + "例: /minerva regen 0, /minerva regen 2");
            return;
        }
        int radius = args.length > radiusArgIndex ? Math.min(8, parsePositiveInt(args[radiusArgIndex], 0)) : 0;
        Chunk center = player.getLocation().getChunk();
        int count = 0;
        int skipped = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                Chunk chunk = center.getWorld().getChunkAt(x, z);
                if (regenerateChunkNow(sender, chunk)) {
                    count++;
                } else {
                    skipped++;
                }
            }
        }
        plugin.saveData();
        sender.sendMessage(ChatColor.GREEN + "強制自然再生を実行しました: " + count + "チャンク / 保護・対象外・失敗: " + skipped + "チャンク");
    }

    void handleChunkCommand(Player player) {
        sendChunkInfo(player, player.getLocation().getChunk(), false);
    }

    void handleProtectCommand(Player player) {
        player.sendMessage(ChatColor.YELLOW + "保護したいチャンク内にビーコンを設置してください。");
        player.sendMessage(ChatColor.YELLOW + "ビーコンが存在するチャンクは、設置者以外が変更できず、自然再生対象から外れます。");
        player.sendMessage(ChatColor.RED + "保護していない建築物・チェスト・地下施設は、警告後に削除される可能性があります。");
    }

    private void sendChunkInfo(Player player, Chunk chunk, boolean admin) {
        String key = chunkKey(chunk);
        player.sendMessage(ChatColor.GREEN + "チャンク情報: " + key);
        player.sendMessage(ChatColor.GRAY + "ビーコン保護: " + (isActiveProtectedChunk(chunk.getBlock(0, chunk.getWorld().getMinHeight(), 0).getLocation()) ? "あり" : "なし"));
        player.sendMessage(ChatColor.GRAY + "再生成対象外: " + (isChunkRegenerationSafe(chunk) ? "はい" : "いいえ"));
        long scheduledAt = plugin.data().getLong("regen." + key + ".regenScheduledAt", 0L);
        if (scheduledAt > 0L) {
            player.sendMessage(ChatColor.RED + "自然再生予定: " + formatDateTime(scheduledAt));
        } else {
            player.sendMessage(ChatColor.GRAY + "自然再生予定: なし");
        }
        player.sendMessage(ChatColor.GRAY + "再生成回数: " + plugin.data().getInt("regen." + key + ".regenCount", 0));
        if (!admin && !isChunkRegenerationSafe(chunk)) {
            player.sendMessage(ChatColor.YELLOW + "建築物やアイテムを残したい場合は、ビーコンで保護してください。");
        }
    }

    private boolean isProtected(Player player, Location location) {
        if (player.hasPermission("minerva.admin")) {
            return false;
        }
        Chunk chunk = location.getChunk();
        String owner = getActiveChunkOwner(chunk);
        if (owner != null) {
            return !owner.equals(player.getUniqueId().toString());
        }
        return isCentralProtectedChunk(chunk) || isConfiguredProtectedChunk(chunk);
    }

    private boolean isActiveProtectedChunk(Location location) {
        return getActiveChunkOwner(location.getChunk()) != null;
    }

    private boolean isBuildProtectedChunk(Chunk chunk) {
        return getActiveChunkOwner(chunk) != null || isCentralProtectedChunk(chunk) || isConfiguredProtectedChunk(chunk);
    }

    private String getActiveChunkOwner(Chunk chunk) {
        String owner = plugin.data().getString("chunks." + chunkKey(chunk));
        return owner != null && chunkContainsBeacon(chunk) ? owner : null;
    }

    private boolean chunkContainsBeacon(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.BEACON) {
                return true;
            }
        }
        return false;
    }

    private void claimChunk(Player player, Chunk chunk) {
        plugin.data().set("chunks." + chunkKey(chunk), player.getUniqueId().toString());
        clearRegenSchedule(chunk);
        plugin.saveData();
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private boolean isChunkRegenerationSafe(Chunk chunk) {
        return isActiveProtectedChunk(chunk.getBlock(0, chunk.getWorld().getMinHeight(), 0).getLocation())
                || isCentralProtectedChunk(chunk)
                || isConfiguredProtectedChunk(chunk)
                || plugin.data().getBoolean("regen." + chunkKey(chunk) + ".excluded", false);
    }

    private boolean isCentralProtectedChunk(Chunk chunk) {
        Location hub = getCentralProtectionLocation(chunk.getWorld());
        if (hub == null || !hub.getWorld().equals(chunk.getWorld())) {
            return false;
        }
        int radius = Math.max(4, plugin.getConfig().getInt("hub-protection-radius-chunks", 4));
        return Math.abs(chunk.getX() - hub.getChunk().getX()) <= radius
                && Math.abs(chunk.getZ() - hub.getChunk().getZ()) <= radius;
    }

    private Location getCentralProtectionLocation(World world) {
        Location hub = plugin.readLocation("hub");
        if (hub != null) {
            return hub;
        }
        return world == null ? null : world.getSpawnLocation();
    }

    private boolean isConfiguredProtectedChunk(Chunk chunk) {
        String key = chunkKey(chunk);
        return plugin.getConfig().getStringList("regen.nation-chunks").contains(key)
                || plugin.getConfig().getStringList("regen.public-facility-chunks").contains(key)
                || plugin.getConfig().getStringList("regen.staff-excluded-chunks").contains(key);
    }

    private boolean isRegenScheduled(Chunk chunk) {
        return plugin.data().getLong("regen." + chunkKey(chunk) + ".regenScheduledAt", 0L) > 0L;
    }

    private void clearRegenSchedule(Chunk chunk) {
        String path = "regen." + chunkKey(chunk);
        plugin.data().set(path + ".warned", false);
        plugin.data().set(path + ".warnedAt", null);
        plugin.data().set(path + ".regenScheduledAt", null);
    }

    private void sendChunkWarning(Player player, Chunk chunk) {
        String key = chunkKey(chunk);
        String warningKey = key + ":place";
        if (warningKey.equals(lastChunkWarning.get(player.getUniqueId()))) {
            return;
        }
        lastChunkWarning.put(player.getUniqueId(), warningKey);
        if (isRegenScheduled(chunk)) {
            player.sendMessage(ChatColor.RED + plugin.getConfig().getString("messages.scheduledRegen",
                    "このチャンクは未保護のため、次回メンテナンス時に自然再生されます。"));
            player.sendMessage(ChatColor.RED + "保護しない場合、設置物・チェスト・地下施設は削除されます。");
        } else {
            player.sendMessage(ChatColor.YELLOW + plugin.getConfig().getString("messages.unprotectedChunk",
                    "このチャンクは保護されていません。次回の自然再生で再生成される可能性があります。"));
        }
        player.sendMessage(ChatColor.YELLOW + plugin.getConfig().getString("messages.protectPrompt",
                "建築物やアイテムを残したい場合は、ビーコンでチャンク保護してください。"));
    }

    private boolean isWarningPlacement(Material material) {
        String name = material.name();
        return name.contains("CHEST")
                || name.endsWith("_BED")
                || name.endsWith("CRAFTING_TABLE")
                || name.endsWith("FURNACE")
                || name.endsWith("ANVIL")
                || name.endsWith("BARREL")
                || name.endsWith("SHULKER_BOX")
                || name.endsWith("HOPPER")
                || name.endsWith("FARMLAND");
    }

    private boolean regenerateChunkNow(CommandSender sender, Chunk chunk) {
        if (isChunkRegenerationSafe(chunk)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = chunkKey(chunk);
        if (plugin.getConfig().getBoolean("backupBeforeRegen", true)) {
            plugin.getLogger().info("Forced regen backup marker: " + key + " at " + now);
        }
        int regenCount = plugin.data().getInt("regen." + key + ".regenCount", 0) + 1;
        boolean regenerated = regenerateChunkUsingSupportedApi(sender, chunk);
        if (!regenerated) {
            return false;
        }
        if (plugin.getConfig().getBoolean("customOreGeneration", true)) {
            applyCustomOreGeneration(chunk.getWorld().getChunkAt(chunk.getX(), chunk.getZ()), regenCount);
        }
        plugin.data().set("regen." + key + ".worldName", chunk.getWorld().getName());
        plugin.data().set("regen." + key + ".chunkX", chunk.getX());
        plugin.data().set("regen." + key + ".chunkZ", chunk.getZ());
        plugin.data().set("regen." + key + ".protected", false);
        plugin.data().set("regen." + key + ".regenCount", regenCount);
        plugin.data().set("regen." + key + ".lastRegen", now);
        plugin.data().set("regen." + key + ".warned", false);
        plugin.data().set("regen." + key + ".warnedAt", null);
        plugin.data().set("regen." + key + ".regenScheduledAt", null);
        plugin.getLogger().info("Force regenerated chunk " + key + " by " + sender.getName());
        return true;
    }

    private boolean regenerateChunkUsingSupportedApi(CommandSender sender, Chunk chunk) {
        sender.sendMessage(ChatColor.RED + "このPaper APIではチャンク再生成はサポートされていません。");
        plugin.getLogger().warning("Chunk regeneration is disabled because World#regenerateChunk is deprecated for removal and unsupported in this API: "
                + chunkKey(chunk));
        return false;
    }

    private void applyCustomOreGeneration(Chunk chunk, int regenCount) {
        reduceVanillaOres(chunk, regenCount);
        placeOreVeins(chunk, regenCount, Material.COAL_ORE, 18, 8, -32, 96);
        placeOreVeins(chunk, regenCount, Material.IRON_ORE, 16, 7, -48, 72);
        placeOreVeins(chunk, regenCount, Material.COPPER_ORE, 14, 8, -16, 80);
        placeOreVeins(chunk, regenCount, Material.REDSTONE_ORE, 8, 6, -64, 16);
        placeOreVeins(chunk, regenCount, Material.GOLD_ORE, 7, 5, -64, 32);
        placeOreVeins(chunk, regenCount, Material.LAPIS_ORE, 5, 5, -64, 32);
        placeOreVeins(chunk, regenCount, Material.DIAMOND_ORE, 4, 4, -64, 8);
        placeOreVeins(chunk, regenCount, Material.EMERALD_ORE, 3, 3, -32, 64);
        if (chunk.getWorld().getEnvironment() == World.Environment.NETHER) {
            placeOreVeins(chunk, regenCount, Material.ANCIENT_DEBRIS, 3, 2, 8, 24);
        }
    }

    private void reduceVanillaOres(Chunk chunk, int regenCount) {
        String mode = plugin.getConfig().getString("vanillaOreMode", "reduce");
        if ("keep".equalsIgnoreCase(mode)) {
            return;
        }
        Random oreRandom = new Random(oreSeed(chunk, regenCount, "vanilla_reduce"));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!isOre(block.getType())) {
                        continue;
                    }
                    if ("remove".equalsIgnoreCase(mode) || oreRandom.nextInt(100) < 70) {
                        block.setType(replacementStone(block.getType(), y), false);
                    }
                }
            }
        }
    }

    private void placeOreVeins(Chunk chunk, int regenCount, Material ore, int veins, int maxSize, int minY, int maxY) {
        Random oreRandom = new Random(oreSeed(chunk, regenCount, ore.name()));
        int worldMin = chunk.getWorld().getMinHeight();
        int worldMax = chunk.getWorld().getMaxHeight() - 1;
        int low = Math.max(worldMin, minY);
        int high = Math.min(worldMax, maxY);
        if (low > high) {
            return;
        }
        for (int i = 0; i < veins; i++) {
            int x = oreRandom.nextInt(16);
            int y = low + oreRandom.nextInt(high - low + 1);
            int z = oreRandom.nextInt(16);
            int size = 1 + oreRandom.nextInt(Math.max(1, maxSize));
            for (int n = 0; n < size; n++) {
                int px = Math.max(0, Math.min(15, x + oreRandom.nextInt(3) - 1));
                int py = Math.max(worldMin, Math.min(worldMax, y + oreRandom.nextInt(3) - 1));
                int pz = Math.max(0, Math.min(15, z + oreRandom.nextInt(3) - 1));
                Block block = chunk.getBlock(px, py, pz);
                if (canReplaceWithOre(block.getType(), ore)) {
                    block.setType(oreForBase(ore, block.getType()), false);
                }
            }
        }
    }

    private long oreSeed(Chunk chunk, int regenCount, String oreType) {
        String input = plugin.getConfig().getString("serverSecret", "change-this")
                + "|" + chunk.getWorld().getName()
                + "|" + chunk.getX()
                + "|" + chunk.getZ()
                + "|" + regenCount
                + "|" + oreType;
        long hash = 1125899906842597L;
        for (int i = 0; i < input.length(); i++) {
            hash = 31L * hash + input.charAt(i);
        }
        return hash;
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private Material replacementStone(Material ore, int y) {
        String name = ore.name();
        if (ore == Material.ANCIENT_DEBRIS) {
            return Material.NETHERRACK;
        }
        return name.startsWith("DEEPSLATE_") || y < 0 ? Material.DEEPSLATE : Material.STONE;
    }

    private boolean canReplaceWithOre(Material base, Material ore) {
        if (ore == Material.ANCIENT_DEBRIS) {
            return base == Material.NETHERRACK;
        }
        return base == Material.STONE || base == Material.DEEPSLATE;
    }

    private Material oreForBase(Material ore, Material base) {
        if (base == Material.DEEPSLATE) {
            Material deepslate = Material.matchMaterial("DEEPSLATE_" + ore.name());
            if (deepslate != null) {
                return deepslate;
            }
        }
        return ore;
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String formatDateTime(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }
}
