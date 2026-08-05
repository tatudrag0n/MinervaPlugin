from pathlib import Path
import re


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = source.find('{', start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[:start] + replacement + source[index + 1:]
    raise SystemExit(f'unclosed method: {signature}')


# ---------- FfaConfig: one-time migrated balance defaults ----------
config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')

migration_marker = '      boolean migratePermanentKits = config.getInt("ffa.kits.kit-balance-version", 0) < 4;'
if 'migrateGameplayBalance' not in config:
    if migration_marker not in config:
        raise SystemExit('FfaConfig migration marker not found')
    config = config.replace(
        migration_marker,
        migration_marker + '\n      boolean migrateGameplayBalance = config.getInt("ffa.kits.gameplay-balance-version", 0) < 1;',
        1,
    )

apply_marker = '      this.applyFfa17Defaults(config, migrateBalancedArmor || migratePermanentKits, migratePermanentKits, migrateVampireBalance);'
if 'this.applyGameplayBalancePass(config, migrateGameplayBalance);' not in config:
    if apply_marker not in config:
        raise SystemExit('FfaConfig balance application marker not found')
    config = config.replace(
        apply_marker,
        apply_marker + '\n      this.applyGameplayBalancePass(config, migrateGameplayBalance);',
        1,
    )

version_marker = '      if (migrateVampireBalance) {\n         config.set("ffa.kits.vampire-balance-version", 1);\n      }'
if 'ffa.kits.gameplay-balance-version' not in config[config.find(version_marker):config.find(version_marker) + 400]:
    if version_marker not in config:
        raise SystemExit('FfaConfig version marker not found')
    config = config.replace(
        version_marker,
        version_marker + '\n\n      if (migrateGameplayBalance) {\n         config.set("ffa.kits.gameplay-balance-version", 1);\n      }',
        1,
    )

helper = '''   private void applyGameplayBalancePass(FileConfiguration config, boolean force) {
      // Basic kits: reliable equipment and modest, consistent utility.
      this.setArmorDefault(config, "axe", force, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
      this.setArmorDefault(config, "bow", force, "leather_helmet", "chainmail_chestplate", "leather_leggings", "leather_boots");
      this.setArmorDefault(config, "spear", force, "leather_helmet", "chainmail_chestplate", "chainmail_leggings", "leather_boots");
      this.setArmorDefault(config, "crossbow", force, "leather_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots");
      this.setArmorDefault(config, "sword", force, "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots");
      this.setArmorDefault(config, "shield", force, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots");
      this.setArmorDefault(config, "trident", force, "turtle_helmet", "chainmail_chestplate", "chainmail_leggings", "iron_boots");
      this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.bow.bow-enchantments", List.of("power:2", "infinity:1"), force);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.damage-multiplier", 0.85, force);
      this.setIfMissingOrForce(config, "ffa.kits.crossbow.reload-ticks", 65, force);
      this.setIfMissingOrForce(config, "ffa.kits.sword.golden-apple-cooldown-seconds", 90, force);

      // Special kits: higher ceiling, but clearer risks and setup requirements.
      this.setIfMissingOrForce(config, "ffa.kits.sniper.damage-multiplier", 2.5, force);
      this.setIfMissingOrForce(config, "ffa.kits.sniper.reload-ticks", 100, force);
      this.setIfMissingOrForce(config, "ffa.kits.mace.max-final-damage", 10.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.mace.wind-charge-refill-seconds", 8, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.damage-buff-threshold", 60.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.max-damage-buff-tier", 4, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.damage-buff-per-tier-percent", 12.5, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.lifesteal-percent", 30.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.vampire.hunger-steal-percent", 30.0, force);
      this.setIfMissingOrForce(config, "ffa.kits.trapper.trap-cooldown-seconds", 16, force);
      this.setIfMissingOrForce(config, "ffa.kits.bug_mania.max-owned-silverfish", 5, force);
      this.setIfMissingOrForce(config, "ffa.kits.crusher.activation-cooldown-ticks", 30, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.slow", 10, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.harm", 13, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.poison", 16, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.weakness", 13, force);
      this.setIfMissingOrForce(config, "ffa.kits.wizard.potion-cooldowns.blindness", 22, force);
   }

'''
if 'private void applyGameplayBalancePass(' not in config:
    marker = '   private void kit('
    if marker not in config:
        raise SystemExit('FfaConfig helper insertion point not found')
    config = config.replace(marker, helper + marker, 1)

config_path.write_text(config, encoding='utf-8', newline='\n')


# ---------- FfaKit: give ranged basic kits dependable backup weapons ----------
kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
kit = kit_path.read_text(encoding='utf-8')

if '§a狩人の石剣' not in kit:
    bow_arrow = '            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "arrow", Material.ARROW, "§f狩人の矢", this.amount(config, "arrows", 1), Map.of())});'
    if bow_arrow not in kit:
        raise SystemExit('Bow arrow insertion point not found')
    kit = kit.replace(
        bow_arrow,
        bow_arrow + '\n            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", Material.STONE_SWORD, "§a狩人の石剣", 1, Map.of())});',
        1,
    )

if '§dリボルバーの石剣' not in kit:
    crossbow_start = kit.find('         case CROSSBOW:')
    crossbow_end = kit.find('            break;', crossbow_start)
    if crossbow_start < 0 or crossbow_end < 0:
        raise SystemExit('Crossbow case not found')
    insertion = '            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", Material.STONE_SWORD, "§dリボルバーの石剣", 1, Map.of())});\n'
    kit = kit[:crossbow_end] + insertion + kit[crossbow_end:]

kit_path.write_text(kit, encoding='utf-8', newline='\n')


# ---------- FfaManager: enforce the approved risk/reward balance ----------
manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

# Vampire: actual 2x natural regeneration, slower progression, speed only at max tier.
manager = manager.replace('player.setSaturatedRegenRate(2);', 'player.setSaturatedRegenRate(5);')
manager = manager.replace('player.setUnsaturatedRegenRate(20);', 'player.setUnsaturatedRegenRate(40);')
manager = manager.replace('if (tier >= 2) {', 'if (tier >= 4) {')
manager = manager.replace('(tier >= 2 ? 1 : 0)', '(tier >= 4 ? 1 : 0)')

# Grappler remains unarmoured, but gains modest damage resistance.
grappler_jump = '         player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 0, false, false, true));'
if 'PotionEffectType.RESISTANCE, 100, 0' not in manager:
    if grappler_jump not in manager:
        raise SystemExit('Grappler effect marker not found')
    manager = manager.replace(
        grappler_jump,
        grappler_jump + '\n         player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, false, false, true));',
        1,
    )
cleanup_marker = '      player.removePotionEffect(PotionEffectType.INFESTED);'
if 'player.removePotionEffect(PotionEffectType.RESISTANCE);' not in manager:
    manager = manager.replace(cleanup_marker, cleanup_marker + '\n      player.removePotionEffect(PotionEffectType.RESISTANCE);', 1)

# Assassin: a successful fatal dagger creates a strong advantage, not a near-guaranteed kill.
manager = manager.replace('double target = Math.max(0.0, victim.getHealth() - 0.5);', 'double target = Math.max(0.0, victim.getHealth() - 4.0);')
manager = manager.replace('victim.setHealth(Math.max(0.5, victim.getHealth() - target));', 'victim.setHealth(Math.max(4.0, victim.getHealth() - target));')
manager = manager.replace('husk.setHealth(0.5);', 'husk.setHealth(Math.min(husk.getHealth(), 4.0));')

# Trapper, Necromancer, Bug Mania small tuning.
manager = manager.replace('target.damage(6.0, owner);', 'target.damage(5.0, owner);')
manager = manager.replace('this.removeSummonEntity(entity.getUniqueId()), 600L', 'this.removeSummonEntity(entity.getUniqueId()), 400L')
manager = manager.replace('&& ThreadLocalRandom.current().nextInt(100) < 10) {\n                     this.spawnBugSilverfish', '&& ThreadLocalRandom.current().nextInt(100) < 8) {\n                     this.spawnBugSilverfish')
manager = manager.replace('session.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10', 'session.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 8')

# Crusher: the approved 50/24/15/10/1 distribution, reduced radii, and 1.5s shared activation cooldown.
crusher_method = '''   private void triggerCrusherExplosion(Player owner, LivingEntity target, boolean attacking) {
      if (owner == null || target == null || this.crusherExplosionDamage.contains(owner.getUniqueId())) {
         return;
      }

      double damage = this.rollCrusherExplosionFor(owner);
      if (damage <= 0.0) {
         return;
      }

      double radius = this.crusherExplosionRadius(damage);
      Location origin = target.getLocation().clone().add(0.0, 1.0, 0.0);
      this.crusherExplosionDamage.add(owner.getUniqueId());
      try {
         for (Entity nearby : target.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead() || living.getUniqueId().equals(owner.getUniqueId())) {
               continue;
            }
            if (living.getLocation().clone().add(0.0, 1.0, 0.0).distanceSquared(origin) > radius * radius) {
               continue;
            }
            if (living instanceof Player player && !this.isPlaying(player)) {
               continue;
            }
            if (!(living instanceof Player) && !(living instanceof org.bukkit.entity.Husk)) {
               continue;
            }

            double distance = origin.distance(living.getLocation().clone().add(0.0, 1.0, 0.0));
            double dealt = Math.max(1.0, damage * Math.max(0.5, 1.0 - distance / Math.max(1.0, radius)));
            living.setNoDamageTicks(0);
            if (living instanceof Player player) {
               this.recordDamage(owner, player);
               player.damage(dealt, owner);
            } else if (living instanceof org.bukkit.entity.Husk husk) {
               husk.setHealth(Math.max(0.0, husk.getHealth() - dealt));
            }
         }
      } finally {
         this.crusherExplosionDamage.remove(owner.getUniqueId());
      }

      owner.getWorld().spawnParticle(Particle.EXPLOSION, origin, damage >= 16.0 ? 4 : 2, 0.8, 0.5, 0.8, 0.05);
      owner.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, damage >= 16.0 ? 1.5F : 0.9F, damage >= 16.0 ? 0.6F : 1.2F);
   }'''
manager = replace_method(manager, '   private void triggerCrusherExplosion(Player owner, LivingEntity target, boolean attacking)', crusher_method)

roll_helper = '''   private double rollCrusherExplosionFor(Player owner) {
      long currentTick = this.plugin.getServer().getCurrentTick();
      long nextAllowedTick = this.crusherExplosionAttemptTick.getOrDefault(owner.getUniqueId(), 0L);
      if (currentTick < nextAllowedTick) {
         return 0.0;
      }

      double damage = this.rollTrainingCrusherExplosionDamage();
      if (damage > 0.0) {
         long cooldown = Math.max(1L, this.plugin.getConfig().getLong(this.config.kitPath(FfaKit.CRUSHER, "activation-cooldown-ticks"), 30L));
         this.crusherExplosionAttemptTick.put(owner.getUniqueId(), currentTick + cooldown);
      }
      return damage;
   }

   private double crusherExplosionRadius(double damage) {
      if (damage >= 32.0) return 8.0;
      if (damage >= 16.0) return 5.0;
      if (damage >= 8.0) return 3.0;
      return 2.0;
   }

'''
if 'private double rollCrusherExplosionFor(' not in manager:
    marker = '   private double rollTrainingCrusherExplosionDamage() {'
    if marker not in manager:
        raise SystemExit('Crusher roll helper insertion point not found')
    manager = manager.replace(marker, roll_helper + marker, 1)

training_method = '''   private void applyTrainingCrusherExplosion(Player crusher, org.bukkit.entity.Husk husk, Location center) {
      if (crusher == null || center == null || center.getWorld() == null) {
         return;
      }
      double damage = this.rollCrusherExplosionFor(crusher);
      if (damage <= 0.0) {
         return;
      }

      double radius = this.crusherExplosionRadius(damage);
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

         double distance = center.distance(living.getLocation());
         double dealt = Math.max(1.0, damage * Math.max(0.5, 1.0 - distance / Math.max(1.0, radius)));
         living.setNoDamageTicks(0);
         if (living instanceof Player nearbyPlayer) {
            UUID targetId = nearbyPlayer.getUniqueId();
            if (!this.crusherExplosionDamage.add(targetId)) {
               continue;
            }
            try {
               nearbyPlayer.damage(dealt, crusher);
            } finally {
               this.crusherExplosionDamage.remove(targetId);
            }
         } else {
            living.setHealth(Math.max(0.0, living.getHealth() - dealt));
         }
         affected++;
      }

      crusher.sendActionBar(Component.text(
         "クラッシャー爆発 " + (int)damage + "ダメージ / 範囲" + (int)radius + " / " + affected + "体",
         NamedTextColor.GOLD
      ));
   }'''
manager = replace_method(
    manager,
    '   private void applyTrainingCrusherExplosion(Player crusher, org.bukkit.entity.Husk husk, Location center)',
    training_method,
)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
