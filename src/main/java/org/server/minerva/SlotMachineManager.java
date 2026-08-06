package org.server.minerva;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Shelf;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

final class SlotMachineManager implements Listener {
   private static final double REDSTONE_CHANCE = 0.1;
   private static final double JACKPOT_WIN_BONUS = 0.05;
   private static final double JACKPOT_REWARD_BONUS = 1.25;
   private final Minerva plugin;
   private final Map<UUID, SlotMachineManager.SpinSession> activeSessions = new HashMap<>();
   private final Map<String, UUID> busyMachines = new HashMap<>();

   SlotMachineManager(Minerva plugin) {
      this.plugin = plugin;
   }

   boolean registerMachine(Block shelf, SlotMachineManager.Difficulty difficulty) {
      if (shelf != null && difficulty != null && shelf.getState() instanceof Shelf) {
         this.unregisterMachine(shelf);
         if (!this.applyDifficultyShelf(shelf, difficulty)) {
            return false;
         }

         this.plugin.data().set(this.machinePath(shelf) + ".difficulty", difficulty.name());
         this.plugin.data().set(this.machinePath(shelf) + ".created-at", System.currentTimeMillis());
         this.plugin.saveData();
         this.updateShelfDisplay(shelf, List.of(SlotMachineManager.Symbol.COAL, SlotMachineManager.Symbol.COAL, SlotMachineManager.Symbol.COAL), false);
         return true;
      } else {
         return false;
      }
   }

   private boolean applyDifficultyShelf(Block shelf, SlotMachineManager.Difficulty difficulty) {
      Material material = Material.matchMaterial(switch (difficulty) {
         case EASY -> "BAMBOO_SHELF";
         case NORMAL -> "ACACIA_SHELF";
         case HARD -> "CRIMSON_SHELF";
         case EXPERT -> "WARPED_SHELF";
      });
      if (material == null) {
         return false;
      }

      BlockFace facing = shelf.getBlockData() instanceof Directional directional ? directional.getFacing() : null;
      shelf.setType(material, false);
      if (facing != null && shelf.getBlockData() instanceof Directional directional) {
         directional.setFacing(facing);
         shelf.setBlockData(directional, false);
      }

      return shelf.getState() instanceof Shelf;
   }

   boolean isMachine(Block shelf) {
      return this.difficultyAt(shelf) != null;
   }

   void unregisterMachine(Block shelf) {
      if (shelf != null) {
         UUID playerId = this.busyMachines.get(this.machineKey(shelf));
         if (playerId != null) {
            this.finishSession(this.activeSessions.get(playerId));
         }

         this.plugin.data().set(this.machinePath(shelf), null);
      }
   }

   private SlotMachineManager.Difficulty difficultyAt(Block shelf) {
      if (shelf != null && shelf.getState() instanceof Shelf) {
         String raw = this.plugin.data().getString(this.machinePath(shelf) + ".difficulty");
         if (raw == null) {
            return null;
         }

         try {
            return SlotMachineManager.Difficulty.valueOf(raw.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private String machinePath(Block block) {
      return "slot-machines." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private String machineKey(Block block) {
      return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
         return;
      }

      Block clicked = event.getClickedBlock();
      SlotMachineManager.Difficulty difficulty = this.difficultyAt(clicked);
      if (difficulty == null) {
         return;
      }

      SlotMachineManager.SpinSession active = this.activeSessions.get(event.getPlayer().getUniqueId());
      if (active != null && active.machineKey.equals(this.machineKey(clicked))) {
         event.setCancelled(true);
         this.requestStop(active, this.clickedColumn(clicked, event.getClickedPosition()));
         return;
      }

      if (this.plugin.isMinervaItem(event.getItem(), "emerald_bundle")) {
         event.setCancelled(true);
         this.startSpin(event.getPlayer(), clicked, difficulty);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.finishSession(this.activeSessions.remove(event.getPlayer().getUniqueId()));
   }

   private void startSpin(Player player, Block shelf, SlotMachineManager.Difficulty difficulty) {
      if (this.activeSessions.containsKey(player.getUniqueId())) {
         player.sendMessage("§c現在抽選中です。");
      } else {
         String machine = this.machineKey(shelf);
         if (this.busyMachines.containsKey(machine)) {
            player.sendMessage("§cこの台はほかのプレイヤーが使用中です。");
         } else if (this.plugin.getEmeralds(player.getUniqueId()) >= difficulty.wager && this.plugin.withdrawEmeralds(player.getUniqueId(), difficulty.wager)) {
            boolean jackpotMode = SlotJackpotSync.beginSpin(this.plugin, player);
            SlotMachineManager.SpinSession session = new SlotMachineManager.SpinSession(player, shelf, difficulty, jackpotMode, machine);
            this.activeSessions.put(player.getUniqueId(), session);
            this.busyMachines.put(machine, player.getUniqueId());
            player.sendMessage("§e" + difficulty.wager + "MPでスロットを回します。§f止めたい列を右クリックしてください。");
            this.animate(session, 0);
         } else {
            player.sendMessage("§cMPが不足しています。必要MP: " + difficulty.wager);
         }
      }
   }

   private void animate(final SlotMachineManager.SpinSession session, final int rerolls) {
      session.rerolls = rerolls;
      session.pendingResult = null;
      session.pendingCompletion = null;
      Arrays.fill(session.stoppedColumns, false);
      session.task = (new BukkitRunnable() {
            private int tick;

            public void run() {
               if (session.player.isOnline() && SlotMachineManager.this.difficultyAt(session.shelf) == session.difficulty) {
                  List<SlotMachineManager.Symbol> display = new ArrayList<>(3);
                  for (int column = 0; column < 3; column++) {
                     if (session.stoppedColumns[column] && session.pendingResult != null) {
                        display.add(session.pendingResult.get(column));
                     } else {
                        display.add(SlotMachineManager.this.randomPreview(session.jackpotMode));
                     }
                  }
                  SlotMachineManager.this.updateShelfDisplay(session.shelf, display, session.jackpotMode);
                  session.shelf.getWorld().playSound(session.shelf.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35F, 0.8F + this.tick * 0.03F);
                  this.tick++;
               } else {
                  SlotMachineManager.this.finishSession(session);
                  this.cancel();
               }
            }
         })
         .runTaskTimer(this.plugin, 0L, 1L);
   }

   private void determineResult(SlotMachineManager.SpinSession session, int rerolls, int startColumn) {
      double roll = ThreadLocalRandom.current().nextDouble();
      double winChance = Math.min(0.95, session.difficulty.winChance + (session.jackpotMode ? 0.05 : 0.0));
      if (roll < winChance) {
         SlotMachineManager.Symbol symbol = this.randomWinningSymbol(session.jackpotMode);
         this.revealResult(session, List.of(symbol, symbol, symbol), startColumn, () -> {
            this.handleWin(session, symbol);
            this.finishSession(session);
         });
      } else if (roll < winChance + 0.1 && rerolls < 8) {
         this.revealResult(
            session, List.of(SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE), startColumn, () -> {
               session.player.sendMessage("§c§lREDSTONE! §e無料で再抽選します。");
               session.shelf.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, session.shelf.getLocation().add(0.5, 0.8, 0.5), 25, 0.5, 0.4, 0.5, 0.05);
               session.shelf.getWorld().playSound(session.shelf.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.4F);
               session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.animate(session, rerolls + 1), 10L);
            }
         );
      } else {
         List<SlotMachineManager.Symbol> loss = this.randomLoss(session.jackpotMode);
         this.revealResult(session, loss, startColumn, () -> {
            session.player.sendMessage("§7外れ… §f0MP");
            session.player.getWorld().playSound(session.player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8F, 0.6F);
            this.finishSession(session);
         });
      }
   }

   private void handleWin(SlotMachineManager.SpinSession session, SlotMachineManager.Symbol symbol) {
      double bonus = session.jackpotMode ? 1.25 : 1.0;
      int reward = (int)Math.max(1L, Math.round(session.difficulty.wager * session.difficulty.payoutScale * symbol.payoutMultiplier * bonus));
      this.plugin.depositEmeralds(session.player.getUniqueId(), reward);
      session.player.sendMessage("§a当たり！ §e" + this.displayName(symbol, session.jackpotMode) + " §6+" + reward + "MP");
      if (symbol == SlotMachineManager.Symbol.DIAMOND && !session.jackpotMode) {
         this.enableJackpotMode(session);
      }

      boolean superJackpot = symbol == SlotMachineManager.Symbol.NETHERITE;
      if (superJackpot) {
         SlotJackpotSync.unlockAndClear(this.plugin, session.player, "ギャンブラー");
         session.player.sendMessage("§5§l✦✦ SUPER JACKPOT! 称号:ギャンブラー 獲得 ✦✦");
      }

      this.playWinEffect(session, symbol, superJackpot);
   }

   private String jackpotPath(UUID uuid) {
      return "players." + uuid + ".slot-jackpot";
   }

   private void playWinEffect(SlotMachineManager.SpinSession session, SlotMachineManager.Symbol symbol, boolean superJackpot) {
      Location location = session.shelf.getLocation().add(0.5, 1.0, 0.5);
      Particle particle = superJackpot ? Particle.DRAGON_BREATH : (symbol == SlotMachineManager.Symbol.DIAMOND ? Particle.FIREWORK : Particle.HAPPY_VILLAGER);
      int count = superJackpot ? 150 : (symbol == SlotMachineManager.Symbol.DIAMOND ? 80 : 15 + symbol.ordinal() * 6);
      session.shelf.getWorld().spawnParticle(particle, location, count, 1.0, 0.8, 1.0, 0.12);
      session.shelf
         .getWorld()
         .playSound(
            location, superJackpot ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, superJackpot ? 2.0F : 1.0F, superJackpot ? 0.7F : 1.3F
         );
   }

   private void revealResult(
      final SlotMachineManager.SpinSession session,
      final List<SlotMachineManager.Symbol> result,
      final int startColumn,
      final Runnable onComplete
   ) {
      session.pendingResult = new ArrayList<>(result);
      session.pendingCompletion = onComplete;
      this.stopSingleColumn(session, startColumn);
   }

   private void requestStop(SlotMachineManager.SpinSession session, int column) {
      if (session == null || column < 0 || column >= 3 || session.stoppedColumns[column]) {
         return;
      }

      if (session.pendingResult == null) {
         this.determineResult(session, session.rerolls, column);
      } else {
         this.stopSingleColumn(session, column);
      }
   }

   private void stopSingleColumn(SlotMachineManager.SpinSession session, int column) {
      if (session == null || session.pendingResult == null || column < 0 || column >= 3 || session.stoppedColumns[column]) {
         return;
      }

      session.stoppedColumns[column] = true;
      session.shelf.getWorld().playSound(session.shelf.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 1.0F + column * 0.2F);

      List<SlotMachineManager.Symbol> display = new ArrayList<>(3);
      boolean allStopped = true;
      for (int current = 0; current < 3; current++) {
         if (session.stoppedColumns[current]) {
            display.add(session.pendingResult.get(current));
         } else {
            allStopped = false;
            display.add(this.randomPreview(session.jackpotMode));
         }
      }
      this.updateShelfDisplay(session.shelf, display, session.jackpotMode);

      if (!allStopped) {
         session.player.sendActionBar(net.kyori.adventure.text.Component.text(
            "停止済み " + this.stoppedColumnCount(session) + "/3 — 次に止める列を右クリック",
            net.kyori.adventure.text.format.NamedTextColor.YELLOW
         ));
         return;
      }

      if (session.task != null) {
         session.task.cancel();
         session.task = null;
      }
      this.updateShelfDisplay(session.shelf, session.pendingResult, session.jackpotMode);
      Runnable completion = session.pendingCompletion;
      session.pendingCompletion = null;
      if (completion != null) {
         completion.run();
      }
   }

   private int stoppedColumnCount(SlotMachineManager.SpinSession session) {
      int count = 0;
      for (boolean stopped : session.stoppedColumns) {
         if (stopped) {
            count++;
         }
      }
      return count;
   }

   private int clickedColumn(Block shelf, Vector clickedPosition) {
      if (clickedPosition == null) {
         return 1;
      }

      double local = switch (shelf.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.NORTH) {
         case NORTH -> clickedPosition.getX();
         case SOUTH -> 1.0 - clickedPosition.getX();
         case EAST -> clickedPosition.getZ();
         case WEST -> 1.0 - clickedPosition.getZ();
         default -> clickedPosition.getX();
      };
      local = Math.max(0.0, Math.min(0.999999, local));

      // Shelf inventory slots are displayed right-to-left. This is identical to Minerva.selectedShelfSlot().
      if (local < 1.0 / 3.0) {
         return 2;
      }
      return local < 2.0 / 3.0 ? 1 : 0;
   }

   private SlotMachineManager.Symbol randomPreview(boolean jackpotMode) {
      SlotMachineManager.Symbol[] values = jackpotMode
         ? new SlotMachineManager.Symbol[]{
            SlotMachineManager.Symbol.COAL,
            SlotMachineManager.Symbol.COPPER,
            SlotMachineManager.Symbol.IRON,
            SlotMachineManager.Symbol.LAPIS,
            SlotMachineManager.Symbol.REDSTONE,
            SlotMachineManager.Symbol.GOLD,
            SlotMachineManager.Symbol.DIAMOND,
            SlotMachineManager.Symbol.NETHERITE
         }
         : new SlotMachineManager.Symbol[]{
            SlotMachineManager.Symbol.COAL,
            SlotMachineManager.Symbol.COPPER,
            SlotMachineManager.Symbol.IRON,
            SlotMachineManager.Symbol.LAPIS,
            SlotMachineManager.Symbol.REDSTONE,
            SlotMachineManager.Symbol.GOLD,
            SlotMachineManager.Symbol.DIAMOND
         };
      return values[ThreadLocalRandom.current().nextInt(values.length)];
   }

   private SlotMachineManager.Symbol randomWinningSymbol(boolean jackpotMode) {
      Map<SlotMachineManager.Symbol, Integer> weights = new EnumMap<>(SlotMachineManager.Symbol.class);

      for (SlotMachineManager.Symbol symbol : SlotMachineManager.Symbol.values()) {
         if (symbol.normalWeight > 0) {
            weights.put(symbol, symbol.normalWeight);
         }
      }

      if (jackpotMode) {
         weights.put(SlotMachineManager.Symbol.DIAMOND, 4);
         weights.put(SlotMachineManager.Symbol.NETHERITE, 1);
      }

      int total = weights.values().stream().mapToInt(Integer::intValue).sum();
      int roll = ThreadLocalRandom.current().nextInt(total);

      for (Entry<SlotMachineManager.Symbol, Integer> entry : weights.entrySet()) {
         roll -= entry.getValue();
         if (roll < 0) {
            return entry.getKey();
         }
      }

      return SlotMachineManager.Symbol.COAL;
   }

   private List<SlotMachineManager.Symbol> randomLoss(boolean jackpotMode) {
      List<SlotMachineManager.Symbol> result = new ArrayList<>();

      do {
         result.clear();
         result.add(this.randomPreview(jackpotMode));
         result.add(this.randomPreview(jackpotMode));
         result.add(this.randomPreview(jackpotMode));
      } while (result.get(0) == result.get(1) && result.get(1) == result.get(2));

      return result;
   }

   private void updateShelfDisplay(Block block, List<SlotMachineManager.Symbol> symbols, boolean jackpotMode) {
      if (block.getState() instanceof Shelf shelf) {
         Inventory inventory = shelf.getInventory();
         inventory.clear();

         for (int slot = 0; slot < Math.min(3, symbols.size()); slot++) {
            inventory.setItem(slot, new ItemStack(symbols.get(slot).material(jackpotMode)));
         }
      }
   }

   private String displayName(SlotMachineManager.Symbol symbol, boolean jackpotMode) {
      return symbol.material(jackpotMode).name().toLowerCase(Locale.ROOT).replace('_', ' ');
   }

   private void finishSession(SlotMachineManager.SpinSession session) {
      if (session != null) {
         if (session.task != null) {
            session.task.cancel();
         }

         this.activeSessions.remove(session.player.getUniqueId(), session);
         this.busyMachines.remove(session.machineKey, session.player.getUniqueId());
      }
   }

   private boolean enableJackpotMode(SlotMachineManager.SpinSession var1) {
      return SlotJackpotSync.enableMode(this.plugin, var1.player);
   }

   enum Difficulty {
      EASY(100, 0.32, 1.0522),
      NORMAL(1000, 0.2, 1.684),
      HARD(10000, 0.12, 2.8067),
      EXPERT(100000, 0.06, 5.6133);

      private final int wager;
      private final double winChance;
      private final double payoutScale;

      Difficulty(int wager, double winChance, double payoutScale) {
         this.wager = wager;
         this.winChance = winChance;
         this.payoutScale = payoutScale;
      }
   }

   private static final class SpinSession {
      private final Player player;
      private final Block shelf;
      private final SlotMachineManager.Difficulty difficulty;
      private final boolean jackpotMode;
      private final String machineKey;
      private BukkitTask task;
      private int rerolls;
      private final boolean[] stoppedColumns = new boolean[3];
      private List<SlotMachineManager.Symbol> pendingResult;
      private Runnable pendingCompletion;

      private SpinSession(Player player, Block shelf, SlotMachineManager.Difficulty difficulty, boolean jackpotMode, String machineKey) {
         this.player = player;
         this.shelf = shelf;
         this.difficulty = difficulty;
         this.jackpotMode = jackpotMode;
         this.machineKey = machineKey;
      }
   }

   private enum Symbol {
      COAL(Material.COAL, Material.COAL, 0.5, 35),
      COPPER(Material.RAW_COPPER, Material.COPPER_INGOT, 1.0, 25),
      IRON(Material.RAW_IRON, Material.IRON_INGOT, 2.0, 18),
      LAPIS(Material.LAPIS_LAZULI, Material.LAPIS_LAZULI, 4.0, 12),
      REDSTONE(Material.REDSTONE, Material.REDSTONE, 0.0, 0),
      GOLD(Material.RAW_GOLD, Material.GOLD_INGOT, 8.0, 8),
      DIAMOND(Material.DIAMOND, Material.DIAMOND, 25.0, 2),
      NETHERITE(Material.NETHERITE_INGOT, Material.NETHERITE_INGOT, 75.0, 0);

      private final Material normalMaterial;
      private final Material jackpotMaterial;
      private final double payoutMultiplier;
      private final int normalWeight;

      Symbol(Material normalMaterial, Material jackpotMaterial, double payoutMultiplier, int normalWeight) {
         this.normalMaterial = normalMaterial;
         this.jackpotMaterial = jackpotMaterial;
         this.payoutMultiplier = payoutMultiplier;
         this.normalWeight = normalWeight;
      }

      Material material(boolean jackpotMode) {
         return jackpotMode ? this.jackpotMaterial : this.normalMaterial;
      }
   }
}
