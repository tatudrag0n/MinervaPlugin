package org.server.minerva;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

final class StructureManager implements Listener {
   private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,48}");
   private final Minerva plugin;
   private final WorldEditHook worldEdit;
   private final Random random = new Random();
   private File file;
   private YamlConfiguration data;

   StructureManager(Minerva plugin) {
      this.plugin = plugin;
      this.worldEdit = new WorldEditHook(plugin);
   }

   void load() {
      this.ensureConfigDefaults();
      this.file = new File(this.plugin.getDataFolder(), "structures.yml");
      if (!this.file.exists()) {
         this.data = new YamlConfiguration();
         this.data.set("structures.registered", new LinkedHashMap());
         this.data.set("structures.generated", new LinkedHashMap());
         this.data.set("structures.generation-rules", new LinkedHashMap());
         this.save();
      } else {
         this.data = YamlConfiguration.loadConfiguration(this.file);
      }

      if (!this.worldEdit.available()) {
         this.plugin.getLogger().warning("WorldEdit is not installed. Clipboard structure registration/paste is disabled, range structures remain available.");
      }
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("minerva.admin")) {
         sender.sendMessage("§c権限がありません。");
         return true;
      }

      if (args.length < 2) {
         sender.sendMessage("§e/mva structure register|generate|delete <name>");
         return true;
      }

      this.ensureLoaded();

      return switch (args[1].toLowerCase(Locale.ROOT)) {
         case "register" -> this.handleRegister(sender, args);
         case "generate" -> this.handleGenerate(sender, args);
         case "delete", "delate" -> this.handleDelete(sender, args);
         default -> {
            sender.sendMessage("§e/mva structure register|generate|delete <name>");
            yield true;
         }
      };
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 2) {
         return List.of("register", "generate", "delete", "delate");
      } else if (args.length == 4 && "register".equalsIgnoreCase(args[1])) {
         return List.of("clipboard", "range");
      } else {
         return args.length == 5 && "generate".equalsIgnoreCase(args[1]) ? List.of("underground", "semiunderground", "ground", "sky", "range") : List.of();
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      if (event.isNewChunk() && this.plugin.getConfig().getBoolean("structures.enabled", true)) {
         this.ensureLoaded();
         ConfigurationSection rules = this.data.getConfigurationSection("structures.generation-rules");
         if (rules != null) {
            for (String name : rules.getKeys(false)) {
               this.tryGenerateInChunk(name, event.getChunk());
            }
         }
      }
   }

   private boolean handleRegister(CommandSender sender, String[] args) {
      if (args.length < 4) {
         sender.sendMessage("§c/mva structure register <name> clipboard|range ...");
         return true;
      }

      String name = args[2].toLowerCase(Locale.ROOT);
      if (!this.isSafeName(name)) {
         sender.sendMessage("§c登録名は英数字、_、- の48文字以内にしてください。");
         return true;
      }

      String source = args[3].toLowerCase(Locale.ROOT);
      if ("clipboard".equals(source)) {
         return this.registerClipboard(sender, name);
      }

      if ("range".equals(source)) {
         return this.registerRange(sender, name, args);
      }

      sender.sendMessage("§csource は clipboard または range です。");
      return true;
   }

   private boolean registerClipboard(CommandSender sender, String name) {
      if (sender instanceof Player player) {
         if (!this.worldEdit.available()) {
            sender.sendMessage("§cWorldEdit がないため clipboard 登録は使用できません。");
            return true;
         }

         File schematic = this.structureFile(name + ".schem");
         if (!this.worldEdit.savePlayerClipboard(player, schematic)) {
            sender.sendMessage("§cWorldEdit clipboard の保存に失敗しました。");
            return true;
         }

         String path = this.registeredPath(name);
         this.data.set(path + ".source", "clipboard");
         this.data.set(path + ".file", schematic.getName());
         int[] size = this.worldEdit.readSchematicSize(schematic);
         if (size != null) {
            this.data.set(path + ".size.x", size[0]);
            this.data.set(path + ".size.y", size[1]);
            this.data.set(path + ".size.z", size[2]);
         }

         this.data.set(path + ".created-at", System.currentTimeMillis());
         this.save();
         sender.sendMessage("§aclipboard 構造物を登録しました: " + name);
         return true;
      } else {
         sender.sendMessage("Player only.");
         return true;
      }
   }

   private boolean registerRange(CommandSender sender, String name, String[] args) {
      if (args.length < 11) {
         sender.sendMessage("§c/mva structure register <name> range <world> <x1> <y1> <z1> <x2> <y2> <z2>");
         return true;
      }

      World world = Bukkit.getWorld(args[4]);
      if (world == null) {
         sender.sendMessage("§cワールドが見つかりません: " + args[4]);
         return true;
      }

      int x1 = this.parseInt(args[5], 0);
      int y1 = this.parseInt(args[6], 0);
      int z1 = this.parseInt(args[7], 0);
      int x2 = this.parseInt(args[8], 0);
      int y2 = this.parseInt(args[9], 0);
      int z2 = this.parseInt(args[10], 0);
      int minX = Math.min(x1, x2);
      int minY = Math.min(y1, y2);
      int minZ = Math.min(z1, z2);
      int maxX = Math.max(x1, x2);
      int maxY = Math.max(y1, y2);
      int maxZ = Math.max(z1, z2);
      File rangeFile = this.structureFile(name + ".yml");
      YamlConfiguration structure = new YamlConfiguration();
      structure.set("size.x", maxX - minX + 1);
      structure.set("size.y", maxY - minY + 1);
      structure.set("size.z", maxZ - minZ + 1);
      List<String> blocks = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               Block block = world.getBlockAt(x, y, z);
               if (block.getType() != Material.AIR) {
                  blocks.add(x - minX + "," + (y - minY) + "," + (z - minZ) + ";" + block.getBlockData().getAsString());
               }
            }
         }
      }

      structure.set("blocks", blocks);

      try {
         rangeFile.getParentFile().mkdirs();
         structure.save(rangeFile);
      } catch (IOException e) {
         sender.sendMessage("§c構造物ファイルを保存できませんでした: " + e.getMessage());
         return true;
      }

      String path = this.registeredPath(name);
      this.data.set(path + ".source", "range");
      this.data.set(path + ".file", rangeFile.getName());
      this.data.set(path + ".size.x", maxX - minX + 1);
      this.data.set(path + ".size.y", maxY - minY + 1);
      this.data.set(path + ".size.z", maxZ - minZ + 1);
      this.data.set(path + ".created-at", System.currentTimeMillis());
      this.save();
      sender.sendMessage("§a範囲構造物を登録しました: " + name + " / blocks: " + blocks.size());
      return true;
   }

   private boolean handleGenerate(CommandSender sender, String[] args) {
      if (args.length < 5) {
         sender.sendMessage("§c/mva structure generate <name> <ratio1-10> <mode> [range...] [biome include|exclude ...]");
         return true;
      }

      String name = args[2].toLowerCase(Locale.ROOT);
      if (!this.data.contains(this.registeredPath(name))) {
         sender.sendMessage("§c構造物が登録されていません: " + name);
         return true;
      }

      int ratio = this.parseInt(args[3], -1);
      if (ratio >= 1 && ratio <= 10) {
         StructurePlacementMode mode = StructurePlacementMode.fromKey(args[4]);
         if (mode == null) {
            sender.sendMessage("§cmode は underground/semiunderground/ground/sky/range です。");
            return true;
         }

         int biomeIndex = this.findArg(args, "biome", 5);
         int rangeEnd = biomeIndex < 0 ? args.length : biomeIndex;
         String path = "structures.generation-rules." + name;
         this.data.set(path + ".ratio", ratio);
         this.data.set(path + ".mode", mode.key());
         if (mode == StructurePlacementMode.RANGE) {
            if (rangeEnd - 5 < 7) {
               sender.sendMessage("§crange mode では <world> <x1> <y1> <z1> <x2> <y2> <z2> が必要です。");
               return true;
            }

            World world = Bukkit.getWorld(args[5]);
            if (world == null) {
               sender.sendMessage("§cワールドが見つかりません: " + args[5]);
               return true;
            }

            this.writeRangeRule(
               path,
               world.getName(),
               this.parseInt(args[6], 0),
               this.parseInt(args[7], 0),
               this.parseInt(args[8], 0),
               this.parseInt(args[9], 0),
               this.parseInt(args[10], 0),
               this.parseInt(args[11], 0)
            );
         } else {
            World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorld(this.plugin.getConfig().getString("hub.world", "world"));
            this.data.set(path + ".world", world == null ? "world" : world.getName());
            this.data.set(path + ".range", null);
         }

         this.writeBiomeRule(path, args, biomeIndex);
         this.save();
         sender.sendMessage("§a構造物生成ルールを保存しました: " + name + " ratio=" + ratio + " mode=" + mode.key());
         return true;
      } else {
         sender.sendMessage("§c生成比率は 1〜10 です。");
         return true;
      }
   }

   private boolean handleDelete(CommandSender sender, String[] args) {
      if (args.length < 3) {
         sender.sendMessage("§c/mva structure delete <name>");
         return true;
      }

      String name = args[2].toLowerCase(Locale.ROOT);
      String fileName = this.data.getString(this.registeredPath(name) + ".file");
      this.data.set(this.registeredPath(name), null);
      this.data.set("structures.generation-rules." + name, null);
      this.data.set("structures.generated." + name, null);
      if (fileName != null) {
         File target = this.structureFile(fileName);
         if (target != null && target.exists() && !target.delete()) {
            this.plugin.getLogger().warning("Could not delete structure file: " + target.getAbsolutePath());
         }
      }

      this.save();
      sender.sendMessage("§e構造物登録と生成管理データを削除しました: " + name);
      return true;
   }

   private void tryGenerateInChunk(String name, Chunk chunk) {
      String path = "structures.generation-rules." + name;
      String worldName = this.data.getString(path + ".world", "");
      if (chunk.getWorld().getName().equals(worldName)) {
         if (!this.alreadyGeneratedInChunk(name, chunk)) {
            int ratio = Math.max(1, Math.min(10, this.data.getInt(path + ".ratio", 1)));
            double percent = this.plugin.getConfig().getDouble("structures.ratio-percent." + ratio, ratio * 0.2);
            if (!(this.random.nextDouble() * 100.0 >= percent)) {
               StructurePlacementMode mode = StructurePlacementMode.fromKey(this.data.getString(path + ".mode"));
               if (mode != null) {
                  Location location = this.chooseLocation(name, chunk, mode, path);
                  if (location != null && this.biomeAllowed(path, location.getBlock().getBiome())) {
                     if (this.isSafePlacement(name, location, mode)) {
                        if (this.place(name, location)) {
                           this.recordGenerated(name, location, chunk);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private Location chooseLocation(String name, Chunk chunk, StructurePlacementMode mode, String path) {
      World world = chunk.getWorld();
      int x = chunk.getX() * 16 + this.random.nextInt(16);
      int z = chunk.getZ() * 16 + this.random.nextInt(16);
      int sizeY = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.y", 1));
      if (mode == StructurePlacementMode.RANGE) {
         ConfigurationSection range = this.data.getConfigurationSection(path + ".range");
         if (range == null) {
            return null;
         } else {
            int minX = Math.max(chunk.getX() * 16, range.getInt("min-x"));
            int maxX = Math.min(chunk.getX() * 16 + 15, range.getInt("max-x"));
            int minZ = Math.max(chunk.getZ() * 16, range.getInt("min-z"));
            int maxZ = Math.min(chunk.getZ() * 16 + 15, range.getInt("max-z"));
            if (maxX >= minX && maxZ >= minZ) {
               int minY = Math.max(world.getMinHeight(), range.getInt("min-y"));
               int maxY = Math.min(world.getMaxHeight() - sizeY, range.getInt("max-y"));
               return maxY < minY
                  ? null
                  : new Location(
                     world,
                     minX + this.random.nextInt(maxX - minX + 1),
                     minY + this.random.nextInt(maxY - minY + 1),
                     minZ + this.random.nextInt(maxZ - minZ + 1)
                  );
            } else {
               return null;
            }
         }
      } else {
         int highest = world.getHighestBlockYAt(x, z);

         int y = switch (mode) {
            case GROUND -> highest + 1;
            case SEMIUNDERGROUND -> Math.max(world.getMinHeight() + 1, highest - Math.max(1, sizeY / 2));
            case UNDERGROUND -> {
               int min = world.getMinHeight() + 8;
               int max = Math.max(min, highest - Math.max(8, sizeY));
               yield min + this.random.nextInt(max - min + 1);
            }
            case SKY -> {
               int min = this.plugin.getConfig().getInt("structures.sky.min-y", 120);
               int max = this.plugin.getConfig().getInt("structures.sky.max-y", 220);
               yield Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - sizeY, min + this.random.nextInt(Math.max(1, max - min + 1))));
            }
            case RANGE -> highest + 1;
         };
         return world.getBlockAt(x, y, z).isLiquid() ? null : new Location(world, x, y, z);
      }
   }

   private boolean isSafePlacement(String name, Location origin, StructurePlacementMode mode) {
      StructureManager.StructureBounds bounds = this.boundsFor(name, origin);
      if (bounds != null && this.boundsWithinWorld(bounds)) {
         if (this.plugin.getConfig().getBoolean("structures.safety.avoid-protected", true) && this.overlapsProtectedArea(bounds)) {
            return false;
         } else {
            return this.plugin.getConfig().getBoolean("structures.safety.avoid-structure-overlap", true) && this.overlapsGeneratedStructure(name, bounds)
               ? false
               : this.hasAcceptableSpace(bounds, mode);
         }
      } else {
         return false;
      }
   }

   private StructureManager.StructureBounds boundsFor(String name, Location origin) {
      int sizeX = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.x", 1));
      int sizeY = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.y", 1));
      int sizeZ = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.z", 1));
      if (this.data.getInt(this.registeredPath(name) + ".size.x", 0) <= 0 && "clipboard".equals(this.data.getString(this.registeredPath(name) + ".source"))) {
         int[] size = this.worldEdit.readSchematicSize(this.structureFile(this.data.getString(this.registeredPath(name) + ".file", "")));
         if (size != null) {
            sizeX = Math.max(1, size[0]);
            sizeY = Math.max(1, size[1]);
            sizeZ = Math.max(1, size[2]);
            this.data.set(this.registeredPath(name) + ".size.x", sizeX);
            this.data.set(this.registeredPath(name) + ".size.y", sizeY);
            this.data.set(this.registeredPath(name) + ".size.z", sizeZ);
            this.save();
         }
      }

      return new StructureManager.StructureBounds(
         origin.getWorld(),
         origin.getBlockX(),
         origin.getBlockY(),
         origin.getBlockZ(),
         origin.getBlockX() + sizeX - 1,
         origin.getBlockY() + sizeY - 1,
         origin.getBlockZ() + sizeZ - 1
      );
   }

   private boolean boundsWithinWorld(StructureManager.StructureBounds bounds) {
      return bounds.world != null && bounds.minY >= bounds.world.getMinHeight() && bounds.maxY < bounds.world.getMaxHeight();
   }

   private boolean overlapsProtectedArea(StructureManager.StructureBounds bounds) {
      for (int chunkX = Math.floorDiv(bounds.minX, 16); chunkX <= Math.floorDiv(bounds.maxX, 16); chunkX++) {
         for (int chunkZ = Math.floorDiv(bounds.minZ, 16); chunkZ <= Math.floorDiv(bounds.maxZ, 16); chunkZ++) {
            int x = chunkX * 16;
            int z = chunkZ * 16;
            int y = Math.max(bounds.world.getMinHeight(), Math.min(bounds.world.getMaxHeight() - 1, bounds.minY));
            if (this.plugin.isStructureProtectedLocation(new Location(bounds.world, x, y, z))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean overlapsGeneratedStructure(String candidateName, StructureManager.StructureBounds candidate) {
      int margin = Math.max(0, this.plugin.getConfig().getInt("structures.safety.overlap-margin-blocks", 2));
      ConfigurationSection generated = this.data.getConfigurationSection("structures.generated");
      if (generated == null) {
         return false;
      }

      StructureManager.StructureBounds expanded = candidate.expand(margin);

      for (String name : generated.getKeys(false)) {
         for (String raw : this.data.getStringList("structures.generated." + name)) {
            StructureManager.StructureBounds existing = this.boundsFromGeneratedEntry(name, raw);
            if (existing != null && expanded.overlaps(existing)) {
               return true;
            }
         }
      }

      return false;
   }

   private StructureManager.StructureBounds boundsFromGeneratedEntry(String name, String raw) {
      String[] parts = raw.split(",");
      if (parts.length < 4) {
         return null;
      } else {
         World world = Bukkit.getWorld(parts[0]);
         if (world == null) {
            return null;
         } else {
            int x = this.parseInt(parts[1], Integer.MIN_VALUE);
            int y = this.parseInt(parts[2], Integer.MIN_VALUE);
            int z = this.parseInt(parts[3], Integer.MIN_VALUE);
            if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE) {
               int sizeX = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.x", 1));
               int sizeY = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.y", 1));
               int sizeZ = Math.max(1, this.data.getInt(this.registeredPath(name) + ".size.z", 1));
               return new StructureManager.StructureBounds(world, x, y, z, x + sizeX - 1, y + sizeY - 1, z + sizeZ - 1);
            } else {
               return null;
            }
         }
      }
   }

   private boolean hasAcceptableSpace(StructureManager.StructureBounds bounds, StructurePlacementMode mode) {
      if (!this.plugin.getConfig().getBoolean("structures.safety.check-space", true)) {
         return true;
      }

      double maxSolidRatio = switch (mode) {
         case GROUND, RANGE -> this.plugin.getConfig().getDouble("structures.safety.max-solid-ratio.ground", 0.2);
         case SEMIUNDERGROUND -> this.plugin.getConfig().getDouble("structures.safety.max-solid-ratio.semiunderground", 0.7);
         case UNDERGROUND -> this.plugin.getConfig().getDouble("structures.safety.max-solid-ratio.underground", 1.0);
         case SKY -> this.plugin.getConfig().getDouble("structures.safety.max-solid-ratio.sky", 0.0);
      };
      int maxSamples = Math.max(32, this.plugin.getConfig().getInt("structures.safety.max-space-samples", 512));
      int totalBlocks = Math.max(1, bounds.volume());
      int step = Math.max(1, (int)Math.ceil(Math.cbrt((double)totalBlocks / maxSamples)));
      int sampled = 0;
      int solid = 0;

      for (int x = bounds.minX; x <= bounds.maxX; x += step) {
         for (int y = bounds.minY; y <= bounds.maxY; y += step) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
               sampled++;
               Material type = bounds.world.getBlockAt(x, y, z).getType();
               if (type.isSolid() || type == Material.WATER || type == Material.LAVA) {
                  solid++;
               }
            }
         }
      }

      return sampled == 0 || (double)solid / sampled <= maxSolidRatio;
   }

   private boolean place(String name, Location location) {
      String source = this.data.getString(this.registeredPath(name) + ".source", "");
      File sourceFile = this.structureFile(this.data.getString(this.registeredPath(name) + ".file", ""));
      if (!"clipboard".equals(source)) {
         if ("range".equals(source) && sourceFile != null && sourceFile.exists()) {
            YamlConfiguration structure = YamlConfiguration.loadConfiguration(sourceFile);

            for (String raw : structure.getStringList("blocks")) {
               int split = raw.indexOf(59);
               if (split > 0) {
                  String[] parts = raw.substring(0, split).split(",");
                  if (parts.length == 3) {
                     int dx = this.parseInt(parts[0], 0);
                     int dy = this.parseInt(parts[1], 0);
                     int dz = this.parseInt(parts[2], 0);

                     try {
                        BlockData blockData = Bukkit.createBlockData(raw.substring(split + 1));
                        location.getWorld()
                           .getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz)
                           .setBlockData(blockData, false);
                     } catch (IllegalArgumentException var14) {
                     }
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return sourceFile != null && this.worldEdit.pasteSchematic(sourceFile, location);
      }
   }

   private void recordGenerated(String name, Location location, Chunk chunk) {
      List<String> entries = this.data.getStringList("structures.generated." + name);
      entries.add(
         location.getWorld().getName()
            + ","
            + location.getBlockX()
            + ","
            + location.getBlockY()
            + ","
            + location.getBlockZ()
            + ","
            + chunk.getX()
            + ","
            + chunk.getZ()
            + ","
            + System.currentTimeMillis()
      );
      this.data.set("structures.generated." + name, entries);
      this.save();
   }

   private boolean alreadyGeneratedInChunk(String name, Chunk chunk) {
      String prefix = chunk.getWorld().getName() + ",";

      for (String raw : this.data.getStringList("structures.generated." + name)) {
         String[] parts = raw.split(",");
         if (parts.length >= 6
            && raw.startsWith(prefix)
            && this.parseInt(parts[4], Integer.MIN_VALUE) == chunk.getX()
            && this.parseInt(parts[5], Integer.MIN_VALUE) == chunk.getZ()) {
            return true;
         }
      }

      return false;
   }

   private void writeRangeRule(String path, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
      this.data.set(path + ".world", world);
      this.data.set(path + ".range.min-x", Math.min(x1, x2));
      this.data.set(path + ".range.min-y", Math.min(y1, y2));
      this.data.set(path + ".range.min-z", Math.min(z1, z2));
      this.data.set(path + ".range.max-x", Math.max(x1, x2));
      this.data.set(path + ".range.max-y", Math.max(y1, y2));
      this.data.set(path + ".range.max-z", Math.max(z1, z2));
   }

   private void writeBiomeRule(String path, String[] args, int biomeIndex) {
      this.data.set(path + ".biome.mode", "none");
      this.data.set(path + ".biome.values", List.of());
      if (biomeIndex >= 0 && biomeIndex + 2 < args.length) {
         String mode = args[biomeIndex + 1].toLowerCase(Locale.ROOT);
         if (mode.equals("include") || mode.equals("exclude")) {
            List<String> values = new ArrayList<>();

            for (int i = biomeIndex + 2; i < args.length; i++) {
               String value = args[i].toLowerCase(Locale.ROOT);
               if (!value.isBlank()) {
                  values.add(value);
               }
            }

            this.data.set(path + ".biome.mode", mode);
            this.data.set(path + ".biome.values", values);
         }
      }
   }

   private boolean biomeAllowed(String path, Biome biome) {
      String mode = this.data.getString(path + ".biome.mode", "none");
      List<String> values = this.data.getStringList(path + ".biome.values");
      if (!"none".equals(mode) && !values.isEmpty() && !values.contains("none") && !values.contains("all")) {
         String key = biome.key().value().toLowerCase(Locale.ROOT);
         boolean matched = values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> value.equals(key));
         if ("include".equals(mode)) {
            return matched;
         } else {
            return "exclude".equals(mode) ? !matched : true;
         }
      } else {
         return true;
      }
   }

   private int findArg(String[] args, String needle, int from) {
      for (int i = from; i < args.length; i++) {
         if (needle.equalsIgnoreCase(args[i])) {
            return i;
         }
      }

      return -1;
   }

   private boolean isSafeName(String name) {
      return name != null && SAFE_NAME.matcher(name).matches();
   }

   private String registeredPath(String name) {
      return "structures.registered." + name;
   }

   private File structureFile(String name) {
      if (name != null && !name.isBlank()) {
         String lowerName = name.toLowerCase(Locale.ROOT);
         if (!lowerName.endsWith(".yml") && !lowerName.endsWith(".schem")) {
            this.plugin.getLogger().warning("Rejected structure file with unsupported extension: " + name);
            return null;
         }

         File baseDir = new File(this.plugin.getDataFolder(), "structures");
         Path basePath = baseDir.toPath().toAbsolutePath().normalize();
         Path candidate = basePath.resolve(name).toAbsolutePath().normalize();
         if (candidate.startsWith(basePath) && basePath.equals(candidate.getParent())) {
            return candidate.toFile();
         }

         this.plugin.getLogger().warning("Rejected structure file outside structures directory: " + name);
         return null;
      } else {
         return null;
      }
   }

   private int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private void ensureLoaded() {
      if (this.data == null) {
         this.load();
      }
   }

   private void save() {
      try {
         if (this.file.getParentFile() != null && !this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         this.data.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save structures.yml: " + e.getMessage());
      }
   }

   private void ensureConfigDefaults() {
      this.setIfMissing("structures.enabled", true);

      for (int i = 1; i <= 10; i++) {
         this.setIfMissing("structures.ratio-percent." + i, i * 0.2);
      }

      this.setIfMissing("structures.sky.min-y", 120);
      this.setIfMissing("structures.sky.max-y", 220);
      this.setIfMissing("structures.safety.avoid-protected", true);
      this.setIfMissing("structures.safety.avoid-structure-overlap", true);
      this.setIfMissing("structures.safety.overlap-margin-blocks", 2);
      this.setIfMissing("structures.safety.check-space", true);
      this.setIfMissing("structures.safety.max-space-samples", 512);
      this.setIfMissing("structures.safety.max-solid-ratio.sky", 0.0);
      this.setIfMissing("structures.safety.max-solid-ratio.ground", 0.2);
      this.setIfMissing("structures.safety.max-solid-ratio.semiunderground", 0.7);
      this.setIfMissing("structures.safety.max-solid-ratio.underground", 1.0);
      this.plugin.saveConfig();
   }

   private void setIfMissing(String path, Object value) {
      if (!this.plugin.getConfig().contains(path)) {
         this.plugin.getConfig().set(path, value);
      }
   }

   private record StructureBounds(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      boolean overlaps(StructureManager.StructureBounds other) {
         return other != null
            && this.world.equals(other.world)
            && this.minX <= other.maxX
            && this.maxX >= other.minX
            && this.minY <= other.maxY
            && this.maxY >= other.minY
            && this.minZ <= other.maxZ
            && this.maxZ >= other.minZ;
      }

      StructureManager.StructureBounds expand(int blocks) {
         return new StructureManager.StructureBounds(
            this.world, this.minX - blocks, this.minY - blocks, this.minZ - blocks, this.maxX + blocks, this.maxY + blocks, this.maxZ + blocks
         );
      }

      int volume() {
         long x = (long)this.maxX - this.minX + 1L;
         long y = (long)this.maxY - this.minY + 1L;
         long z = (long)this.maxZ - this.minZ + 1L;
         long volume = Math.max(1L, x * y * z);
         return volume > 2147483647L ? Integer.MAX_VALUE : (int)volume;
      }
   }
}
