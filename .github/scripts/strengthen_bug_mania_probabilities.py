from pathlib import Path

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

replacements = [
    (
        '''                  if (session.kit == FfaKit.BUG_MANIA
                     && this.isFfaItem(mainHand)
                     && "bug_sword".equals(this.itemKind(mainHand))
                     && ThreadLocalRandom.current().nextInt(100) < 8) {''',
        '''                  if (session.kit == FfaKit.BUG_MANIA
                     && this.isFfaItem(mainHand)
                     && "bug_sword".equals(this.itemKind(mainHand))
                     && this.rollBugManiaChance("attack-summon-chance-percent", 15)) {''',
        'player attack summon chance',
    ),
    (
        '''                  if (victimSession != null && victimSession.kit == FfaKit.BUG_MANIA && ThreadLocalRandom.current().nextInt(100) < 10) {''',
        '''                  if (victimSession != null
                     && victimSession.kit == FfaKit.BUG_MANIA
                     && this.rollBugManiaChance("counter-infestation-chance-percent", 20)) {''',
        'counter infestation chance',
    ),
    (
        '''      if (session.kit == FfaKit.BUG_MANIA && this.isFfaItem(mainHand) && "bug_sword".equals(itemKind)
         && ThreadLocalRandom.current().nextInt(100) < 10) {''',
        '''      if (session.kit == FfaKit.BUG_MANIA && this.isFfaItem(mainHand) && "bug_sword".equals(itemKind)
         && this.rollBugManiaChance("attack-summon-chance-percent", 15)) {''',
        'training Husk summon chance',
    ),
    (
        '''            if (killer != null && (owner == null || !owner.equals(killer.getUniqueId())) && ThreadLocalRandom.current().nextInt(100) < 5) {''',
        '''            if (killer != null
               && (owner == null || !owner.equals(killer.getUniqueId()))
               && this.rollBugManiaChance("silverfish-death-infestation-chance-percent", 10)) {''',
        'silverfish death infestation chance',
    ),
]

for old, new, label in replacements:
    if old not in manager:
        raise SystemExit(f'Could not find {label}')
    manager = manager.replace(old, new, 1)

helper = '''   private boolean rollBugManiaChance(String setting, int fallbackPercent) {
      int chance = Math.max(
         0,
         Math.min(
            100,
            this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.BUG_MANIA, setting), fallbackPercent)
         )
      );
      return chance >= 100 || chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance;
   }

'''
marker = '   private void spawnBugSilverfish(Player owner, Location location) {'
if 'private boolean rollBugManiaChance(' not in manager:
    if marker not in manager:
        raise SystemExit('Could not find Bug Mania helper insertion point')
    manager = manager.replace(marker, helper + marker, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')

config_path = Path('src/main/java/org/server/minerva/FfaConfig.java')
config = config_path.read_text(encoding='utf-8')

config_marker = '      this.setIfMissing(config, "ffa.kits.bug_mania.max-global-silverfish", 30);'
config_defaults = '''      this.setIfMissing(config, "ffa.kits.bug_mania.max-global-silverfish", 30);
      this.setIfMissing(config, "ffa.kits.bug_mania.attack-summon-chance-percent", 15);
      this.setIfMissing(config, "ffa.kits.bug_mania.counter-infestation-chance-percent", 20);
      this.setIfMissing(config, "ffa.kits.bug_mania.silverfish-death-infestation-chance-percent", 10);'''

if 'ffa.kits.bug_mania.attack-summon-chance-percent' not in config:
    if config_marker not in config:
        raise SystemExit('Could not find Bug Mania config insertion point')
    config = config.replace(config_marker, config_defaults, 1)

config_path.write_text(config, encoding='utf-8', newline='\n')
