from pathlib import Path
import re

# Triggered follow-up balance repair.
path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

# Fatal dagger: consume exactly once after a successful fatal hit.
text = text.replace(
'''                        if (target > 0.0) {
                           victim.setHealth(Math.max(0.5, victim.getHealth() - target));
                           mainHand.setAmount(0);
                           attacker.sendActionBar(Component.text("致命の短剣を使用しました", NamedTextColor.DARK_RED));
                        }
''',
'''                        if (target > 0.0) {
                           victim.setHealth(Math.max(0.5, victim.getHealth() - target));
                           mainHand.setAmount(0);
                           attacker.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                           attacker.updateInventory();
                           attacker.sendActionBar(Component.text("致命の短剣を使用しました", NamedTextColor.DARK_RED));
                        }
''',
1,
)

# Gambler: keep chat output and add an unmistakable on-screen subtitle.
text = text.replace(
'         var2.sendActionBar(Component.text("防御補正 " + (var8 >= 0 ? "-" : "+") + Math.abs(var8), var8 >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));',
'         var2.sendActionBar(Component.text("防御補正 " + (var8 >= 0 ? "-" : "+") + Math.abs(var8), var8 >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));\n         var2.sendTitle("", "§6防御補正 §e" + var8, 0, 20, 5);',
1,
)
text = text.replace(
'         var2.sendActionBar(Component.text("攻撃抽選 " + (var10 >= 0 ? "+" : "") + var10 + "ダメージ", var10 > 0 ? NamedTextColor.GOLD : var10 < 0 ? NamedTextColor.RED : NamedTextColor.GRAY));',
'         var2.sendActionBar(Component.text("攻撃抽選 " + (var10 >= 0 ? "+" : "") + var10 + "ダメージ", var10 > 0 ? NamedTextColor.GOLD : var10 < 0 ? NamedTextColor.RED : NamedTextColor.GRAY));\n         var2.sendTitle("", "§6攻撃抽選 §e" + var10 + "ダメージ", 0, 20, 5);',
1,
)

# Vampire: show accumulated drain in chat as well as the action bar.
text = text.replace(
'                     attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", hungerGain) + " / 累計 " + (int)total, NamedTextColor.RED));',
'                     attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", hungerGain) + " / 累計 " + (int)total, NamedTextColor.RED));\n                     attacker.sendMessage("§4吸血蓄積: §c" + (int)total + " §7/ 満腹度回復 +" + String.format(Locale.ROOT, "%.1f", hungerGain));',
1,
)
text = text.replace(
'                     attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", heal) + " / 累計 " + (int)total, NamedTextColor.RED));',
'                     attacker.sendActionBar(Component.text("吸血 +" + String.format(Locale.ROOT, "%.1f", heal) + " / 累計 " + (int)total, NamedTextColor.RED));\n                     attacker.sendMessage("§4吸血蓄積: §c" + (int)total);',
1,
)

# Necromancer summons: target nearest valid living entity, not only players.
text = text.replace(
'''            Player target = this.nearestEnemy(owner, entity.getLocation(), false);
            if (target != null) {
               mob.setTarget(target);
            }
''',
'''            LivingEntity target = this.nearestSummonTarget(owner, entity);
            if (target != null) {
               mob.setTarget(target);
            }
''',
1,
)
marker = '   private Player nearestEnemy(Player owner, Location location, boolean avoidBugMania) {'
if marker not in text:
    raise SystemExit('nearestEnemy marker not found')
helper = '''   private LivingEntity nearestSummonTarget(Player owner, Entity summon) {
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (Entity nearby : summon.getNearbyEntities(32.0, 16.0, 32.0)) {
         if (!(nearby instanceof LivingEntity living) || living.isDead() || living.getUniqueId().equals(owner.getUniqueId())) {
            continue;
         }
         String kind = nearby.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
         if ("summon".equals(kind)) {
            continue;
         }
         if (nearby instanceof Player player && !this.isPlaying(player)) {
            continue;
         }
         double distance = nearby.getLocation().distanceSquared(summon.getLocation());
         if (distance < bestDistance) {
            best = living;
            bestDistance = distance;
         }
      }
      return best;
   }

'''
text = text.replace(marker, helper + marker, 1)

# Crusher: attack-side and defense-side use the same probability table; damage is applied to the direct target and nearby valid targets.
start = text.index('   private void triggerCrusherExplosion(')
end = text.index('   private void capFinalDamage(', start)
crusher = '''   private void triggerCrusherExplosion(Player owner, LivingEntity target, boolean attacking) {
      if (owner == null || target == null || this.crusherExplosionDamage.contains(owner.getUniqueId())) {
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
            }
            living.damage(dealt, owner);
         }
      } finally {
         this.crusherExplosionDamage.remove(owner.getUniqueId());
      }
      owner.getWorld().spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, 0.8, 0.5, 0.8, 0.05);
      owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
   }

'''
text = text[:start] + crusher + text[end:]

path.write_text(text, encoding='utf-8', newline='\n')
