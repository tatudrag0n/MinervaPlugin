from pathlib import Path

kit_path = Path('src/main/java/org/server/minerva/FfaKit.java')
kit = kit_path.read_text(encoding='utf-8')
old_fallback = 'this.enchantments(config, "weapon-enchantments", Map.of("sharpness", 1))'
new_fallback = 'this.enchantments(config, "weapon-enchantments", Map.of("sharpness", 2))'
if old_fallback not in kit:
    raise SystemExit('Warrior Sharpness fallback not found')
kit = kit.replace(old_fallback, new_fallback, 1)
kit_path.write_text(kit, encoding='utf-8', newline='\n')

config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')
old_config = 'this.setListIfMissingOrEmpty(config, "ffa.kits.axe.weapon-enchantments", List.of("sharpness:1"));'
new_config = 'this.setListIfMissingOrEmptyOrForce(config, "ffa.kits.axe.weapon-enchantments", List.of("sharpness:2"), true);'
if old_config not in config:
    raise SystemExit('Warrior enchantment config line not found')
config = config.replace(old_config, new_config, 1)
config_path.write_text(config, encoding='utf-8', newline='\n')
