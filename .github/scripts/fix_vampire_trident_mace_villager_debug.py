from pathlib import Path
import re

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
listener_path = Path('src/main/java/org/server/minerva/FfaListener.java')

manager = manager_path.read_text(encoding='utf-8')
kit = kit_path.read_text(encoding='utf-8')
listener = listener_path.read_text(encoding='utf-8')

# 1) Vampire receives no configured food.
old_food = '''      for (ItemStack food : configuredFoodItems(config, this, plugin)) {
         inventory.addItem(new ItemStack[]{food});
      }
'''
new_food = '''      if (this != VAMPIRE) {
         for (ItemStack food : configuredFoodItems(config, this, plugin)) {
            inventory.addItem(new ItemStack[]{food});
         }
      }
'''
if old_food in kit:
    kit = kit.replace(old_food, new_food, 1)
elif new_food not in kit:
    raise SystemExit('Configured food block not found')

# 2) Vampire attack restores hunger instead of health.
old_vampire = re.compile(r'''                  if \(session\.kit == FfaKit\.VAMPIRE && event\.getFinalDamage\(\) > 0\.0\) \{.*?attacker\.sendActionBar\(Component\.text\("吸血 \+" \+ String\.format\(Locale\.ROOT, "%.1f", heal\) \+ " / 累計 " \+ \(int\)total, NamedTextColor\.RED\)\);\n                  \}''', re.S)
new_vampire = '''                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double dealt = Math.max(0.0, event.getFinalDamage());
                     double hungerGain = dealt
                        * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "hunger-steal-percent"), 50.0))
                        / 100.0;
                     int before = attacker.getFoodLevel();
                     int restored = Math.max(1, (int)Math.ceil(hungerGain));
                     attacker.setFoodLevel(Math.min(20, before + restored));
                     attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
                     attacker.sendActionBar(Component.text("吸血 満腹度 +" + (attacker.getFoodLevel() - before), NamedTextColor.RED));
                  }'''
if old_vampire.search(manager):
    manager = old_vampire.sub(new_vampire, manager, count=1)
elif 'hunger-steal-percent' not in manager:
    raise SystemExit('Vampire lifesteal block not found')

# 3) Triple natural regeneration rate for vampire and restore defaults on cleanup.
old_effect = '''      if (kit == FfaKit.VAMPIRE) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
      }
'''
new_effect = '''      if (kit == FfaKit.VAMPIRE) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false, true));
         player.setSaturatedRegenRate(3);
         player.setUnsaturatedRegenRate(27);
      }
'''
if old_effect in manager:
    manager = manager.replace(old_effect, new_effect, 1)
elif 'player.setSaturatedRegenRate(3);' not in manager:
    raise SystemExit('Vampire effect block not found')

cleanup_marker = '''      player.setCooldown(Material.SPLASH_POTION, 0);
'''
cleanup_new = '''      player.setCooldown(Material.SPLASH_POTION, 0);
      player.setSaturatedRegenRate(10);
      player.setUnsaturatedRegenRate(80);
'''
if cleanup_marker in manager and 'player.setUnsaturatedRegenRate(80);' not in manager:
    manager = manager.replace(cleanup_marker, cleanup_new, 1)

# Sun damage must exceed accelerated regeneration.
manager = manager.replace('this.config.kitPath(FfaKit.VAMPIRE, "sun-damage"), 1.0)', 'this.config.kitPath(FfaKit.VAMPIRE, "sun-damage"), 14.0)')

# 4) Wind charge: infinite with a 2-second material cooldown.
old_wind = '''         if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            this.scheduleWindChargeRefillCheck(player);
         }
'''
new_wind = '''         if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
            player.setCooldown(Material.WIND_CHARGE, 40);
            ItemStack restoredWindCharge = item.clone();
            restoredWindCharge.setAmount(1);
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && this.isPlaying(player)) {
                  FfaManager.FfaSession current = this.sessions.get(player.getUniqueId());
                  if (current != null && current.kit == FfaKit.MACE && this.countFfaItem(player, "wind_charge") <= 0) {
                     this.tagOwner(restoredWindCharge, player.getUniqueId());
                     player.getInventory().addItem(new ItemStack[]{restoredWindCharge});
                     player.updateInventory();
                  }
               }
            }, 2L);
         }
'''
if old_wind in manager:
    manager = manager.replace(old_wind, new_wind, 1)
elif 'player.setCooldown(Material.WIND_CHARGE, 40);' not in manager:
    raise SystemExit('Wind charge use block not found')

# Initial amount is one reusable wind charge.
kit = re.sub(r'''kitItem\(plugin, this, "wind_charge", Material\.WIND_CHARGE, "§f重戦士のウィンドチャージ", this\.amount\(config, "wind-charge", 10\), Map\.of\(\)\)''',
             'kitItem(plugin, this, "wind_charge", Material.WIND_CHARGE, "§f重戦士のウィンドチャージ", 1, Map.of())', kit, count=1)

# 5) Reject picked-up/foreign tridents before they become throwable kit weapons.
anchor = '''   boolean handleArrowPickup(PlayerPickupArrowEvent event) {
'''
if 'boolean isSafeFfaTridentPickup(PlayerPickupArrowEvent event)' not in manager:
    helper = '''   boolean isSafeFfaTridentPickup(PlayerPickupArrowEvent event) {
      if (!(event.getArrow() instanceof Trident trident)) {
         return true;
      }
      String kind = trident.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING);
      UUID owner = this.parseUuid(trident.getPersistentDataContainer().get(this.projectileOwnerKey, PersistentDataType.STRING));
      Player player = event.getPlayer();
      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      return "trident".equals(kind) && owner != null && owner.equals(player.getUniqueId()) && session != null && session.kit == FfaKit.TRIDENT;
   }

'''
    if anchor not in manager:
        raise SystemExit('Arrow pickup handler not found')
    manager = manager.replace(anchor, helper + anchor, 1)

pickup_start = '''   boolean handleArrowPickup(PlayerPickupArrowEvent event) {
      AbstractArrow arrow = event.getArrow();
'''
pickup_new = '''   boolean handleArrowPickup(PlayerPickupArrowEvent event) {
      AbstractArrow arrow = event.getArrow();
      if (arrow instanceof Trident && !this.isSafeFfaTridentPickup(event)) {
         event.setCancelled(true);
         arrow.remove();
         return true;
      }
'''
if pickup_start in manager:
    manager = manager.replace(pickup_start, pickup_new, 1)

# 6) Ordinary villagers are debug targets for attack-time kit effects.
if 'void adjustTrainingVillagerDamage(' not in manager:
    method = r'''
   void adjustTrainingVillagerDamage(EntityDamageByEntityEvent event, Player attacker, org.bukkit.entity.Villager villager) {
      if (attacker == null || villager == null || !this.isPlaying(attacker) || event.isCancelled()) {
         return;
      }

      FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());
      if (session == null) {
         return;
      }

      Object damager = event.getDamager();
      if (damager instanceof Projectile projectile) {
         String kind = projectile.getPersistentDataContainer().get(this.projectileKindKey, PersistentDataType.STRING);
         if ("sniper".equals(kind)) {
            event.setDamage(event.getDamage() * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.SNIPER, "damage-multiplier"), 3.0)));
         } else if ("revolver".equals(kind)) {
            event.setDamage(event.getDamage() * Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.CROSSBOW, "damage-multiplier"), 0.7)));
         } else if ("event_one_shot_arrow".equals(kind)) {
            event.setDamage(Math.max(event.getDamage(), villager.getHealth() + 2.0));
         }
      }

      ItemStack mainHand = attacker.getInventory().getItemInMainHand();
      String itemKind = this.itemKind(mainHand);
      if (session.kit == FfaKit.GAMBLER && this.isFfaItem(mainHand) && "weapon".equals(itemKind)) {
         double min = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.GAMBLER, "min-damage-multiplier"), -10.0);
         double max = this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.GAMBLER, "max-damage-multiplier"), 15.0);
         int steps = (int)Math.round((Math.max(min, max) - Math.min(min, max)) / 0.5);
         double multiplier = Math.min(min, max) + ThreadLocalRandom.current().nextInt(steps + 1) * 0.5;
         if (multiplier < 0.0) {
            event.setCancelled(true);
            villager.setHealth(Math.min(villager.getAttribute(Attribute.MAX_HEALTH).getValue(), villager.getHealth() + Math.abs(multiplier)));
         } else {
            event.setDamage(event.getDamage() * multiplier);
         }
      }

      if (session.kit == FfaKit.ASSASSIN && this.isFfaItem(mainHand)) {
         if ("fatal_sword".equals(itemKind) || "fatal_dagger".equals(itemKind)) {
            event.setDamage(0.0);
            villager.setHealth(0.5);
         } else if ("poison_sword".equals(itemKind)) {
            villager.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1, false, false, true));
         }
      }

      if (session.kit == FfaKit.BUG_MANIA && this.isFfaItem(mainHand) && "bug_sword".equals(itemKind)
         && ThreadLocalRandom.current().nextInt(100) < 10) {
         this.spawnBugSilverfish(attacker, villager.getLocation());
      }

      if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
         int before = attacker.getFoodLevel();
         int restored = Math.max(1, (int)Math.ceil(event.getFinalDamage() * 0.5));
         attacker.setFoodLevel(Math.min(20, before + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
      }

      if (session.kit == FfaKit.MACE && this.isFfaItem(mainHand) && "mace".equals(itemKind)) {
         this.capFinalDamage(event, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.MACE, "max-final-damage"), 12.0));
      }

      if (session.kit == FfaKit.CRUSHER) {
         villager.getWorld().spawnParticle(Particle.EXPLOSION, villager.getLocation().add(0.0, 1.0, 0.0), 2);
      }
   }
'''
    pos = manager.rfind('\n}')
    if pos < 0:
        raise SystemExit('Manager class end not found')
    manager = manager[:pos] + method + manager[pos:]

if 'public void onTrainingVillagerDamage(' not in listener:
    method = r'''

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onTrainingVillagerDamage(EntityDamageByEntityEvent event) {
      if (!(event.getEntity() instanceof org.bukkit.entity.Villager villager)) {
         return;
      }

      Player attacker = null;
      if (event.getDamager() instanceof Player player) {
         attacker = player;
      } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
         attacker = player;
      }

      if (attacker != null && this.ffa.isPlaying(attacker)) {
         this.ffa.adjustTrainingVillagerDamage(event, attacker, villager);
      }
   }
'''
    pos = listener.rfind('\n}')
    if pos < 0:
        raise SystemExit('Listener class end not found')
    listener = listener[:pos] + method + listener[pos:]

kit_path.write_text(kit, encoding='utf-8', newline='\n')
manager_path.write_text(manager, encoding='utf-8', newline='\n')
listener_path.write_text(listener, encoding='utf-8', newline='\n')
