from pathlib import Path
import re

manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

# 1) Vampire natural regeneration: normal saturated/unsaturated rates are 10/80 ticks.
# Four-times speed => 2.5/20 ticks; Bukkit requires integer ticks, so use 2/20.
manager = manager.replace('player.setSaturatedRegenRate(3);', 'player.setSaturatedRegenRate(2);')
manager = manager.replace('player.setUnsaturatedRegenRate(27);', 'player.setUnsaturatedRegenRate(20);')

# 2) Add explicit exhaustion recovery so lifesteal can refill hidden hunger reserves as well.
# Saturation is the visible hidden reserve; exhaustion controls when saturation/food is consumed.
def add_hidden_hunger_recovery(block: str) -> str:
    if 'attacker.setExhaustion(' in block:
        return block
    marker = 'attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));'
    replacement = marker + '\n                     attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));'
    return block.replace(marker, replacement)

manager = add_hidden_hunger_recovery(manager)

# 3) Display cumulative Vampire damage and the active attack-power tier.
# Existing code already stores cumulative dealt damage in vampireDamage; attach a stable display
# wherever total is updated. Threshold defaults remain configurable.
patterns = [
    r'(double total\s*=\s*this\.vampireDamage\.merge\(attacker\.getUniqueId\(\),\s*dealt,\s*Double::sum\);)',
    r'(double total\s*=\s*this\.vampireDamage\.merge\(attacker\.getUniqueId\(\),\s*event\.getFinalDamage\(\),\s*Double::sum\);)',
]
for pattern in patterns:
    match = re.search(pattern, manager)
    if match:
        insert = match.group(1) + '''
                     double threshold = Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-threshold"), 50.0));
                     int tier = Math.max(0, (int)Math.floor(total / threshold));
                     attacker.sendActionBar(Component.text("吸血蓄積 " + String.format(Locale.ROOT, "%.1f", total) + " / 攻撃強化 Lv." + tier, NamedTextColor.RED));'''
        manager = manager[:match.start()] + insert + manager[match.end():]
        break
else:
    # Fallback for versions where total was not persisted in the Vampire block.
    vampire_marker = 'if (session.kit == FfaKit.VAMPIRE && event.getFinalDamage() > 0.0) {'
    idx = manager.find(vampire_marker)
    if idx < 0:
        raise SystemExit('Vampire damage block not found')
    brace = manager.find('{', idx)
    manager = manager[:brace+1] + '''
                     double dealt = Math.max(0.0, event.getFinalDamage());
                     double total = this.vampireDamage.merge(attacker.getUniqueId(), dealt, Double::sum);
                     double threshold = Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-threshold"), 50.0));
                     int tier = Math.max(0, (int)Math.floor(total / threshold));
                     attacker.sendActionBar(Component.text("吸血蓄積 " + String.format(Locale.ROOT, "%.1f", total) + " / 攻撃強化 Lv." + tier, NamedTextColor.RED));
''' + manager[brace+1:]

# 4) Make the training Husk path cover the same Vampire accumulation and hidden hunger behavior.
sig = '   void adjustTrainingHuskDamage(EntityDamageByEntityEvent event, Player attacker, org.bukkit.entity.Husk husk)'
start = manager.find(sig)
if start < 0:
    raise SystemExit('Training Husk method not found')
brace = manager.find('{', start)
depth = 0
end = None
for i in range(brace, len(manager)):
    if manager[i] == '{':
        depth += 1
    elif manager[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None:
    raise SystemExit('Training Husk method is unclosed')
method = manager[start:end]

# Replace/append the Vampire section inside the Husk adapter.
vampire_husk = '''
      if (session.kit == FfaKit.VAMPIRE && !event.isCancelled() && event.getDamage() > 0.0) {
         double dealt = Math.max(0.0, event.getDamage());
         double total = this.vampireDamage.merge(attacker.getUniqueId(), dealt, Double::sum);
         int restored = Math.max(1, (int)Math.ceil(dealt * 0.5));
         attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + restored));
         attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));
         attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));
         double threshold = Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-threshold"), 50.0));
         int tier = Math.max(0, (int)Math.floor(total / threshold));
         attacker.sendActionBar(Component.text("吸血蓄積 " + String.format(Locale.ROOT, "%.1f", total) + " / 攻撃強化 Lv." + tier, NamedTextColor.RED));
         attacker.sendMessage("§4ヴァンパイア累計ダメージ: §c" + String.format(Locale.ROOT, "%.1f", total) + " §7/ 攻撃強化 Lv." + tier);
      }
'''
# Remove prior simple Vampire adapter to prevent duplicate recovery.
method = re.sub(r'\n\s*if \(session\.kit == FfaKit\.VAMPIRE.*?\n\s*\}', '', method, count=1, flags=re.S)
insert_at = method.rfind('\n   }')
method = method[:insert_at] + vampire_husk + method[insert_at:]
manager = manager[:start] + method + manager[end:]

manager_path.write_text(manager, encoding='utf-8', newline='\n')
