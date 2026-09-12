# Changelog

## Unreleased

- One Java 21 JAR now targets Paper 1.21.x and current 26.x, with compatible attribute names and fallbacks for newer bait materials and biomes.
- Fixed inventory close rewriting names and appending blank lore lines to non-FishRework items.
- Added automatic EvenMoreFish detection and optional one-time configuration with an EMF config backup.
- Added rod-based catch ownership so tagged EMF rods bypass Fish Rework while regular rods remain unchanged.
- Added a configurable, automatically discovered crafting recipe for EvenMoreFish's default custom rod.

## 1.0.0

- Initial public release.
- Custom fishing progression, mobs, loot, bags, recipes, and advancement systems.
- YAML-driven content loading for items, mobs, biomes, recipes, and artifacts.

## 1.0.1

- Added release hardening for publishing:
- Added base command permission enforcement (`fishrework.use`).
- Added feature-toggle wiring for recipes, advancements, display cases, totems, bags, and tips.
- Added treasure chest toggle handling in fishing roll logic.
- Added YAML default-key migration for bundled resource files.
- Added configurable datapack extraction target and optional legacy alias output.
- Added Gradle resource expansion for plugin version.
- Added Incendium custom Nether biome fallback mapping for fishing profiles.
- Added compatibility and legal-notice documentation for AuraSkills, other fishing plugins, and third-party heads.
