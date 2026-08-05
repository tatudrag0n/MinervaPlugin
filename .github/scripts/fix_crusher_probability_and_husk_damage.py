from pathlib import Path

# Triggered repair: one probability roll per crusher damage event tick, and direct husk health damage.
path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

field_marker = '   private final Set<UUID> crusherExplosionDamage = new HashSet<>();\n'
if field_marker not in text:
    raise SystemExit('crusher field marker not found')
text = text.replace(
    field_marker,
    field_marker + '   private final Map<UUID, Long> crusherExplosionAttemptTick = new HashMap<>();\n',
    1,
)

start = text.index('   private void triggerCrusherExplosion(')
end = text.index('   private void capFinalDamage(', start)
replacement = '''   private void triggerCrusherExplosion(Player owner, LivingEntity target, boolean attacking) {
      if (owner == null || target == null || this.crusherExplosionDamage.contains(owner.getUniqueId())) {
         return;
      }

      long currentTick = this.plugin.getServer().getCurrentTick();
      Long previousAttempt = this.crusherExplosionAttemptTick.put(owner.getUniqueId(), currentTick);
      if (previousAttempt != null && previousAttempt == currentTick) {
         return;
      }

      double roll = ThreadLocalRandom.current().nextDouble();
      double damage;
      double radius;
      if (roll < 0.0625) {
         damage = 32.0;
         radius = 16.0;
      } else if (roll < 0.125) {
         damage = 16.0;
         radius = 8.0;
      } else if (roll < 0.25) {
         damage = 8.0;
         radius = 4.0;
      } else if (roll < 0.5) {
         damage = 4.0;
         radius = 2.0;
      } else {
         return;
      }

      Location origin = target.getLocation().clone().add(0.0, 1.0, 0.0);
      this.crusherExplosionDamage.add(owner.getUniqueId());
      try {
         for (Entity nearby : target.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead() || living.getUniqueId().equals(owner.getUniqueId())) {
               continue;
            }
            if (living instanceof Player player && !this.isPlaying(player)) {
               continue;
            }
            if (!(living instanceof Player) && !(living instanceof org.bukkit.entity.Husk)) {
               continue;
            }

            double distance = origin.distance(living.getLocation().clone().add(0.0, 1.0, 0.0));
            double dealt = Math.max(1.0, damage * Math.max(0.25, 1.0 - distance / Math.max(1.0, radius)));
            if (living instanceof Player player) {
               this.recordDamage(owner, player);
               living.damage(dealt, owner);
            } else if (living instanceof org.bukkit.entity.Husk husk) {
               husk.setNoDamageTicks(0);
               husk.setHealth(Math.max(0.0, husk.getHealth() - dealt));
            }
         }
      } finally {
         this.crusherExplosionDamage.remove(owner.getUniqueId());
      }

      owner.getWorld().spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, 0.8, 0.5, 0.8, 0.05);
      owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
   }

'''
text = text[:start] + replacement + text[end:]

shutdown_marker = '      this.deathLeaveRestores.clear();\n'
if shutdown_marker in text:
    text = text.replace(shutdown_marker, shutdown_marker + '      this.crusherExplosionAttemptTick.clear();\n', 1)

path.write_text(text, encoding='utf-8', newline='\n')
