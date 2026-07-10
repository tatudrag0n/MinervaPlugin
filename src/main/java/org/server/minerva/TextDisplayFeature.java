package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class TextDisplayFeature implements Listener {
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String PERMISSION = "minerva.text.admin";

    private final Minerva plugin;
    private final org.bukkit.NamespacedKey displayKey;
    private final File file;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private YamlConfiguration config;

    TextDisplayFeature(Minerva plugin) {
        this.plugin = plugin;
        this.displayKey = new org.bukkit.NamespacedKey(plugin, "text_display_id");
        this.file = new File(plugin.getDataFolder(), "text-displays.yml");
    }

    void load() {
        removeSpawnedDisplays();
        entries.clear();
        ensureFile();
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("displays");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Entry entry = readEntry(id, section.getConfigurationSection(id));
            if (entry == null) {
                continue;
            }
            entries.put(id, entry);
            spawn(entry);
        }
    }

    void disable() {
        removeSpawnedDisplays();
    }

    boolean handleCommand(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(Minerva.ChatColor.RED + "権限がありません。");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> create(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "tp" -> teleport(sender, args);
            case "movehere" -> moveHere(sender, args);
            case "settext" -> setText(sender, args);
            case "setscale" -> setScale(sender, args);
            case "setbillboard" -> setBillboard(sender, args);
            case "reload" -> {
                load();
                sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayを再読込しました。");
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("create", "remove", "list", "tp", "movehere", "settext", "setscale", "setbillboard", "reload");
        }
        if (args.length == 3 && List.of("remove", "tp", "movehere", "settext", "setscale", "setbillboard").contains(args[1].toLowerCase(Locale.ROOT))) {
            return sortedIds();
        }
        if (args.length == 4 && "setbillboard".equalsIgnoreCase(args[1])) {
            return List.of("fixed", "center", "vertical", "horizontal");
        }
        if (args.length == 4 && "setscale".equalsIgnoreCase(args[1])) {
            return List.of("0.5", "1.0", "1.5", "2.0");
        }
        return Collections.emptyList();
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Minerva.ChatColor.RED + "/mva text create <id> <text>");
            return;
        }
        String id = args[2];
        if (!isSafeId(id)) {
            sender.sendMessage(Minerva.ChatColor.RED + "idは英数字、_、- の1-32文字にしてください。");
            return;
        }
        if (entries.containsKey(id)) {
            sender.sendMessage(Minerva.ChatColor.RED + "TextDisplayは既に存在します: " + id);
            return;
        }
        Entry entry = Entry.defaults(id, player.getLocation(), joinArgs(args, 3));
        entries.put(id, entry);
        spawn(entry);
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayを作成しました: " + id);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Minerva.ChatColor.RED + "/mva text remove <id>");
            return;
        }
        String id = args[2];
        Entry removed = entries.remove(id);
        TextDisplay display = displays.remove(id);
        if (display != null && display.isValid()) {
            display.remove();
        }
        if (removed == null) {
            sender.sendMessage(Minerva.ChatColor.RED + "TextDisplayが見つかりません: " + id);
            return;
        }
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayを削除しました: " + id);
    }

    private void list(CommandSender sender) {
        if (entries.isEmpty()) {
            sender.sendMessage(Minerva.ChatColor.GRAY + "TextDisplayはありません。");
            return;
        }
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplays:");
        sortedIds().forEach(id -> {
            Entry entry = entries.get(id);
            sender.sendMessage(Minerva.ChatColor.GRAY + "- " + id + " @ " + entry.worldName()
                    + " " + format(entry.x()) + " " + format(entry.y()) + " " + format(entry.z())
                    + " scale=" + format(entry.scale()) + " billboard=" + entry.billboard().name().toLowerCase(Locale.ROOT));
        });
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return;
        }
        Entry entry = getEntry(sender, args, "/mva text tp <id>");
        if (entry == null) {
            return;
        }
        Location location = entry.location();
        if (location == null) {
            sender.sendMessage(Minerva.ChatColor.RED + "ワールドが見つかりません: " + entry.worldName());
            return;
        }
        player.teleport(location);
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayへ移動しました: " + entry.id());
    }

    private void moveHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return;
        }
        Entry entry = getEntry(sender, args, "/mva text movehere <id>");
        if (entry == null) {
            return;
        }
        Entry moved = entry.withLocation(player.getLocation());
        entries.put(moved.id(), moved);
        respawn(moved);
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayを現在地へ移動しました: " + moved.id());
    }

    private void setText(CommandSender sender, String[] args) {
        Entry entry = getEntry(sender, args, "/mva text settext <id> <text>");
        if (entry == null) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Minerva.ChatColor.RED + "/mva text settext <id> <text>");
            return;
        }
        Entry updated = entry.withText(joinArgs(args, 3));
        entries.put(updated.id(), updated);
        apply(updated);
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayの文字を更新しました: " + updated.id());
    }

    private void setScale(CommandSender sender, String[] args) {
        Entry entry = getEntry(sender, args, "/mva text setscale <id> <scale>");
        if (entry == null) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Minerva.ChatColor.RED + "/mva text setscale <id> <scale>");
            return;
        }
        double scale;
        try {
            scale = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Minerva.ChatColor.RED + "scaleは数字で指定してください。");
            return;
        }
        if (scale <= 0.0 || scale > 10.0) {
            sender.sendMessage(Minerva.ChatColor.RED + "scaleは0より大きく10以下にしてください。");
            return;
        }
        Entry updated = entry.withScale(scale);
        entries.put(updated.id(), updated);
        apply(updated);
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayのscaleを更新しました: " + updated.id());
    }

    private void setBillboard(CommandSender sender, String[] args) {
        Entry entry = getEntry(sender, args, "/mva text setbillboard <id> <fixed|center|vertical|horizontal>");
        if (entry == null) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Minerva.ChatColor.RED + "/mva text setbillboard <id> <fixed|center|vertical|horizontal>");
            return;
        }
        Display.Billboard billboard = parseBillboard(args[3], null);
        if (billboard == null) {
            sender.sendMessage(Minerva.ChatColor.RED + "billboardは fixed, center, vertical, horizontal のいずれかです。");
            return;
        }
        Entry updated = entry.withBillboard(billboard);
        entries.put(updated.id(), updated);
        apply(updated);
        save();
        sender.sendMessage(Minerva.ChatColor.GREEN + "TextDisplayのbillboardを更新しました: " + updated.id());
    }

    private Entry getEntry(CommandSender sender, String[] args, String usage) {
        if (args.length < 3) {
            sender.sendMessage(Minerva.ChatColor.RED + usage);
            return null;
        }
        Entry entry = entries.get(args[2]);
        if (entry == null) {
            sender.sendMessage(Minerva.ChatColor.RED + "TextDisplayが見つかりません: " + args[2]);
        }
        return entry;
    }

    private void spawn(Entry entry) {
        Location location = entry.location();
        if (location == null) {
            plugin.getLogger().warning("Could not spawn TextDisplay because world is missing: " + entry.id() + " / " + entry.worldName());
            return;
        }
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, entry.id());
        applyProperties(display, entry);
        displays.put(entry.id(), display);
        if (!display.isValid()) {
            displays.remove(entry.id());
            plugin.getLogger().warning("Could not spawn TextDisplay because spawn was cancelled or invalidated: " + entry.id());
        }
    }

    private void respawn(Entry entry) {
        TextDisplay current = displays.remove(entry.id());
        if (current != null && current.isValid()) {
            current.remove();
        }
        spawn(entry);
    }

    private void apply(Entry entry) {
        TextDisplay display = displays.get(entry.id());
        if (display == null || !display.isValid()) {
            displays.remove(entry.id());
            spawn(entry);
            return;
        }
        applyProperties(display, entry);
    }

    private void applyProperties(TextDisplay display, Entry entry) {
        display.setText(entry.text());
        display.setBillboard(entry.billboard());
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f((float) entry.scale(), (float) entry.scale(), (float) entry.scale()),
                new Quaternionf()));
        display.setBackgroundColor(entry.backgroundColor());
        display.setShadowed(entry.shadow());
        display.setSeeThrough(entry.seeThrough());
        display.setAlignment(entry.alignment());
        Location location = entry.location();
        if (location != null) {
            display.teleport(location);
        }
    }

    private void removeSpawnedDisplays() {
        for (TextDisplay display : new ArrayList<>(displays.values())) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isManagedDisplay(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private Entry readEntry(String id, ConfigurationSection section) {
        if (section == null || !isSafeId(id)) {
            return null;
        }
        String worldName = section.getString("worldName", "");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        String text = section.getString("text", id);
        double scale = Math.max(0.1, Math.min(10.0, section.getDouble("scale", 1.0)));
        Display.Billboard billboard = parseBillboard(section.getString("billboard"), Display.Billboard.CENTER);
        Color backgroundColor = parseColor(section.getString("backgroundColor", "0,0,0,96"));
        boolean shadow = section.getBoolean("shadow", true);
        boolean seeThrough = section.getBoolean("seeThrough", false);
        TextDisplay.TextAlignment alignment = parseAlignment(section.getString("alignment"), TextDisplay.TextAlignment.CENTER);
        return new Entry(id, worldName, x, y, z, yaw, pitch, text, scale, billboard, backgroundColor, shadow, seeThrough, alignment);
    }

    private void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("displays", null);
        for (Entry entry : entries.values().stream().sorted(Comparator.comparing(Entry::id)).toList()) {
            String path = "displays." + entry.id() + ".";
            config.set(path + "id", entry.id());
            config.set(path + "worldName", entry.worldName());
            config.set(path + "x", entry.x());
            config.set(path + "y", entry.y());
            config.set(path + "z", entry.z());
            config.set(path + "yaw", entry.yaw());
            config.set(path + "pitch", entry.pitch());
            config.set(path + "text", entry.text());
            config.set(path + "scale", entry.scale());
            config.set(path + "billboard", entry.billboard().name().toLowerCase(Locale.ROOT));
            config.set(path + "backgroundColor", colorString(entry.backgroundColor()));
            config.set(path + "shadow", entry.shadow());
            config.set(path + "seeThrough", entry.seeThrough());
            config.set(path + "alignment", entry.alignment().name().toLowerCase(Locale.ROOT));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save text-displays.yml: " + e.getMessage());
        }
    }

    private void ensureFile() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Could not create data folder: " + parent.getAbsolutePath());
            return;
        }
        if (file.exists()) {
            return;
        }
        try {
            YamlConfiguration empty = new YamlConfiguration();
            empty.createSection("displays");
            empty.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create text-displays.yml: " + e.getMessage());
        }
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION) || sender.hasPermission("minerva.admin");
    }

    private boolean isManagedDisplay(Entity entity) {
        return entity instanceof TextDisplay
                && entity.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (isManagedDisplay(event.getRightClicked()) && !hasAdminPermission(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isManagedDisplay(event.getEntity())) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !hasAdminPermission(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (isManagedDisplay(event.getEntity()) && !(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    private List<String> sortedIds() {
        return entries.keySet().stream().sorted().toList();
    }

    private static boolean isSafeId(String id) {
        return id != null && SAFE_ID_PATTERN.matcher(id).matches();
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().replace("\\n", "\n");
    }

    private static Display.Billboard parseBillboard(String value, Display.Billboard fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Display.Billboard.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static TextDisplay.TextAlignment parseAlignment(String value, TextDisplay.TextAlignment fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return TextDisplay.TextAlignment.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Color parseColor(String value) {
        if (value == null || value.isBlank()) {
            return Color.fromARGB(96, 0, 0, 0);
        }
        String[] parts = value.split(",");
        try {
            if (parts.length == 4) {
                return Color.fromARGB(clampColor(parts[3]), clampColor(parts[0]), clampColor(parts[1]), clampColor(parts[2]));
            }
            if (parts.length == 3) {
                return Color.fromRGB(clampColor(parts[0]), clampColor(parts[1]), clampColor(parts[2]));
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        return Color.fromARGB(96, 0, 0, 0);
    }

    private static int clampColor(String raw) {
        return Math.max(0, Math.min(255, Integer.parseInt(raw.trim())));
    }

    private static String colorString(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(Minerva.ChatColor.YELLOW + "/mva text create <id> <text>");
        sender.sendMessage(Minerva.ChatColor.YELLOW + "/mva text remove|list|tp|movehere|settext|setscale|setbillboard|reload");
    }

    private record Entry(
            String id,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String text,
            double scale,
            Display.Billboard billboard,
            Color backgroundColor,
            boolean shadow,
            boolean seeThrough,
            TextDisplay.TextAlignment alignment) {
        private static Entry defaults(String id, Location location, String text) {
            return new Entry(
                    id,
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch(),
                    text,
                    1.0,
                    Display.Billboard.CENTER,
                    Color.fromARGB(96, 0, 0, 0),
                    true,
                    false,
                    TextDisplay.TextAlignment.CENTER);
        }

        private Location location() {
            World world = Bukkit.getWorld(worldName);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }

        private Entry withLocation(Location location) {
            return new Entry(id, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), text, scale, billboard, backgroundColor, shadow, seeThrough, alignment);
        }

        private Entry withText(String value) {
            return new Entry(id, worldName, x, y, z, yaw, pitch, value, scale, billboard, backgroundColor, shadow, seeThrough, alignment);
        }

        private Entry withScale(double value) {
            return new Entry(id, worldName, x, y, z, yaw, pitch, text, value, billboard, backgroundColor, shadow, seeThrough, alignment);
        }

        private Entry withBillboard(Display.Billboard value) {
            return new Entry(id, worldName, x, y, z, yaw, pitch, text, scale, value, backgroundColor, shadow, seeThrough, alignment);
        }
    }
}
