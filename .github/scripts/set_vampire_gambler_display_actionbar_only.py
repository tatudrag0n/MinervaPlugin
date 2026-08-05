from pathlib import Path

path = Path('src/main/java/org/server/minerva/FfaManager.java')
text = path.read_text(encoding='utf-8')
lines = text.splitlines()

# Keep Vampire/Gambler status output only in the action bar (the strip above the XP bar).
# Remove chat and title/subtitle output for their rolls/progression while preserving sounds,
# damage calculations, title unlocks, and sendActionBar calls.
blocked_fragments = (
    'sendMessage("§6ギャンブラー',
    'sendMessage("§4ヴァンパイア累計ダメージ',
    'sendMessage("§6§l✦✦✦ LUCKY PUNCH!',
    'sendTitle("", "§6攻撃抽選',
    'sendTitle("", "§6防御抽選',
    'sendTitle("§6',
)

filtered = []
removed = 0
for line in lines:
    if any(fragment in line for fragment in blocked_fragments):
        removed += 1
        continue
    filtered.append(line)

# Also remove multiline sendTitle calls whose first line has no Japanese text.
result = []
i = 0
while i < len(filtered):
    line = filtered[i]
    if '.sendTitle(' in line:
        block = [line]
        j = i + 1
        while j < len(filtered) and ');' not in filtered[j]:
            block.append(filtered[j])
            j += 1
        if j < len(filtered):
            block.append(filtered[j])
        joined = '\n'.join(block)
        if ('ギャンブラー' in joined or '攻撃抽選' in joined or '防御抽選' in joined
                or '吸血蓄積' in joined or 'ヴァンパイア' in joined):
            removed += len(block)
            i = j + 1
            continue
    result.append(line)
    i += 1

new_text = '\n'.join(result) + '\n'

# Guardrails: action-bar output must remain for both kits.
if 'sendActionBar' not in new_text or '吸血蓄積' not in new_text:
    raise SystemExit('Vampire action-bar display not found after filtering')
if ('攻撃抽選' not in new_text and 'ギャンブラー倍率' not in new_text) or 'sendActionBar' not in new_text:
    raise SystemExit('Gambler action-bar display not found after filtering')

if removed == 0:
    # Idempotent success only when no forbidden output remains.
    forbidden_remaining = any(fragment in new_text for fragment in blocked_fragments)
    if forbidden_remaining:
        raise SystemExit('Forbidden Vampire/Gambler display remained')

path.write_text(new_text, encoding='utf-8', newline='\n')
