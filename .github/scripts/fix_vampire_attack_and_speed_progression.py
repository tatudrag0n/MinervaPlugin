from pathlib import Path
import re

# Triggered repair: actually apply Vampire attack and movement progression.
path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

helper = '''
   private double applyVampireProgression(Player attacker, double baseDamage) {
      double dealt = Math.max(0.0, baseDamage);
      double total = this.vampireDamage.merge(attacker.getUniqueId(), dealt, Double::sum);
      double threshold = Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-threshold"), 50.0));
      int maxTier = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.VAMPIRE, "max-damage-buff-tier"), 5));
      int tier = Math.min(maxTier, Math.max(0, (int)Math.floor(total / threshold)));
      double damagePerTier = Math.max(0.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-per-tier-percent"), 10.0));
      double multiplier = 1.0 + tier * damagePerTier / 100.0;

      if (tier > 0) {
         int speedAmplifier = Math.min(2, (tier - 1) / 2);
         attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, speedAmplifier, false, false, true));
      }

      attacker.sendActionBar(Component.text(
         "吸血蓄積 " + String.format(Locale.ROOT, "%.1f", total)
            + " / 攻撃 x" + String.format(Locale.ROOT, "%.2f", multiplier)
            + " / 速度 Lv." + (tier <= 0 ? 0 : Math.min(3, (tier + 1) / 2)),
         NamedTextColor.RED
      ));
      return multiplier;
   }
'''
if 'private double applyVampireProgression(' not in text:
    pos = text.rfind('\n}')
    if pos < 0:
        raise SystemExit('manager class end not found')
    text = text[:pos] + helper + text[pos:]

normal_marker = '''                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double dealt = Math.max(0.0, event.getFinalDamage());
'''
normal_replacement = '''                  if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {
                     double multiplier = this.applyVampireProgression(attacker, event.getDamage());
                     event.setDamage(event.getDamage() * multiplier);
                     double dealt = Math.max(0.0, event.getFinalDamage());
'''
if normal_marker in text:
    text = text.replace(normal_marker, normal_replacement, 1)
elif 'double multiplier = this.applyVampireProgression(attacker, event.getDamage());' not in text:
    raise SystemExit('normal Vampire damage block not found')

sig = '   void adjustTrainingHuskDamage(EntityDamageByEntityEvent event, Player attacker, org.bukkit.entity.Husk husk)'
start = text.find(sig)
if start < 0:
    raise SystemExit('training Husk method not found')
brace = text.find('{', start)
depth = 0
end = None
for i in range(brace, len(text)):
    if text[i] == '{': depth += 1
    elif text[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
method = text[start:end]

old_husk_vampire = re.compile(r'''\n\s*if \(session\.kit == FfaKit\.VAMPIRE && !event\.isCancelled\(\) && event\.getDamage\(\) > 0\.0\) \{.*?\n\s*\}''', re.S)
method = old_husk_vampire.sub('', method, count=1)
insert = '''
      if (session.kit == FfaKit.VAMPIRE && !event.isCancelled() && event.getDamage() > 0.0) {
         double multiplier = this.applyVampireProgression(attacker, event.getDamage());
         event.setDamage(event.getDamage() * multiplier);
         double dealt = Math.max(0.0, event.getDamage());
         int restored = Math.max(1, (int)Math.ceil(dealt * 0.5));
         attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
         attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));
      }
'''
insert_at = method.rfind('\n   }')
method = method[:insert_at] + insert + method[insert_at:]
text = text[:start] + method + text[end:]

path.write_text(text, encoding='utf-8', newline='\n')
