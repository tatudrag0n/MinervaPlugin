from pathlib import Path

config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')

replacements = [
    (
        'this.setArmorDefault(config, "axe", migrateBalancedArmor || migratePermanentKits, "iron_helmet", "iron_chestplate", "chainmail_leggings", "iron_boots");',
        'this.setArmorDefault(config, "axe", true, "iron_helmet", "diamond_chestplate", "iron_leggings", "iron_boots");',
        'Warrior armor',
    ),
    (
        'this.setIfMissingOrForce(config, "ffa.kits.axe.weapon", "iron_axe", migratePermanentKits);',
        'this.setIfMissingOrForce(config, "ffa.kits.axe.weapon", "diamond_axe", true);',
        'Warrior weapon',
    ),
    (
        'config, "spear", migrateBalancedArmor || migratePermanentKits, "chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots"',
        'config, "spear", true, "chainmail_helmet", "iron_chestplate", "iron_leggings", "chainmail_boots"',
        'Spear armor',
    ),
    (
        'this.setIfMissing(config, "ffa.kits.spear.backup-weapon", "stone_sword");',
        'this.setIfMissingOrForce(config, "ffa.kits.spear.backup-weapon", "iron_sword", true);',
        'Spear backup weapon',
    ),
    (
        'this.setListIfMissingOrEmpty(config, "ffa.kits.spear.weapon-enchantments", List.of("lunge:1"));',
        'this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.spear.weapon-enchantments", List.of("lunge:2"), true);',
        'Spear enchantment',
    ),
]

for old, new, label in replacements:
    if old not in config:
        raise SystemExit(f'{label} line not found')
    config = config.replace(old, new, 1)

config_path.write_text(config, encoding='utf-8', newline='\n')

kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
kit = kit_path.read_text(encoding='utf-8')

old_axe = '''                     material(this.configValue(config, "weapon", "iron_axe"), Material.IRON_AXE),
                     "§6戦士の鉄斧",'''
new_axe = '''                     material(this.configValue(config, "weapon", "diamond_axe"), Material.DIAMOND_AXE),
                     "§6戦士のダイヤ斧",'''
if old_axe not in kit:
    raise SystemExit('Warrior item block not found')
kit = kit.replace(old_axe, new_axe, 1)

old_spear = '''            if (spear != null) {
               inventory.addItem(
                  new ItemStack[]{kitItem(plugin, this, "spear", spear, "§e槍使いの鉄槍", 1, this.enchantments(config, "weapon-enchantments", Map.of()))}
               );
            }
            break;'''
new_spear = '''            if (spear != null) {
               inventory.addItem(
                  new ItemStack[]{kitItem(plugin, this, "spear", spear, "§e槍使いの鉄槍", 1, this.enchantments(config, "weapon-enchantments", Map.of("lunge", 2)))}
               );
            }
            Material spearBackup = material(this.configValue(config, "backup-weapon", "iron_sword"), Material.IRON_SWORD);
            inventory.addItem(new ItemStack[]{kitItem(plugin, this, "backup_weapon", spearBackup, "§e槍使いの鉄剣", 1, Map.of())});
            break;'''
if old_spear not in kit:
    raise SystemExit('Spear item block not found')
kit = kit.replace(old_spear, new_spear, 1)

kit_path.write_text(kit, encoding='utf-8', newline='\n')
