from pathlib import Path

# Triggered change: stop reels from the shelf column the player right-clicked.
path = Path('src/main/java/org/server/minerva/SlotMachineManager.java')
text = path.read_text(encoding='utf-8')

if 'import org.bukkit.util.Vector;' not in text:
    text = text.replace('import org.bukkit.scheduler.BukkitTask;\n', 'import org.bukkit.scheduler.BukkitTask;\nimport org.bukkit.util.Vector;\n')

old_interact = '''   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.plugin.isMinervaItem(event.getItem(), "emerald_bundle")) {
         SlotMachineManager.Difficulty difficulty = this.difficultyAt(event.getClickedBlock());
         if (difficulty != null) {
            event.setCancelled(true);
            this.startSpin(event.getPlayer(), event.getClickedBlock(), difficulty);
         }
      }
   }
'''
new_interact = '''   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
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
'''
if old_interact not in text:
    raise SystemExit('interaction handler not found')
text = text.replace(old_interact, new_interact, 1)

old_start_tail = '''            player.sendMessage("§e" + difficulty.wager + "MPでスロットを回します。");
            this.animate(session, 0);
'''
new_start_tail = '''            player.sendMessage("§e" + difficulty.wager + "MPでスロットを回します。§f止めたい列を右クリックしてください。");
            this.animate(session, 0);
'''
text = text.replace(old_start_tail, new_start_tail, 1)

old_animate_end = '''                  if (++this.tick >= 20) {
                     this.cancel();
                     SlotMachineManager.this.determineResult(session, rerolls);
                  }
'''
new_animate_end = '''                  this.tick++;
'''
if old_animate_end not in text:
    raise SystemExit('automatic stop block not found')
text = text.replace(old_animate_end, new_animate_end, 1)

text = text.replace('private void determineResult(SlotMachineManager.SpinSession session, int rerolls) {',
                    'private void determineResult(SlotMachineManager.SpinSession session, int rerolls, int startColumn) {', 1)
text = text.replace('this.revealResult(session, List.of(symbol, symbol, symbol), () -> {',
                    'this.revealResult(session, List.of(symbol, symbol, symbol), startColumn, () -> {', 1)
text = text.replace('session, List.of(SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE), () -> {',
                    'session, List.of(SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE, SlotMachineManager.Symbol.REDSTONE), startColumn, () -> {', 1)
text = text.replace('this.revealResult(session, loss, () -> {',
                    'this.revealResult(session, loss, startColumn, () -> {', 1)

text = text.replace('session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.animate(session, rerolls + 1), 10L);',
                    'session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> { session.stopping = false; this.animate(session, rerolls + 1); }, 10L);', 1)

old_reveal_sig = 'private void revealResult(final SlotMachineManager.SpinSession session, final List<SlotMachineManager.Symbol> result, final Runnable onComplete) {'
new_reveal_sig = 'private void revealResult(final SlotMachineManager.SpinSession session, final List<SlotMachineManager.Symbol> result, final int startColumn, final Runnable onComplete) {'
if old_reveal_sig not in text:
    raise SystemExit('reveal signature not found')
text = text.replace(old_reveal_sig, new_reveal_sig, 1)

old_loop = '''               this.fixedColumns++;
               List<SlotMachineManager.Symbol> display = new ArrayList<>(3);

               for (int column = 0; column < 3; column++) {
                  display.add(column < this.fixedColumns ? result.get(column) : SlotMachineManager.this.randomPreview(session.jackpotMode));
               }
'''
new_loop = '''               this.fixedColumns++;
               List<SlotMachineManager.Symbol> display = new ArrayList<>(List.of(
                  SlotMachineManager.this.randomPreview(session.jackpotMode),
                  SlotMachineManager.this.randomPreview(session.jackpotMode),
                  SlotMachineManager.this.randomPreview(session.jackpotMode)
               ));

               for (int fixed = 0; fixed < this.fixedColumns; fixed++) {
                  int column = (startColumn + fixed) % 3;
                  display.set(column, result.get(column));
               }
'''
if old_loop not in text:
    raise SystemExit('reveal loop not found')
text = text.replace(old_loop, new_loop, 1)

marker = '   private SlotMachineManager.Symbol randomPreview(boolean jackpotMode) {'
helper = '''   private void requestStop(SlotMachineManager.SpinSession session, int startColumn) {
      if (session == null || session.stopping) {
         return;
      }
      session.stopping = true;
      if (session.task != null) {
         session.task.cancel();
         session.task = null;
      }
      session.shelf.getWorld().playSound(session.shelf.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 1.6F);
      this.determineResult(session, session.rerolls, Math.max(0, Math.min(2, startColumn)));
   }

   private int clickedColumn(Block shelf, Vector clickedPosition) {
      if (clickedPosition == null) {
         return 1;
      }
      double coordinate = clickedPosition.getX();
      if (shelf.getBlockData() instanceof Directional directional) {
         coordinate = switch (directional.getFacing()) {
            case SOUTH -> 1.0 - clickedPosition.getX();
            case EAST -> clickedPosition.getZ();
            case WEST -> 1.0 - clickedPosition.getZ();
            default -> clickedPosition.getX();
         };
      }
      return Math.max(0, Math.min(2, (int)Math.floor(coordinate * 3.0)));
   }

'''
if marker not in text:
    raise SystemExit('randomPreview marker not found')
text = text.replace(marker, helper + marker, 1)

old_fields = '''      private final String machineKey;
      private BukkitTask task;
'''
new_fields = '''      private final String machineKey;
      private BukkitTask task;
      private boolean stopping;
      private int rerolls;
'''
if old_fields not in text:
    raise SystemExit('session fields not found')
text = text.replace(old_fields, new_fields, 1)

old_animate_sig = '''   private void animate(final SlotMachineManager.SpinSession session, final int rerolls) {
      session.task = (new BukkitRunnable() {
'''
new_animate_sig = '''   private void animate(final SlotMachineManager.SpinSession session, final int rerolls) {
      session.rerolls = rerolls;
      session.stopping = false;
      session.task = (new BukkitRunnable() {
'''
if old_animate_sig not in text:
    raise SystemExit('animate signature block not found')
text = text.replace(old_animate_sig, new_animate_sig, 1)

path.write_text(text, encoding='utf-8', newline='\n')
