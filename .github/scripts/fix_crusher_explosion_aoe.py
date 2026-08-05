from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
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

replacement = '''   private void applyTrainingCrusherExplosion(Player crusher, org.bukkit.entity.Husk husk, Location center) {
      double damage = this.rollTrainingCrusherExplosionDamage();
      if (damage <= 0.0 || crusher == null || center == null || center.getWorld() == null) {
         return;
      }

      double radius = damage >= 32.0 ? 16.0 : damage >= 16.0 ? 8.0 : damage >= 8.0 ? 4.0 : 2.0;
      World world = center.getWorld();
      world.spawnParticle(Particle.EXPLOSION, center.clone().add(0.0, 1.0, 0.0), Math.max(2, (int)(radius / 2.0)));
      world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.0F);

      int affected = 0;
      for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
         if (!(entity instanceof LivingEntity living) || living.isDead() || living.getUniqueId().equals(crusher.getUniqueId())) {
            continue;
         }
         if (living.getLocation().distanceSquared(center) > radius * radius) {
            continue;
         }
         if (living instanceof Player nearbyPlayer && !this.isPlaying(nearbyPlayer)) {
            continue;
         }

         living.setNoDamageTicks(0);
         if (living instanceof Player nearbyPlayer) {
            UUID targetId = nearbyPlayer.getUniqueId();
            if (!this.crusherExplosionDamage.add(targetId)) {
               continue;
            }
            try {
               nearbyPlayer.damage(damage, crusher);
            } finally {
               this.crusherExplosionDamage.remove(targetId);
            }
         } else {
            AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
            double maximum = maxHealth == null ? living.getHealth() : maxHealth.getValue();
            living.setHealth(Math.max(0.0, Math.min(maximum, living.getHealth()) - damage));
         }
         affected++;
      }

      crusher.sendActionBar(Component.text(
         "クラッシャー爆発 " + (int)damage + "ダメージ / 範囲" + (int)radius + " / " + affected + "体",
         NamedTextColor.GOLD
      ));
   }'''

text = replace_method(
    text,
    '   private void applyTrainingCrusherExplosion(Player crusher, org.bukkit.entity.Husk husk, Location center)',
    replacement,
)

path.write_text(text, encoding='utf-8', newline='\n')
