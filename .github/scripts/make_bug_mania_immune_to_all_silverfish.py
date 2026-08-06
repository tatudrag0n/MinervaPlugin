from pathlib import Path

listener_path = Path('src/main/java/org/server/minerva/FfaListener.java')
listener = listener_path.read_text(encoding='utf-8')

old_damage = '''      } else if (event.getEntity() instanceof Player victim) {
         Entity var9 = event.getDamager();
         boolean isBugSilverfish = false;
'''
new_damage = '''      } else if (event.getEntity() instanceof Player victim) {
         Entity var9 = event.getDamager();
         if (var9 instanceof org.bukkit.entity.Silverfish && this.ffa.isBugMania(victim)) {
            event.setCancelled(true);
            return;
         }
         boolean isBugSilverfish = false;
'''
if old_damage not in listener:
    raise SystemExit('FfaListener damage insertion point not found')
listener = listener.replace(old_damage, new_damage, 1)
listener_path.write_text(listener, encoding='utf-8', newline='\n')

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

old_target = '''   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
      Entity entity = event.getEntity();
      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
'''
new_target = '''   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
      Entity entity = event.getEntity();
      if (entity instanceof org.bukkit.entity.Silverfish
         && event.getTarget() instanceof Player player
         && this.isBugMania(player)) {
         event.setCancelled(true);
         if (entity instanceof Mob mob) {
            mob.setTarget(null);
         }
         return;
      }

      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
'''
if old_target not in manager:
    raise SystemExit('FfaManager target insertion point not found')
manager = manager.replace(old_target, new_target, 1)
manager_path.write_text(manager, encoding='utf-8', newline='\n')
