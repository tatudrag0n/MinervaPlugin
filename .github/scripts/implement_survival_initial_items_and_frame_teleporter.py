from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)

root = Path(".")

# Utility item: keep the teleporter as an Ender Eye, but describe the new frame-based use.
p = root / "src/main/java/org/server/minerva/UtilityItemsFeature.java"
s = p.read_text(encoding="utf-8")
s = replace_once(
    s,
    'this.createMinervaItem(Material.ENDER_EYE, "teleporter", ChatColor.LIGHT_PURPLE + "テレポーター", List.of(ChatColor.GRAY + "右クリック: サーバーショートカット"))',
    'this.createMinervaItem(Material.ENDER_EYE, "teleporter", ChatColor.LIGHT_PURPLE + "テレポーター", List.of(ChatColor.GRAY + "対応するエンドポータルフレームに使用して移動", ChatColor.DARK_GRAY + "投げることはできません"))',
    "teleporter lore",
)
p.write_text(s, encoding="utf-8")

# Minerva: stop opening the old teleporter GUI; the ServerPortalFeature now handles frames.
p = root / "src/main/java/org/server/minerva/Minerva.java"
s = p.read_text(encoding="utf-8")
s = replace_once(
    s,
    '"§dテレポーター§7: 右クリックでサーバー移動UIを開けます。",',
    '"§dテレポーター§7: 対応するエンドポータルフレームに使用すると、そのワールドへ移動できます。",',
    "tutorial teleporter text",
)
s = replace_once(
    s,
    '''   private void giveInitialItems(Player player) {\n      this.utilityItemsFeature.giveInitialItems(player);\n   }\n''',
    '''   private void giveInitialItems(Player player) {\n      this.utilityItemsFeature.giveInitialItems(player);\n   }\n\n   void giveInitialItemsAfterInventoryRestore(Player player) {\n      this.utilityItemsFeature.giveInitialItems(player);\n   }\n''',
    "inventory restore helper",
)
s = replace_once(
    s,
    '''                  if (this.isMinervaItem(item, "teleporter") && event.getAction().isRightClick()) {\n                     this.openTeleportUi(player);\n                     event.setCancelled(true);\n                  }\n''',
    '''                  if (this.isMinervaItem(item, "teleporter") && event.getAction().isRightClick()) {\n                     // Teleporter use is handled by ServerPortalFeature. Cancelling here also\n                     // prevents the custom Ender Eye from being thrown in the air.\n                     event.setCancelled(true);\n                  }\n''',
    "disable old teleporter UI",
)
p.write_text(s, encoding="utf-8")

# Inventory groups: the survival inventory can replace the inventory after Minerva's join handler.
# Re-ensure the fixed initial items after the survival inventory has been restored/created.
p = root / "src/main/java/org/server/minerva/InventoryGroupFeature.java"
s = p.read_text(encoding="utf-8")
s = replace_once(
    s,
    '''               if (this.hasSave(var1.getUniqueId(), var2)) {\n                  this.load(var1, var2);\n               } else if (var2 == InventoryGroupFeature.Group.SURVIVAL) {\n                  this.clearPlayerState(var1);\n               }\n''',
    '''               if (this.hasSave(var1.getUniqueId(), var2)) {\n                  this.load(var1, var2);\n               } else if (var2 == InventoryGroupFeature.Group.SURVIVAL) {\n                  this.clearPlayerState(var1);\n               }\n\n               this.ensureInitialItems(var1, var2);\n''',
    "join survival starter items",
)
s = replace_once(
    s,
    '''                     if (this.hasSave(var2.getUniqueId(), var4)) {\n                        this.load(var2, var4);\n                     } else {\n                        this.clearPlayerState(var2);\n                     }\n''',
    '''                     if (this.hasSave(var2.getUniqueId(), var4)) {\n                        this.load(var2, var4);\n                     } else {\n                        this.clearPlayerState(var2);\n                     }\n\n                     this.ensureInitialItems(var2, var4);\n''',
    "world change survival starter items",
)
s = replace_once(
    s,
    '''   private InventoryGroupFeature.Group groupOf(World var1) {\n''',
    '''   private void ensureInitialItems(Player player, InventoryGroupFeature.Group group) {\n      if (group == InventoryGroupFeature.Group.SURVIVAL && this.plugin instanceof Minerva minerva) {\n         minerva.giveInitialItemsAfterInventoryRestore(player);\n      }\n   }\n\n   private InventoryGroupFeature.Group groupOf(World var1) {\n''',
    "ensure initial items helper",
)
p.write_text(s, encoding="utf-8")

# ServerPortalFeature: server wand can bind an End Portal Frame to an existing destination,
# and the custom teleporter eye uses that frame. The eye is visual only and is cleared next tick.
p = root / "src/main/java/org/server/minerva/ServerPortalFeature.java"
s = p.read_text(encoding="utf-8")
s = replace_once(
    s,
    'import org.bukkit.block.data.Orientable;\n',
    'import org.bukkit.block.data.Orientable;\nimport org.bukkit.block.data.type.EndPortalFrame;\n',
    "EndPortalFrame import",
)
s = replace_once(
    s,
    'import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;\n',
    'import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;\nimport org.bukkit.inventory.EquipmentSlot;\n',
    "EquipmentSlot import",
)

old = '''         } else if (event.getAction().isRightClick()) {\n            if (this.isServerPortal(block)) {\n               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n               event.setCancelled(true);\n            } else {\n               block.setType(Material.NETHER_PORTAL, false);\n               this.applyServerPortalFacing(block, player);\n               this.setServerPortal(block, true);\n               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n               player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。移動先サーバーを選択してください。");\n               event.setCancelled(true);\n            }\n         } else {\n            if (event.getAction().isLeftClick()) {\n               if (!this.isServerPortal(block)) {\n                  player.sendMessage(ChatColor.YELLOW + "このブロックはサーバーポータルではありません。");\n                  event.setCancelled(true);\n                  return;\n               }\n\n               this.setServerPortal(block, false);\n               block.setType(Material.AIR, false);\n               player.sendMessage(ChatColor.GREEN + "サーバーポータルを削除しました。");\n               event.setCancelled(true);\n            }\n         }\n'''
new = '''         } else if (block.getType() == Material.END_PORTAL_FRAME) {\n            if (event.getAction().isRightClick()) {\n               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n               player.sendMessage(ChatColor.LIGHT_PURPLE + "このエンドポータルフレームの移動先を選択してください。");\n            } else if (event.getAction().isLeftClick()) {\n               this.clearPortalTarget(block);\n               player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");\n            }\n            event.setCancelled(true);\n         } else if (event.getAction().isRightClick()) {\n            if (this.isServerPortal(block)) {\n               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n               event.setCancelled(true);\n            } else {\n               block.setType(Material.NETHER_PORTAL, false);\n               this.applyServerPortalFacing(block, player);\n               this.setServerPortal(block, true);\n               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n               player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。移動先サーバーを選択してください。");\n               event.setCancelled(true);\n            }\n         } else {\n            if (event.getAction().isLeftClick()) {\n               if (!this.isServerPortal(block)) {\n                  player.sendMessage(ChatColor.YELLOW + "このブロックはサーバーポータルではありません。");\n                  event.setCancelled(true);\n                  return;\n               }\n\n               this.setServerPortal(block, false);\n               block.setType(Material.AIR, false);\n               player.sendMessage(ChatColor.GREEN + "サーバーポータルを削除しました。");\n               event.setCancelled(true);\n            }\n         }\n'''
s = replace_once(s, old, new, "server wand frame binding")

handler_anchor = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n   public void onServerPortalTeleport(PlayerTeleportEvent event) {\n'''
handler = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n   public void onTeleporterUse(PlayerInteractEvent event) {\n      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {\n         return;\n      }\n\n      ItemStack item = event.getItem();\n      if (!this.isTeleporter(item)) {\n         return;\n      }\n\n      // Always cancel vanilla Ender Eye behaviour: the Minerva teleporter can never be thrown.\n      event.setCancelled(true);\n      Block frame = event.getClickedBlock();\n      if (frame == null || frame.getType() != Material.END_PORTAL_FRAME) {\n         return;\n      }\n\n      String target = this.serverPortalTarget(frame);\n      if (target == null || target.isBlank()) {\n         event.getPlayer().sendMessage(ChatColor.YELLOW + "このエンドポータルフレームには移動先が設定されていません。");\n         return;\n      }\n\n      this.setFrameEye(frame, true);\n      event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8F, 1.1F);\n      this.plugin.teleportToConfigLocation(event.getPlayer(), target);\n\n      // The inserted eye is only a short visual cue. It must never remain in the frame.\n      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.setFrameEye(frame, false));\n   }\n\n   private boolean isTeleporter(ItemStack item) {\n      return item != null\n         && item.hasItemMeta()\n         && "teleporter".equals(item.getItemMeta().getPersistentDataContainer().get(this.minervaItemKey, PersistentDataType.STRING));\n   }\n\n   private void setFrameEye(Block frame, boolean eye) {\n      if (frame != null && frame.getType() == Material.END_PORTAL_FRAME && frame.getBlockData() instanceof EndPortalFrame data) {\n         data.setEye(eye);\n         frame.setBlockData(data, false);\n      }\n   }\n\n'''
s = replace_once(s, handler_anchor, handler + handler_anchor, "teleporter interaction handler")

s = replace_once(
    s,
    '''   void setServerPortalTarget(String portalKey, String targetPath) {\n      if (portalKey != null && !portalKey.isBlank() && targetPath != null && !targetPath.isBlank()) {\n         Block block = this.blockFromKey(portalKey);\n         if (block == null) {\n            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);\n            this.plugin.saveData();\n         } else {\n            for (String key : this.portalClusterKeys(block)) {\n               this.plugin.data().set(this.serverPortalTargetPath(key), targetPath);\n            }\n\n            this.plugin.saveData();\n         }\n      }\n   }\n''',
    '''   void setServerPortalTarget(String portalKey, String targetPath) {\n      if (portalKey != null && !portalKey.isBlank() && targetPath != null && !targetPath.isBlank()) {\n         Block block = this.blockFromKey(portalKey);\n         if (block == null) {\n            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);\n            this.plugin.saveData();\n         } else if (block.getType() == Material.END_PORTAL_FRAME) {\n            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);\n            this.plugin.saveData();\n         } else {\n            for (String key : this.portalClusterKeys(block)) {\n               this.plugin.data().set(this.serverPortalTargetPath(key), targetPath);\n            }\n\n            this.plugin.saveData();\n         }\n      }\n   }\n\n   private void clearPortalTarget(Block block) {\n      if (block != null) {\n         this.plugin.data().set(this.serverPortalTargetPath(this.blockKey(block)), null);\n         this.plugin.saveData();\n         this.setFrameEye(block, false);\n      }\n   }\n''',
    "frame target storage",
)
p.write_text(s, encoding="utf-8")

# Workflow trigger marker: v1
print("Applied survival initial-item and End Portal Frame teleporter changes.")
