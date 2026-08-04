from pathlib import Path

manager = Path('src/main/java/org/server/minerva/FfaManager.java')
text = manager.read_text(encoding='utf-8')
old = '''      UUID ownerId = owner.getUniqueId();
      List<UUID> owned = this.summonedMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
      int maxOwned = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.NECROMANCER, "max-summons"), 5));

      while (owned.size() >= maxOwned) {
         this.removeSummonEntity(owned.remove(0));
      }

      owned.add(entity.getUniqueId());'''
new = '''      UUID ownerId = owner.getUniqueId();
      List<UUID> owned = this.summonedMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
      owned.add(entity.getUniqueId());'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Necromancer summon limit block was not found')
manager.write_text(text, encoding='utf-8', newline='\n')

listener = Path('src/main/java/org/server/minerva/FfaListener.java')
text = listener.read_text(encoding='utf-8')
marker = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDamage(EntityDamageByEntityEvent event) {'''
insert = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFallDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player
         && this.ffa.isPlaying(player)
         && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
         event.setCancelled(true);
         player.setFallDistance(0.0F);
      }
   }

'''
if 'public void onFallDamage(EntityDamageEvent event)' not in text:
    if marker not in text:
        raise SystemExit('FFA damage handler marker was not found')
    text = text.replace(marker, insert + marker, 1)
listener.write_text(text, encoding='utf-8', newline='\n')

config = Path('src/main/resources/config.yml')
if config.exists():
    cfg = config.read_text(encoding='utf-8')
    cfg = cfg.replace('      max-summons: 5\n', '')
    config.write_text(cfg, encoding='utf-8', newline='\n')
