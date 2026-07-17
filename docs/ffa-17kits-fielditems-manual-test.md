# FFA 17 Kits / Field Items Manual Test

Run on a Paper test server with the built jar installed.

1. Confirm `/mv` is not registered by MinerVa: `/help minerva`, `/help mva`, and Multiverse `/mv` still belongs to Multiverse-Core.
2. Set FFA center: `/mva ffa setcenter`.
3. Create kit stands: `/mva ffa createkits`.
4. Confirm the selector shows 17 kits and invalid/hidden kit data falls back to `sword`.
5. Join each kit and verify the configured weapon, armor, food, and special item appear.
6. For all kit food, right click twice: first use restores hunger/saturation and leaves one item, second use shows cooldown.
7. For `sword`, use the kit golden apple twice and verify 100 second cooldown persists after death/rejoin.
8. For `crossbow`, fire 6 shots, verify 70% damage, reload sound, and 3x reload timing.
9. For `sniper`, fire 2 shots, verify 5x damage, loud reload sound, and 5x reload timing.
10. For `wizard` / chemist, throw all five potion types, verify each is not consumed and has its own cooldown. Own negative splash should not affect self.
11. For `trident`, throw and verify it returns to the owner inventory and other players cannot pick it up.
12. For `mace`, use all wind charges and verify 10 are restored after 10 seconds only when the count reaches 0. Verify mace final player damage is capped at 12 HP.
13. For `gambler`, hit players repeatedly and verify action bar multipliers, negative self damage, and kill MP +/- changes.
14. For `vampire`, hit players and verify lifesteal and strength progression up to level III.
15. For `assassin`, use fatal sword on a player above 1 HP and verify target is left at 1 HP and the sword is consumed.
16. For `necromancer`, use each summon egg, verify per-mob cooldown, max 5 owned mobs, 30 second despawn, no drops/XP, no owner targeting, and cleanup on death/leave.
17. For `trapper`, place all three trap types, verify one active trap per type, protected-area rejection, activation by enemies only, original block restoration, and cleanup on death/leave.
18. For `bug_mania`, verify permanent Infested, 10% attacker Infested reflection, 10% silverfish summon on hit, max owned/global silverfish, no drops/MP, and cleanup on death/leave.
19. Register field item spawn point: `/mva ffa fielditem spawnpoint add`, then list/remove with the numbered commands.
20. Spawn loot manually: `/mva ffa fielditem spawn legendary`; verify it appears, glows, can be picked by FFA players, and disappears on FFA exit/death.
21. Verify field item entities are not destroyed or collected by fire, cactus, explosions, or hoppers.
22. Start each event manually with `/mva ffa fielditem start <event>` and verify start/end messages and cleanup:
    `rain`, `snow`, `blizzard`, `berserk`, `speed`, `iron_body`, `overdrive`, `one_shot_bow`, `mp_fever`, `sky_spear`, `time_shift`, `heal_self`, `heal_all`.
23. Verify MP kill reward sequence against the same target: 50, 25, 12, 6, 3, 1, then 0 MP. Kill a different player or wait 10 minutes to reset.
24. Stop everything: `/mva ffa fielditem stop`, then reload: `/mva ffa fielditem reload`.
25. Stop the server while players are in FFA and verify inventories, tasks, field items, and active events are cleaned up.
