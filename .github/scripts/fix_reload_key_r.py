from pathlib import Path

listener_path = Path("src/main/java/org/server/minerva/FfaListener.java")
manager_path = Path("src/main/java/org/server/minerva/FfaManager.java")

listener = listener_path.read_text(encoding="utf-8")
manager = manager_path.read_text(encoding="utf-8")

# Add swap-hand event import. Minecraft servers cannot receive arbitrary keyboard keys,
# so the reload action uses the vanilla swap-hand packet. Players bind that action to R.
import_line = "import org.bukkit.event.player.PlayerSwapHandItemsEvent;\n"
if import_line not in listener:
    anchor = "import org.bukkit.event.player.PlayerRespawnEvent;\n"
    if anchor not in listener:
        raise SystemExit("PlayerRespawnEvent import anchor was not found")
    listener = listener.replace(anchor, anchor + import_line, 1)

handler = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onReloadKey(PlayerSwapHandItemsEvent event) {
      if (this.ffa.handleReloadKey(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

'''
if "public void onReloadKey(PlayerSwapHandItemsEvent event)" not in listener:
    anchor = "   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n   public void onInteract(PlayerInteractEvent var1) {"
    if anchor not in listener:
        raise SystemExit("onInteract handler anchor was not found")
    listener = listener.replace(anchor, handler + anchor, 1)

# Replace empty-ammo behavior in the firing handler: never auto-reload.
old = '''            if (ammo <= 0) {
               event.setCancelled(true);
               if (expectedKit == FfaKit.SNIPER) {
                  player.sendActionBar(Component.text("右クリックでリロード", NamedTextColor.YELLOW));
               } else {
                  this.startCrossbowReload(player, expectedKit, ammoMap, reloadTasks, capacity, label);
               }
            } else {'''
new = '''            if (ammo <= 0) {
               event.setCancelled(true);
               player.sendActionBar(Component.text("Rでリロード", NamedTextColor.YELLOW));
            } else {'''
if old in manager:
    manager = manager.replace(old, new, 1)
elif new not in manager:
    raise SystemExit("Empty-ammo auto-reload block was not found")

# Change post-shot sniper prompt.
manager = manager.replace(
    'player.sendActionBar(Component.text("右クリックでリロード", NamedTextColor.YELLOW));',
    'player.sendActionBar(Component.text("Rでリロード", NamedTextColor.YELLOW));'
)

# Right click no longer starts reload. It only reports the R-key instruction when empty.
start = manager.find("   boolean handleEmptyCrossbowInteract(PlayerInteractEvent")
end = manager.find("\n   void handleTrapStep", start)
if start < 0 or end < 0:
    raise SystemExit("handleEmptyCrossbowInteract method bounds were not found")
replacement = '''   boolean handleEmptyCrossbowInteract(PlayerInteractEvent event) {
      if (!event.getAction().isRightClick()) {
         return false;
      }

      Player player = event.getPlayer();
      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      if (session == null || (session.kit != FfaKit.CROSSBOW && session.kit != FfaKit.SNIPER)) {
         return false;
      }

      ItemStack item = event.getItem();
      if (!this.isFfaItem(item)) {
         return false;
      }

      String kind = this.itemKind(item);
      if (session.kit == FfaKit.CROSSBOW && "revolver".equals(kind)) {
         int capacity = Math.max(1, session.kit.revolverCapacity(this.config));
         if (this.revolverAmmo.getOrDefault(player.getUniqueId(), capacity) <= 0) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Rでリロード", NamedTextColor.YELLOW));
            return true;
         }
      }

      if (session.kit == FfaKit.SNIPER && "sniper".equals(kind)) {
         int capacity = Math.max(1, session.kit.sniperCapacity(this.config));
         if (this.sniperAmmo.getOrDefault(player.getUniqueId(), capacity) <= 0) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Rでリロード", NamedTextColor.YELLOW));
            return true;
         }
      }

      return false;
   }

   boolean handleReloadKey(Player player) {
      if (player == null || !this.isPlaying(player)) {
         return false;
      }

      FfaManager.FfaSession session = this.sessions.get(player.getUniqueId());
      if (session == null) {
         return false;
      }

      ItemStack held = player.getInventory().getItemInMainHand();
      if (!this.isFfaItem(held)) {
         return false;
      }

      UUID uuid = player.getUniqueId();
      if (session.kit == FfaKit.CROSSBOW && "revolver".equals(this.itemKind(held))) {
         int capacity = Math.max(1, session.kit.revolverCapacity(this.config));
         if (this.revolverReloadTasks.containsKey(uuid)) {
            player.sendActionBar(Component.text("リロード中", NamedTextColor.RED));
         } else if (this.revolverAmmo.getOrDefault(uuid, capacity) >= capacity) {
            player.sendActionBar(Component.text("リボルバーは装填済み", NamedTextColor.GRAY));
         } else {
            this.startCrossbowReload(player, FfaKit.CROSSBOW, this.revolverAmmo, this.revolverReloadTasks, capacity, "リボルバー");
         }
         return true;
      }

      if (session.kit == FfaKit.SNIPER && "sniper".equals(this.itemKind(held))) {
         int capacity = 1;
         if (this.sniperReloadTasks.containsKey(uuid)) {
            player.sendActionBar(Component.text("リロード中", NamedTextColor.RED));
         } else if (this.sniperAmmo.getOrDefault(uuid, capacity) >= capacity) {
            player.sendActionBar(Component.text("スナイパーは装填済み", NamedTextColor.GRAY));
         } else {
            this.startCrossbowReload(player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, capacity, "スナイパー");
         }
         return true;
      }

      return false;
   }
'''
manager = manager[:start] + replacement + manager[end:]

# The general right-click item handler also had a sniper reload branch. Remove it.
old_sniper_branch = '''      } else if ("sniper".equals(kind) && session.kit == FfaKit.SNIPER) {
         int capacity = Math.max(1, session.kit.sniperCapacity(this.config));
         if (!this.sniperReloadTasks.containsKey(player.getUniqueId())) {
            this.startCrossbowReload(player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, capacity, "スナイパー");
         }

         return true;'''
new_sniper_branch = '''      } else if ("sniper".equals(kind) && session.kit == FfaKit.SNIPER) {
         if (this.sniperAmmo.getOrDefault(player.getUniqueId(), 1) <= 0) {
            player.sendActionBar(Component.text("Rでリロード", NamedTextColor.YELLOW));
         }

         return true;'''
if old_sniper_branch in manager:
    manager = manager.replace(old_sniper_branch, new_sniper_branch, 1)
elif new_sniper_branch not in manager:
    raise SystemExit("Sniper right-click reload branch was not found")

listener_path.write_text(listener, encoding="utf-8", newline="\n")
manager_path.write_text(manager, encoding="utf-8", newline="\n")
