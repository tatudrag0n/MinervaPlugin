package org.server.minerva;

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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
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

final class TextDisplayFeature implements Listener {
   private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
   private static final String PERMISSION = "minerva.text.admin";
   private final Minerva plugin;
   private final NamespacedKey displayKey;
   private final File file;
   private final Map<String, TextDisplayFeature.Entry> entries = new HashMap<>();
   private final Map<String, TextDisplay> displays = new HashMap<>();
   private YamlConfiguration config;

   TextDisplayFeature(Minerva plugin) {
      this.plugin = plugin;
      this.displayKey = new NamespacedKey(plugin, "text_display_id");
      this.file = new File(plugin.getDataFolder(), "text-displays.yml");
   }

   void load() {
      this.removeSpawnedDisplays();
      this.entries.clear();
      this.ensureFile();
      this.config = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection section = this.config.getConfigurationSection("displays");
      if (section != null) {
         for (String id : section.getKeys(false)) {
            TextDisplayFeature.Entry entry = this.readEntry(id, section.getConfigurationSection(id));
            if (entry != null) {
               this.entries.put(id, entry);
               this.spawn(entry);
            }
         }
      }
   }

   void disable() {
      this.removeSpawnedDisplays();
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (!this.hasAdminPermission(sender)) {
         sender.sendMessage("§c権限がありません。");
         return true;
      }

      if (args.length < 2) {
         sendUsage(sender);
         return true;
      }

      String action = args[1].toLowerCase(Locale.ROOT);
      switch (action) {
         case "create":
            this.create(sender, args);
            break;
         case "remove":
            this.remove(sender, args);
            break;
         case "list":
            this.list(sender);
            break;
         case "tp":
            this.teleport(sender, args);
            break;
         case "movehere":
            this.moveHere(sender, args);
            break;
         case "settext":
            this.setText(sender, args);
            break;
         case "setscale":
            this.setScale(sender, args);
            break;
         case "setbillboard":
            this.setBillboard(sender, args);
            break;
         case "reload":
            this.load();
            sender.sendMessage("§aTextDisplayを再読込しました。");
            break;
         default:
            sendUsage(sender);
      }

      return true;
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 2) {
         return List.of("create", "remove", "list", "tp", "movehere", "settext", "setscale", "setbillboard", "reload");
      } else if (args.length == 3 && List.of("remove", "tp", "movehere", "settext", "setscale", "setbillboard").contains(args[1].toLowerCase(Locale.ROOT))) {
         return this.sortedIds();
      } else if (args.length == 4 && "setbillboard".equalsIgnoreCase(args[1])) {
         return List.of("fixed", "center", "vertical", "horizontal");
      } else {
         return args.length == 4 && "setscale".equalsIgnoreCase(args[1]) ? List.of("0.5", "1.0", "1.5", "2.0") : Collections.emptyList();
      }
   }

   private void create(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length < 4) {
            sender.sendMessage("§c/mva text create <id> <text>");
         } else {
            String id = args[2];
            if (!isSafeId(id)) {
               sender.sendMessage("§cidは英数字、_、- の1-32文字にしてください。");
            } else if (this.entries.containsKey(id)) {
               sender.sendMessage("§cTextDisplayは既に存在します: " + id);
            } else {
               TextDisplayFeature.Entry entry = TextDisplayFeature.Entry.defaults(id, player.getLocation(), joinArgs(args, 3));
               this.entries.put(id, entry);
               this.spawn(entry);
               this.save();
               sender.sendMessage("§aTextDisplayを作成しました: " + id);
            }
         }
      } else {
         sender.sendMessage("Player only.");
      }
   }

   private void remove(CommandSender sender, String[] args) {
      if (args.length < 3) {
         sender.sendMessage("§c/mva text remove <id>");
      } else {
         String id = args[2];
         TextDisplayFeature.Entry removed = this.entries.remove(id);
         TextDisplay display = this.displays.remove(id);
         if (display != null && display.isValid()) {
            display.remove();
         }

         if (removed == null) {
            sender.sendMessage("§cTextDisplayが見つかりません: " + id);
         } else {
            this.save();
            sender.sendMessage("§aTextDisplayを削除しました: " + id);
         }
      }
   }

   private void list(CommandSender sender) {
      if (this.entries.isEmpty()) {
         sender.sendMessage("§7TextDisplayはありません。");
      } else {
         sender.sendMessage("§aTextDisplays:");
         this.sortedIds()
            .forEach(
               id -> {
                  TextDisplayFeature.Entry entry = this.entries.get(id);
                  sender.sendMessage(
                     "§7- "
                        + id
                        + " @ "
                        + entry.worldName()
                        + " "
                        + format(entry.x())
                        + " "
                        + format(entry.y())
                        + " "
                        + format(entry.z())
                        + " scale="
                        + format(entry.scale())
                        + " billboard="
                        + entry.billboard().name().toLowerCase(Locale.ROOT)
                  );
               }
            );
      }
   }

   private void teleport(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         TextDisplayFeature.Entry entry = this.getEntry(sender, args, "/mva text tp <id>");
         if (entry != null) {
            Location location = entry.location();
            if (location == null) {
               sender.sendMessage("§cワールドが見つかりません: " + entry.worldName());
            } else {
               player.teleport(location);
               sender.sendMessage("§aTextDisplayへ移動しました: " + entry.id());
            }
         }
      } else {
         sender.sendMessage("Player only.");
      }
   }

   private void moveHere(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         TextDisplayFeature.Entry entry = this.getEntry(sender, args, "/mva text movehere <id>");
         if (entry != null) {
            TextDisplayFeature.Entry moved = entry.withLocation(player.getLocation());
            this.entries.put(moved.id(), moved);
            this.respawn(moved);
            this.save();
            sender.sendMessage("§aTextDisplayを現在地へ移動しました: " + moved.id());
         }
      } else {
         sender.sendMessage("Player only.");
      }
   }

   private void setText(CommandSender sender, String[] args) {
      TextDisplayFeature.Entry entry = this.getEntry(sender, args, "/mva text settext <id> <text>");
      if (entry != null) {
         if (args.length < 4) {
            sender.sendMessage("§c/mva text settext <id> <text>");
         } else {
            TextDisplayFeature.Entry updated = entry.withText(joinArgs(args, 3));
            this.entries.put(updated.id(), updated);
            this.apply(updated);
            this.save();
            sender.sendMessage("§aTextDisplayの文字を更新しました: " + updated.id());
         }
      }
   }

   private void setScale(CommandSender sender, String[] args) {
      TextDisplayFeature.Entry entry = this.getEntry(sender, args, "/mva text setscale <id> <scale>");
      if (entry != null) {
         if (args.length < 4) {
            sender.sendMessage("§c/mva text setscale <id> <scale>");
         } else {
            double scale;
            try {
               scale = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
               sender.sendMessage("§cscaleは数字で指定してください。");
               return;
            }

            if (!(scale <= 0.0) && !(scale > 10.0)) {
               TextDisplayFeature.Entry updated = entry.withScale(scale);
               this.entries.put(updated.id(), updated);
               this.apply(updated);
               this.save();
               sender.sendMessage("§aTextDisplayのscaleを更新しました: " + updated.id());
            } else {
               sender.sendMessage("§cscaleは0より大きく10以下にしてください。");
            }
         }
      }
   }

   private void setBillboard(CommandSender sender, String[] args) {
      TextDisplayFeature.Entry entry = this.getEntry(sender, args, "/mva text setbillboard <id> <fixed|center|vertical|horizontal>");
      if (entry != null) {
         if (args.length < 4) {
            sender.sendMessage("§c/mva text setbillboard <id> <fixed|center|vertical|horizontal>");
         } else {
            Billboard billboard = parseBillboard(args[3], null);
            if (billboard == null) {
               sender.sendMessage("§cbillboardは fixed, center, vertical, horizontal のいずれかです。");
            } else {
               TextDisplayFeature.Entry updated = entry.withBillboard(billboard);
               this.entries.put(updated.id(), updated);
               this.apply(updated);
               this.save();
               sender.sendMessage("§aTextDisplayのbillboardを更新しました: " + updated.id());
            }
         }
      }
   }

   private TextDisplayFeature.Entry getEntry(CommandSender sender, String[] args, String usage) {
      if (args.length < 3) {
         sender.sendMessage("§c" + usage);
         return null;
      }

      TextDisplayFeature.Entry entry = this.entries.get(args[2]);
      if (entry == null) {
         sender.sendMessage("§cTextDisplayが見つかりません: " + args[2]);
      }

      return entry;
   }

   private void spawn(TextDisplayFeature.Entry entry) {
      Location location = entry.location();
      if (location == null) {
         this.plugin.getLogger().warning("Could not spawn TextDisplay because world is missing: " + entry.id() + " / " + entry.worldName());
      } else {
         TextDisplay display = (TextDisplay)location.getWorld().spawn(location, TextDisplay.class);
         display.setPersistent(false);
         display.getPersistentDataContainer().set(this.displayKey, PersistentDataType.STRING, entry.id());
         this.applyProperties(display, entry);
         this.displays.put(entry.id(), display);
         if (!display.isValid()) {
            this.displays.remove(entry.id());
            this.plugin.getLogger().warning("Could not spawn TextDisplay because spawn was cancelled or invalidated: " + entry.id());
         }
      }
   }

   private void respawn(TextDisplayFeature.Entry entry) {
      TextDisplay current = this.displays.remove(entry.id());
      if (current != null && current.isValid()) {
         current.remove();
      }

      this.spawn(entry);
   }

   private void apply(TextDisplayFeature.Entry entry) {
      TextDisplay display = this.displays.get(entry.id());
      if (display != null && display.isValid()) {
         this.applyProperties(display, entry);
      } else {
         this.displays.remove(entry.id());
         this.spawn(entry);
      }
   }

   private void applyProperties(TextDisplay display, TextDisplayFeature.Entry entry) {
      display.setText(entry.text());
      display.setBillboard(entry.billboard());
      display.setTransformation(
         new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f((float)entry.scale(), (float)entry.scale(), (float)entry.scale()), new Quaternionf()
         )
      );
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
      for (TextDisplay display : new ArrayList<>(this.displays.values())) {
         if (display != null && display.isValid()) {
            display.remove();
         }
      }

      this.displays.clear();

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (this.isManagedDisplay(entity)) {
               entity.remove();
            }
         }
      }
   }

   private TextDisplayFeature.Entry readEntry(String id, ConfigurationSection section) {
      if (section != null && isSafeId(id)) {
         String worldName = section.getString("worldName", "");
         double x = section.getDouble("x");
         double y = section.getDouble("y");
         double z = section.getDouble("z");
         float yaw = (float)section.getDouble("yaw");
         float pitch = (float)section.getDouble("pitch");
         String text = section.getString("text", id);
         double scale = Math.max(0.1, Math.min(10.0, section.getDouble("scale", 1.0)));
         Billboard billboard = parseBillboard(section.getString("billboard"), Billboard.CENTER);
         Color backgroundColor = parseColor(section.getString("backgroundColor", "0,0,0,96"));
         boolean shadow = section.getBoolean("shadow", true);
         boolean seeThrough = section.getBoolean("seeThrough", false);
         TextAlignment alignment = parseAlignment(section.getString("alignment"), TextAlignment.CENTER);
         return new TextDisplayFeature.Entry(id, worldName, x, y, z, yaw, pitch, text, scale, billboard, backgroundColor, shadow, seeThrough, alignment);
      } else {
         return null;
      }
   }

   private void save() {
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }

      this.config.set("displays", null);

      for (TextDisplayFeature.Entry entry : this.entries.values().stream().sorted(Comparator.comparing(TextDisplayFeature.Entry::id)).toList()) {
         String path = "displays." + entry.id() + ".";
         this.config.set(path + "id", entry.id());
         this.config.set(path + "worldName", entry.worldName());
         this.config.set(path + "x", entry.x());
         this.config.set(path + "y", entry.y());
         this.config.set(path + "z", entry.z());
         this.config.set(path + "yaw", entry.yaw());
         this.config.set(path + "pitch", entry.pitch());
         this.config.set(path + "text", entry.text());
         this.config.set(path + "scale", entry.scale());
         this.config.set(path + "billboard", entry.billboard().name().toLowerCase(Locale.ROOT));
         this.config.set(path + "backgroundColor", colorString(entry.backgroundColor()));
         this.config.set(path + "shadow", entry.shadow());
         this.config.set(path + "seeThrough", entry.seeThrough());
         this.config.set(path + "alignment", entry.alignment().name().toLowerCase(Locale.ROOT));
      }

      try {
         this.config.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save text-displays.yml: " + e.getMessage());
      }
   }

   private void ensureFile() {
      File parent = this.file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         this.plugin.getLogger().severe("Could not create data folder: " + parent.getAbsolutePath());
      } else if (!this.file.exists()) {
         try {
            YamlConfiguration empty = new YamlConfiguration();
            empty.createSection("displays");
            empty.save(this.file);
         } catch (IOException e) {
            this.plugin.getLogger().severe("Could not create text-displays.yml: " + e.getMessage());
         }
      }
   }

   private boolean hasAdminPermission(CommandSender sender) {
      return sender.hasPermission("minerva.text.admin") || sender.hasPermission("minerva.admin");
   }

   private boolean isManagedDisplay(Entity entity) {
      return entity instanceof TextDisplay && entity.getPersistentDataContainer().has(this.displayKey, PersistentDataType.STRING);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEntityEvent event) {
      if (this.isManagedDisplay(event.getRightClicked()) && !this.hasAdminPermission(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDamageByEntity(EntityDamageByEntityEvent event) {
      if (this.isManagedDisplay(event.getEntity())) {
         if (!(event.getDamager() instanceof Player player && this.hasAdminPermission(player))) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDamage(EntityDamageEvent event) {
      if (this.isManagedDisplay(event.getEntity()) && !(event instanceof EntityDamageByEntityEvent)) {
         event.setCancelled(true);
      }
   }

   private List<String> sortedIds() {
      return this.entries.keySet().stream().sorted().toList();
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

   private static Billboard parseBillboard(String value, Billboard fallback) {
      if (value == null) {
         return fallback;
      }

      try {
         return Billboard.valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return fallback;
      }
   }

   private static TextAlignment parseAlignment(String value, TextAlignment fallback) {
      if (value == null) {
         return fallback;
      }

      try {
         return TextAlignment.valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return fallback;
      }
   }

   private static Color parseColor(String value) {
      if (value != null && !value.isBlank()) {
         String[] parts = value.split(",");

         try {
            if (parts.length == 4) {
               return Color.fromARGB(clampColor(parts[3]), clampColor(parts[0]), clampColor(parts[1]), clampColor(parts[2]));
            }

            if (parts.length == 3) {
               return Color.fromRGB(clampColor(parts[0]), clampColor(parts[1]), clampColor(parts[2]));
            }
         } catch (NumberFormatException var3) {
         }

         return Color.fromARGB(96, 0, 0, 0);
      } else {
         return Color.fromARGB(96, 0, 0, 0);
      }
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
      sender.sendMessage("§e/mva text create <id> <text>");
      sender.sendMessage("§e/mva text remove|list|tp|movehere|settext|setscale|setbillboard|reload");
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
      Billboard billboard,
      Color backgroundColor,
      boolean shadow,
      boolean seeThrough,
      TextAlignment alignment
   ) {
      private static TextDisplayFeature.Entry defaults(String id, Location location, String text) {
         return new TextDisplayFeature.Entry(
            id,
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            text,
            1.0,
            Billboard.CENTER,
            Color.fromARGB(96, 0, 0, 0),
            true,
            false,
            TextAlignment.CENTER
         );
      }

      private Location location() {
         World world = Bukkit.getWorld(this.worldName);
         return world == null ? null : new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
      }

      private TextDisplayFeature.Entry withLocation(Location location) {
         return new TextDisplayFeature.Entry(
            this.id,
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            this.text,
            this.scale,
            this.billboard,
            this.backgroundColor,
            this.shadow,
            this.seeThrough,
            this.alignment
         );
      }

      private TextDisplayFeature.Entry withText(String value) {
         return new TextDisplayFeature.Entry(
            this.id,
            this.worldName,
            this.x,
            this.y,
            this.z,
            this.yaw,
            this.pitch,
            value,
            this.scale,
            this.billboard,
            this.backgroundColor,
            this.shadow,
            this.seeThrough,
            this.alignment
         );
      }

      private TextDisplayFeature.Entry withScale(double value) {
         return new TextDisplayFeature.Entry(
            this.id,
            this.worldName,
            this.x,
            this.y,
            this.z,
            this.yaw,
            this.pitch,
            this.text,
            value,
            this.billboard,
            this.backgroundColor,
            this.shadow,
            this.seeThrough,
            this.alignment
         );
      }

      private TextDisplayFeature.Entry withBillboard(Billboard value) {
         return new TextDisplayFeature.Entry(
            this.id,
            this.worldName,
            this.x,
            this.y,
            this.z,
            this.yaw,
            this.pitch,
            this.text,
            this.scale,
            value,
            this.backgroundColor,
            this.shadow,
            this.seeThrough,
            this.alignment
         );
      }
   }
}
