# Trigger workflow after workflow file creation.
from pathlib import Path

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

sig = '   void adjustTrainingHuskDamage(EntityDamageByEntityEvent event, Player attacker, org.bukkit.entity.Husk husk)'
start, end = method_bounds(manager, sig)
old_method = manager[start:end]
needle = '      FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());\n'
if needle not in old_method:
    raise SystemExit('training husk session marker not found')
gambler_block = '''      FfaManager.FfaSession session = this.sessions.get(attacker.getUniqueId());
      if (session != null && session.kit == FfaKit.GAMBLER) {
         int min = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "min-random-damage"), -10);
         int max = this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.GAMBLER, "max-random-damage"), 20);
         int roll = ThreadLocalRandom.current().nextInt(Math.min(min, max), Math.max(min, max) + 1);
         String shown = (roll >= 0 ? "+" : "") + roll;
         attacker.sendMessage("§6ギャンブラー攻撃抽選: §e" + shown + " ダメージ");
         attacker.sendActionBar(Component.text("攻撃抽選 " + shown + "ダメージ", roll > 0 ? NamedTextColor.GOLD : roll < 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
         attacker.sendTitle("", "§6攻撃抽選 §e" + shown + "ダメージ", 0, 30, 10);
         if (roll < 0) {
            event.setCancelled(true);
            husk.setHealth(Math.min(husk.getMaxHealth(), husk.getHealth() + Math.abs(roll)));
         } else if (roll == 0) {
            event.setCancelled(true);
         } else {
            event.setDamage(roll);
         }
         return;
      }
'''
old_method = old_method.replace(needle, gambler_block, 1)
manager = manager[:start] + old_method + manager[end:]

manual_method = '''
   boolean handleManualKitProjectileUse(PlayerInteractEvent event) {
      if (!event.getAction().isRightClick() || !this.isPlaying(event.getPlayer())) {
         return false;
      }

      Player player = event.getPlayer();
      ItemStack item = event.getItem();
      if (!this.isFfaItem(item)) {
         return false;
      }

      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      if (session == null) {
         return false;
      }

      String kind = this.itemKind(item);
      if ("sniper".equals(kind) && session.kit == FfaKit.SNIPER) {
         UUID id = player.getUniqueId();
         if (this.sniperReloadTasks.containsKey(id)) {
            player.sendActionBar(Component.text("スナイパーをリロード中", NamedTextColor.YELLOW));
            return true;
         }

         int ammo = this.sniperAmmo.getOrDefault(id, 1);
         if (ammo <= 0) {
            this.startCrossbowReload(player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, 1, "スナイパー");
            return true;
         }

         long now = System.currentTimeMillis();
         long readyAt = this.sniperShotCooldownUntil.getOrDefault(id, 0L);
         if (now < readyAt) {
            return true;
         }
         this.sniperShotCooldownUntil.put(id, now + 1000L);

         org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
         arrow.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(4.0));
         arrow.setCritical(true);
         arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
         arrow.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, "sniper");
         arrow.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, id.toString());
         this.sniperAmmo.put(id, ammo - 1);
         player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.7F);
         player.sendActionBar(Component.text("残弾 0 — 右クリックでリロード", NamedTextColor.RED));
         return true;
      }

      if ("wind_charge".equals(kind) && session.kit == FfaKit.MACE) {
         if (player.hasCooldown(Material.WIND_CHARGE)) {
            return true;
         }
         player.setCooldown(Material.WIND_CHARGE, 40);
         org.bukkit.entity.WindCharge charge = player.launchProjectile(org.bukkit.entity.WindCharge.class);
         charge.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.5));
         charge.getPersistentDataContainer().set(this.projectileKindKey, PersistentDataType.STRING, "mace_wind_charge");
         charge.getPersistentDataContainer().set(this.projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         return true;
      }

      return false;
   }
'''
insert_before = '   boolean handleEmptyCrossbowInteract(PlayerInteractEvent'
idx = manager.find(insert_before)
if idx < 0:
    raise SystemExit('handleEmptyCrossbowInteract marker not found')
if 'boolean handleManualKitProjectileUse(' not in manager:
    manager = manager[:idx] + manual_method + '\n' + manager[idx:]

listener_old = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent var1) {
      if (!this.ffa.handleEmptyCrossbowInteract(var1)) {
         if (this.ffa.handlePotionUse(var1)) {
            var1.setCancelled(true);
         }
      }
   }
'''
listener_new = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent var1) {
      if (this.ffa.handleManualKitProjectileUse(var1)) {
         var1.setCancelled(true);
         return;
      }
      if (!this.ffa.handleEmptyCrossbowInteract(var1)) {
         if (this.ffa.handlePotionUse(var1)) {
            var1.setCancelled(true);
         }
      }
   }
'''
if listener_old not in listener:
    raise SystemExit('listener interact method not found')
listener = listener.replace(listener_old, listener_new, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')
listener_path.write_text(listener, encoding='utf-8', newline='\n')
