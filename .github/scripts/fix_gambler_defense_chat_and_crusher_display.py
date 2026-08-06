from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

old_gambler = '''         var1.setDamage(Math.max(0.0, var1.getDamage() - var8));
         var2.sendActionBar(Component.text("防御補正 " + (var8 >= 0 ? "-" : "+") + Math.abs(var8), var8 >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
         var2.sendTitle("", "§6防御補正 §e" + var8, 0, 20, 5);'''
new_gambler = '''         var1.setDamage(Math.max(0.0, var1.getDamage() - var8));
         String shown = (var8 >= 0 ? "-" : "+") + Math.abs(var8);
         var2.sendMessage((var8 >= 0 ? "§a" : "§c") + "防御補正 " + shown + "ダメージ");'''
if old_gambler not in text:
    raise SystemExit('Gambler defense display block not found')
text = text.replace(old_gambler, new_gambler, 1)

old_start = '''      this.crusherExplosionDamage.add(owner.getUniqueId());
      try {
         for (Entity nearby : target.getWorld().getNearbyEntities(origin, radius, radius, radius)) {'''
new_start = '''      int affected = 0;
      this.crusherExplosionDamage.add(owner.getUniqueId());
      try {
         for (Entity nearby : target.getWorld().getNearbyEntities(origin, radius, radius, radius)) {'''
if old_start not in text:
    raise SystemExit('Crusher explosion loop start not found')
text = text.replace(old_start, new_start, 1)

old_player_damage = '''            if (living instanceof Player player) {
               this.recordDamage(owner, player);
               player.damage(dealt, owner);
            } else if (living instanceof org.bukkit.entity.Husk husk) {
               husk.setHealth(Math.max(0.0, husk.getHealth() - dealt));
            }'''
new_player_damage = '''            if (living instanceof Player player) {
               this.recordDamage(owner, player);
               player.damage(dealt, owner);
            } else if (living instanceof org.bukkit.entity.Husk husk) {
               husk.setHealth(Math.max(0.0, husk.getHealth() - dealt));
            }
            affected++;'''
if old_player_damage not in text:
    raise SystemExit('Crusher target damage block not found')
text = text.replace(old_player_damage, new_player_damage, 1)

old_end = '''      owner.getWorld().spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, 0.8, 0.5, 0.8, 0.05);
      owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
   }'''
new_end = '''      owner.getWorld().spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, 0.8, 0.5, 0.8, 0.05);
      owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
      owner.sendActionBar(Component.text(
         "クラッシャー爆発 " + (int)damage + "ダメージ / 範囲" + (int)radius + " / " + affected + "体",
         NamedTextColor.GOLD
      ));
   }'''
if old_end not in text:
    raise SystemExit('Crusher explosion display insertion point not found')
text = text.replace(old_end, new_end, 1)

path.write_text(text, encoding='utf-8', newline='\n')
