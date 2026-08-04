package org.server.minerva;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

final class AthleticManager implements Listener {
   private final Minerva plugin;
   private final NamespacedKey controlKey;
   private final NamespacedKey panelKey;
   private final Map<UUID, AthleticManager.Run> activeRuns = new HashMap<>();
   private BukkitTask ticker;

   AthleticManager(Minerva plugin) {
      this.plugin = plugin;
      this.controlKey = new NamespacedKey(plugin, "athletic_control");
      this.panelKey = new NamespacedKey(plugin, "athletic_panel");
   }

   void load() {
      this.updateAllPanels();
      if (this.ticker != null) {
         this.ticker.cancel();
      }

      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 2L);
   }

   void shutdown() {
      if (this.ticker != null) {
         this.ticker.cancel();
         this.ticker = null;
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.removeControlItems(player);
      }

      this.activeRuns.clear();
   }

   boolean handleCommand(Player player, String[] args) {
      if (args.length >= 2 && ("start".equalsIgnoreCase(args[1]) || "goal".equalsIgnoreCase(args[1]) || "panel".equalsIgnoreCase(args[1]))) {
         if (!player.hasPermission("minerva.admin")) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
         }

         boolean remove = args.length >= 3 && "remove".equalsIgnoreCase(args[2]);
         int nameIndex = remove ? 3 : 2;
         if (args.length > nameIndex && this.validName(args[nameIndex])) {
            String name = args[nameIndex].toLowerCase(Locale.ROOT);
            if (remove) {
               switch (args[1].toLowerCase(Locale.ROOT)) {
                  case "start":
                     this.removePoint(player, name, "starts", "スタート地点");
                     break;
                  case "goal":
                     this.removePoint(player, name, "goals", "ゴール地点");
                     break;
                  case "panel":
                     this.removePanel(player, name);
               }

               return true;
            } else {
               switch (args[1].toLowerCase(Locale.ROOT)) {
                  case "start":
                     this.savePoint(name, "starts", player.getLocation());
                     player.sendMessage(ChatColor.GREEN + "アスレチックのスタート地点を設定しました: " + name);
                     break;
                  case "goal":
                     this.savePoint(name, "goals", player.getLocation());
                     player.sendMessage(ChatColor.GREEN + "アスレチックのゴール地点を設定しました: " + name);
                     break;
                  case "panel":
                     this.createPanel(name, player.getLocation());
                     player.sendMessage(ChatColor.GREEN + "アスレチックランキングパネルを設定しました: " + name);
               }

               return true;
            }
         } else {
            player.sendMessage(ChatColor.RED + "/mva athletic start|goal|panel [remove] <name>");
            return true;
         }
      } else {
         return false;
      }
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 2) {
         return List.of("start", "goal", "panel", "complete");
      } else if (args.length != 3 || !"start".equalsIgnoreCase(args[1]) && !"goal".equalsIgnoreCase(args[1]) && !"panel".equalsIgnoreCase(args[1])) {
         return args.length == 3 && "complete".equalsIgnoreCase(args[1]) ? List.of("easy", "normal", "hard", "hardcore") : List.of();
      } else {
         return List.of("remove");
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      Location to = event.getTo();
      if (to != null && !this.sameBlock(event.getFrom(), to)) {
         Player player = event.getPlayer();
         String startName = this.pointAt("starts", to.getBlock());
         if (startName != null && !this.activeRuns.containsKey(player.getUniqueId())) {
            this.beginRun(player, startName, to.getBlock().getLocation());
         } else {
            AthleticManager.Run run = this.activeRuns.get(player.getUniqueId());
            if (run != null) {
               if (this.pointAt("goals", to.getBlock()) != null && this.pointAt("goals", to.getBlock()).equals(run.name())) {
                  this.finishRun(player, run);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (item != null && item.hasItemMeta()) {
         String action = (String)item.getItemMeta().getPersistentDataContainer().get(this.controlKey, PersistentDataType.STRING);
         if (action != null && event.getAction().isRightClick()) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            AthleticManager.Run run = this.activeRuns.get(player.getUniqueId());
            if (run != null) {
               if ("return".equals(action)) {
                  player.teleport(run.start());
               } else if ("end".equals(action)) {
                  player.teleport(run.start());
                  this.activeRuns.remove(player.getUniqueId());
                  this.removeControlItems(player);
                  player.sendActionBar(Component.text("アスレチックを終了しました", NamedTextColor.YELLOW));
               }
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.activeRuns.remove(event.getPlayer().getUniqueId());
   }

   private void beginRun(Player player, String name, Location start) {
      this.activeRuns.put(player.getUniqueId(), new AthleticManager.Run(name, start.clone().add(0.5, 0.0, 0.5), System.currentTimeMillis()));
      this.removeControlItems(player);
      player.getInventory().addItem(new ItemStack[]{this.controlItem("return", Material.COMPASS, "§bスタート地点へ戻る")});
      player.getInventory().addItem(new ItemStack[]{this.controlItem("end", Material.BARRIER, "§cアスレチックを終了")});
      player.sendMessage(ChatColor.GREEN + "アスレチック開始: " + name);
   }

   private void finishRun(Player player, AthleticManager.Run run) {
      long elapsed = Math.max(0L, System.currentTimeMillis() - run.startedAt());
      this.activeRuns.remove(player.getUniqueId());
      this.removeControlItems(player);
      String path = "athletic.runs." + run.name() + ".scores." + player.getUniqueId();
      long previous = this.plugin.data().getLong(path, Long.MAX_VALUE);
      if (elapsed < previous) {
         this.plugin.data().set(path, elapsed);
         this.plugin.data().set("athletic.runs." + run.name() + ".names." + player.getUniqueId(), player.getName());
         this.plugin.saveData();
         this.updatePanels(run.name());
      }

      this.showRanking(player, run.name());
      player.sendMessage(ChatColor.GOLD + "ゴール！ タイム: " + this.formatTime(elapsed));
   }

   private void tick() {
      long now = System.currentTimeMillis();

      for (Entry<UUID, AthleticManager.Run> entry : new ArrayList<>(this.activeRuns.entrySet())) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player != null && player.isOnline()) {
            long elapsed = Math.max(0L, now - entry.getValue().startedAt());
            player.sendActionBar(Component.text("タイム " + this.formatTime(elapsed), NamedTextColor.AQUA));
         } else {
            this.activeRuns.remove(entry.getKey());
         }
      }
   }

   private void savePoint(String name, String type, Location location) {
      ConfigurationSection section = this.plugin.data().getConfigurationSection("athletic.runs." + name + "." + type);
      if (section == null) {
         section = this.plugin.data().createSection("athletic.runs." + name + "." + type);
      }

      int index = section.getKeys(false).stream().mapToInt(key -> {
         try {
            return Integer.parseInt(key);
         } catch (NumberFormatException ignored) {
            return -1;
         }
      }).max().orElse(-1) + 1;
      String path = "athletic.runs." + name + "." + type + "." + index;
      this.plugin.data().set(path + ".world", location.getWorld().getName());
      this.plugin.data().set(path + ".x", location.getBlockX());
      this.plugin.data().set(path + ".y", location.getBlockY());
      this.plugin.data().set(path + ".z", location.getBlockZ());
      this.plugin.saveData();
   }

   private void removePoint(Player player, String name, String type, String label) {
      ConfigurationSection points = this.plugin.data().getConfigurationSection("athletic.runs." + name + "." + type);
      if (points == null) {
         player.sendMessage(ChatColor.YELLOW + "この場所には" + label + "がありません: " + name);
      } else {
         Block current = player.getLocation().getBlock();

         for (String index : new ArrayList<String>(points.getKeys(false))) {
            String world = points.getString(index + ".world");
            if (world != null
               && world.equals(current.getWorld().getName())
               && points.getInt(index + ".x") == current.getX()
               && points.getInt(index + ".y") == current.getY()
               && points.getInt(index + ".z") == current.getZ()) {
               points.set(index, null);
               this.plugin.saveData();
               player.sendMessage(ChatColor.GREEN + label + "を削除しました: " + name);
               return;
            }
         }

         player.sendMessage(ChatColor.YELLOW + "この場所には" + label + "がありません: " + name);
      }
   }

   private void removePanel(Player player, String name) {
      String path = "athletic.runs." + name + ".panel";
      String raw = this.plugin.data().getString(path);
      if (raw == null) {
         player.sendMessage(ChatColor.YELLOW + "ランキングパネルがありません: " + name);
      } else {
         try {
            Entity entity = Bukkit.getEntity(UUID.fromString(raw));
            if (entity != null) {
               entity.remove();
            }
         } catch (IllegalArgumentException var6) {
         }

         this.plugin.data().set(path, null);
         this.plugin.saveData();
         player.sendMessage(ChatColor.GREEN + "ランキングパネルを削除しました: " + name);
      }
   }

   private String pointAt(String type, Block block) {
      ConfigurationSection runs = this.plugin.data().getConfigurationSection("athletic.runs");
      if (runs != null && block != null) {
         for (String name : runs.getKeys(false)) {
            ConfigurationSection points = this.plugin.data().getConfigurationSection("athletic.runs." + name + "." + type);
            if (points != null) {
               for (String index : points.getKeys(false)) {
                  String world = points.getString(index + ".world");
                  if (world != null
                     && world.equals(block.getWorld().getName())
                     && points.getInt(index + ".x") == block.getX()
                     && points.getInt(index + ".y") == block.getY()
                     && points.getInt(index + ".z") == block.getZ()) {
                     return name;
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void createPanel(String name, Location location) {
      String path = "athletic.runs." + name + ".panel";
      String old = this.plugin.data().getString(path);
      if (old != null) {
         try {
            Entity entity = Bukkit.getEntity(UUID.fromString(old));
            if (entity != null) {
               entity.remove();
            }
         } catch (IllegalArgumentException var6) {
         }
      }

      TextDisplay display = (TextDisplay)location.getWorld().spawnEntity(location.clone().add(0.5, 1.0, 0.5), EntityType.TEXT_DISPLAY);
      display.setBillboard(Billboard.CENTER);
      display.setPersistent(true);
      display.getPersistentDataContainer().set(this.panelKey, PersistentDataType.STRING, name);
      this.plugin.data().set(path, display.getUniqueId().toString());
      this.plugin.saveData();
      this.updatePanel(name, display);
   }

   private void updateAllPanels() {
      ConfigurationSection runs = this.plugin.data().getConfigurationSection("athletic.runs");
      if (runs != null) {
         for (String name : runs.getKeys(false)) {
            this.updatePanels(name);
         }
      }
   }

   private void updatePanels(String name) {
      String raw = this.plugin.data().getString("athletic.runs." + name + ".panel");
      if (raw != null) {
         try {
            if (Bukkit.getEntity(UUID.fromString(raw)) instanceof TextDisplay display) {
               this.updatePanel(name, display);
            }
         } catch (IllegalArgumentException var5) {
         }
      }
   }

   private void updatePanel(String name, TextDisplay display) {
      display.text(Component.text(this.rankingText(name)));
   }

   private void showRanking(Player player, String name) {
      player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      Scoreboard board = player.getScoreboard();
      Objective objective = board.registerNewObjective("athletic", "dummy", "§bアスレチック " + name);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      List<AthleticManager.Score> scores = this.scores(name);
      int score = scores.size();

      for (AthleticManager.Score entry : scores) {
         objective.getScore("§e" + entry.name() + " §f" + this.formatTime(entry.time())).setScore(score--);
      }
   }

   private String rankingText(String name) {
      StringBuilder text = new StringBuilder("§bアスレチック " + name + "\n§7ランキング\n");
      int rank = 1;

      for (AthleticManager.Score score : this.scores(name)) {
         text.append("§e").append(rank++).append(". §f").append(score.name()).append(" §7").append(this.formatTime(score.time())).append("\n");
      }

      if (rank == 1) {
         text.append("§8まだ記録がありません");
      }

      return text.toString();
   }

   private List<AthleticManager.Score> scores(String name) {
      ConfigurationSection scores = this.plugin.data().getConfigurationSection("athletic.runs." + name + ".scores");
      List<AthleticManager.Score> result = new ArrayList<>();
      if (scores == null) {
         return result;
      }

      for (String uuid : scores.getKeys(false)) {
         long time = scores.getLong(uuid, Long.MAX_VALUE);
         String playerName = this.plugin.data().getString("athletic.runs." + name + ".names." + uuid, uuid);
         result.add(new AthleticManager.Score(playerName, time));
      }

      result.sort(Comparator.comparingLong(AthleticManager.Score::time));
      return result.subList(0, Math.min(10, result.size()));
   }

   private ItemStack controlItem(String action, Material material, String name) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.getPersistentDataContainer().set(this.controlKey, PersistentDataType.STRING, action);
      item.setItemMeta(meta);
      return item;
   }

   private void removeControlItems(Player player) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.controlKey, PersistentDataType.STRING)) {
            item.setAmount(0);
         }
      }
   }

   private boolean sameBlock(Location first, Location second) {
      return first.getWorld() == second.getWorld()
         && first.getBlockX() == second.getBlockX()
         && first.getBlockY() == second.getBlockY()
         && first.getBlockZ() == second.getBlockZ();
   }

   private boolean validName(String name) {
      return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
   }

   private String formatTime(long millis) {
      return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60000L, millis / 1000L % 60L, millis % 1000L);
   }

   private record Run(String name, Location start, long startedAt) {
   }

   private record Score(String name, long time) {
   }
}
