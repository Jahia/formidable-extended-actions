package org.jahia.modules.formidable.pdf;

import org.jahia.modules.formidable.pdf.store.ReceiptStore;
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
 * The editor sees raw technical names when a label is missing, and the visitor a key: the type and every property of
 * the CND have a label and a tooltip, every sentence exists, in English and in French, and French is not English.
 */
class PdfCopyPartsTest {

    private static final String TYPE = "fmdbpdf_pdfCopyAction";
    private static final Pattern PROPERTY = Pattern.compile("^ - ([A-Za-z:]+) \\(", Pattern.MULTILINE);
    private static final List<String> SENTENCES = List.of("visitor.download", "visitor.expired", "pdf.subtitle", "pdf.reference",
            "pdf.submittedOn", "pdf.yes", "pdf.no", "pdf.footer");

    private static List<String> keys() throws IOException {
        String cnd;
        try (InputStream in = PdfCopyPartsTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertNotNull(in, "definitions.cnd is in the bundle");
            cnd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> keys = new ArrayList<>(List.of(TYPE, TYPE + ".ui.tooltip"));
        Matcher matcher = PROPERTY.matcher(cnd);
        while (matcher.find()) {
            if (!matcher.group(1).equals("jcr:title")) {
                keys.add(TYPE + "." + matcher.group(1));
                keys.add(TYPE + "." + matcher.group(1) + ".ui.tooltip");
            }
        }
        keys.addAll(SENTENCES);
        return keys;
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(Texts.BUNDLE, locale, PdfCopyPartsTest.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Test
    void everyLabelTooltipAndSentenceExistsInEnglishAndInFrench() throws IOException {
        // Verifies both bundles: a property added to the CND without its label and tooltip fails here.
        ResourceBundle english = bundle(Locale.ROOT);
        ResourceBundle french = bundle(Locale.FRENCH);
        List<String> keys = keys();
        assertTrue(keys.size() >= 16, "the type, its three properties and the eight sentences");
        for (String key : keys) {
            assertTrue(english.containsKey(key) && !english.getString(key).isBlank(), "English: " + key);
            assertTrue(french.containsKey(key) && !french.getString(key).isBlank(), "French: " + key);
            if (!key.equals("pdf.footer") && !key.equals("fmdbpdf_pdfCopyAction.buttonLabel.ui.tooltip")) {
                assertNotEquals(english.getString(key).replace("PDF", ""), french.getString(key).replace("PDF", ""), "French is a translation: " + key);
            }
        }
    }

    @Test
    void aSentenceIsReadInTheVisitorsLanguageAndInEnglishOtherwise() {
        assertEquals("Télécharger une copie (PDF)", Texts.in(Locale.FRENCH, "visitor.download"));
        assertEquals("Download a copy (PDF)", Texts.in(Locale.JAPANESE, "visitor.download"));
        assertEquals("Download a copy (PDF)", Texts.in(null, "visitor.download"));
        assertEquals("Référence 3F9A-12BC-77D0 - page {page} sur {pages}", Texts.in(Locale.FRENCH, "pdf.footer", "3F9A-12BC-77D0"));
        assertEquals("no.such.key", Texts.in(Locale.FRENCH, "no.such.key"));
    }

    @Test
    void theStoreKeepsWhatTheEnricherHandsToTheBrowser() {
        ReceiptStore.Receipt receipt = new ReceiptStore.Receipt(new byte[] {1}, "claim-3F9A-12BC-77D0.pdf", "Download");
        assertFalse(receipt.fileName().contains("\""), "a name that needs no escaping in a header");
    }
}
