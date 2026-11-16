package org.jahia.modules.formidable.pdf.render;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.pdf.action.PdfCopyFormAction;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

/**
 * Puts the page script of the download button (formidable-pdf.js) on the pages whose form has the PDF copy action,
 * and on no other page. Same mechanism as the engine's own jExperience integration: a static asset declared in
 * front of the form's markup, which the platform moves to the head and keeps once per page.
 *
 * <p>Live only: the button follows a real submission. The declaration is cached with the form's fragment, and a
 * form whose actions change is rendered again. Stateless; thread-safe.</p>
 */
@Component(service = RenderFilter.class, immediate = true)
public class PdfCopyScriptFilter extends AbstractFilter {

    // named for what it is rather than a path: a module's static resource, not a configurable location
    static final String SCRIPT_RESOURCE = "/modules/formidable-pdf/javascript/formidable-pdf.js";
    static final String ACTIONS = "actions";

    private static final Logger log = LoggerFactory.getLogger(PdfCopyScriptFilter.class);

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Activate
    public void activate() {
        // just inside the fragment cache, as the engine's own script filter (priority 17 there)
        setPriority(17.1f);
        setApplyOnNodeTypes(FmdbMixin.FORM_ROOT);
        setApplyOnTemplateTypes("html");
        setApplyOnModes("live");
        // the same node goes through a second chain as a wrapper, an include or an option: declare the script once
        setSkipOnConfigurations("include,wrapper,option");
        setDescription("Adds the page script of the PDF copy download button before a form that has the action");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) {
        try {
            return hasAction(resource.getNode()) ? scriptAsset(renderContext.getRequest().getContextPath(), bundleVersion()) + previousOut : previousOut;
        } catch (RepositoryException | RuntimeException e) {
            // a form that renders is worth more than its button
            log.warn("[PdfCopyScriptFilter] The page script of the PDF copy could not be declared; the form renders without it", e);
            return previousOut;
        }
    }

    static boolean hasAction(JCRNodeWrapper form) throws RepositoryException {
        if (!form.hasNode(ACTIONS)) {
            return false;
        }
        JCRNodeIteratorWrapper actions = form.getNode(ACTIONS).getNodes();
        while (actions.hasNext()) {
            if (((JCRNodeWrapper) actions.nextNode()).isNodeType(PdfCopyFormAction.NODE_TYPE)) {
                return true;
            }
        }
        return false;
    }

    /** {@code key=""} makes the platform merge the declaration by its path, so several forms declare one script. */
    static String scriptAsset(String contextPath, String version) {
        return "<jahia:resource type=\"javascript\" path=\"" + (contextPath == null ? "" : contextPath) + SCRIPT_RESOURCE
                + (version.isEmpty() ? "" : "?v=" + version) + "\" insert=\"false\" key=\"\" defer=\"true\" />\n";
    }

    private static String bundleVersion() {
        Bundle bundle = FrameworkUtil.getBundle(PdfCopyScriptFilter.class);
        return bundle == null ? "" : bundle.getVersion().toString();
    }
}
