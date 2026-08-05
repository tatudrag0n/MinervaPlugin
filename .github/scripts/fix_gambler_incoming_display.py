from pathlib import Path
import re

# Trigger repair after workflow registration.
path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')


def method_bounds(source: str, signature: str):
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'method not found: {signature}')
    brace = source.find('{', start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return start, index + 1
    raise SystemExit(f'unclosed method: {signature}')

# Remove intrusive title/subtitle calls from both gambler result paths.
text = re.sub(r'\n\s*[^\n;]+\.sendTitle\([^;]+;\n', '\n', text)

# Replace the incoming gambler calculation with an explicit -5..+5 adjustment.
# Positive values add incoming damage; negative values reduce it.
signature = '   private void applyGamblerIncoming('
start, end = method_bounds(text, signature)
old = text[start:end]
header_end = old.find('{') + 1
method_header = old[:header_end]
new_body = '''
      if (event.isCancelled() || victim == null || !this.isPlaying(victim)) {
         return;
      }

      FfaManager.FfaSession session = this.sessions.get(victim.getUniqueId());
      if (session == null || session.kit != FfaKit.GAMBLER || this.gamblerSelfDamage.contains(victim.getUniqueId())) {
         return;
      }

      int adjustment = ThreadLocalRandom.current().nextInt(-5, 6);
      double original = event.getDamage();
      double adjusted = Math.max(0.0, original + adjustment);
      event.setDamage(adjusted);

      String shown = (adjustment >= 0 ? "+" : "") + adjustment;
      String effect = adjustment < 0 ? "軽減" : adjustment > 0 ? "増加" : "変化なし";
      NamedTextColor color = adjustment < 0 ? NamedTextColor.GREEN : adjustment > 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
      victim.sendActionBar(Component.text("防御抽選 " + shown + "ダメージ（" + effect + "）", color));
      victim.sendMessage("§6ギャンブラー防御抽選: §e" + shown + " ダメージ §7(" + effect + ")");
   }'''
text = text[:start] + method_header + new_body + text[end:]

# The Husk attack-debug path should use action bar + chat only as requested.
text = re.sub(
    r'\n\s*attacker\.sendTitle\("", "§6攻撃抽選[^;]+;\n',
    '\n',
    text,
)

path.write_text(text, encoding='utf-8', newline='\n')
