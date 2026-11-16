package org.jahia.modules.formidable.pdf.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jahia.modules.formidable.pdf.model.ReceiptContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptPdfTest {

    private static ReceiptContent content(List<ReceiptContent.Section> sections) {
        return new ReceiptContent("Réclamation bagage", "Copie de votre envoi",
                List.of(new ReceiptContent.Entry("Référence", List.of("3F9A-12BC-77D0"))), sections,
                "Notre service bagages vous répond sous trois jours ouvrés.", "Référence 3F9A-12BC-77D0 - page {page} sur {pages}", "3f9a12bc77d0");
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void theDocumentReadsBackWithItsAccentsItsGroupsAndItsFoot() throws IOException {
        // Verifies the copy end to end: what goes in is what a PDF reader finds, in the order of the form.
        byte[] pdf = new ReceiptPdf().render(content(List.of(
                new ReceiptContent.Section("Votre voyage", List.of(new ReceiptContent.Entry("Numéro de vol", List.of("SL812")),
                        new ReceiptContent.Entry("Aéroport d'arrivée", List.of("Tokyo Narita (NRT)")))),
                new ReceiptContent.Section("Votre réclamation / Les dommages", List.of(
                        new ReceiptContent.Entry("Photos des dommages", List.of("coque-fissurée.jpg (45 KB)", "roue.jpg (46 KB)")))))));

        String text = text(pdf);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        for (String expected : List.of("Réclamation bagage", "Copie de votre envoi", "3F9A-12BC-77D0", "Votre voyage", "Numéro de vol", "SL812",
                "Aéroport d'arrivée", "Les dommages", "coque-fissurée.jpg (45 KB)", "trois jours ouvrés", "page 1 sur 1")) {
            assertTrue(text.contains(expected), expected + " in:\n" + text);
        }
        assertTrue(text.indexOf("Votre voyage") < text.indexOf("Les dommages"), "the groups keep the form's order");
    }

    @Test
    void aLongSubmissionRunsOverSeveralNumberedPages() throws IOException {
        List<ReceiptContent.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 70; i++) {
            entries.add(new ReceiptContent.Entry("Question " + i, List.of("Answer " + i)));
        }

        byte[] pdf = new ReceiptPdf().render(content(List.of(new ReceiptContent.Section("", entries))));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 3, "pages: " + document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Answer 70"));
            assertTrue(text.contains("page 1 sur " + document.getNumberOfPages()));
            assertTrue(text.contains("page " + document.getNumberOfPages() + " sur " + document.getNumberOfPages()));
        }
    }

    @Test
    void aLongWordIsCutAndAMissingGlyphBecomesAQuestionMark() throws IOException {
        // Verifies the two inputs that would otherwise fail the document: no space to break on, no glyph to draw.
        String word = "x".repeat(400);
        byte[] pdf = new ReceiptPdf().render(content(List.of(new ReceiptContent.Section("", List.of(
                new ReceiptContent.Entry("Long", List.of(word)), new ReceiptContent.Entry("Name", List.of("陳美玲 Mei\tChan\u0007")))))));

        String text = text(pdf);
        assertEquals(400, text.chars().filter(c -> c == 'x').count());
        assertTrue(text.contains("??? Mei Chan"), text);
        assertFalse(text.contains("陳"));
    }
}
