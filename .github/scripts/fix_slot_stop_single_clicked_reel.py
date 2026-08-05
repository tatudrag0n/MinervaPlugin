from pathlib import Path

path = Path('src/main/java/org/server/minerva/SlotMachineManager.java')
text = path.read_text(encoding='utf-8')


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = source.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit(f'unclosed method: {signature}')
    return source[:start] + replacement + source[end:]

# Each animation tick must preserve reels that the player has already stopped.
old_display = '''                  SlotMachineManager.this.updateShelfDisplay(
                     session.shelf,
                     List.of(
                        SlotMachineManager.this.randomPreview(session.jackpotMode),
                        SlotMachineManager.this.randomPreview(session.jackpotMode),
                        SlotMachineManager.this.randomPreview(session.jackpotMode)
                     ),
                     session.jackpotMode
                  );'''
new_display = '''                  List<SlotMachineManager.Symbol> display = new ArrayList<>(3);
                  for (int column = 0; column < 3; column++) {
                     if (session.stoppedColumns[column] && session.pendingResult != null) {
                        display.add(session.pendingResult.get(column));
                     } else {
                        display.add(SlotMachineManager.this.randomPreview(session.jackpotMode));
                     }
                  }
                  SlotMachineManager.this.updateShelfDisplay(session.shelf, display, session.jackpotMode);'''
if old_display in text:
    text = text.replace(old_display, new_display, 1)
elif new_display not in text:
    raise SystemExit('animation display block not found')

# Do not reset a global stopping flag; reels are stopped independently.
text = text.replace('      session.stopping = false;\n', '')
text = text.replace('session.stopping = false; this.animate(session, rerolls + 1);', 'this.animate(session, rerolls + 1);')

request_stop = '''   private void requestStop(SlotMachineManager.SpinSession session, int column) {
      if (session == null || column < 0 || column >= 3 || session.stoppedColumns[column]) {
         return;
      }

      if (session.pendingResult == null) {
         this.determineResult(session, session.rerolls, column);
         return;
      }

      this.stopSingleColumn(session, column);
   }'''
text = replace_method(text, '   private void requestStop(SlotMachineManager.SpinSession session, int startColumn)', request_stop)

reveal = '''   private void revealResult(final SlotMachineManager.SpinSession session, final List<SlotMachineManager.Symbol> result, final int startColumn, final Runnable onComplete) {
      session.pendingResult = new ArrayList<>(result);
      session.pendingCompletion = onComplete;
      this.stopSingleColumn(session, startColumn);
   }'''
text = replace_method(text, '   private void revealResult(final SlotMachineManager.SpinSession session, final List<SlotMachineManager.Symbol> result, final int startColumn, final Runnable onComplete)', reveal)

marker = '   private int clickedColumn(Block shelf, Vector clickedPosition) {'
helper = '''   private void stopSingleColumn(SlotMachineManager.SpinSession session, int column) {
      if (session == null || session.pendingResult == null || column < 0 || column >= 3 || session.stoppedColumns[column]) {
         return;
      }

      session.stoppedColumns[column] = true;
      session.shelf.getWorld().playSound(session.shelf.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.0F + column * 0.2F);

      List<SlotMachineManager.Symbol> display = new ArrayList<>(3);
      boolean allStopped = true;
      for (int i = 0; i < 3; i++) {
         if (session.stoppedColumns[i]) {
            display.add(session.pendingResult.get(i));
         } else {
            allStopped = false;
            display.add(this.randomPreview(session.jackpotMode));
         }
      }
      this.updateShelfDisplay(session.shelf, display, session.jackpotMode);

      if (allStopped) {
         if (session.task != null) {
            session.task.cancel();
            session.task = null;
         }
         Runnable completion = session.pendingCompletion;
         session.pendingCompletion = null;
         if (completion != null) {
            completion.run();
         }
      }
   }

'''
if 'private void stopSingleColumn(' not in text:
    if marker not in text:
        raise SystemExit('clickedColumn marker not found')
    text = text.replace(marker, helper + marker, 1)

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
    raise SystemExit('SpinSession fields not found')

# Free reroll starts a completely fresh three-reel stop sequence.
old_reroll = 'session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> { this.animate(session, rerolls + 1); }, 10L);'
new_reroll = '''session.task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                  session.pendingResult = null;
                  session.pendingCompletion = null;
                  java.util.Arrays.fill(session.stoppedColumns, false);
                  this.animate(session, rerolls + 1);
               }, 10L);'''
if old_reroll in text:
    text = text.replace(old_reroll, new_reroll, 1)

path.write_text(text, encoding='utf-8', newline='\n')
