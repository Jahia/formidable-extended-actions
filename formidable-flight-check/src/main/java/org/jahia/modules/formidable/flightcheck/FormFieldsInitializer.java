package org.jahia.modules.formidable.flightcheck;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Choice list {@code fmdbfltFormFields}: the fields of the form the edited flight check belongs to, so the contributor
 * picks the date and airport fields by their title instead of typing a name. Each choice is "Title (name)", its value
 * the field's node name, as the submission keys it. The parameter narrows the list: {@code date} to date fields,
 * {@code place} to text fields and drop-down lists (what can hold an airport), {@code text} to text fields; none lists
 * every field with a value.
 *
 * <p>The form is found from the edited node ({@code contextNode}) or, while the check is being created, from its
 * parent ({@code contextParent}). Thread-safe: no state.</p>
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormFieldsInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "fmdbfltFormFields";
    private static final Logger log = LoggerFactory.getLogger(FormFieldsInitializer.class);

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values,
                                                     Locale locale, Map<String, Object> context) {
        List<ChoiceListValue> choices = new ArrayList<>();
        try {
            JCRNodeWrapper form = formOf(context);
            if (form != null && form.hasNode("fields")) {
                collect(form.getNode("fields"), param, choices);
            }
        } catch (RepositoryException e) {
            log.debug("Form fields unreadable for the flight check's pickers", e);
        }
        return choices;
    }

    private static JCRNodeWrapper formOf(Map<String, Object> context) throws RepositoryException {
        if (context == null) {
            return null;
        }
        Object start = context.get("contextNode");
        if (!(start instanceof JCRNodeWrapper)) {
            start = context.get("contextParent");
        }
        if (!(start instanceof JCRNodeWrapper)) {
            return null;
        }
        JCRNodeWrapper node = (JCRNodeWrapper) start;
        while (node != null && !node.isNodeType("fmdbmix:formRoot")) {
            node = "/".equals(node.getPath()) ? null : node.getParent();
        }
        return node;
    }

    private static void collect(JCRNodeWrapper parent, String kind, List<ChoiceListValue> choices) throws RepositoryException {
        JCRNodeIteratorWrapper children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType("fmdbmix:submittableField")) {
                if (fits(child, kind)) {
                    choices.add(new ChoiceListValue(child.getDisplayableName() + " (" + child.getName() + ")", child.getName()));
                }
            } else if (child.isNodeType("fmdbmix:formContainer")) {
                collect(child, kind, choices);
            }
        }
    }

    private static boolean fits(JCRNodeWrapper field, String kind) throws RepositoryException {
        if ("date".equals(kind)) {
            return field.isNodeType("fmdb:inputDate");
        }
        if ("place".equals(kind)) {
            return field.isNodeType("fmdb:inputText") || field.isNodeType("fmdb:select");
        }
        if ("text".equals(kind)) {
            return field.isNodeType("fmdb:inputText");
        }
        return true;
    }

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key.
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
