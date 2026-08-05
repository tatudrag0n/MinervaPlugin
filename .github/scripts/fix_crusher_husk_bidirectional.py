from pathlib import Path

# Triggered repair for bidirectional Crusher testing against ordinary Husks.
manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
listener_path = Path('src/main/java/org/server/minerva/FfaListener.java')
manager = manager_path.read_text(encoding='utf-8')
listener = listener_path.read_text(encoding='utf-8')


def method_bounds(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = text.find('{', start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f'unclosed method: {signature}')

helper = '''
   private double rollTrainingCrusherExplosionDamage() {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll < 50) return 0.0;
      if (roll < 74) return 4.0;
      if (roll < 89) return 8.0;
      if (roll < 99) return 16.0;
      return 32.0;
   }

   private void applyTrainingCrusherExplosion(Player crusher, org.bukkit.entity.Husk husk, Location center) {
      double damage = this.rollTrainingCrusherExplosionDamage();
      if (damage <= 0.0 || crusher == null || husk == null || husk.isDead()) return;
      World world = center.getWorld();
      if (world != null) {
         world.spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 1.0, 0.0), 2);
         world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.0F);
      }
      husk.setNoDamageTicks(0);
      husk.setHealth(Math.max(0.0, husk.getHealth() - damage));
      crusher.sendActionBar(Component.text("クラッシャー爆発 " + (int)damage + "ダメージ", NamedTextColor.GOLD));
   }

   void handleTrainingHuskAttack(EntityDamageByEntityEvent event, org.bukkit.entity.Husk husk, Player victim) {
      if (event.isCancelled() || husk == null || victim == null || !this.isPlaying(victim)) return;
      FfaManager.FfaSession session = this.sessions.get(victim.getUniqueId());
      if (session != null && session.kit == FfaKit.CRUSHER) {
         this.applyTrainingCrusherExplosion(victim, husk, victim.getLocation());
      }
   }
'''
insert_at = manager.rfind('\n}')
if insert_at < 0:
    raise SystemExit('manager class end not found')

if 'rollTrainingCrusherExplosionDamage()' in manager:
    start, end = method_bounds(manager, '   private double rollTrainingCrusherExplosionDamage()')
    method = '''   private double rollTrainingCrusherExplosionDamage() {
      int roll = ThreadLocalRandom.current().nextInt(100);
      if (roll < 50) return 0.0;
      if (roll < 74) return 4.0;
      if (roll < 89) return 8.0;
      if (roll < 99) return 16.0;
      return 32.0;
   }'''
    manager = manager[:start] + method + manager[end:]
else:
    manager = manager[:insert_at] + helper + manager[insert_at:]

old = '''      if (session.kit == FfaKit.CRUSHER) {
         husk.getWorld().spawnParticle(Particle.EXPLOSION, husk.getLocation().add(0.0, 1.0, 0.0), 2);
      }
'''
new = '''      if (session.kit == FfaKit.CRUSHER) {
         this.applyTrainingCrusherExplosion(attacker, husk, husk.getLocation());
      }
'''
if old in manager:
    manager = manager.replace(old, new, 1)
elif new not in manager:
    raise SystemExit('training Husk Crusher branch not found')

needle = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onTrainingHuskDamage(EntityDamageByEntityEvent event) {
'''
reverse = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTrainingHuskAttack(EntityDamageByEntityEvent event) {
      if (!(event.getDamager() instanceof org.bukkit.entity.Husk husk)
         || !(event.getEntity() instanceof Player victim)
         || !this.ffa.isPlaying(victim)) {
         return;
      }
      this.ffa.handleTrainingHuskAttack(event, husk, victim);
   }

'''
if 'public void onTrainingHuskAttack(' not in listener:
    if needle not in listener:
        raise SystemExit('training Husk listener marker not found')
    listener = listener.replace(needle, reverse + needle, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
listener_path.write_text(listener, encoding='utf-8', newline='\n')
