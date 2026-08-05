from pathlib import Path
import re

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
listener_path = Path('src/main/java/org/server/minerva/FfaListener.java')
kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')

manager = manager_path.read_text(encoding='utf-8')
listener = listener_path.read_text(encoding='utf-8')
kit = kit_path.read_text(encoding='utf-8')

# 1) Sniper: duplicate Bukkit shoot events must not cancel the real projectile.
manager = manager.replace(
'''      if (this.isDuplicateCrossbowShot(player)) {
         event.setCancelled(true);
         return;
      }
''',
'''      if (this.isDuplicateCrossbowShot(player)) {
         return;
      }
''', 1)

# 2) Necromancer summons must never target their owner.
old_target_head = '''      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if (!"bug_silverfish".equals(kind)) {
         return;
      }
'''
new_target_head = '''      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if ("summon".equals(kind)) {
         UUID ownerId = this.summonOwners.get(entity.getUniqueId());
         if (ownerId == null) {
            ownerId = this.parseUuid(entity.getPersistentDataContainer().get(this.entityOwnerKey, PersistentDataType.STRING));
         }
         if (event.getTarget() instanceof Player player && ownerId != null && ownerId.equals(player.getUniqueId())) {
            event.setCancelled(true);
            if (entity instanceof Mob mob) {
               mob.setTarget(null);
            }
         }
         return;
      }
      if (!"bug_silverfish".equals(kind)) {
         return;
      }
'''
if old_target_head not in manager:
    raise SystemExit('entity target marker not found')
manager = manager.replace(old_target_head, new_target_head, 1)

# 3) Crusher explosion: always produce damaging attack-side explosions and include debug husks.
start = manager.index('   private void triggerCrusherExplosion(')
end = manager.index('   private void capFinalDamage(', start)
new_crusher = '''   private void triggerCrusherExplosion(Player owner, LivingEntity target, boolean attacking) {
      if (owner == null || target == null || this.crusherExplosionDamage.contains(owner.getUniqueId())) {
         return;
      }

      double roll = ThreadLocalRandom.current().nextDouble();
      double damage;
      double radius;
      if (roll < 0.125) {
         damage = 32.0;
         radius = 16.0;
      } else if (roll < 0.25) {
         damage = 16.0;
         radius = 8.0;
      } else if (roll < 0.5) {
         damage = 8.0;
         radius = 4.0;
      } else {
         damage = 4.0;
         radius = 2.0;
      }

      Location origin = target.getLocation().clone().add(0.0, 1.0, 0.0);
      this.crusherExplosionDamage.add(owner.getUniqueId());
      try {
         for (Entity nearby : target.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(owner.getUniqueId()) || living.isDead()) {
               continue;
            }
            if (living instanceof Player player && !this.isPlaying(player)) {
               continue;
            }
            if (!(living instanceof Player) && !(living instanceof org.bukkit.entity.Husk)) {
               continue;
            }
            double distance = origin.distance(living.getLocation().clone().add(0.0, 1.0, 0.0));
            double dealt = damage * Math.max(0.25, 1.0 - distance / Math.max(1.0, radius));
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
manager = manager[:start] + new_crusher + manager[end:]

# 4) Mace wind charge: one reusable charge, restored next tick, 2-second material cooldown.
kit = re.sub(
    r'kitItem\(plugin, this, "wind_charge", Material\.WIND_CHARGE, "§f重戦士のウィンドチャージ", this\.amount\(config, "wind-charge", 10\), Map\.of\(\)\)',
    'kitItem(plugin, this, "wind_charge", Material.WIND_CHARGE, "§f重戦士のウィンドチャージ", 1, Map.of())',
    kit,
    count=1,
)
wind_pattern = re.compile(r'''         if \("wind_charge"\.equals\(kind\) && session\.kit == FfaKit\.MACE\) \{.*?         \}\n\n         return false;''', re.S)
wind_replacement = '''         if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            if (player.hasCooldown(Material.WIND_CHARGE)) {
               return true;
            }
            player.setCooldown(Material.WIND_CHARGE, 40);
            ItemStack restoredWindCharge = item.clone();
            restoredWindCharge.setAmount(1);
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
               if (player.isOnline() && this.isPlaying(player)) {
                  FfaManager.FfaSession current = this.sessions.get(player.getUniqueId());
                  if (current != null && current.kit == FfaKit.MACE) {
                     this.removeFfaItems(player, "wind_charge");
                     this.tagOwner(restoredWindCharge, player.getUniqueId());
                     player.getInventory().addItem(restoredWindCharge);
                     player.updateInventory();
                  }
               }
            });
         }

         return false;'''
manager, n = wind_pattern.subn(wind_replacement, manager, count=1)
if n != 1:
    raise SystemExit('wind charge block not found')

# 5) Gambler roll display: chat plus action bar above the hotbar.
manager = manager.replace(
'         var2.sendMessage("§6ギャンブラー防御抽選: §e" + var8 + " ダメージ補正");',
'         var2.sendMessage("§6ギャンブラー防御抽選: §e" + var8 + " ダメージ補正");\n         var2.sendActionBar(Component.text("防御補正 " + (var8 >= 0 ? "-" : "+") + Math.abs(var8), var8 >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));',
1)
manager = manager.replace(
'         var2.sendMessage("§6ギャンブラー攻撃抽選: §e" + var10 + " ダメージ");',
'         var2.sendMessage("§6ギャンブラー攻撃抽選: §e" + var10 + " ダメージ");\n         var2.sendActionBar(Component.text("攻撃抽選 " + (var10 >= 0 ? "+" : "") + var10 + "ダメージ", var10 > 0 ? NamedTextColor.GOLD : var10 < 0 ? NamedTextColor.RED : NamedTextColor.GRAY));',
1)

# 6) Debug target is Husk, not Villager.
listener = listener.replace('onTrainingVillagerDamage', 'onTrainingHuskDamage')
listener = listener.replace('org.bukkit.entity.Villager villager', 'org.bukkit.entity.Husk husk')
listener = listener.replace('adjustTrainingVillagerDamage(event, attacker, villager)', 'adjustTrainingHuskDamage(event, attacker, husk)')
manager = manager.replace('adjustTrainingVillagerDamage', 'adjustTrainingHuskDamage')
manager = manager.replace('org.bukkit.entity.Villager villager', 'org.bukkit.entity.Husk husk')
manager = manager.replace('villager', 'husk')

# 7) Restore all three Assassin starting items.
old_assassin = '''         case ASSASSIN:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "invisibility_potion", Material.POTION, "§7透明化ポーション", 1, Map.of())});
            break;
'''
new_assassin = '''         case ASSASSIN:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "fatal_dagger", Material.GOLDEN_SWORD, "§4致命の短剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "poison_sword", Material.STONE_SWORD, "§2毒の短剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "invisibility_potion", Material.POTION, "§7透明化ポーション", 1, Map.of())});
            break;
'''
if old_assassin not in kit:
    raise SystemExit('assassin item block not found')
kit = kit.replace(old_assassin, new_assassin, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
listener_path.write_text(listener, encoding='utf-8', newline='\n')
kit_path.write_text(kit, encoding='utf-8', newline='\n')
