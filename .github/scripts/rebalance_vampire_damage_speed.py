from pathlib import Path

# Trigger Vampire damage-focused rebalance.
path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

old_damage = 'double damagePerTier = Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-per-tier-percent"), 10.0));'
new_damage = 'double damagePerTier = Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-per-tier-percent"), 20.0));'
if old_damage in text:
    text = text.replace(old_damage, new_damage, 1)
elif new_damage not in text:
    raise SystemExit('Vampire damage-per-tier setting not found')

old_speed = '''      if (tier > 0) {
         int speedAmplifier = Math.min(2, (tier - 1) / 2);
         attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, speedAmplifier, false, false, true));
      }
'''
new_speed = '''      if (tier >= 2) {
         attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
      }
'''
if old_speed in text:
    text = text.replace(old_speed, new_speed, 1)
elif new_speed not in text:
    raise SystemExit('Vampire speed progression block not found')

old_display = '            + " / 速度 Lv." + (tier <= 0 ? 0 : Math.min(3, (tier + 1) / 2)),'
new_display = '            + " / 速度 Lv." + (tier >= 2 ? 1 : 0),'
if old_display in text:
    text = text.replace(old_display, new_display, 1)
elif new_display not in text:
    raise SystemExit('Vampire speed display not found')

path.write_text(text, encoding='utf-8', newline='\n')
