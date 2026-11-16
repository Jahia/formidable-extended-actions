package org.jahia.modules.formidable.pdf.enrich;

import org.jahia.bin.Jahia;
import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionResponseEnricher;
import org.jahia.modules.formidable.pdf.model.Fingerprint;
import org.jahia.modules.formidable.pdf.servlet.ReceiptServlet;
import org.jahia.modules.formidable.pdf.store.ReceiptStore;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tells the browser where the visitor's PDF copy is, once the submission is accepted: the engine calls an enricher
 * only after every action succeeded, so a refused submission never gets a link. The answer gains a {@code pdfCopy}
 * block, {@code {"url", "label", "fileName"}}, which the page script (formidable-pdf.js) turns into the download
 * button. A form without the PDF copy action, or a copy that could not be built, adds nothing.
 *
 * <p>Stateless apart from the store; thread-safe.</p>
 */
@Component(service = SubmissionResponseEnricher.class, immediate = true)
public class ReceiptEnricher implements SubmissionResponseEnricher {

    /** The entry of the submission answer this module owns. */
    public static final String BLOCK = "pdfCopy";

    private static final Logger log = LoggerFactory.getLogger(ReceiptEnricher.class);

    private ReceiptStore store;

    @Reference
    public void setStore(ReceiptStore store) {
        this.store = store;
    }

    @Override
    public Map<String, Object> enrich(AcceptedSubmission submission) {
        try {
            return store.release(Fingerprint.of(submission.formNode().getIdentifier(), submission.parameters()))
                    .map(released -> Map.<String, Object>of(BLOCK, block(Jahia.getContextPath(), released.getKey(), released.getValue())))
                    .orElseGet(Map::of);
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[ReceiptEnricher] The link to a PDF copy could not be given: the submission is accepted without it.", e);
            return Map.of();
        }
    }

    /** The platform's context path, never the request's: the request reached the engine through {@code /modules}. */
    static Map<String, Object> block(String contextPath, String token, ReceiptStore.Receipt receipt) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("url", (contextPath == null ? "" : contextPath) + "/modules" + ReceiptServlet.ALIAS + "?" + ReceiptServlet.TOKEN + "=" + token);
        block.put("label", receipt.buttonLabel());
        block.put("fileName", receipt.fileName());
        return block;
    }
}
