from pathlib import Path
import re

# Triggered repair for the projectile disappearance regression.
manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
listener_path = Path('src/main/java/org/server/minerva/FfaListener.java')

manager = manager_path.read_text(encoding='utf-8')
listener = listener_path.read_text(encoding='utf-8')

# 1) Do not suppress crossbow shoot events merely because another event occurred in the same tick.
manager = manager.replace(
'''      if (this.isDuplicateCrossbowShot(player)) {
         return;
      }

''',
'',
1,
)
manager = manager.replace(
'''            } else {
               ammoMap.put(player.getUniqueId(), --ammo);
               if (event.getProjectile() instanceof Entity projectile) {
''',
'''            } else {
               event.setCancelled(false);
               ammoMap.put(player.getUniqueId(), --ammo);
               if (event.getProjectile() instanceof Entity projectile) {
''',
1,
)

# 2) Wind charge: never remove/re-add the held stack in the same tick as launch.
wind_pattern = re.compile(r'''         if \("wind_charge"\.equals\(kind\) && session\.kit == FfaKit\.MACE\) \{.*?         \}\n\n         return false;''', re.S)
wind_replacement = '''         if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            if (player.hasCooldown(Material.WIND_CHARGE)) {
               return true;
            }
            player.setCooldown(Material.WIND_CHARGE, 40);
            ItemStack restoredWindCharge = item.clone();
            restoredWindCharge.setAmount(1);
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && this.isPlaying(player)) {
                  FfaManager.FfaSession current = this.sessions.get(player.getUniqueId());
                  if (current != null && current.kit == FfaKit.MACE && this.countFfaItem(player, "wind_charge") <= 0) {
                     this.tagOwner(restoredWindCharge, player.getUniqueId());
                     player.getInventory().addItem(restoredWindCharge);
                     player.updateInventory();
                  }
               }
            }, 3L);
         }

         return false;'''
manager, n = wind_pattern.subn(wind_replacement, manager, count=1)
if n != 1:
    raise SystemExit('wind charge block not found')

# 3) Explicitly allow FFA arrows and wind charges at projectile launch time.
old_launch = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProjectileLaunch(ProjectileLaunchEvent event) {
      this.ffa.handleProjectileLaunch(event);
   }
'''
new_launch = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProjectileLaunch(ProjectileLaunchEvent event) {
      Projectile projectile = event.getEntity();
      if (projectile.getShooter() instanceof Player player
         && this.ffa.isPlaying(player)
         && (projectile instanceof org.bukkit.entity.AbstractArrow
            || projectile.getType() == org.bukkit.entity.EntityType.WIND_CHARGE)) {
         event.setCancelled(false);
      }
      this.ffa.handleProjectileLaunch(event);
   }
'''
if old_launch not in listener:
    raise SystemExit('projectile launch listener marker not found')
listener = listener.replace(old_launch, new_launch, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
listener_path.write_text(listener, encoding='utf-8', newline='\n')
