from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaListener.java')
text = path.read_text(encoding='utf-8')
old = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFoodLevelChange(FoodLevelChangeEvent event) {
      if (event.getEntity() instanceof Player player
         && !"survival".equalsIgnoreCase(player.getWorld().getName())
         && event.getFoodLevel() < player.getFoodLevel()) {
         event.setCancelled(true);
      }
   }
'''
new = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFoodLevelChange(FoodLevelChangeEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }

      if (this.ffa.isPlaying(player)) {
         event.setCancelled(false);
         return;
      }

      if (!"survival".equalsIgnoreCase(player.getWorld().getName())
         && event.getFoodLevel() < player.getFoodLevel()) {
         event.setCancelled(true);
      }
   }
'''
if old not in text:
    if new in text:
        raise SystemExit(0)
    raise SystemExit('Food level handler block was not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8', newline='\n')
