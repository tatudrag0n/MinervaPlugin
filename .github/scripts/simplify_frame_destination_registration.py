from pathlib import Path

p = Path('src/main/java/org/server/minerva/ServerPortalFeature.java')
s = p.read_text(encoding='utf-8')

old = '   private final Map<UUID, Long> portalUseCooldowns = new ConcurrentHashMap<>();\n'
new = '   private final Map<UUID, Long> portalUseCooldowns = new ConcurrentHashMap<>();\n   private final Map<UUID, Location> pendingCoordinateTargets = new ConcurrentHashMap<>();\n'
if old not in s:
    raise SystemExit('missing map insertion target')
s = s.replace(old, new, 1)

old = '''         List.of(\n            Component.text(ChatColor.GRAY + "右クリック: ブロックをサーバーポータル化"),\n            Component.text(ChatColor.GRAY + "ポータルを右クリック: 移動先サーバー設定"),\n            Component.text(ChatColor.GRAY + "左クリック: サーバーポータルを削除")\n         )\n'''
new = '''         List.of(\n            Component.text(ChatColor.GRAY + "Shift+右クリック: 現在地を移動先として記憶"),\n            Component.text(ChatColor.GRAY + "エンドポータルフレームを右クリック: 記憶した移動先を登録"),\n            Component.text(ChatColor.GRAY + "移動先未記憶でフレーム右クリック: 移動先UI"),\n            Component.text(ChatColor.GRAY + "左クリック: 設定解除 / ポータル削除")\n         )\n'''
if old not in s:
    raise SystemExit('missing lore target')
s = s.replace(old, new, 1)

old = '''      } else {\n         Block block = this.resolveWandTargetBlock(event);\n         if (block == null) {\n            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");\n            event.setCancelled(true);\n         } else if (block.getType() == Material.END_PORTAL_FRAME) {\n            if (event.getAction().isRightClick()) {\n               if (player.isSneaking()) {\n                  this.setCoordinateTarget(block, player.getLocation());\n                  player.sendMessage(ChatColor.GREEN + "現在地をテレポート先として設定しました: " + this.formatLocation(player.getLocation()));\n               } else {\n                  this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n                  player.sendMessage(ChatColor.LIGHT_PURPLE + "移動先を選択してください。Shift+右クリックで現在地の座標を直接登録できます。");\n               }\n            } else if (event.getAction().isLeftClick()) {\n               this.clearPortalTarget(block);\n               player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");\n            }\n            event.setCancelled(true);\n'''
new = '''      } else {\n         Block clicked = event.getClickedBlock();\n         if (event.getAction().isRightClick()\n            && player.isSneaking()\n            && (clicked == null || clicked.getType() != Material.END_PORTAL_FRAME)) {\n            Location remembered = player.getLocation().clone();\n            this.pendingCoordinateTargets.put(player.getUniqueId(), remembered);\n            player.sendMessage(ChatColor.GREEN + "移動先を記憶しました: " + this.formatLocation(remembered));\n            player.sendMessage(ChatColor.GRAY + "次に、登録したいエンドポータルフレームを右クリックしてください。");\n            event.setCancelled(true);\n            return;\n         }\n\n         Block block = this.resolveWandTargetBlock(event);\n         if (block == null) {\n            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");\n            event.setCancelled(true);\n         } else if (block.getType() == Material.END_PORTAL_FRAME) {\n            if (event.getAction().isRightClick()) {\n               Location remembered = this.pendingCoordinateTargets.remove(player.getUniqueId());\n               if (remembered != null) {\n                  this.setCoordinateTarget(block, remembered);\n                  player.sendMessage(ChatColor.GREEN + "記憶した移動先をこのフレームに登録しました: " + this.formatLocation(remembered));\n               } else {\n                  this.plugin.openServerPortalTargetUi(player, this.blockKey(block));\n                  player.sendMessage(ChatColor.LIGHT_PURPLE + "移動先を選択してください。座標登録は、移動先でShift+右クリック → このフレームを右クリックです。");\n               }\n            } else if (event.getAction().isLeftClick()) {\n               this.clearPortalTarget(block);\n               player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");\n            }\n            event.setCancelled(true);\n'''
if old not in s:
    raise SystemExit('missing handleWandClick target')
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('Simplified End Portal Frame destination registration.')
