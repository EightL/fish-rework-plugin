package com.fishrework;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourceTest {

    @Test
    void configDoesNotContainTranslatableMessageSections() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));

        assertFalse(config.contains("\nmessages:\n"), "config.yml should not keep translatable messages");
        assertFalse(config.contains("\n  tips:\n"), "config.yml should not keep translatable fishing tip text");
    }

    @Test
    void langFileContainsKeysForMigratedRuntimeStrings() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/lang_en.yml"));

        assertTrue(lang.contains("lavabobbertask.lava_catch_escaped:"), "lang_en.yml should define lava escape text");
        assertTrue(lang.contains("lavabobbertask.lava_reel_now:"), "lang_en.yml should define lava reel prompt text");
        assertTrue(lang.contains("fishingtip.prefix:"), "lang_en.yml should define fishing tip prefix text");
        assertTrue(lang.contains("fishingtip.tip_1:"), "lang_en.yml should define fishing tip content");
        assertTrue(lang.contains("specialcraftinggui.crafted_result:"), "lang_en.yml should define crafted-result feedback");
        assertTrue(lang.contains("sellshopgui.balance_prefix:"), "lang_en.yml should define vendor balance text");
        assertTrue(lang.contains("skill.fishing.name:"), "lang_en.yml should define localized skill names");
        assertTrue(lang.contains("skill.fishing.description:"), "lang_en.yml should define localized skill descriptions");
        assertTrue(lang.contains("skill.fishing.mob_source:"), "lang_en.yml should define localized mob XP source text");
        assertTrue(lang.contains("biomegroup.normal_ocean.name:"), "lang_en.yml should define localized biome group names");
        assertTrue(lang.contains("rarity.common.name:"), "lang_en.yml should define localized rarity names");
        assertTrue(lang.contains("heattier.normal.name:"), "lang_en.yml should define localized heat tier names");
        assertTrue(lang.contains("heatmanager.scc_bonus_suffix:"), "lang_en.yml should define localized SCC suffix text");
        assertTrue(lang.contains("itemmanager.treasure.common_name:"), "lang_en.yml should define treasure chest names");
        assertTrue(lang.contains("itemmanager.treasure.nether_name:"), "lang_en.yml should define nether treasure names");
        assertTrue(lang.contains("currentfishchancesgui.bait_inactive_suffix:"), "lang_en.yml should define inactive bait suffix text");
        assertTrue(lang.contains("currentfishchancesgui.page_sort:"), "lang_en.yml should define chance page sort text");
        assertTrue(lang.contains("skilldetailgui.top_chance_entry:"), "lang_en.yml should define top chance entry formatting");
        assertTrue(lang.contains("fishingcommand.ui_state_on:"), "lang_en.yml should define localized ON state text");
    }

    @Test
    void translatedLangFilesContainAllEnglishKeys() throws IOException {
        Set<String> englishKeys = extractYamlKeys("src/main/resources/lang_en.yml");
        Set<String> spanishKeys = extractYamlKeys("src/main/resources/lang_es.yml");
        Set<String> chineseKeys = extractYamlKeys("src/main/resources/lang_zh_CN.yml");

        Set<String> missingSpanishKeys = new TreeSet<>(englishKeys);
        missingSpanishKeys.removeAll(spanishKeys);

        Set<String> missingChineseKeys = new TreeSet<>(englishKeys);
        missingChineseKeys.removeAll(chineseKeys);

        assertTrue(missingSpanishKeys.isEmpty(), "lang_es.yml should contain every lang_en.yml key, missing: " + missingSpanishKeys);
        assertTrue(missingChineseKeys.isEmpty(), "lang_zh_CN.yml should contain every lang_en.yml key, missing: " + missingChineseKeys);
    }

    @Test
    void langFileContainsKeysForLocalizedCurrencyAndBiomeBaits() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/lang_en.yml"));

        assertTrue(lang.contains("common.currency_name:"), "lang_en.yml should define the localized currency label");
        assertTrue(lang.contains("bait.treasure_bait.name:"), "lang_en.yml should define regular bait names");
        assertTrue(lang.contains("bait.treasure_bait.desc:"), "lang_en.yml should define regular bait descriptions");
        assertTrue(lang.contains("bait.xp_bait.name:"), "lang_en.yml should define XP bait names");
        assertTrue(lang.contains("bait.frozen_peaks.name:"), "lang_en.yml should define biome bait names");
        assertTrue(lang.contains("bait.nether_wastes.name:"), "lang_en.yml should define nether biome bait names");
    }

    @Test
    void shopAndLoreSourcesAvoidLocaleLockedFallbackLogic() throws IOException {
        String buyShop = Files.readString(Path.of("src/main/java/com/fishrework/gui/BuyShopGUI.java"));
        String itemLoader = Files.readString(Path.of("src/main/java/com/fishrework/loader/YamlItemLoader.java"));
        String loreManager = Files.readString(Path.of("src/main/java/com/fishrework/manager/LoreManager.java"));
        String loreUpdateListener = Files.readString(Path.of("src/main/java/com/fishrework/listener/LoreUpdateListener.java"));

        assertFalse(buyShop.contains("economy.currency_name"),
                "BuyShopGUI should not read the currency label directly from config");
        assertTrue(itemLoader.contains(".lore."),
                "YamlItemLoader should resolve localized lore keys from lang files");
        assertFalse(loreManager.contains("plain.contains(\"Rare Creature Chance:\")"),
                "LoreManager should not strip generated lore using hardcoded English stat labels");
        assertFalse(loreManager.contains("statLine(\"Water Movement: \""),
                "LoreManager full-set lore labels should not be rendered from hardcoded English text");
        assertFalse(loreManager.contains("statLine(\"Night multiplier: \""),
                "LoreManager full-set bonus text should not be rendered from hardcoded English text");
        assertFalse(loreUpdateListener.contains("freshMeta.displayName(oldMeta.displayName())"),
                "LoreUpdateListener should not restore stale display names after localized rebuilds");
    }

    private Set<String> extractYamlKeys(String file) throws IOException {
        Pattern keyPattern = Pattern.compile("^([A-Za-z0-9_.-]+):", Pattern.MULTILINE);
        Matcher matcher = keyPattern.matcher(Files.readString(Path.of(file)));
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
