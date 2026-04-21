# Plugin Localization Guide

This plugin utilizes a dual-system approach for localization to ensure maximum flexibility. Player-facing system text (GUIs, command outputs, actionbars) is handled via a `lang_<locale>.yml` file, while configurable game content (custom items, pets, mobs) remains in their respective data files.

To fully translate this plugin into a new language (for example, Chinese `zh` or Spanish `es`), follow this step-by-step guide.

## Step 1: Set the Locale in Config

Open the main `config.yml` file for the plugin and define the locale string you want to use. Add this property at the root level:

```yaml
locale: "zh"
```

## Step 2: Create the Language File

The plugin automatically extracts a default `lang_en.yml` file when the server starts.
1. Navigate to the plugin's configuration folder (e.g., `plugins/BabyPets/` or `plugins/FishRework/`).
2. Make a duplicate of the existing `lang_en.yml` file.
3. Rename the copy to match the locale you set in Step 1 (e.g., rename to `lang_zh.yml`).

## Step 3: Translate the System Text (The New File)

Open your newly created language file (`lang_zh.yml`) and translate the values on the right side of the colon.

**Example Original:**
```yaml
gui.leftclick_to_select: "Left-click to select"
```
**Example Translated:**
```yaml
gui.leftclick_to_select: "左键点击选择"
```

*Note: If you leave a specific key untranslated or missing, the plugin will seamlessly fall back to displaying the default English text for that specific line.*

## Step 4: Translate the Content Configurations (The Existing Files)

Because the plugin loads complex custom recipes, item stats, and mob parameters from YAML, the text related to those objects lives alongside their configurations. 

You must manually translate the `display_name`, `name`, and `lore` properties inside files such as:
* `pets.yml` (for BabyPets names/descriptions)
* `items.yml` (for FishRework custom items/baits)
* `mobs.yml` (for FishRework custom sea creatures)

## Step 5: Reload or Restart

Save all of your files and either fully restart your server or run the plugin's reload command (e.g., `/pets reload` or `/fishing reload`). All menus, command outputs, and stat displays will now dynamically render in your target language!
