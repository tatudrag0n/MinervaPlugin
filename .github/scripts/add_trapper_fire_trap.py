from pathlib import Path

# Triggered after the workflow was registered.

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


# Add the fourth Trapper item.
kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
kit = kit_path.read_text(encoding='utf-8')
poison_item = '            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_poison", Material.SPRUCE_PRESSURE_PLATE, "§2毒になる感圧板", 1, Map.of())});'
fire_item = '            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "trap_fire", Material.CRIMSON_PRESSURE_PLATE, "§6火炎トラップ", 1, Map.of())});'
if fire_item not in kit:
    if poison_item not in kit:
        raise SystemExit('Trapper poison item insertion point not found')
    kit = kit.replace(poison_item, poison_item + '\n' + fire_item, 1)
kit_path.write_text(kit, encoding='utf-8', newline='\n')


# Add configurable fire-trap defaults without overwriting existing server tuning.
config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')
cooldown_line = '      this.setIfMissing(config, "ffa.kits.trapper.trap-cooldown-seconds", 20);'
fire_defaults = '''      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-initial-damage", 2.0);
      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-burn-seconds", 6);
      this.setIfMissing(config, "ffa.kits.trapper.fire-trap-radius", 2.5);'''
if 'ffa.kits.trapper.fire-trap-initial-damage' not in config:
    if cooldown_line not in config:
        raise SystemExit('Trapper config insertion point not found')
    config = config.replace(cooldown_line, cooldown_line + '\n' + fire_defaults, 1)
config_path.write_text(config, encoding='utf-8', newline='\n')


manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

trap_material = '''   private Material trapMaterial(String type) {
      return switch (type) {
         case "explosion" -> Material.STONE_PRESSURE_PLATE;
         case "web" -> Material.OAK_PRESSURE_PLATE;
         case "poison" -> Material.SPRUCE_PRESSURE_PLATE;
         case "fire" -> Material.CRIMSON_PRESSURE_PLATE;
         default -> null;
      };
   }'''
manager = replace_method(manager, '   private Material trapMaterial(String type)', trap_material)

trigger_trap = '''   private void triggerTrap(FfaManager.TrapState trap, Player target) {
      Player owner = this.plugin.getServer().getPlayer(trap.owner());
      this.restoreTrap(trap);
      if (owner != null && this.isPlaying(owner)) {
         this.recordDamage(owner, target);
         World world = trap.location().getWorld();
         if ("explosion".equals(trap.type())) {
            target.damage(5.0, owner);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 128, false, false, true));
            if (world != null) {
               world.spawnParticle(Particle.EXPLOSION, trap.location().clone().add(0.5, 0.5, 0.5), 1);
               world.playSound(trap.location(), Sound.ENTITY_GENERIC_EXPLODE, 0.9F, 1.1F);
            }
         } else if ("poison".equals(trap.type())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, false, true));
            if (world != null) {
               AreaEffectCloud cloud = (AreaEffectCloud)world.spawn(trap.location().clone().add(0.5, 0.2, 0.5), AreaEffectCloud.class);
               cloud.setRadius(2.0F);
               cloud.setDuration(100);
               cloud.setRadiusOnUse(0.0F);
               cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 1), true);
               world.playSound(trap.location(), Sound.ENTITY_SPIDER_AMBIENT, 0.8F, 1.0F);
            }
         } else if ("fire".equals(trap.type())) {
            this.triggerFireTrap(owner, trap.location().clone().add(0.5, 0.2, 0.5));
         } else if ("web".equals(trap.type())) {
            this.placeTemporaryWebs(target.getLocation());
         }
      }
   }'''
manager = replace_method(manager, '   private void triggerTrap(FfaManager.TrapState trap, Player target)', trigger_trap)

fire_helper = '''   private void triggerFireTrap(Player owner, Location center) {
      World world = center.getWorld();
      if (world == null) {
         return;
      }

      double radius = Math.max(0.5, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.TRAPPER, "fire-trap-radius"), 2.5));
      double initialDamage = Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.TRAPPER, "fire-trap-initial-damage"), 2.0));
      int burnTicks = Math.max(20, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.TRAPPER, "fire-trap-burn-seconds"), 6) * 20);
      int affected = 0;

      for (Player player : world.getPlayers()) {
         if (!this.isPlaying(player) || player.getUniqueId().equals(owner.getUniqueId())) {
            continue;
         }
         if (player.getLocation().distanceSquared(center) > radius * radius) {
            continue;
         }

         this.recordDamage(owner, player);
         player.setNoDamageTicks(0);
         if (initialDamage > 0.0) {
            player.damage(initialDamage, owner);
         }
         player.setFireTicks(Math.max(player.getFireTicks(), burnTicks));
         affected++;
      }

      world.spawnParticle(Particle.FLAME, center.clone().add(0.0, 0.6, 0.0), 45, radius * 0.45, 0.55, radius * 0.45, 0.02);
      world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.85F);
      owner.sendActionBar(Component.text("火炎トラップ発動 / " + affected + "人を炎上", NamedTextColor.GOLD));
   }

'''
if 'private void triggerFireTrap(' not in manager:
    marker = '   private void placeTemporaryWebs(Location center) {'
    if marker not in manager:
        raise SystemExit('Fire trap helper insertion point not found')
    manager = manager.replace(marker, fire_helper + marker, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
