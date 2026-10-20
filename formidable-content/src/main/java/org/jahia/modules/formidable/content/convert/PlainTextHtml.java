package org.jahia.modules.formidable.content.convert;

/**
 * Turns the plain text a visitor typed into a field into HTML that is safe to store in a rich-text property.
 *
 * <p>Everything the visitor typed is escaped ({@code &}, {@code <}, {@code >}, {@code "}, {@code '}), so no tag, attribute or
 * entity can come out of the text: a value is never HTML, whatever it looks like. Blank lines separate paragraphs
 * ({@code <p>}), a single line break inside a paragraph becomes {@code <br>}. A text with no visible character gives
 * the empty string, which writes nothing. Linear in the length of the text (no regular expression). Pure and
 * stateless.</p>
 */
public final class PlainTextHtml {

    private PlainTextHtml() {
    }

    /**
     * @param text what the visitor typed, may be {@code null}
     * @return paragraphs of escaped text, or the empty string when the text is blank
     */
    public static String of(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder html = new StringBuilder(text.length() + 16);
        StringBuilder paragraph = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                flush(html, paragraph);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append("<br>");
            }
            escapeInto(paragraph, trimmed);
        }
        flush(html, paragraph);
        return html.toString();
    }

    private static void flush(StringBuilder html, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            html.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    private static void escapeInto(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
    }
}
