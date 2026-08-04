from pathlib import Path


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Method signature not found: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise SystemExit(f"Opening brace not found: {signature}")
    depth = 0
    end = None
    for i in range(brace, len(source)):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit(f"Closing brace not found: {signature}")
    return source[:start] + replacement + source[end:]


manager_path = Path("src/main/java/org/server/minerva/FfaManager.java")
manager = manager_path.read_text(encoding="utf-8")

replacement = '''   void handleEntityTarget(EntityTargetLivingEntityEvent event) {
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

      event.setCancelled(true);
      if (entity instanceof org.bukkit.entity.Mob mob) {
         mob.setTarget(null);
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!entity.isValid() || entity.isDead()) {
               return;
            }

            org.bukkit.entity.LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Entity nearby : entity.getNearbyEntities(16.0, 8.0, 16.0)) {
               if (!(nearby instanceof org.bukkit.entity.LivingEntity living) || nearby.isDead()) {
                  continue;
               }
               if (nearby instanceof Player player) {
                  if (!this.isPlaying(player) || ownerId != null && ownerId.equals(player.getUniqueId())) {
                     continue;
                  }
               }
               String nearbyKind = nearby.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
               if ("bug_silverfish".equals(nearbyKind)) {
                  continue;
               }

               double distance = nearby.getLocation().distanceSquared(entity.getLocation());
               if (distance < nearestDistance) {
                  nearest = living;
                  nearestDistance = distance;
               }
            }

            if (nearest != null && mob.isValid() && !mob.isDead()) {
               mob.setTarget(nearest);
            }
         });
      }
   }'''

manager = replace_method(
    manager,
    "   void handleEntityTarget(EntityTargetLivingEntityEvent event)",
    replacement,
)
manager_path.write_text(manager, encoding="utf-8", newline="\n")
