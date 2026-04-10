# Fish Rework

This plugin redesigns the fishing mechanic by fishing up unique sea creatures instead of normal fish items. A **Fishing Skill** progression system, **90+ creatures**, **120+ custom items**, **80+ crafting recipes** -- custom gear, weapons, armor. **Lava fishing** mechanic. **Treasure chests** with collectable **artifacts**. New fishing GUI featuring **Fishing Shop**, **Encyclopedia**, Advancements and much more!

---

## How it works

To get started, open up fishing GUI with `/fish`, and start fishing! Each level gives you additive bonuses + unlocks new *biome exclusive* sea creatures and recipes. This plugin is hard -- you should follow the progression system, leveling up your fishing level and crafting subsequent items which are unlocked. Use the *recipe browser* to see custom recipes for the items. Type `/fish help` for available commands.

>**TIP #1:** Progression is locked behind biome-specific sea creatures, though you don't have to travel to the biome, instead you can **use baits**. Open up the Fishing Shop (`/fs shop`), in which you can buy biome-specific baits. Put them in your off-hand slot while fishing.  


>**TIP #2:** Type `/fs chances` which displays available sea creatures in the specific biome you are in.
---

## Features

### Fishing progression
- 50 levels with a scaling XP curve
- Per-level bonuses to catch speed, treasure chance, and XP multiplier
- Level gates on gear, recipes, and shop access
- Double catch mechanic that scales with level

### Sea creatures
- 90+ custom mobs across 6 rarity tiers
- Biome-specific spawn pools — cold ocean, frozen ocean, river, jungle, all Nether biomes,
  and more
- 30+ custom mob abilities (projectile volleys, AoE explosions, tether attacks, charges)

### Lava fishing
- A separate fishing system for lava, unlocks at level 27 (**custom rods required!**)
- Heat system: lava catches build heat, high heat applies slowness and weakness
- Heat tiers also grant Sea Creature Chance bonuses,
- Dedicated Nether mob pool with its own more difficult creature set

### Gear and crafting
- 120+ custom items: fishing rods, armor sets, crafting materials
- Custom shaped recipes, gated by fishing levels and advancements
- Custom Gear Upgrade GUI: applies Sea Creature Attack/Defense bonuses to weapons and armor
- Custom enchantments: 2 new enchantments; fishing enchantments max level -> 6 (though Gear Upgrade GUI)

### Economy
- Custom currency: Doubloons
- Sell shop for fish, materials, and custom drops
- Buy shop for enchanted books, baits, and equipment
- Auto-sell toggle for common catches (experimental)

### Collection and storage
- Treasure chests: small chance to fish up a treasure chest (custom loot, Common--Legendary rarity)
- 20 artifacts — rare cosmetic collectibles found in treasure chests
- Fishing encyclopedia tracking every creature you've caught
- Fish Bags: purchasable in Fishing Shop, sea creature drops automatically go in
- Display cases to showcase items

### GUIs
- Skills menu with fishing stats
- Recipe browser with recipe guides. Special crafting GUI (custom items can still be crafted in normal crafting table -- though vanilla recipe browser is buggy)
- Artifact collection
- Fishing Shop/Fish Vendor
- Upgrade GUI (unlocks at level 20)

### Everything else
- 12 bait types: rare creature chance, XP multiplier, treasure chance, heat negation, and
  biome-specific variants
- Custom advancement tree
- Fishing tips shown to active players (toggleable per player)
- Floating damage numbers on sea creature hits
- Server-wide rare catch broadcasts
- Fishing journal
  
### Nearly everything is tunable in the YAML configs

---

## Requirements

|                   |                |
|-------------------|----------------|
| Server software   | Paper 26.1.1+  |
| Java              | 25             |
| Hard dependencies | None           |

---

## Installation

1. Drop the jar into your `/plugins` folder
2. Start the server once to generate configs
3. Tune `config.yml`, `mobs.yml`, `items.yml`, etc. to your liking

---

## Useful Commands

All commands work under `/fishing`, `/fish`, or `/fs`.

| Command               | What it does                                    |
|-----------------------|-------------------------------------------------|
| `/fish`               | Open the skills menu                            |
| `/fish chances`       | Show sea creature chance pool for current biome |
| `/fish shop`          | Buy/sell shop                                   |
| `/fish recipe`        | Browse crafting recipes                         |
| `/fish encyclopedia`  | Creatures you've caught                         |
| `/fish artifacts`     | Artifact collection                             |
| `/fish heat`          | Check your current heat level (lava fishing)    |
| `/fish autosell`      | Toggle auto-selling common fish (experimental)  |
| `/fish notifications` | Toggle fishing tips                             |
| `/fish particles`     | Reduce intensive particles                      |
| `/fish dmgindicator`  | Toggle damage indicators                        |
| `/fish help`          | Full command list                               |

**Admin commands** (requires `fishrework.admin`):

| Command                | What it does                              |
|------------------------|-------------------------------------------|
| `/fish addxp`          | Add fishing XP to a player                |
| `/fish give`           | Give a custom item to a player            |
| `/fish spawn`          | Spawn a specific sea creature             |
| `/fish reset`          | Reset a player's fishing progression      |
| `/fish setcoins`       | Set a player's Doubloon balance           |
| `/fish setheat`        | Set a player's heat level                 |
| `/fish setchance`      | Set a specific creature's chance weight   |
| `/fish resetchances`   | Reset creature chance pool to default     |
| `/fish xpmultiplier`   | Set XP multiplier for sea creatures       |
| `/fish fulfill`        | Fulfill collection entries, recipes, etc. |
| `/fish reload`         | Reload config without a restart           |

---

## Configuration

Config is split across six files: `config.yml`, `mobs.yml`, `items.yml`, `biomes.yml`,
`recipes.yml`, and `artifacts.yml`. 
Individual systems (economy, lava fishing, custom mobs, advancements, etc.) can each be toggled off independently.

Wiki: **[fish-rework.vercel.app](https://fish-rework.vercel.app)**

---

## Compatibility

- Compatible with **Terralith**, **Tectonic**, **Incendium** custom biomes
- **AuraSkills** is incompatible unless its **Fishing skill is disabled: `plugins/AuraSkills/skills.yml` → `fishing` → `enabled: false`**
- Running alongside other fishing plugins is experimental and unsupported

---

> ### Balance disclaimer
>
> This plugin has not been heavily playtested. Drop rates, economy prices, and mob
> difficulty are rough starting values — some things are probably too easy, some too
> punishing. 
>
> If something feels off, I'd genuinely like to know.

---

## Feedback and support

Bug reports, balance feedback, and suggestions are all welcome.

Discord: **[discord.gg/axZRQ5Sy](https://discord.gg/axZRQ5Sy)**

GitHub issues are also open if you prefer that.

---

*GitHub Repo: SOON*
