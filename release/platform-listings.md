# Fish Rework Platform Listings

## Modrinth

### Suggested Metadata

```yaml
name: Fish Rework
project_type: plugin
license: MIT
loaders:
  - paper
server_side: required
client_side: optional
game_versions:
  - 1.21.11
```

### Suggested Tags

- fishing
- progression
- mmorpg
- paper
- custom-mobs

### Summary

Progression-focused fishing overhaul for Paper: fish up creatures, fight mobs, earn XP, loot, and upgrades.

### Description

Fish Rework is a server-side Paper plugin that turns fishing into a progression system.

Instead of only catching items, players can fish up creatures, fight custom mobs, gain Fishing XP, unlock progression, and collect loot through a full gameplay loop.

## Highlights

- Fishing progression with XP and level-based unlock flow.
- Custom mobs and combat catches integrated into fishing.
- Treasure, bags, recipes, advancements, and GUI systems.
- YAML-driven content for items, mobs, recipes, biomes, and artifacts.
- Built for Paper servers (Java 21, API 1.21).

## Datapack Behavior

On startup, the plugin writes a server-side datapack with custom fishing enchantment definitions into the overworld datapacks folder.

This is automatic and does not require a client mod.

Config keys:

- `datapack.name`
- `datapack.write_legacy_alias`

## Compatibility

- Includes custom-biome fallback mapping for Terralith, Tectonic, and Incendium.
- AuraSkills users must disable AuraSkills Fishing skill:
  `plugins/AuraSkills/skills.yml` -> `fishing` -> `enabled: false`
- Experimental incompatibility with other fishing plugins. Use at your own risk.

## Commands and Permissions

Command:

- `/fishing` (aliases: `/fish`, `/fs`)

Permissions:

- `fishrework.use`
- `fishrework.admin`
- `fishrework.reload`

## Legal and Attribution

- Fish Rework is inspired by MMORPG-style fishing loops.
- Not affiliated with or endorsed by Hypixel or Hypixel SkyBlock.
- Some custom heads are sourced from community databases, including minecraft-heads.com.
- See `THIRD_PARTY_NOTICES.md` for third-party notices and attribution guidance.

## Recommended Release Notes Snippet (1.0.1)

- Publishing hardening update.
- Feature-toggle wiring and permission tightening.
- Datapack extraction configurability improvements.
- Added Incendium custom Nether biome fallbacks.
- Added explicit compatibility and third-party notices.

## Hangar

### Suggested Metadata

```yaml
platforms:
  - PAPER
java: 21
minecraft: 1.21.11
license: MIT
```

### Tagline

Progression-focused fishing overhaul with combat catches, custom mobs, loot, and leveling.

### Description

Fish Rework overhauls fishing into a progression mechanic for Paper servers.

Players can fish up creatures and mobs, fight them for rewards, and progress through a leveling loop with unlock-driven gameplay.

It is server-side only and auto-manages its enchantment datapack output in world datapacks.

Compatibility notes:

- Terralith/Tectonic/Incendium biome keys are mapped to fishing fallback groups.
- AuraSkills requires Fishing skill disabled to avoid overlap.
- Any other fishing plugin combination is experimental and unsupported.

Legal notes:

- Inspired by MMORPG fishing loops.
- Not affiliated with Hypixel or Hypixel SkyBlock.
- Community head textures may require source attribution/terms compliance.

## Spigot

### Resource Short Description

Progression fishing plugin for Paper: fish up creatures, fight mobs, earn XP, and unlock loot systems.

### Resource Long Description

Fish Rework transforms fishing into a full progression path.

Core loop:

- Fish to trigger catches, including combat encounters.
- Defeat fishing-spawned mobs for rewards and progression.
- Level up fishing and access broader systems (loot, recipes, GUI-based flows).

Tech and setup:

- Paper API 1.21
- Java 21
- Server-side plugin, no client mod required
- Writes a fishing enchantment datapack to world datapacks on startup

Compatibility:

- Terralith, Tectonic, and Incendium custom biome fallbacks included.
- AuraSkills: disable AuraSkills Fishing skill before use.
- Mixing with other fishing plugins is experimental and may break behavior.

Commands and permissions:

- `/fishing` (`/fish`, `/fs`)
- `fishrework.use`
- `fishrework.admin`
- `fishrework.reload`

Legal and attribution:

- Inspired by MMORPG-style fishing progression loops.
- Not affiliated with or endorsed by Hypixel/Hypixel SkyBlock.
- Includes community-sourced head textures; check third-party terms and attribution.

## Mandatory Statements To Keep In Listing

- "Not affiliated with or endorsed by Hypixel or Hypixel SkyBlock."
- "AuraSkills requires its Fishing skill to be disabled when used with Fish Rework."
- "Using Fish Rework with other fishing plugins is experimental and unsupported."
- "Fish Rework writes a server-side datapack for custom enchantments into world datapacks on startup."
- "Some custom head textures are third-party/community sourced and may require attribution based on source terms."

## Additional Statements Recommended

- "Back up your world before first startup, since datapack files are written into world datapacks."
- "Fish Rework is a Paper plugin and is not a client-side mod."
- "Third-party assets and trademarks are not re-licensed by this project's MIT license."
- "Tested target: Paper 1.21.11 with Java 21."
