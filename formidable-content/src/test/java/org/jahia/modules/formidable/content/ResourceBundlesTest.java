package org.jahia.modules.formidable.content;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor sees raw technical names when a label is missing: every type and every property of the CND has a label and
 * a tooltip in English, French and Portuguese, and the French and Portuguese texts are not the English ones.
 */
class ResourceBundlesTest {

    private static final String TYPE = "fmdbcnt_createContentAction";
    private static final Pattern PROPERTY = Pattern.compile("^ - ([A-Za-z:]+) \\(", Pattern.MULTILINE);
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt");

    /** The module's resource bundle: the editor labels and the sentences a visitor reads (the bundle is named after the module). */
    private static final String MODULE_BUNDLE = "formidable-content";

    /** The node type of the module in its definitions.cnd. */
    private static String cnd() throws IOException {
        try (InputStream in = ResourceBundlesTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in, "definitions.cnd is in the bundle");
            String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = all.indexOf("[fmdbcnt:createContentAction]");
            assertTrue(start >= 0, "the content type is in definitions.cnd");
            return all.substring(start);
        }
    }

    private static List<String> keys() throws IOException {
        List<String> keys = new ArrayList<>(List.of(TYPE, TYPE + ".ui.tooltip"));
        Matcher matcher = PROPERTY.matcher(cnd());
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name.equals("jcr:title")) {
                continue;
            }
            keys.add(TYPE + "." + name);
            keys.add(TYPE + "." + name + ".ui.tooltip");
        }
        return keys;
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("resources." + MODULE_BUNDLE, locale, ResourceBundlesTest.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Test
    void theCndHasTheTenProperties() throws IOException {
        // 9 settings (the mapping document replaced eight text settings) + the autocreated title: a property added to
        // the CND without a label shows up here
        long settings = PROPERTY.matcher(cnd()).results().count();
        org.junit.jupiter.api.Assertions.assertEquals(10L, settings, "properties declared by the CND");
    }

    @Test
    void everyTypeAndPropertyHasALabelAndATooltipInEveryLanguage() throws IOException {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, PORTUGUESE}) {
            ResourceBundle bundle = bundle(locale);
            for (String key : keys()) {
                assertTrue(bundle.containsKey(key), key + " is missing in " + locale);
                assertFalse(bundle.getString(key).isBlank(), key + " is blank in " + locale);
            }
        }
    }

    @Test
    void frenchAndPortugueseAreTranslatedNotCopied() throws IOException {
        ResourceBundle english = bundle(Locale.ENGLISH);
        for (Locale locale : new Locale[]{Locale.FRENCH, PORTUGUESE}) {
            ResourceBundle bundle = bundle(locale);
            for (String key : keys()) {
                assertNotEquals(english.getString(key), bundle.getString(key), key + " is not translated in " + locale);
            }
        }
    }

    @Test
    void theFilesAreAsciiWithEscapes() throws IOException {
        List<String> names = new ArrayList<>();
        for (String suffix : List.of("", "_fr", "_pt")) {
            names.add(MODULE_BUNDLE + suffix + ".properties");
        }
        for (String name : names) {
            try (InputStream in = getClass().getResourceAsStream("/resources/" + name)) {
                assertNotNull(in, name);
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.chars().allMatch(c -> c < 128), name + " is ASCII");
            }
        }
    }

    @Test
    void theSubmitterProfileMixinHasItsLabelsAndTooltipsInEveryLanguageAndIsAddableToAnyContent() throws IOException {
        // the mixin, its one property and the way an editor adds it: declared in definitions.cnd before the action type
        String all;
        try (InputStream in = ResourceBundlesTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in);
            all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = all.indexOf("[fmdbcntmix:submitterProfile] mixin");
        assertTrue(start >= 0, "the mixin is declared");
        String body = all.substring(start, all.indexOf("[fmdbcnt:createContentAction]"));
        assertTrue(body.contains("extends = jnt:content") && body.contains("itemtype = content"), "addable to any content in the Content Editor");
        assertTrue(body.contains("- submitterUser (string) indexed=untokenized"), "searchable by exact user name");
        assertFalse(body.contains("hidden"), "editors set it");
        assertTrue(all.contains("<fmdbcntmix = 'http://www.jahia.org/jahia/fmdbcnt/mix/1.0'>"));
        assertTrue(all.indexOf("<fmdbcntmix") < all.indexOf("[fmdbcntmix:submitterProfile]"));

        List<String> mixinKeys = List.of("fmdbcntmix_submitterProfile", "fmdbcntmix_submitterProfile.ui.tooltip",
                "fmdbcntmix_submitterProfile.submitterUser", "fmdbcntmix_submitterProfile.submitterUser.ui.tooltip");
        ResourceBundle english = bundle(Locale.ENGLISH);
        assertEquals("Submitter profile", english.getString("fmdbcntmix_submitterProfile"));
        assertEquals("Submitter profile: user name", english.getString("fmdbcntmix_submitterProfile.submitterUser"));
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, PORTUGUESE}) {
            ResourceBundle bundle = bundle(locale);
            for (String key : mixinKeys) {
                assertTrue(bundle.containsKey(key), key + " is missing in " + locale);
                assertFalse(bundle.getString(key).isBlank(), key + " is blank in " + locale);
                if (!locale.equals(Locale.ENGLISH)) {
                    assertNotEquals(english.getString(key), bundle.getString(key), key + " is not translated in " + locale);
                }
            }
        }
    }
}
