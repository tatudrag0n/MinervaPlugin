from pathlib import Path


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"method not found: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise SystemExit(f"method body not found: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[:start] + replacement + source[index + 1:]
    raise SystemExit(f"unclosed method: {signature}")


path = Path("src/main/java/org/server/minerva/SlotMachineManager.java")
text = path.read_text(encoding="utf-8")

if "import java.util.Arrays;" not in text:
    text = text.replace("import java.util.ArrayList;\n", "import java.util.ArrayList;\nimport java.util.Arrays;\n", 1)

# Root cause 1: the animation replaced all three shelf slots every tick.
old_animation = '''                  SlotMachineManager.this.updateShelfDisplay(
                     session.shelf,
                     List.of(
                        SlotMachineManager.this.randomPreview(session.jackpotMode),
                        SlotMachineManager.this.randomPreview(session.jackpotMode),
                        SlotMachineManager.this.randomPreview(session.jackpotMode)
                     ),
                     session.jackpotMode
                  );'''
new_animation = '''                  List<SlotMachineManager.Symbol> display = new ArrayList<>(3);
                  for (int column = 0; column < 3; column++) {
                     if (session.stoppedColumns[column] && session.pendingResult != null) {
                        display.add(session.pendingResult.get(column));
                     } else {
                        display.add(SlotMachineManager.this.randomPreview(session.jackpotMode));
                     }
                  }
                  SlotMachineManager.this.updateShelfDisplay(session.shelf, display, session.jackpotMode);'''
if old_animation in text:
    text = text.replace(old_animation, new_animation, 1)
elif new_animation not in text:
    raise SystemExit("slot animation display block not found")

# Every paid spin or free reroll starts with three moving reels.
old_animate_header = '''   private void animate(final SlotMachineManager.SpinSession session, final int rerolls) {
      session.rerolls = rerolls;
      session.stopping = false;
      session.task = (new BukkitRunnable() {'''
new_animate_header = '''   private void animate(final SlotMachineManager.SpinSession session, final int rerolls) {
      session.rerolls = rerolls;
      session.pendingResult = null;
      session.pendingCompletion = null;
      Arrays.fill(session.stoppedColumns, false);
      session.task = (new BukkitRunnable() {'''
if old_animate_header in text:
    text = text.replace(old_animate_header, new_animate_header, 1)
elif new_animate_header not in text:
    raise SystemExit("animate header not found")

# The outcome is selected once on the first stop click. Each later click reveals only that reel.
reveal_method = '''   private void revealResult(
      final SlotMachineManager.SpinSession session,
      final List<SlotMachineManager.Symbol> result,
      final int startColumn,
      final Runnable onComplete
   ) {
      session.pendingResult = new ArrayList<>(result);
      session.pendingCompletion = onComplete;
      this.stopSingleColumn(session, startColumn);
   }'''
text = replace_method(
    text,
    "   private void revealResult(final SlotMachineManager.SpinSession session, final List<SlotMachineManager.Symbol> result, final int startColumn, final Runnable onComplete)",
    reveal_method,
)

request_method = '''   private void requestStop(SlotMachineManager.SpinSession session, int column) {
      if (session == null || column < 0 || column >= 3 || session.stoppedColumns[column]) {
         return;
      }

      if (session.pendingResult == null) {
         this.determineResult(session, session.rerolls, column);
      } else {
         this.stopSingleColumn(session, column);
      }
   }'''
text = replace_method(text, "   private void requestStop(SlotMachineManager.SpinSession session, int startColumn)", request_method)

stop_helper = '''   private void stopSingleColumn(SlotMachineManager.SpinSession session, int column) {
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

'''
clicked_signature = "   private int clickedColumn(Block shelf, Vector clickedPosition)"
if "private void stopSingleColumn(" not in text:
    marker = text.find(clicked_signature)
    if marker < 0:
        raise SystemExit("clickedColumn insertion point not found")
    text = text[:marker] + stop_helper + text[marker:]

# Root cause 2: slot-machine column numbering was opposite to the working shelf-shop mapping.
clicked_method = '''   private int clickedColumn(Block shelf, Vector clickedPosition) {
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
   }'''
text = replace_method(text, clicked_signature, clicked_method)

# A redstone reroll must reset all three reels instead of reviving a global stop flag.
old_reroll = '''               session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> { session.stopping = false; this.animate(session, rerolls + 1); }, 10L);'''
new_reroll = '''               session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.animate(session, rerolls + 1), 10L);'''
if old_reroll in text:
    text = text.replace(old_reroll, new_reroll, 1)
elif new_reroll not in text:
    raise SystemExit("redstone reroll callback not found")

old_fields = '''      private BukkitTask task;
      private boolean stopping;
      private int rerolls;'''
new_fields = '''      private BukkitTask task;
      private int rerolls;
      private final boolean[] stoppedColumns = new boolean[3];
      private List<SlotMachineManager.Symbol> pendingResult;
      private Runnable pendingCompletion;'''
if old_fields in text:
    text = text.replace(old_fields, new_fields, 1)
elif new_fields not in text:
    raise SystemExit("SpinSession state fields not found")

# Guard against stale references from the previous implementation.
if "session.stopping" in text or "private boolean stopping;" in text:
    raise SystemExit("global stopping state still remains")

path.write_text(text, encoding="utf-8", newline="\n")
