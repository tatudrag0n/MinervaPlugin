package org.server.minerva;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

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
        ensureConfigDefaults();
        file = new File(plugin.getDataFolder(), "structures.yml");
        if (!file.exists()) {
            data = new YamlConfiguration();
            data.set("structures.registered", new java.util.LinkedHashMap<>());
            data.set("structures.generated", new java.util.LinkedHashMap<>());
            data.set("structures.generation-rules", new java.util.LinkedHashMap<>());
            save();
        } else {
            data = YamlConfiguration.loadConfiguration(file);
        }
        if (!worldEdit.available()) {
            plugin.getLogger().warning("WorldEdit is not installed. Clipboard structure registration/paste is disabled, range structures remain available.");
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
        ensureLoaded();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "register" -> handleRegister(sender, args);
            case "generate" -> handleGenerate(sender, args);
            case "delete", "delate" -> handleDelete(sender, args);
            default -> {
                sender.sendMessage("§e/mva structure register|generate|delete <name>");
                yield true;
            }
        };
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("register", "generate", "delete", "delate");
        }
        if (args.length == 4 && "register".equalsIgnoreCase(args[1])) {
            return List.of("clipboard", "range");
        }
        if (args.length == 5 && "generate".equalsIgnoreCase(args[1])) {
            return List.of("underground", "semiunderground", "ground", "sky", "range");
        }
        return List.of();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("structures.enabled", true)) {
            return;
        }
        ensureLoaded();
        ConfigurationSection rules = data.getConfigurationSection("structures.generation-rules");
        if (rules == null) {
            return;
        }
        for (String name : rules.getKeys(false)) {
            tryGenerateInChunk(name, event.getChunk());
        }
    }

    private boolean handleRegister(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c/mva structure register <name> clipboard|range ...");
            return true;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!isSafeName(name)) {
            sender.sendMessage("§c登録名は英数字、_、- の48文字以内にしてください。");
            return true;
        }
        String source = args[3].toLowerCase(Locale.ROOT);
        if ("clipboard".equals(source)) {
            return registerClipboard(sender, name);
        }
        if ("range".equals(source)) {
            return registerRange(sender, name, args);
        }
        sender.sendMessage("§csource は clipboard または range です。");
        return true;
    }

    private boolean registerClipboard(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (!worldEdit.available()) {
            sender.sendMessage("§cWorldEdit がないため clipboard 登録は使用できません。");
            return true;
        }
        File schematic = structureFile(name + ".schem");
        if (!worldEdit.savePlayerClipboard(player, schematic)) {
            sender.sendMessage("§cWorldEdit clipboard の保存に失敗しました。");
            return true;
        }
        String path = registeredPath(name);
        data.set(path + ".source", "clipboard");
        data.set(path + ".file", schematic.getName());
        data.set(path + ".created-at", System.currentTimeMillis());
        save();
        sender.sendMessage("§aclipboard 構造物を登録しました: " + name);
        return true;
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
        int x1 = parseInt(args[5], 0);
        int y1 = parseInt(args[6], 0);
        int z1 = parseInt(args[7], 0);
        int x2 = parseInt(args[8], 0);
        int y2 = parseInt(args[9], 0);
        int z2 = parseInt(args[10], 0);
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        File rangeFile = structureFile(name + ".yml");
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
                        blocks.add((x - minX) + "," + (y - minY) + "," + (z - minZ) + ";" + block.getBlockData().getAsString());
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
        String path = registeredPath(name);
        data.set(path + ".source", "range");
        data.set(path + ".file", rangeFile.getName());
        data.set(path + ".size.x", maxX - minX + 1);
        data.set(path + ".size.y", maxY - minY + 1);
        data.set(path + ".size.z", maxZ - minZ + 1);
        data.set(path + ".created-at", System.currentTimeMillis());
        save();
        sender.sendMessage("§a範囲構造物を登録しました: " + name + " / blocks: " + blocks.size());
        return true;
    }

    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c/mva structure generate <name> <ratio1-10> <mode> [range...] [biome include|exclude ...]");
            return true;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!data.contains(registeredPath(name))) {
            sender.sendMessage("§c構造物が登録されていません: " + name);
            return true;
        }
        int ratio = parseInt(args[3], -1);
        if (ratio < 1 || ratio > 10) {
            sender.sendMessage("§c生成比率は 1〜10 です。");
            return true;
        }
        StructurePlacementMode mode = StructurePlacementMode.fromKey(args[4]);
        if (mode == null) {
            sender.sendMessage("§cmode は underground/semiunderground/ground/sky/range です。");
            return true;
        }
        int biomeIndex = findArg(args, "biome", 5);
        int rangeEnd = biomeIndex < 0 ? args.length : biomeIndex;
        String path = "structures.generation-rules." + name;
        data.set(path + ".ratio", ratio);
        data.set(path + ".mode", mode.key());
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
            writeRangeRule(path, world.getName(),
                    parseInt(args[6], 0), parseInt(args[7], 0), parseInt(args[8], 0),
                    parseInt(args[9], 0), parseInt(args[10], 0), parseInt(args[11], 0));
        } else {
            World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorld(plugin.getConfig().getString("hub.world", "world"));
            data.set(path + ".world", world == null ? "world" : world.getName());
            data.set(path + ".range", null);
        }
        writeBiomeRule(path, args, biomeIndex);
        save();
        sender.sendMessage("§a構造物生成ルールを保存しました: " + name + " ratio=" + ratio + " mode=" + mode.key());
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c/mva structure delete <name>");
            return true;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        String fileName = data.getString(registeredPath(name) + ".file");
        data.set(registeredPath(name), null);
        data.set("structures.generation-rules." + name, null);
        data.set("structures.generated." + name, null);
        if (fileName != null) {
            File target = structureFile(fileName);
            if (target != null && target.exists() && !target.delete()) {
                plugin.getLogger().warning("Could not delete structure file: " + target.getAbsolutePath());
            }
        }
        save();
        sender.sendMessage("§e構造物登録と生成管理データを削除しました: " + name);
        return true;
    }

    private void tryGenerateInChunk(String name, Chunk chunk) {
        String path = "structures.generation-rules." + name;
        String worldName = data.getString(path + ".world", "");
        if (!chunk.getWorld().getName().equals(worldName)) {
            return;
        }
        if (alreadyGeneratedInChunk(name, chunk)) {
            return;
        }
        int ratio = Math.max(1, Math.min(10, data.getInt(path + ".ratio", 1)));
        double percent = plugin.getConfig().getDouble("structures.ratio-percent." + ratio, ratio * 0.2D);
        if (random.nextDouble() * 100.0D >= percent) {
            return;
        }
        StructurePlacementMode mode = StructurePlacementMode.fromKey(data.getString(path + ".mode"));
        if (mode == null) {
            return;
        }
        Location location = chooseLocation(name, chunk, mode, path);
        if (location == null || !biomeAllowed(path, location.getBlock().getBiome())) {
            return;
        }
        if (place(name, location)) {
            recordGenerated(name, location, chunk);
        }
    }

    private Location chooseLocation(String name, Chunk chunk, StructurePlacementMode mode, String path) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + random.nextInt(16);
        int z = chunk.getZ() * 16 + random.nextInt(16);
        int sizeY = Math.max(1, data.getInt(registeredPath(name) + ".size.y", 1));
        if (mode == StructurePlacementMode.RANGE) {
            ConfigurationSection range = data.getConfigurationSection(path + ".range");
            if (range == null) {
                return null;
            }
            int minX = Math.max(chunk.getX() * 16, range.getInt("min-x"));
            int maxX = Math.min(chunk.getX() * 16 + 15, range.getInt("max-x"));
            int minZ = Math.max(chunk.getZ() * 16, range.getInt("min-z"));
            int maxZ = Math.min(chunk.getZ() * 16 + 15, range.getInt("max-z"));
            if (maxX < minX || maxZ < minZ) {
                return null;
            }
            int minY = Math.max(world.getMinHeight(), range.getInt("min-y"));
            int maxY = Math.min(world.getMaxHeight() - sizeY, range.getInt("max-y"));
            if (maxY < minY) {
                return null;
            }
            return new Location(world,
                    minX + random.nextInt(maxX - minX + 1),
                    minY + random.nextInt(maxY - minY + 1),
                    minZ + random.nextInt(maxZ - minZ + 1));
        }
        int highest = world.getHighestBlockYAt(x, z);
        int y = switch (mode) {
            case GROUND -> highest + 1;
            case SEMIUNDERGROUND -> Math.max(world.getMinHeight() + 1, highest - Math.max(1, sizeY / 2));
            case UNDERGROUND -> {
                int min = world.getMinHeight() + 8;
                int max = Math.max(min, highest - Math.max(8, sizeY));
                yield min + random.nextInt(max - min + 1);
            }
            case SKY -> {
                int min = plugin.getConfig().getInt("structures.sky.min-y", 120);
                int max = plugin.getConfig().getInt("structures.sky.max-y", 220);
                yield Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - sizeY, min + random.nextInt(Math.max(1, max - min + 1))));
            }
            case RANGE -> highest + 1;
        };
        if (world.getBlockAt(x, y, z).isLiquid()) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    private boolean place(String name, Location location) {
        String source = data.getString(registeredPath(name) + ".source", "");
        File sourceFile = structureFile(data.getString(registeredPath(name) + ".file", ""));
        if ("clipboard".equals(source)) {
            return sourceFile != null && worldEdit.pasteSchematic(sourceFile, location);
        }
        if (!"range".equals(source) || sourceFile == null || !sourceFile.exists()) {
            return false;
        }
        YamlConfiguration structure = YamlConfiguration.loadConfiguration(sourceFile);
        for (String raw : structure.getStringList("blocks")) {
            int split = raw.indexOf(';');
            if (split <= 0) {
                continue;
            }
            String[] parts = raw.substring(0, split).split(",");
            if (parts.length != 3) {
                continue;
            }
            int dx = parseInt(parts[0], 0);
            int dy = parseInt(parts[1], 0);
            int dz = parseInt(parts[2], 0);
            try {
                BlockData blockData = Bukkit.createBlockData(raw.substring(split + 1));
                location.getWorld().getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz)
                        .setBlockData(blockData, false);
            } catch (IllegalArgumentException ignored) {
                // Skip block data from newer/unknown versions.
            }
        }
        return true;
    }

    private void recordGenerated(String name, Location location, Chunk chunk) {
        List<String> entries = data.getStringList("structures.generated." + name);
        entries.add(location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                + "," + chunk.getX() + "," + chunk.getZ() + "," + System.currentTimeMillis());
        data.set("structures.generated." + name, entries);
        save();
    }

    private boolean alreadyGeneratedInChunk(String name, Chunk chunk) {
        String prefix = chunk.getWorld().getName() + ",";
        for (String raw : data.getStringList("structures.generated." + name)) {
            String[] parts = raw.split(",");
            if (parts.length >= 6
                    && raw.startsWith(prefix)
                    && parseInt(parts[4], Integer.MIN_VALUE) == chunk.getX()
                    && parseInt(parts[5], Integer.MIN_VALUE) == chunk.getZ()) {
                return true;
            }
        }
        return false;
    }

    private void writeRangeRule(String path, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        data.set(path + ".world", world);
        data.set(path + ".range.min-x", Math.min(x1, x2));
        data.set(path + ".range.min-y", Math.min(y1, y2));
        data.set(path + ".range.min-z", Math.min(z1, z2));
        data.set(path + ".range.max-x", Math.max(x1, x2));
        data.set(path + ".range.max-y", Math.max(y1, y2));
        data.set(path + ".range.max-z", Math.max(z1, z2));
    }

    private void writeBiomeRule(String path, String[] args, int biomeIndex) {
        data.set(path + ".biome.mode", "none");
        data.set(path + ".biome.values", List.of());
        if (biomeIndex < 0 || biomeIndex + 2 >= args.length) {
            return;
        }
        String mode = args[biomeIndex + 1].toLowerCase(Locale.ROOT);
        if (!mode.equals("include") && !mode.equals("exclude")) {
            return;
        }
        List<String> values = new ArrayList<>();
        for (int i = biomeIndex + 2; i < args.length; i++) {
            String value = args[i].toLowerCase(Locale.ROOT);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        data.set(path + ".biome.mode", mode);
        data.set(path + ".biome.values", values);
    }

    private boolean biomeAllowed(String path, Biome biome) {
        String mode = data.getString(path + ".biome.mode", "none");
        List<String> values = data.getStringList(path + ".biome.values");
        if ("none".equals(mode) || values.isEmpty() || values.contains("none") || values.contains("all")) {
            return true;
        }
        String key = biome.key().value().toLowerCase(Locale.ROOT);
        boolean matched = values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> value.equals(key));
        if ("include".equals(mode)) {
            return matched;
        }
        if ("exclude".equals(mode)) {
            return !matched;
        }
        return true;
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
        if (name == null || name.isBlank()) {
            return null;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".yml") && !lowerName.endsWith(".schem")) {
            plugin.getLogger().warning("Rejected structure file with unsupported extension: " + name);
            return null;
        }
        File baseDir = new File(plugin.getDataFolder(), "structures");
        Path basePath = baseDir.toPath().toAbsolutePath().normalize();
        Path candidate = basePath.resolve(name).toAbsolutePath().normalize();
        if (!candidate.startsWith(basePath) || !basePath.equals(candidate.getParent())) {
            plugin.getLogger().warning("Rejected structure file outside structures directory: " + name);
            return null;
        }
        return candidate.toFile();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void ensureLoaded() {
        if (data == null) {
            load();
        }
    }

    private void save() {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save structures.yml: " + e.getMessage());
        }
    }

    private void ensureConfigDefaults() {
        setIfMissing("structures.enabled", true);
        for (int i = 1; i <= 10; i++) {
            setIfMissing("structures.ratio-percent." + i, i * 0.2D);
        }
        setIfMissing("structures.sky.min-y", 120);
        setIfMissing("structures.sky.max-y", 220);
        plugin.saveConfig();
    }

    private void setIfMissing(String path, Object value) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, value);
        }
    }
}
