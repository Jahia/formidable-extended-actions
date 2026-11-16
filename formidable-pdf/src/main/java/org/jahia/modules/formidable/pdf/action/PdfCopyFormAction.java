package org.jahia.modules.formidable.pdf.action;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.pdf.Texts;
import org.jahia.modules.formidable.pdf.model.Fingerprint;
import org.jahia.modules.formidable.pdf.model.ReceiptContent;
import org.jahia.modules.formidable.pdf.model.ReceiptReader;
import org.jahia.modules.formidable.pdf.pdf.ReceiptPdf;
import org.jahia.modules.formidable.pdf.store.ReceiptStore;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * The "PDF copy" action: builds a PDF of what the visitor submitted and holds it for the download button the page
 * shows with the confirmation message.
 *
 * <p>The action only prepares the copy. The link reaches the browser once every action of the form succeeded,
 * through {@link org.jahia.modules.formidable.pdf.enrich.ReceiptEnricher}: a submission another action refuses gives
 * no copy. The action writes nothing to the repository and never fails a submission: a copy that cannot be built is
 * logged, and the visitor gets the confirmation without the button.</p>
 *
 * <p>A singleton shared by every submission; it keeps no state of its own. Thread-safe.</p>
 */
@Component(service = FormAction.class)
public class PdfCopyFormAction implements ContextualFormAction {

    public static final String NODE_TYPE = "fmdbpdf:pdfCopyAction";
    static final String DOCUMENT_TITLE = "documentTitle";
    static final String BUTTON_LABEL = "buttonLabel";
    static final String CLOSING_NOTE = "closingNote";
    /** The header the form island sends with the visitor's time zone, as the Save to JCR action reads it. */
    static final String TIME_ZONE_HEADER = "X-Formidable-Time-Zone";

    private static final Logger log = LoggerFactory.getLogger(PdfCopyFormAction.class);

    private final Clock clock;
    private ReceiptStore store;

    public PdfCopyFormAction() {
        this(Clock.systemUTC());
    }

    PdfCopyFormAction(Clock clock) {
        this.clock = clock;
    }

    @Reference
    public void setStore(ReceiptStore store) {
        this.store = store;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        try {
            JCRNodeWrapper action = context.actionNode();
            Locale locale = context.locale();
            ReceiptContent content = ReceiptReader.read(context, text(action, locale, DOCUMENT_TITLE), text(action, locale, CLOSING_NOTE),
                    clock.instant(), zone(context.request().getHeader(TIME_ZONE_HEADER)));
            String label = text(action, locale, BUTTON_LABEL);
            store.hold(Fingerprint.of(context.formNode().getIdentifier(), context.parameters()), new ReceiptStore.Receipt(
                    new ReceiptPdf().render(content), fileName(content.title(), Fingerprint.reference(content.fingerprint())),
                    label == null ? Texts.in(locale, "visitor.download") : label));
        } catch (RepositoryException | IOException | RuntimeException e) {
            // never fail a submission over its copy: the answers are worth more than the document
            log.warn("[PdfCopyFormAction] The PDF copy of a submission could not be built: the submission is accepted without it.", e);
        }
        return FormActionResult.none();
    }

    /** A property of the action in the submission's language, or {@code null} when the contributor left it empty. */
    private static String text(JCRNodeWrapper action, Locale locale, String property) throws RepositoryException {
        if (!action.hasI18N(locale) || !action.getI18N(locale).hasProperty(property)) {
            return null;
        }
        String value = action.getI18N(locale).getProperty(property).getString();
        return value.isBlank() ? null : value.trim();
    }

    /** The visitor's zone when their browser named a real one, UTC otherwise. */
    static ZoneId zone(String header) {
        try {
            return header == null || header.isBlank() || header.length() > 64 ? ZoneOffset.UTC : ZoneId.of(header.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    /** A file name any system accepts: the title in ASCII letters and digits, then the reference. */
    static String fileName(String title, String reference) {
        String name = trimHyphens(java.text.Normalizer.normalize(title == null ? "" : title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT));
        return (name.isEmpty() ? "submission" : name.substring(0, Math.min(60, name.length()))) + "-" + reference + ".pdf";
    }

    private static String trimHyphens(String name) {
        int start = 0;
        int end = name.length();
        while (start < end && name.charAt(start) == '-') {
            start++;
        }
        while (end > start && name.charAt(end - 1) == '-') {
            end--;
        }
        return name.substring(start, end);
    }
}
