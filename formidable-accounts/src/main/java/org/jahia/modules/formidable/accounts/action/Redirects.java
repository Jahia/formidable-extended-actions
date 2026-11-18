package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.PageLinks;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Where an action sends the visitor next: the page the contributor picked ({@code redirectPage}), or, ahead of it, the
 * place the visitor was going before the form ({@code redirectParameter}: the name of a query parameter of the form's
 * own page, as in {@code /login?redirect=/account}). Either way the answer is a same-origin path or nothing
 * ({@link RedirectTargets}): the parameter is the visitor's to set, so it is never trusted beyond that rule.
 *
 * <p>Stateless apart from its page-URL seam; thread-safe.</p>
 */
final class Redirects {

    /** How a page becomes a path: the platform's URL rewriting in production. */
    @FunctionalInterface
    interface PageUrls {
        Optional<String> pathOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response);
    }

    private static final Logger log = LoggerFactory.getLogger(Redirects.class);

    private final PageUrls pageUrls;

    Redirects() {
        this(PageLinks::urlOf);
    }

    Redirects(PageUrls pageUrls) {
        this.pageUrls = pageUrls;
    }

    /**
     * @param context     the submission
     * @param useReturn   whether the visitor is signed in, so that "where they were going" is a place they can reach
     * @return the path to send the visitor to, empty for none
     */
    Optional<String> resolve(FormActionContext context, boolean useReturn) {
        JCRNodeWrapper action = context.actionNode();
        if (useReturn) {
            String parameter = ActionProps.string(action, "redirectParameter");
            Optional<String> back = returnPath(context.request().getHeader("Referer"), context.request().getServerName(), parameter);
            if (back.isPresent()) {
                return back;
            }
        }
        return page(context, action, "redirectPage");
    }

    /**
     * @param context  the submission
     * @param property the weak-reference property of the action naming a page
     * @return the page's path, empty when the property is unset or the page does not resolve
     */
    Optional<String> page(FormActionContext context, JCRNodeWrapper action, String property) {
        try {
            if (!action.hasProperty(property)) {
                return Optional.empty();
            }
            JCRNodeWrapper page = (JCRNodeWrapper) action.getProperty(property).getNode();
            return pageUrls.pathOf(page, context.request(), context.response());
        } catch (RepositoryException | RuntimeException e) {
            // an unpublished or deleted page: the submission is accepted without it
            log.warn("[formidable-accounts] The page of '{}' could not be resolved: no redirect.", property);
            return Optional.empty();
        }
    }

    /**
     * The value of one query parameter of the page the visitor submitted from, accepted only when it is a safe
     * same-origin path: the "send me back where I was going" pattern ({@code /login?redirect=/account}).
     *
     * @param referer       the {@code Referer} header, may be {@code null}
     * @param serverName    the host name the request reached
     * @param parameterName the query parameter to read, for instance {@code redirect}
     * @return the path, when the page is on this server, the parameter is present and its value is a safe path
     */
    static Optional<String> returnPath(String referer, String serverName, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return Optional.empty();
        }
        String page = RedirectTargets.pathOnServer(referer, serverName).orElse("");
        int question = page.indexOf('?');
        if (question < 0) {
            return Optional.empty();
        }
        for (String pair : page.substring(question + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (parameterName.equals(decode(equals < 0 ? pair : pair.substring(0, equals)))) {
                return RedirectTargets.sameOriginPath(equals < 0 ? "" : decode(pair.substring(equals + 1)));
            }
        }
        return Optional.empty();
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * @param result the answer so far
     * @param target what {@link #resolve} or {@link #page} answered: a path of this server, or the absolute URL the
     *               platform printed for a page of a site with another server name
     * @return the answer with its redirect; unchanged when the target is neither
     */
    static FormActionResult withRedirect(FormActionResult result, String target) {
        if (RedirectTargets.sameOriginPath(target).isPresent()) {
            return result.withRedirect(target);
        }
        return RedirectTargets.httpUrl(target).map(result::withRedirectToUrl).orElse(result);
    }
}
