package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.convert.PlainTextHtml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PlainTextHtmlTest {

    @Test
    void blankLinesSeparateParagraphsAndASingleBreakBecomesBr() {
        assertEquals("<p>one</p>", PlainTextHtml.of("one"));
        assertEquals("<p>one<br>two</p>", PlainTextHtml.of("one\ntwo"));
        assertEquals("<p>one</p><p>two</p>", PlainTextHtml.of("one\n\ntwo"));
        assertEquals("<p>a<br>b</p><p>c</p><p>d</p>", PlainTextHtml.of("a\nb\n\nc\n\n\n\nd"));
    }

    @Test
    void everyKindOfLineEndingAndLinesOfBlanksAreHandled() {
        assertEquals("<p>one<br>two</p><p>three</p>", PlainTextHtml.of("one\r\ntwo\r\n\r\nthree"));
        assertEquals("<p>one</p><p>two</p>", PlainTextHtml.of("one\r\rtwo"));
        assertEquals("<p>one</p><p>two</p>", PlainTextHtml.of("one\n   \t \ntwo"), "a line of blanks is a blank line");
        assertEquals("<p>one<br>two</p>", PlainTextHtml.of("  one  \n  two  "), "the ends of a line are trimmed");
        assertEquals("<p>one</p>", PlainTextHtml.of("\n\n one \n\n"));
    }

    @Test
    void nothingVisibleWritesNothing() {
        assertEquals("", PlainTextHtml.of(null));
        assertEquals("", PlainTextHtml.of(""));
        assertEquals("", PlainTextHtml.of(" \n\t\r\n "));
    }

    @Test
    void everythingTheVisitorTypedIsEscaped() {
        assertEquals("<p>&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;</p>", PlainTextHtml.of("<script>alert(\"x\")</script>"));
        assertEquals("<p>Tom &amp; Jerry&#39;s &amp;amp; &lt;b&gt;</p>", PlainTextHtml.of("Tom & Jerry's &amp; <b>"));
        assertEquals("<p>&lt;img src=x onerror=alert(1)&gt;</p>", PlainTextHtml.of("<img src=x onerror=alert(1)>"));
        String html = PlainTextHtml.of("<p>already</p>\n\n<a href=\"javascript:x\">y</a> 'z'");
        assertFalse(html.replace("<p>", "").replace("</p>", "").replace("<br>", "").contains("<"), "no tag but the ones we add");
        assertFalse(html.contains("\"") || html.contains("'"));
    }

    @Test
    void aHugeTextOfBlankLinesIsLinear() {
        String text = "a" + "\n \n".repeat(200_000) + "b";
        String html = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> PlainTextHtml.of(text));
        assertEquals("<p>a</p><p>b</p>", html);
    }
}
