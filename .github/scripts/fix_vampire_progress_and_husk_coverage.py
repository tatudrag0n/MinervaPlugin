from pathlib import Path
import re

# Triggered repair: Vampire regeneration/progression and complete training-Husk coverage.
manager_path = Path('src/main/java/org/server/minerva/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

manager = manager.replace('player.setSaturatedRegenRate(3);', 'player.setSaturatedRegenRate(2);')
manager = manager.replace('player.setUnsaturatedRegenRate(27);', 'player.setUnsaturatedRegenRate(20);')

marker = 'attacker.setSaturation(Math.min(20.0F, attacker.getSaturation() + Math.max(1.0F, restored * 0.5F)));'
if 'attacker.setExhaustion(' not in manager and marker in manager:
    manager = manager.replace(marker, marker + '\n                     attacker.setExhaustion(Math.max(0.0F, attacker.getExhaustion() - Math.max(1.0F, restored * 0.5F)));', 1)

patterns = [
    r'(double total\s*=\s*this\.vampireDamage\.merge\(attacker\.getUniqueId\(\),\s*dealt,\s*Double::sum\);)',
    r'(double total\s*=\s*this\.vampireDamage\.merge\(attacker\.getUniqueId\(\),\s*event\.getFinalDamage\(\),\s*Double::sum\);)',
]
for pattern in patterns:
    match = re.search(pattern, manager)
    if match and '攻撃強化 Lv.' not in manager[match.start():match.start()+800]:
        insert = match.group(1) + '''
                     double threshold = Math.max(1.0, this.plugin.getConfig().getDouble(this.config.kitPath(FfaKit.VAMPIRE, "damage-buff-threshold"), 50.0));
                     int tier = Math.max(0, (int)Math.floor(total / threshold));
                     attacker.sendActionBar(Component.text("吸血蓄積 " + String.format(Locale.ROOT, "%.1f", total) + " / 攻撃強化 Lv." + tier, NamedTextColor.RED));'''
        manager = manager[:match.start()] + insert + manager[match.end():]
        break

sig = '   void adjustTrainingHuskDamage(EntityDamageByEntityEvent event, Player attacker, org.bukkit.entity.Husk husk)'
start = manager.find(sig)
if start < 0:
    raise SystemExit('Training Husk method not found')
brace = manager.find('{', start)
depth = 0
end = None
for i in range(brace, len(manager)):
    if manager[i] == '{': depth += 1
    elif manager[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None:
    raise SystemExit('Training Husk method is unclosed')
method = manager[start:end]

if 'ヴァンパイア累計ダメージ' not in method:
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
    insert_at = method.rfind('\n   }')
    method = method[:insert_at] + vampire_husk + method[insert_at:]
    manager = manager[:start] + method + manager[end:]

manager_path.write_text(manager, encoding='utf-8', newline='\n')
