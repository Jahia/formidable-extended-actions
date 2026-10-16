package org.jahia.modules.formidable.redirect;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor sees raw technical names when a label is missing: the type, every property of the CND and every choice of
 * the destination have a label in every language the module ships, a tooltip for the type and the properties, and the
 * translations are not the English text.
 */
class ResourceBundlesTest {

    private static final String TYPE = "fmdbred_redirectAction";
    private static final Pattern PROPERTY = Pattern.compile("^ - ([A-Za-z:]+) \\(", Pattern.MULTILINE);
    private static final List<Locale> TRANSLATIONS = List.of(Locale.FRENCH, Locale.GERMAN, Locale.forLanguageTag("es"),
            Locale.forLanguageTag("pt"));

    private static List<String> keys() throws IOException {
        String cnd;
        try (InputStream in = ResourceBundlesTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in, "definitions.cnd is in the bundle");
            cnd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> keys = new ArrayList<>(List.of(TYPE, TYPE + ".ui.tooltip", TYPE + ".targetType.page", TYPE + ".targetType.path"));
        Matcher matcher = PROPERTY.matcher(cnd);
        while (matcher.find()) {
            if (!matcher.group(1).equals("jcr:title")) {
                keys.add(TYPE + "." + matcher.group(1));
                keys.add(TYPE + "." + matcher.group(1) + ".ui.tooltip");
            }
        }
        return keys;
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("resources.formidable-redirect", locale, ResourceBundlesTest.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Test
    void everyKeyHasAnEnglishText() throws IOException {
        // Verifies the base bundle: a property added to the CND without its label and tooltip fails here.
        ResourceBundle english = bundle(Locale.ROOT);
        List<String> keys = keys();
        assertTrue(keys.size() >= 10, "the type, its three properties and the two destinations");
        for (String key : keys) {
            assertTrue(english.containsKey(key), "English: " + key);
            assertFalse(english.getString(key).isBlank(), "English text of " + key);
        }
    }

    @Test
    void everyKeyIsTranslated() throws IOException {
        // Verifies each translation: present for every key, and not the English text left in place.
        ResourceBundle english = bundle(Locale.ROOT);
        for (Locale locale : TRANSLATIONS) {
            ResourceBundle translated = bundle(locale);
            for (String key : keys()) {
                assertTrue(translated.containsKey(key), locale + ": " + key);
                if (key.endsWith(".ui.tooltip") || key.equals(TYPE)) {
                    assertNotEquals(english.getString(key), translated.getString(key), locale + " translates " + key);
                }
            }
        }
    }
}
