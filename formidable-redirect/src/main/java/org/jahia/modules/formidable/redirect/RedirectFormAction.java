package org.jahia.modules.formidable.redirect;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.PageLinks;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * Sends the visitor to another page of the site once the submission is accepted: the action of
 * {@code fmdbred:redirectAction}. The engine does the rest: the servlet names the target in the response, the form
 * island navigates (formidable-engine 0.6, docs/architecture/redirect-action.md of the Formidable repository).
 *
 * <p>The target is either a page of the site picked in the Content Editor ({@code page}, a weak reference: it follows a move or a
 * rename; its URL is built from the node in the visitor's language, through the platform's URL rewriting) or a
 * same-origin relative path typed by the contributor ({@code path}, per language). Never another host: the rule is
 * {@link RedirectTargets}. The action fails nothing: a missing, unpublished or unusable target is logged and the
 * submission is accepted without a redirect.</p>
 *
 * <p>Threading: a singleton with no state; everything comes from the {@link FormActionContext}. Writes nothing, so
 * the type carries {@code fmdbmix:readOnlyCompatibleAction}.</p>
 */
@Component(service = FormAction.class)
public class RedirectFormAction implements ContextualFormAction {

    private static final Logger log = LoggerFactory.getLogger(RedirectFormAction.class);

    /** The node type this action runs for. */
    static final String NODE_TYPE = "fmdbred:redirectAction";
    /** {@code page} or {@code path}. */
    static final String TARGET_TYPE = "targetType";
    /** The weakreference to the page to send the visitor to, when {@link #TARGET_TYPE} is {@code page}. */
    static final String PAGE = "page";
    /** A same-origin relative path (i18n), when {@link #TARGET_TYPE} is {@code path}. */
    static final String PATH = "path";

    static final String TARGET_PAGE = "page";
    static final String TARGET_PATH = "path";

    /** How a page becomes a path: the platform's URL rewriting in production, a stub in the tests. */
    @FunctionalInterface
    interface PageUrls {
        Optional<String> pathOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response);
    }

    private final PageUrls pageUrls;

    public RedirectFormAction() {
        this(PageLinks::urlOf);
    }

    RedirectFormAction(PageUrls pageUrls) {
        this.pageUrls = pageUrls;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        JCRNodeWrapper action = context.actionNode();
        try {
            String type = action.hasProperty(TARGET_TYPE)
                    ? action.getProperty(TARGET_TYPE).getString() : TARGET_PAGE;
            Optional<String> target = TARGET_PATH.equals(type) ? path(action) : page(context, action);
            return target.map(FormActionResult::redirectTo).orElseGet(FormActionResult::none);
        } catch (RepositoryException | RuntimeException e) {
            // never fail a submission over where to send the visitor next
            log.warn("[RedirectFormAction] The redirect target could not be resolved: the submission is accepted without a redirect.", e);
            return FormActionResult.none();
        }
    }

    private static Optional<String> path(JCRNodeWrapper action) throws RepositoryException {
        if (!action.hasProperty(PATH)) {
            log.warn("[RedirectFormAction] The action has no path: no redirect.");
            return Optional.empty();
        }
        Optional<String> path = RedirectTargets.sameOriginPath(action.getProperty(PATH).getString());
        if (path.isEmpty()) {
            log.warn("[RedirectFormAction] The configured path is not a same-origin relative path: no redirect.");
        }
        return path;
    }

    private Optional<String> page(FormActionContext context, JCRNodeWrapper action) throws RepositoryException {
        if (!action.hasProperty(PAGE)) {
            log.warn("[RedirectFormAction] The action has no page: no redirect.");
            return Optional.empty();
        }
        // read in live, in the submission's locale (the action node came from that session): an unpublished or
        // deleted page does not resolve, and the visitor is not sent to a page they cannot see
        JCRNodeWrapper page = (JCRNodeWrapper) action.getProperty(PAGE).getNode();
        return pageUrls.pathOf(page, context.request(), context.response());
    }
}
