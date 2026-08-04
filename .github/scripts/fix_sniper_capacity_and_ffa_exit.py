from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0:
        if new in text:
            return text
        raise SystemExit(f"{label}: target not found")
    if count > 1:
        raise SystemExit(f"{label}: target found {count} times")
    return text.replace(old, new, 1)

kit = Path("src/main/java/org/server/minerva/FfaKit.java")
text = kit.read_text(encoding="utf-8")
text = replace_once(
    text,
    '   int sniperCapacity(FfaConfig config) {\n      return this.amount(config, "ammo-capacity", 2);\n   }',
    '   int sniperCapacity(FfaConfig config) {\n      return 1;\n   }',
    "hard-code sniper capacity",
)
text = text.replace('case SNIPER -> "2発式クロスボウ";', 'case SNIPER -> "単発式クロスボウ";')
kit.write_text(text, encoding="utf-8", newline="\n")

manager = Path("src/main/java/org/server/minerva/FfaManager.java")
text = manager.read_text(encoding="utf-8")
text = text.replace(
    'this.handleAmmoCrossbow(event, player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, 2, "スナイパー", "sniper");',
    'this.handleAmmoCrossbow(event, player, FfaKit.SNIPER, this.sniperAmmo, this.sniperReloadTasks, 1, "スナイパー", "sniper");',
)
manager.write_text(text, encoding="utf-8", newline="\n")

listener = Path("src/main/java/org/server/minerva/FfaListener.java")
text = listener.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {\n         if (player.isOnline() && this.ffa.isPlaying(player)) {\n            this.ffa.respawn(player);\n         }\n      });''',
    '''      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {\n         if (player.isOnline() && this.ffa.isPlaying(player)) {\n            this.ffa.leave(player, true);\n         }\n      });''',
    "exit FFA after lethal damage",
)
listener.write_text(text, encoding="utf-8", newline="\n")

config = Path("src/main/resources/config.yml")
text = config.read_text(encoding="utf-8")
text = text.replace('      ammo-capacity: 2\n      reload-ticks: 60', '      ammo-capacity: 1\n      reload-ticks: 60')
config.write_text(text, encoding="utf-8", newline="\n")
