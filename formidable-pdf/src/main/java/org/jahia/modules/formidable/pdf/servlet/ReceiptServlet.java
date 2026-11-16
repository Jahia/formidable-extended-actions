package org.jahia.modules.formidable.pdf.servlet;

import org.jahia.modules.formidable.pdf.Texts;
import org.jahia.modules.formidable.pdf.store.ReceiptStore;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serves a visitor's PDF copy: {@code GET <context>/modules/formidable-pdf/copy?t=<token>}.
 *
 * <p>The token is the whole authorisation: 256 random bits the submission answer gave to the browser that made it,
 * good for the minutes the configuration allows. The servlet reads nothing else from the request, changes nothing
 * (a GET, so the CSRF guard has nothing to protect), and tells every cache and every later page nothing: no store,
 * no referrer. An unknown or expired token answers 404 with one sentence, the same for both.</p>
 *
 * <p>Stateless apart from the store; thread-safe.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + ReceiptServlet.ALIAS}, immediate = true)
public class ReceiptServlet extends HttpServlet {

    public static final String ALIAS = "/formidable-pdf/copy";
    public static final String TOKEN = "t";

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ReceiptServlet.class);

    /** Set once by Declarative Services before the servlet is registered; the holder publishes it to every request thread. */
    private final transient AtomicReference<ReceiptStore> store = new AtomicReference<>();

    @Reference
    public void setStore(ReceiptStore store) {
        this.store.set(store);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            answer(req, resp);
        } catch (IOException e) {
            // the browser went away during the download: nothing to answer, the copy stays until it expires
            log.debug("[formidable-pdf] A PDF copy could not be sent: {}", e.getMessage());
        }
    }

    private void answer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store, private, max-age=0");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Robots-Tag", "noindex");
        Optional<ReceiptStore.Receipt> receipt = store.get().find(req.getParameter(TOKEN));
        if (receipt.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.getWriter().write(Texts.in(req.getLocale(), "visitor.expired"));
            return;
        }
        byte[] pdf = receipt.get().pdf();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/pdf");
        // the name is built by the action from ASCII letters, digits and hyphens: nothing to escape in the header
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + receipt.get().fileName() + "\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Allow", "GET");
        resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
