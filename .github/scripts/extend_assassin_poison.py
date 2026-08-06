from pathlib import Path

config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')
marker = '      this.setListIfMissingOrEmpty(config, "ffa.kits.assassin.fatal-sword-enchantments", List.of("unbreaking:1"));'
addition = marker + '\n      this.setIfMissingOrForce(config, "ffa.kits.assassin.poison-duration-seconds", 4, true);'
if 'ffa.kits.assassin.poison-duration-seconds' not in config:
    if marker not in config:
        raise SystemExit('Assassin config insertion point not found')
    config = config.replace(marker, addition, 1)
config_path.write_text(config, encoding='utf-8', newline='\n')

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')
old = 'new PotionEffect(PotionEffectType.POISON, 40, 1, false, false, true)'
new = 'new PotionEffect(PotionEffectType.POISON, Math.max(20, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.ASSASSIN, "poison-duration-seconds"), 4) * 20), 1, false, false, true)'
count = manager.count(old)
if count != 2:
    raise SystemExit(f'Expected 2 Assassin poison duration sites, found {count}')
manager = manager.replace(old, new)
manager_path.write_text(manager, encoding='utf-8', newline='\n')
