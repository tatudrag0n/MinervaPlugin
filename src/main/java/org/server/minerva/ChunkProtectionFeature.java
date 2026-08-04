package org.server.minerva;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class ChunkProtectionFeature implements Listener {
   private final Minerva plugin;
   private final NamespacedKey protectionBeaconKey;
   private final Map<UUID, String> lastChunkWarning = new ConcurrentHashMap<>();

   ChunkProtectionFeature(Minerva plugin) {
      this.plugin = plugin;
      this.protectionBeaconKey = new NamespacedKey(plugin, "chunk_protection_beacon");
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.plugin.isShopBlock(event.getBlock())) {
         event.setCancelled(true);
         event.setDropItems(false);
         event.setExpToDrop(0);
         event.getPlayer().sendMessage(ChatColor.YELLOW + "ショップ化されたブロックはショップワンドで解除してください。");
      } else if (this.isProtected(event.getPlayer(), event.getBlock().getLocation())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
      } else {
         if (event.getBlock().getType() == Material.BEACON && this.isMarkedChunkProtectionBeacon(event.getBlock().getState())) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), this.plugin.createChunkProtectionBeacon());
         }

         this.plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-broken", 1);
      }
   }

   @EventHandler
   public void onBlockPlace(BlockPlaceEvent event) {
      if (this.isProtected(event.getPlayer(), event.getBlockPlaced().getLocation())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
      } else if (event.getBlockPlaced().getType() == Material.BEACON && this.isChunkProtectionBeaconItem(event.getItemInHand())) {
         this.markChunkProtectionBeacon(event.getBlockPlaced());
         this.claimChunk(event.getPlayer(), event.getBlockPlaced().getChunk());
         this.plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-placed", 1);
         this.plugin.recordQuestProgress(event.getPlayer(), "protected_chunks", 1);
         event.getPlayer().sendMessage(ChatColor.GREEN + "このチャンクを保護しました。");
      } else {
         if (this.isWarningPlacement(event.getBlockPlaced().getType()) && !this.isChunkRegenerationSafe(event.getBlockPlaced().getChunk())) {
            this.sendChunkWarning(event.getPlayer(), event.getBlockPlaced().getChunk());
         }

         this.plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-placed", 1);
      }
   }

   @EventHandler
   public void onEntityExplode(EntityExplodeEvent event) {
      event.blockList().removeIf(block -> this.isBuildProtectedChunk(block.getChunk()) || this.plugin.isShopBlock(block));
   }

   @EventHandler
   public void onBlockExplode(BlockExplodeEvent event) {
      event.blockList().removeIf(block -> this.isBuildProtectedChunk(block.getChunk()) || this.plugin.isShopBlock(block));
   }

   void handleRegenCommand(CommandSender sender, String[] args) {
      if (this.plugin.hasPermission(sender, "minerva.admin.regen.force") && sender instanceof Player player) {
         int radiusArgIndex = args.length >= 2 && "force".equalsIgnoreCase(args[1]) ? 2 : 1;
         if (args.length > radiusArgIndex && !args[radiusArgIndex].matches("\\d+")) {
            sender.sendMessage(ChatColor.RED + "/minerva regen [radius]");
            sender.sendMessage(ChatColor.GRAY + "例: /minerva regen 0, /minerva regen 2");
         } else {
            int radius = args.length > radiusArgIndex ? Math.min(8, this.parsePositiveInt(args[radiusArgIndex], 0)) : 0;
            Chunk center = player.getLocation().getChunk();
            int count = 0;
            int skipped = 0;

            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
               for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                  Chunk chunk = center.getWorld().getChunkAt(x, z);
                  if (this.regenerateChunkNow(sender, chunk)) {
                     count++;
                  } else {
                     skipped++;
                  }
               }
            }

            this.plugin.saveData();
            sender.sendMessage(ChatColor.GREEN + "強制自然再生を実行しました: " + count + "チャンク / 保護・対象外・失敗: " + skipped + "チャンク");
         }
      }
   }

   void handleChunkCommand(Player player) {
      this.sendChunkInfo(player, player.getLocation().getChunk(), false);
   }

   void handleProtectCommand(Player player) {
      ItemStack beacon = this.plugin.createChunkProtectionBeacon();
      Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{beacon});
      if (!leftovers.isEmpty()) {
         player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
      } else {
         player.sendMessage(ChatColor.YELLOW + "保護したいチャンク内にチャンク保護ビーコンを設置してください。");
         player.sendMessage(ChatColor.YELLOW + "通常ビーコンではチャンク保護されません。");
         player.sendMessage(ChatColor.YELLOW + "チャンク保護ビーコンが存在するチャンクは、設置者以外が変更できず、自然再生対象から外れます。");
         player.sendMessage(ChatColor.RED + "保護していない建築物・チェスト・地下施設は、警告後に削除される可能性があります。");
      }
   }

   private void sendChunkInfo(Player player, Chunk chunk, boolean admin) {
      String key = this.chunkKey(chunk);
      player.sendMessage(ChatColor.GREEN + "チャンク情報: " + key);
      player.sendMessage(
         ChatColor.GRAY + "ビーコン保護: " + (this.isActiveProtectedChunk(chunk.getBlock(0, chunk.getWorld().getMinHeight(), 0).getLocation()) ? "あり" : "なし")
      );
      player.sendMessage(ChatColor.GRAY + "再生成対象外: " + (this.isChunkRegenerationSafe(chunk) ? "はい" : "いいえ"));
      long scheduledAt = this.plugin.data().getLong("regen." + key + ".regenScheduledAt", 0L);
      if (scheduledAt > 0L) {
         player.sendMessage(ChatColor.RED + "自然再生予定: " + this.formatDateTime(scheduledAt));
      } else {
         player.sendMessage(ChatColor.GRAY + "自然再生予定: なし");
      }

      player.sendMessage(ChatColor.GRAY + "再生成回数: " + this.plugin.data().getInt("regen." + key + ".regenCount", 0));
      if (!admin && !this.isChunkRegenerationSafe(chunk)) {
         player.sendMessage(ChatColor.YELLOW + "建築物やアイテムを残したい場合は、ビーコンで保護してください。");
      }
   }

   private boolean isProtected(Player player, Location location) {
      if (!player.hasPermission("minerva.admin") && !player.hasPermission("minerva.protect.bypass")) {
         Chunk chunk = location.getChunk();
         String owner = this.getActiveChunkOwner(chunk);
         return owner != null ? !this.isTrusted(player, location) : this.isCentralProtectedChunk(chunk) || this.isConfiguredProtectedChunk(chunk);
      } else {
         return false;
      }
   }

   boolean isProtectedLocation(Location location) {
      return location != null && this.isBuildProtectedChunk(location.getChunk());
   }

   boolean canBuild(Player player, Location location) {
      return !this.isProtected(player, location);
   }

   boolean isTrusted(Player player, Location location) {
      if (player != null && location != null) {
         Chunk chunk = location.getChunk();
         String owner = this.getActiveChunkOwner(chunk);
         if (owner == null) {
            return player.hasPermission("minerva.admin") || player.hasPermission("minerva.protect.bypass");
         } else {
            return owner.equals(player.getUniqueId().toString())
               ? true
               : this.plugin.data().getStringList("chunk-trust." + this.chunkKey(chunk)).contains(player.getUniqueId().toString());
         }
      } else {
         return false;
      }
   }

   boolean isSpawnProtected(Location location) {
      return location != null && this.isCentralProtectedChunk(location.getChunk());
   }

   private boolean isActiveProtectedChunk(Location location) {
      return this.getActiveChunkOwner(location.getChunk()) != null;
   }

   private boolean isBuildProtectedChunk(Chunk chunk) {
      return this.getActiveChunkOwner(chunk) != null || this.isCentralProtectedChunk(chunk) || this.isConfiguredProtectedChunk(chunk);
   }

   private String getActiveChunkOwner(Chunk chunk) {
      String owner = this.plugin.data().getString("chunks." + this.chunkKey(chunk));
      return owner != null && this.chunkContainsProtectionBeacon(chunk) ? owner : null;
   }

   private boolean chunkContainsProtectionBeacon(Chunk chunk) {
      for (BlockState state : chunk.getTileEntities()) {
         if (state.getType() == Material.BEACON && this.isMarkedChunkProtectionBeacon(state)) {
            return true;
         }
      }

      return false;
   }

   private boolean isChunkProtectionBeaconItem(ItemStack item) {
      return this.plugin.isMinervaItem(item, "chunk_protection_beacon");
   }

   private void markChunkProtectionBeacon(Block block) {
      if (block.getState() instanceof TileState tileState) {
         tileState.getPersistentDataContainer().set(this.protectionBeaconKey, PersistentDataType.BOOLEAN, true);
         tileState.update(true, false);
      }
   }

   private boolean isMarkedChunkProtectionBeacon(BlockState state) {
      return state instanceof TileState tileState
         && Boolean.TRUE.equals(tileState.getPersistentDataContainer().get(this.protectionBeaconKey, PersistentDataType.BOOLEAN));
   }

   private void claimChunk(Player player, Chunk chunk) {
      this.plugin.data().set("chunks." + this.chunkKey(chunk), player.getUniqueId().toString());
      this.clearRegenSchedule(chunk);
      this.plugin.saveData();
   }

   private String chunkKey(Chunk chunk) {
      return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
   }

   private boolean isChunkRegenerationSafe(Chunk chunk) {
      return this.isActiveProtectedChunk(chunk.getBlock(0, chunk.getWorld().getMinHeight(), 0).getLocation())
         || this.isCentralProtectedChunk(chunk)
         || this.isConfiguredProtectedChunk(chunk)
         || this.plugin.data().getBoolean("regen." + this.chunkKey(chunk) + ".excluded", false);
   }

   private boolean isCentralProtectedChunk(Chunk chunk) {
      Location hub = this.getCentralProtectionLocation(chunk.getWorld());
      if (hub != null && hub.getWorld().equals(chunk.getWorld())) {
         int radius = Math.max(4, this.plugin.getConfig().getInt("hub-protection-radius-chunks", 4));
         return Math.abs(chunk.getX() - hub.getChunk().getX()) <= radius && Math.abs(chunk.getZ() - hub.getChunk().getZ()) <= radius;
      } else {
         return false;
      }
   }

   private Location getCentralProtectionLocation(World world) {
      Location hub = this.plugin.readLocation("hub");
      if (hub != null) {
         return hub;
      } else {
         return world == null ? null : world.getSpawnLocation();
      }
   }

   private boolean isConfiguredProtectedChunk(Chunk chunk) {
      String key = this.chunkKey(chunk);
      return this.plugin.getConfig().getStringList("regen.nation-chunks").contains(key)
         || this.plugin.getConfig().getStringList("regen.public-facility-chunks").contains(key)
         || this.plugin.getConfig().getStringList("regen.staff-excluded-chunks").contains(key);
   }

   private boolean isRegenScheduled(Chunk chunk) {
      return this.plugin.data().getLong("regen." + this.chunkKey(chunk) + ".regenScheduledAt", 0L) > 0L;
   }

   private void clearRegenSchedule(Chunk chunk) {
      String path = "regen." + this.chunkKey(chunk);
      this.plugin.data().set(path + ".warned", false);
      this.plugin.data().set(path + ".warnedAt", null);
      this.plugin.data().set(path + ".regenScheduledAt", null);
   }

   private void sendChunkWarning(Player player, Chunk chunk) {
      String key = this.chunkKey(chunk);
      String warningKey = key + ":place";
      if (!warningKey.equals(this.lastChunkWarning.get(player.getUniqueId()))) {
         this.lastChunkWarning.put(player.getUniqueId(), warningKey);
         if (this.isRegenScheduled(chunk)) {
            player.sendMessage(ChatColor.RED + this.plugin.getConfig().getString("messages.scheduledRegen", "このチャンクは未保護のため、次回メンテナンス時に自然再生されます。"));
            player.sendMessage(ChatColor.RED + "保護しない場合、設置物・チェスト・地下施設は削除されます。");
         } else {
            player.sendMessage(ChatColor.YELLOW + this.plugin.getConfig().getString("messages.unprotectedChunk", "このチャンクは保護されていません。次回の自然再生で再生成される可能性があります。"));
         }

         player.sendMessage(ChatColor.YELLOW + this.plugin.getConfig().getString("messages.protectPrompt", "建築物やアイテムを残したい場合は、ビーコンでチャンク保護してください。"));
      }
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
      if (this.isChunkRegenerationSafe(chunk)) {
         return false;
      }

      long now = System.currentTimeMillis();
      String key = this.chunkKey(chunk);
      if (this.plugin.getConfig().getBoolean("backupBeforeRegen", true)) {
         this.plugin.getLogger().info("Forced regen backup marker: " + key + " at " + now);
      }

      int regenCount = this.plugin.data().getInt("regen." + key + ".regenCount", 0) + 1;
      boolean regenerated = this.regenerateChunkUsingSupportedApi(sender, chunk);
      if (!regenerated) {
         return false;
      }

      if (this.plugin.getConfig().getBoolean("customOreGeneration", true)) {
         this.applyCustomOreGeneration(chunk.getWorld().getChunkAt(chunk.getX(), chunk.getZ()), regenCount);
      }

      this.plugin.data().set("regen." + key + ".worldName", chunk.getWorld().getName());
      this.plugin.data().set("regen." + key + ".chunkX", chunk.getX());
      this.plugin.data().set("regen." + key + ".chunkZ", chunk.getZ());
      this.plugin.data().set("regen." + key + ".protected", false);
      this.plugin.data().set("regen." + key + ".regenCount", regenCount);
      this.plugin.data().set("regen." + key + ".lastRegen", now);
      this.plugin.data().set("regen." + key + ".warned", false);
      this.plugin.data().set("regen." + key + ".warnedAt", null);
      this.plugin.data().set("regen." + key + ".regenScheduledAt", null);
      this.plugin.getLogger().info("Force regenerated chunk " + key + " by " + sender.getName());
      return true;
   }

   private boolean regenerateChunkUsingSupportedApi(CommandSender sender, Chunk chunk) {
      sender.sendMessage(ChatColor.RED + "このPaper APIではチャンク再生成はサポートされていません。");
      this.plugin
         .getLogger()
         .warning("Chunk regeneration is disabled because World#regenerateChunk is deprecated for removal and unsupported in this API: " + this.chunkKey(chunk));
      return false;
   }

   private void applyCustomOreGeneration(Chunk chunk, int regenCount) {
      this.reduceVanillaOres(chunk, regenCount);
      this.placeOreVeins(chunk, regenCount, Material.COAL_ORE, 18, 8, -32, 96);
      this.placeOreVeins(chunk, regenCount, Material.IRON_ORE, 16, 7, -48, 72);
      this.placeOreVeins(chunk, regenCount, Material.COPPER_ORE, 14, 8, -16, 80);
      this.placeOreVeins(chunk, regenCount, Material.REDSTONE_ORE, 8, 6, -64, 16);
      this.placeOreVeins(chunk, regenCount, Material.GOLD_ORE, 7, 5, -64, 32);
      this.placeOreVeins(chunk, regenCount, Material.LAPIS_ORE, 5, 5, -64, 32);
      this.placeOreVeins(chunk, regenCount, Material.DIAMOND_ORE, 4, 4, -64, 8);
      this.placeOreVeins(chunk, regenCount, Material.EMERALD_ORE, 3, 3, -32, 64);
      if (chunk.getWorld().getEnvironment() == Environment.NETHER) {
         this.placeOreVeins(chunk, regenCount, Material.ANCIENT_DEBRIS, 3, 2, 8, 24);
      }
   }

   private void reduceVanillaOres(Chunk chunk, int regenCount) {
      String mode = this.plugin.getConfig().getString("vanillaOreMode", "reduce");
      if (!"keep".equalsIgnoreCase(mode)) {
         Random oreRandom = new Random(this.oreSeed(chunk, regenCount, "vanilla_reduce"));

         for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
               for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                  Block block = chunk.getBlock(x, y, z);
                  if (this.isOre(block.getType()) && ("remove".equalsIgnoreCase(mode) || oreRandom.nextInt(100) < 70)) {
                     block.setType(this.replacementStone(block.getType(), y), false);
                  }
               }
            }
         }
      }
   }

   private void placeOreVeins(Chunk chunk, int regenCount, Material ore, int veins, int maxSize, int minY, int maxY) {
      Random oreRandom = new Random(this.oreSeed(chunk, regenCount, ore.name()));
      int worldMin = chunk.getWorld().getMinHeight();
      int worldMax = chunk.getWorld().getMaxHeight() - 1;
      int low = Math.max(worldMin, minY);
      int high = Math.min(worldMax, maxY);
      if (low <= high) {
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
               if (this.canReplaceWithOre(block.getType(), ore)) {
                  block.setType(this.oreForBase(ore, block.getType()), false);
               }
            }
         }
      }
   }

   private long oreSeed(Chunk chunk, int regenCount, String oreType) {
      String secret = this.plugin.getConfig().getString("serverSecret", "change-this");
      if (secret == null || secret.isBlank() || "change-this".equals(secret)) {
         secret = UUID.randomUUID().toString();
         this.plugin.getConfig().set("serverSecret", secret);
         this.plugin.saveConfig();
         this.plugin.getLogger().warning("Generated a random serverSecret for ore regeneration seeds. Keep config.yml private.");
      }

      String input = secret + "|" + chunk.getWorld().getName() + "|" + chunk.getX() + "|" + chunk.getZ() + "|" + regenCount + "|" + oreType;
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
      } else {
         return !name.startsWith("DEEPSLATE_") && y >= 0 ? Material.STONE : Material.DEEPSLATE;
      }
   }

   private boolean canReplaceWithOre(Material base, Material ore) {
      return ore == Material.ANCIENT_DEBRIS ? base == Material.NETHERRACK : base == Material.STONE || base == Material.DEEPSLATE;
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
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
   }
}
