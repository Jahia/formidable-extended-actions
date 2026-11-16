package org.jahia.modules.formidable.pdf.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jahia.modules.formidable.pdf.model.ReceiptContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Lays a {@link ReceiptContent} out as an A4 PDF: the heading, the facts about the submission, then each answer under
 * its label, group by group, and at the foot of every page the reference and the page number.
 *
 * <p>The text is set in Liberation Sans, the font PDFBox carries, embedded in the document: Latin, Greek and Cyrillic
 * print as typed. A character the font has no glyph for (Chinese, Arabic, an emoji) prints as a question mark, rather
 * than failing the document.</p>
 *
 * <p>Stateless; thread-safe: each call builds its own document.</p>
 */
public final class ReceiptPdf {

    /** The font PDFBox ships as its last-resort font, read from PDFBox's own jar. */
    private static final String FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final float MARGIN = 56f;
    private static final float WIDTH = PDRectangle.A4.getWidth();
    private static final float HEIGHT = PDRectangle.A4.getHeight();
    private static final float TEXT_WIDTH = WIDTH - 2 * MARGIN;
    private static final float FOOT = 64f;
    private static final float[] INK = {0.10f, 0.12f, 0.15f};
    private static final float[] MUTED = {0.38f, 0.42f, 0.47f};
    private static final float[] RULE = {0.80f, 0.83f, 0.86f};

    /**
     * @param content what the document says
     * @return the PDF
     * @throws IOException when the document cannot be written
     */
    public byte[] render(ReceiptContent content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            Page page = new Page(document, font(document));
            page.text(content.title(), 20f, INK, 26f);
            page.text(content.subtitle(), 10.5f, MUTED, 16f);
            page.space(6f);
            page.rule();
            page.space(14f);
            for (ReceiptContent.Entry fact : content.meta()) {
                page.entry(fact);
            }
            for (ReceiptContent.Section section : content.sections()) {
                page.space(8f);
                if (!section.title().isBlank()) {
                    page.keepTogether(46f);
                    page.text(section.title(), 13f, INK, 18f);
                    page.space(6f);
                    page.rule();
                    page.space(10f);
                }
                for (ReceiptContent.Entry entry : section.entries()) {
                    page.entry(entry);
                }
            }
            if (content.note() != null && !content.note().isBlank()) {
                page.space(10f);
                page.text(content.note(), 9.5f, MUTED, 13.5f);
            }
            page.close();
            page.footers(content.footer());
            document.getDocumentInformation().setTitle(content.title());
            document.getDocumentInformation().setKeywords(content.fingerprint());
            document.getDocumentInformation().setCreationDate(Calendar.getInstance());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PDFont font(PDDocument document) throws IOException {
        try (InputStream in = PDDocument.class.getResourceAsStream(FONT)) {
            // a PDFBox without its font file still writes the copy, in Helvetica: Latin-1 only
            return in == null ? new PDType1Font(Standard14Fonts.FontName.HELVETICA) : PDType0Font.load(document, in);
        }
    }

    /** The pen: where the next line goes, and a new page when the current one is full. */
    private static final class Page {

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float y;

        Page(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            open();
        }

        private void open() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = HEIGHT - MARGIN;
        }

        void close() throws IOException {
            stream.close();
        }

        void space(float height) {
            y -= height;
        }

        /** Starts a new page unless this much room is left: a heading is not left alone at the foot of a page. */
        void keepTogether(float height) throws IOException {
            if (y - height < FOOT) {
                stream.close();
                open();
            }
        }

        void rule() throws IOException {
            stream.setStrokingColor(RULE[0], RULE[1], RULE[2]);
            stream.setLineWidth(0.6f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(WIDTH - MARGIN, y);
            stream.stroke();
        }

        /** A label in small grey type, then each value on its own lines. */
        void entry(ReceiptContent.Entry entry) throws IOException {
            keepTogether(34f);
            text(entry.label(), 8.5f, MUTED, 12f);
            for (String value : entry.values()) {
                text(value, 11f, INK, 15f);
            }
            space(7f);
        }

        /** Writes a text, wrapped to the page width, one line every {@code leading} points. */
        void text(String text, float size, float[] colour, float leading) throws IOException {
            for (String line : wrap(text, size)) {
                keepTogether(leading);
                y -= leading;
                write(line, size, colour, MARGIN, y);
            }
        }

        private void write(String line, float size, float[] colour, float x, float baseline) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(colour[0], colour[1], colour[2]);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(line);
            stream.endText();
        }

        /** The foot of every page, written once the number of pages is known. */
        void footers(String footer) throws IOException {
            int pages = document.getNumberOfPages();
            for (int index = 0; index < pages; index++) {
                PDPage page = document.getPage(index);
                String line = printable(footer.replace("{page}", String.valueOf(index + 1)).replace("{pages}", String.valueOf(pages)));
                try (PDPageContentStream foot = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream = foot;
                    stream.setStrokingColor(RULE[0], RULE[1], RULE[2]);
                    stream.setLineWidth(0.6f);
                    stream.moveTo(MARGIN, FOOT - 14f);
                    stream.lineTo(WIDTH - MARGIN, FOOT - 14f);
                    stream.stroke();
                    write(line, 8f, MUTED, MARGIN, FOOT - 28f);
                }
            }
        }

        /** Breaks a text on its own line ends, then on spaces, then inside a word longer than a line. */
        private List<String> wrap(String text, float size) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : (text == null ? "" : text).split("\\R", -1)) {
                StringBuilder line = new StringBuilder();
                for (String word : printable(paragraph).split(" ")) {
                    String candidate = line.length() == 0 ? word : line + " " + word;
                    if (width(candidate, size) <= TEXT_WIDTH) {
                        line.setLength(0);
                        line.append(candidate);
                        continue;
                    }
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    String rest = word;
                    while (width(rest, size) > TEXT_WIDTH) {
                        int cut = rest.length() - 1;
                        while (cut > 1 && width(rest.substring(0, cut), size) > TEXT_WIDTH) {
                            cut--;
                        }
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    line.append(rest);
                }
                lines.add(line.toString());
            }
            return lines;
        }

        private float width(String text, float size) throws IOException {
            return font.getStringWidth(text) / 1000f * size;
        }

        /** The text with what the font cannot draw replaced: a tab by a space, a missing glyph by a question mark. */
        private String printable(String text) {
            StringBuilder out = new StringBuilder();
            text.codePoints().forEach(codePoint -> {
                if (codePoint == '\t') {
                    out.append(' ');
                } else if (!Character.isISOControl(codePoint)) {
                    String character = new String(Character.toChars(codePoint));
                    try {
                        font.encode(character);
                        out.append(character);
                    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                        out.append('?');
                    }
                }
            });
            return out.toString();
        }
    }
}
