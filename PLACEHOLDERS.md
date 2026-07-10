# PlaceholderAPI

FishRework registers an internal PlaceholderAPI expansion when PlaceholderAPI is installed. No eCloud expansion download is required.

## Persisted placeholders

These work for online and offline players. Offline values are loaded asynchronously and cached for 60 seconds, so the first parse can briefly return zero while the cache is populated.

| Placeholder | Value |
| --- | --- |
| `%fishrework_fish_caught%` | Lifetime fish caught |
| `%fishrework_total_fish_caught%` | Alias of `fish_caught` |
| `%fishrework_treasures_caught%` | Lifetime treasure chests caught |
| `%fishrework_treasure_catches%` | Alias of `treasures_caught` |
| `%fishrework_treasure_chest_catches%` | Alias of `treasures_caught` |
| `%fishrework_total_treasures_caught%` | Alias of `treasures_caught` |
| `%fishrework_fishing_level%` | Fishing level |
| `%fishrework_fishing_xp%` | XP in the current level |
| `%fishrework_fishing_xp_required%` | XP required for the next level |
| `%fishrework_fishing_xp_remaining%` | XP remaining until the next level |
| `%fishrework_fishing_xp_percent%` | Current-level XP percentage |
| `%fishrework_fishing_max_level%` | Configured maximum fishing level |
| `%fishrework_balance%` | Raw balance |
| `%fishrework_balance_raw%` | Alias of `balance` |
| `%fishrework_balance_fixed%` | Balance with two decimal places |
| `%fishrework_balance_formatted%` | Balance formatted by the active economy provider |
| `%fishrework_doubloons%` | Alias of `balance` |
| `%fishrework_doubloons_fixed%` | Alias of `balance_fixed` |
| `%fishrework_doubloons_formatted%` | Alias of `balance_formatted` |
| `%fishrework_currency_name%` | Active currency name |
| `%fishrework_lifetime_doubloons%` | Lifetime internal Doubloons earned |
| `%fishrework_encyclopedia_caught%` | Unique encyclopedia entries discovered |
| `%fishrework_encyclopedia_total%` | Total registered encyclopedia entries |
| `%fishrework_encyclopedia_remaining%` | Entries remaining to discover |
| `%fishrework_encyclopedia_percent%` | Encyclopedia completion percentage |
| `%fishrework_artifacts_found%` | Unique artifacts discovered |
| `%fishrework_artifacts_total%` | Total registered artifacts |
| `%fishrework_artifacts_remaining%` | Artifacts remaining to discover |
| `%fishrework_artifacts_percent%` | Artifact completion percentage |
| `%fishrework_auto_sell_mode%` | `off`, `other`, or `all` |
| `%fishrework_economy_provider%` | Active provider ID |
| `%fishrework_economy_provider_name%` | Active provider display name |
| `%fishrework_double_catch_bonus_level%` | Level-derived double-catch percentage |
| `%fishrework_treasure_bonus_level%` | Level-derived treasure percentage |
| `%fishrework_xp_multiplier_level%` | Level-derived XP multiplier |

Offline balance values are blank when an external Vault or VaultUnlocked economy is active because its offline balance is not stored by FishRework.

## Live placeholders

These include live equipment, held bait, artifacts, enchantments, nearby totems, world, or session state. They return blank for offline players.

| Placeholder | Value |
| --- | --- |
| `%fishrework_double_catch_bonus%` | Current level, equipment, and artifact bonus |
| `%fishrework_treasure_bonus%` | Current level, equipment, artifact, totem, enchantment, and bait bonus |
| `%fishrework_rare_creature_bonus%` | Current equipment, artifact, enchantment, and bait bonus |
| `%fishrework_xp_multiplier%` | Effective level, global, equipment, and artifact XP multiplier |
| `%fishrework_fishing_speed%` | Current rod, enchantment, and artifact fishing speed |
| `%fishrework_heat%` | Current heat |
| `%fishrework_active_bait%` | Localized name of the held bait |
| `%fishrework_session_catches%` | Catches this session |
| `%fishrework_session_treasures%` | Treasures this session |
| `%fishrework_session_xp%` | Fishing XP earned this session |
| `%fishrework_session_doubloons%` | Currency earned this session |
| `%fishrework_session_mobs_killed%` | Fished mobs killed this session |
| `%fishrework_session_levels_gained%` | Fishing levels gained this session |
| `%fishrework_session_discoveries%` | New encyclopedia discoveries this session |
| `%fishrework_session_peak_heat%` | Highest heat reached this session |
| `%fishrework_session_heat_damage%` | Heat damage taken this session |

Test a placeholder with `/papi parse me %fishrework_fishing_level%`.
