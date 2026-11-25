package org.jahia.modules.formidable.pdf.action;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfCopyFormActionTest {

    @Test
    void theZoneIsTheVisitorsWhenItIsARealOne() {
        // Verifies the header is never trusted beyond naming a zone the platform knows.
        assertEquals(ZoneId.of("Asia/Hong_Kong"), PdfCopyFormAction.zone(" Asia/Hong_Kong "));
        assertEquals(ZoneOffset.UTC, PdfCopyFormAction.zone(null));
        assertEquals(ZoneOffset.UTC, PdfCopyFormAction.zone(""));
        assertEquals(ZoneOffset.UTC, PdfCopyFormAction.zone("Mars/Olympus"));
        assertEquals(ZoneOffset.UTC, PdfCopyFormAction.zone("x".repeat(65)));
    }

    @Test
    void theFileNameHoldsOnlyLettersDigitsAndHyphens() {
        // Verifies the name the download header carries unescaped: no quote, no slash, no accent, no line break.
        assertEquals("reclamation-bagage-3F9A-12BC-77D0.pdf", PdfCopyFormAction.fileName("Réclamation bagage", "3F9A-12BC-77D0"));
        assertEquals("a-b-c-3F9A.pdf", PdfCopyFormAction.fileName("\"a\"/..\\b\r\nc", "3F9A"));
        assertEquals("submission-3F9A.pdf", PdfCopyFormAction.fileName("陳美玲", "3F9A"));
        assertEquals("submission-3F9A.pdf", PdfCopyFormAction.fileName(null, "3F9A"));
        assertEquals(60 + "-3F9A.pdf".length(), PdfCopyFormAction.fileName("x".repeat(200), "3F9A").length());
    }
}
