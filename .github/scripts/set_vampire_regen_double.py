from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')

replacements = {
    'player.setSaturatedRegenRate(2);': 'player.setSaturatedRegenRate(5);',
    'player.setUnsaturatedRegenRate(20);': 'player.setUnsaturatedRegenRate(40);',
    'player.setSaturatedRegenRate(3);': 'player.setSaturatedRegenRate(5);',
    'player.setUnsaturatedRegenRate(27);': 'player.setUnsaturatedRegenRate(40);',
}

changed = False
for old, new in replacements.items():
    if old in text:
        text = text.replace(old, new)
        changed = True

if not changed and ('player.setSaturatedRegenRate(5);' not in text or 'player.setUnsaturatedRegenRate(40);' not in text):
    raise SystemExit('Vampire regeneration settings not found')

path.write_text(text, encoding='utf-8', newline='\n')
