# Fish Rework

Custom fishing progression plugin for Paper servers.

## Inspiration

Fish Rework is inspired by MMORPG-style fishing progression loops.

It is not affiliated with, endorsed by, or connected to Hypixel or Hypixel SkyBlock.

"Hypixel" and "SkyBlock" are trademarks of their respective owners.

## Requirements

- Paper `26.1.1`
- Java `25`
- Internet access on first startup (Paper downloads declared runtime libraries)

## Install

1. Build or download `FishRework-<version>.jar`.
2. Put it into `plugins/`.
3. Back up your world folder.
4. Start the server once.
5. Configure files in `plugins/FishRework/`.
6. Restart or run `/fishing reload` for config-only changes.

## Datapack Extraction

On startup, the plugin writes a server-side datapack used for custom fishing enchantments into the overworld datapacks folder.

This is auto-managed by the plugin and does not require a client mod.

Config keys:

- `datapack.name`
- `datapack.write_legacy_alias`

## Documentation

Wiki:

- https://eightl-projects.vercel.app/overview

Key sections:

- Mechanics hub: https://eightl-projects.vercel.app/mechanics
- Commands reference: https://eightl-projects.vercel.app/commands
- Config guide: https://eightl-projects.vercel.app/config
- Progression mechanics: https://eightl-projects.vercel.app/mechanics/progression
- Lava and heat mechanics: https://eightl-projects.vercel.app/mechanics/lava-and-heat

Use the wiki for full player-facing guides, progression routing, and YAML examples.

## Compatibility

- Biome key fallback mapping includes Terralith, Tectonic, and Incendium custom biomes.
- AuraSkills is incompatible unless its Fishing skill is disabled:
  `plugins/AuraSkills/skills.yml` -> `fishing` -> `enabled: false`
- Running alongside other fishing plugins is experimental and unsupported. Use at your own risk.

## Third-Party Assets

- Some custom heads are sourced from community head databases (including minecraft-heads.com) and Mojang texture servers.
- Third-party assets are not re-licensed by this project's MIT license.
- See `THIRD_PARTY_NOTICES.md` for attribution and usage notes.

## Main Command

- `/fishing`
- Aliases: `/fish`, `/fs`

Common player subcommands:

- `/fish help`
- `/fish chances`
- `/fish shop`
- `/fish bag`
- `/fish recipe`
- `/fish encyclopedia`
- `/fish artifacts`
- `/fish stats`
- `/fish autosell`
- `/fish heat`

Admin subcommands:

- `/fish addxp <player> <amount>`
- `/fish setlevel <player> <level>`
- `/fish setchance <mob|artifact> <chance>`
- `/fish resetchances`
- `/fish reset <player>`
- `/fish give <player> <item> [count]`
- `/fish spawn <mob>`
- `/fish fulfill <player> <all|creatures|artifacts>`
- `/fish setheat <player> <amount>`
- `/fish setcoins <player> <amount>`
- `/fish reload`

Permissions:

- `fishrework.use`
- `fishrework.admin`
- `fishrework.reload`

## Notes

- This is a Paper plugin, not a client mod.
- Use the same plugin version and config set across all server nodes.
- Modrinth page: https://modrinth.com/plugin/fish-rework
