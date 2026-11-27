package org.jahia.modules.formidable.flightcheck;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Adds the flight autofill to a rendered form: after the form's HTML, a JSON configuration
 * ({@code <script type="application/json" data-fmdbflt-autofill>}) and the page script {@code flight-autofill.js},
 * for each flight check of the form whose {@code autofill} is on. The script, on the visitor's side, asks
 * {@link FlightLookupServlet} once the date and the flight number are filled and writes the airports into the fields
 * the contributor picked.
 *
 * <p>Runs outside the output cache (priority 10, as Formidable's CAPTCHA filter), in preview and live only: Page
 * Builder shows the form as the contributor builds it. The labels come from the module's resource bundle in the
 * page's language. Thread-safe: no state.</p>
 */
@Component(service = RenderFilter.class, immediate = true)
public class FlightAutofillFilter extends AbstractFilter {

    private static final Logger log = LoggerFactory.getLogger(FlightAutofillFilter.class);
    private static final String BUNDLE = "resources.formidable-flight-check";
    private static final String OTHER_OPTION_VALUE = "otherOptionValue";
    private static final String[] MESSAGES = {"found", "departure", "notFound", "otherAirport", "unavailable"};

    /** Sets the filter on every form, for HTML renderings. */
    @Activate
    public void activate() {
        setPriority(10);
        setApplyOnNodeTypes("fmdbmix:formRoot");
        setApplyOnTemplateTypes("html");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) {
        if (renderContext.isEditMode()) {
            return previousOut;
        }
        try {
            JCRNodeWrapper form = resource.getNode();
            StringBuilder out = new StringBuilder(previousOut);
            boolean any = false;
            if (form.hasNode("fields")) {
                Locale locale = renderContext.getMainResourceLocale();
                String context = renderContext.getRequest().getContextPath();
                for (JCRNodeWrapper check : checksOf(form.getNode("fields"))) {
                    out.append(configOf(form.getIdentifier(), check, locale, context));
                    any = true;
                }
                if (any) {
                    out.append("<script src=\"").append(context)
                            .append("/modules/formidable-flight-check/javascript/flight-autofill.js\" defer></script>");
                }
            }
            return any ? out.toString() : previousOut;
        } catch (RepositoryException e) {
            log.warn("Flight autofill not added to form {}", resource.getNodePath(), e);
            return previousOut;
        }
    }

    /** The flight checks with autofill on, anywhere under the form's fields (steps and fieldsets included). */
    private static java.util.List<JCRNodeWrapper> checksOf(JCRNodeWrapper parent) throws RepositoryException {
        java.util.List<JCRNodeWrapper> found = new java.util.ArrayList<>();
        JCRNodeIteratorWrapper children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(FlightCheckFieldAction.NODE_TYPE)) {
                if (child.hasProperty("autofill") && child.getProperty("autofill").getBoolean()) {
                    found.add(child);
                }
            } else {
                found.addAll(checksOf(child));
            }
        }
        return found;
    }

    private static String configOf(String formId, JCRNodeWrapper check, Locale locale, String context) throws RepositoryException {
        // check -> fmdb:fieldActionList -> the field it checks
        String flightField = check.getParent().getParent().getName();
        String otherOption = property(check, OTHER_OPTION_VALUE);
        JSONObject config = new JSONObject()
                .put("endpoint", context + "/modules/formidable-flight-check/lookup")
                .put("flightField", flightField)
                .put("dateField", property(check, "dateField"))
                .put("departureField", property(check, "departureField"))
                .put("arrivalField", property(check, "arrivalField"))
                .put("departureOtherField", property(check, "departureOtherField"))
                .put("arrivalOtherField", property(check, "arrivalOtherField"))
                .put(OTHER_OPTION_VALUE, otherOption.isEmpty() ? "other" : otherOption);
        JSONObject messages = new JSONObject();
        for (String key : MESSAGES) {
            messages.put(key, message(locale, "fmdbflt.autofill." + key));
        }
        config.put("messages", messages);
        // JSON inside a script element: a "<" could close it
        String json = config.toString().replace("<", "\\u003c");
        return "<script type=\"application/json\" data-fmdbflt-autofill=\"" + formId + "\">" + json + "</script>";
    }

    private static String property(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : "";
    }

    private static String message(Locale locale, String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.ENGLISH : locale,
                    FlightAutofillFilter.class.getClassLoader()).getString(key);
        } catch (MissingResourceException e) {
            return "";
        }
    }
}
