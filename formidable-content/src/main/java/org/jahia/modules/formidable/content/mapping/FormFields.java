package org.jahia.modules.formidable.content.mapping;

import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The submittable fields of a form, indexed by node identifier, field key and name, so that a mapping row resolves to
 * the name its field is submitted under (the node name: the HTML {@code name} and the key of the submitted values),
 * even after the field was renamed. Walks {@code fields} recursively (steps and fieldsets nest freely); a field is a
 * {@code fmdbmix:formElement} that is not {@code fmdbmix:nonSubmittable}; the first occurrence of a name wins, as in
 * the engine. It also tells which choice fields are in category mode and the root category of each. Built once per submission; not thread-safe, not shared.
 */
public final class FormFields {

    private static final Logger log = LoggerFactory.getLogger(FormFields.class);
    static final String FORM_ELEMENT = "fmdbmix:formElement";
    static final String NON_SUBMITTABLE = "fmdbmix:nonSubmittable";
    static final String FIELD_KEY = "fieldKey";
    static final String CATEGORY_OPTIONS = "fmdbmix:categoryOptions";
    static final String CATEGORY_ROOT = "optionsRootCategory";

    private final Map<String, String> nameById = new HashMap<>();
    private final Map<String, String> nameByKey = new HashMap<>();
    private final Set<String> names = new HashSet<>();
    private final Map<String, String> categoryRootByName = new HashMap<>();

    private FormFields() {
    }

    /**
     * @param form the form node ({@code fmdb:form}), may be {@code null}
     * @return the index, empty when the form is unknown or cannot be read
     */
    public static FormFields of(JCRNodeWrapper form) {
        FormFields index = new FormFields();
        if (form == null) {
            return index;
        }
        try {
            if (form.hasNode("fields")) {
                index.collect(form.getNode("fields"));
            }
        } catch (RepositoryException e) {
            log.warn("[formidable-content] The fields of the form {} could not be read: {}", form.getPath(), e.getMessage());
        }
        return index;
    }

    private void collect(JCRNodeWrapper node) throws RepositoryException {
        for (JCRNodeWrapper child : node.getNodes()) {
            if (child.isNodeType(FORM_ELEMENT) && !child.isNodeType(NON_SUBMITTABLE)) {
                String name = child.getName();
                if (names.add(name) && child.isNodeType(CATEGORY_OPTIONS) && child.hasProperty(CATEGORY_ROOT)) {
                    // the first field of a name wins, as in the engine; the weak reference reads as the root's identifier
                    String root = child.getProperty(CATEGORY_ROOT).getString();
                    if (root != null && !root.isBlank()) {
                        categoryRootByName.put(name, root.trim());
                    }
                }
                nameById.putIfAbsent(child.getIdentifier(), name);
                if (child.hasProperty(FIELD_KEY)) {
                    String key = child.getProperty(FIELD_KEY).getString();
                    if (key != null && !key.isBlank()) {
                        nameByKey.putIfAbsent(key, name);
                    }
                }
            }
            collect(child);
        }
    }

    /**
     * @param fieldName the name a field is submitted under
     * @return the identifier of the root category of a choice field in category mode (mixin
     *         {@code fmdbmix:categoryOptions} with {@code optionsRootCategory}), empty for any other field. The
     *         submitted values of such a field are the node names of the direct children of that root.
     */
    public Optional<String> categoryRootOf(String fieldName) {
        return Optional.ofNullable(categoryRootByName.get(fieldName));
    }

    /**
     * @param row a field or files row
     * @return the name the row's field is submitted under: by node identifier, then field key, then the stored name
     */
    public Optional<String> nameOf(ContentMapping.Row row) {
        if (!row.nodeId().isEmpty() && nameById.containsKey(row.nodeId())) {
            return Optional.of(nameById.get(row.nodeId()));
        }
        if (!row.fieldKey().isEmpty() && nameByKey.containsKey(row.fieldKey())) {
            return Optional.of(nameByKey.get(row.fieldKey()));
        }
        if (!row.fieldName().isEmpty() && names.contains(row.fieldName())) {
            return Optional.of(row.fieldName());
        }
        return Optional.empty();
    }
}
