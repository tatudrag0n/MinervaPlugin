from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise SystemExit(f"{label} block was not found")

manager_path = Path("src/main/java/org/server/minerva/FfaManager.java")
manager = manager_path.read_text(encoding="utf-8")

manager = replace_once(
    manager,
    '''      if (this.isDuplicateCrossbowShot(player)) {
         event.setCancelled(true);
         return;
      }''',
    '''      if (this.isDuplicateCrossbowShot(player)) {
         return;
      }''',
    "duplicate crossbow shot",
)

manager = replace_once(
    manager,
    '''                  FfaManager.FfaSession victimSession = this.sessions.get(victim.getUniqueId());
                  if (victimSession != null && victimSession.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10) {
                     attacker.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 1200, 0, false, false, true));
                     attacker.sendActionBar(Component.text("虫食いを受けました", NamedTextColor.DARK_GREEN));
                  }''',
    '''                  FfaManager.FfaSession victimSession = this.sessions.get(victim.getUniqueId());
                  if (victimSession != null && victimSession.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10) {
                     this.spawnBugSilverfish(victim, attacker.getLocation());
                     attacker.sendActionBar(Component.text("虫食いを受けました", NamedTextColor.DARK_GREEN));
                  }''',
    "bug mania infested retaliation",
)

manager = replace_once(
    manager,
    '''      if (kit == FfaKit.BUG_MANIA) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 100, 0, false, false, true));
      }

''',
    '''      if (kit == FfaKit.BUG_MANIA) {
         player.removePotionEffect(PotionEffectType.INFESTED);
      }

''',
    "bug mania passive infested effect",
)

old_target = '''   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
      Entity entity = event.getEntity();
      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if (!"bug_silverfish".equals(kind)) {
         return;
      }

      UUID ownerId = this.bugOwnerOf(entity);
      org.bukkit.entity.LivingEntity target = event.getTarget();
      boolean invalidTarget = target == null;
      if (target instanceof Player player) {
         invalidTarget = ownerId != null && ownerId.equals(player.getUniqueId()) || !this.isPlaying(player);
      }

      if (!invalidTarget) {
         event.setCancelled(false);
         return;
      }
'''
new_target = '''   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
      Entity entity = event.getEntity();
      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      UUID ownerId;
      if ("summon".equals(kind)) {
         ownerId = this.summonOwners.get(entity.getUniqueId());
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

      ownerId = this.bugOwnerOf(entity);
      org.bukkit.entity.LivingEntity target = event.getTarget();
      boolean invalidTarget = target == null;
      if (target instanceof Player player) {
         invalidTarget = ownerId != null && ownerId.equals(player.getUniqueId()) || !this.isPlaying(player) || this.isBugMania(player);
      }

      if (!invalidTarget) {
         event.setCancelled(false);
         return;
      }
'''
manager = replace_once(manager, old_target, new_target, "entity target protection")

manager_path.write_text(manager, encoding="utf-8", newline="\n")

kit_path = Path("src/main/java/org/server/minerva/FfaKit.java")
kit = kit_path.read_text(encoding="utf-8")
kit = replace_once(
    kit,
    '''         case ASSASSIN:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "invisibility_potion", Material.POTION, "§7透明化ポーション", 1, Map.of())});
            break;''',
    '''         case ASSASSIN:
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "fatal_sword", Material.GOLDEN_SWORD, "§4致命の剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "poison_sword", Material.STONE_SWORD, "§2毒の剣", 1, Map.of())});
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "invisibility_potion", Material.POTION, "§7透明化ポーション", 1, Map.of())});
            break;''',
    "assassin starter items",
)
kit_path.write_text(kit, encoding="utf-8", newline="\n")
