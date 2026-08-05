from pathlib import Path

kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')

kit = kit_path.read_text(encoding='utf-8')
manager = manager_path.read_text(encoding='utf-8')

# Keep the wooden sword for basic self-defense; remove the phantom summon instead.
kit = kit.replace(
'List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton", "phantom")',
'List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton")',
1,
)

old_block = '''            for (String mob : List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton")) {
               inventory.addItem(new ItemStack[]{kitItem(plugin, this, "summon_" + mob, spawnEgg(mob), summonName(mob), 1, Map.of())});
            }
            break;
'''
new_block = '''            for (String mob : List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton")) {
               inventory.addItem(new ItemStack[]{kitItem(plugin, this, "summon_" + mob, spawnEgg(mob), summonName(mob), 1, Map.of())});
            }
            inventory.setItem(8, kitItem(plugin, this, "food", Material.ROTTEN_FLESH, "§5死霊術師の腐肉", 1, Map.of()));
            break;
'''
if old_block not in kit:
    raise SystemExit('necromancer kit block not found')
kit = kit.replace(old_block, new_block, 1)

# Remove phantom from runtime cooldown restoration and fixed-slot mappings.
manager = manager.replace(
'private static final List<String> NECROMANCER_MOBS = List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton", "phantom");',
'private static final List<String> NECROMANCER_MOBS = List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton");',
1,
)
manager = manager.replace('         case "phantom" -> 8;\n', '', 1)

kit_path.write_text(kit, encoding='utf-8', newline='\n')
manager_path.write_text(manager, encoding='utf-8', newline='\n')
