# MinerVa Plugin

MinerVa command aliases:

- `/minerva`
- `/mva`

`/mv` is reserved for Multiverse-Core and is not registered by MinerVa. For example, use Multiverse-Core commands such as `/mv create` only for world management provided by Multiverse-Core.

Recommended MinerVa world/admin command style:

- `/mva check`
- `/mva list`
- `/mva tp <worldKey>`
- `/mva gamerules <world>`
- `/mva info`
- `/mva reload`

The current plugin command implementation keeps existing `/minerva` subcommands and exposes them through `/mva` as the short alias.

## Spawn Protection

Vanilla `spawn-protection` can conflict with custom shop interactions because it may cancel block interaction before shop logic can finish.

Recommended `server.properties` setting:

```properties
spawn-protection=0
```

Use MinerVa's `ProtectionService` and central-area protection instead. Protected spawn/central chunks still block normal building, doors, trapdoors, containers, item frames, armor stands, signs, and hopper movement, while explicitly allowing MinerVa shop purchases, auction bids, status-book UI, teleporter UI, and admin shop-wand actions.

## Shops

Shopified shelves and barrels must be managed with the shop wand:

- Right click: create shop
- Left click: remove shop

Normal block breaking, explosions, pistons, liquids, and burning do not break shop blocks or drop shop display/internal items.

## Stored Data and Privacy

MinerVa stores server-side gameplay data in the plugin data folder. Treat these files as private server data and do not publish them.

- `data.yml`: player UUIDs, names, MP balances, status/progression data, friend relationships, friend requests, and limited offline friend messages.
- `auth.yml`: Discord-auth verification state, temporary auth codes, player UUIDs, and player names.
- `proposals.yml`: pending/reviewed proposal metadata, which may include Discord user IDs when imported by external tooling.
- `structures.yml`, `text-displays.yml`, `ffa-stats.yml`: admin-created server content, locations, generated-structure records, and FFA stats.

Temporary Discord auth codes expire according to `discord-auth.expire-minutes`. To remove a player's stored data, delete that player's UUID section from the relevant YAML files while the server is stopped, or use the available admin reset commands where applicable.

## Legal Notes

MinerVa is an unofficial Minecraft server plugin and is not affiliated with, endorsed by, or approved by Mojang or Microsoft.

Do not sell or exchange MinerVa MP or other in-game rewards for real-world money or transferable value. If the server is monetized, keep rewards compliant with the current Minecraft EULA and Usage Guidelines.
