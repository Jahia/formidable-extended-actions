package org.jahia.modules.formidable.content.mapping;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * The {@code mapping} JSON document of a Create content action, written by the ContentMapping selector type of the
 * Content Editor: what fills each property of the content node and of its stored files.
 *
 * <pre>
 * {"version":1,"rows":[
 *   {"target":"content","property":"jcr:title","source":"field","nodeId":"…","fieldKey":"…","fieldName":"title"},
 *   {"target":"content","property":"photos","source":"files","nodeId":"…","fieldName":"media"},
 *   {"target":"content","property":"status","source":"constant","values":["submitted"]},
 *   {"target":"content","property":"submittedBy","source":"submitter"},
 *   {"target":"content","property":"company","source":"organization"},
 *   {"target":"content","property":"brief","source":"page"},
 *   {"target":"content","property":"j:defaultCategory","source":"field","nodeId":"…","fieldName":"topic"},
 *   {"target":"content","property":"j:defaultCategory","source":"pageCategories"},
 *   {"target":"content","property":"authors","source":"submitterProfile"},
 *   {"target":"file","property":"credit","source":"field","fieldName":"credit"}
 * ]}
 * </pre>
 *
 * <p>Pure and stateless. Unknown properties of a row are ignored, so a document written by a newer editor still loads;
 * an unknown target or source, a row without a property, or a field row whose field cannot be found is a
 * {@link ConfigException}: the action refuses rather than run a mapping its author did not mean.</p>
 */
public final class ContentMapping {

    /** Which node a row fills: the content node, or every file stored for it. */
    public enum Target { CONTENT, FILE }

    /** Where a row's value comes from. */
    public enum Source {
        /** A form field's value, converted to the property's type. */
        FIELD,
        /** The files of a file field, stored and referenced by a (weak)reference property. */
        FILES,
        /** Values typed in the mapping. */
        CONSTANT,
        /** The signed-in submitter's user name. */
        SUBMITTER,
        /** The submitter's organization. */
        ORGANIZATION,
        /** A reference to the page the form was submitted from. */
        PAGE,
        /** The {@code j:defaultCategory} values of the page the form was submitted from, copied as they are. */
        PAGE_CATEGORIES,
        /** The content of the submitter's profile (nodes carrying {@code fmdbcntmix:submitterProfile}), by reference. */
        SUBMITTER_PROFILE
    }

    /**
     * One row, as stored.
     *
     * @param target    the node it fills
     * @param property  the property name, never blank
     * @param source    where the value comes from
     * @param nodeId    the form field's node identifier (field and files rows), may be blank
     * @param fieldKey  the form field's business key (field and files rows), may be blank
     * @param fieldName the form field's node name when the row was written (field and files rows), may be blank
     * @param values    the values of a constant row, never {@code null}
     */
    public record Row(Target target, String property, Source source, String nodeId, String fieldKey, String fieldName,
                      List<String> values) {
        public Row {
            values = values == null ? List.of() : List.copyOf(values);
        }

        /** Whether the row reads a form field. */
        public boolean readsField() {
            return source == Source.FIELD || source == Source.FILES;
        }
    }

    /**
     * What the rows mean for the plan, in the shape the plan builder takes.
     *
     * @param titleField           the field giving {@code jcr:title}, or {@code null}
     * @param fieldMappings        {@code formField=property}
     * @param fileMappings         {@code fileField=property}
     * @param fileProperties       {@code formField=fileProperty}
     * @param fixedValues          {@code property=value}, one entry per value
     * @param submitterProperty    the property receiving the user name, or {@code null}
     * @param organizationProperty the property receiving the organization, or {@code null}
     * @param pageProperty         the property receiving the page reference, or {@code null}
     * @param categoryFields       the form fields (choice fields in category mode) whose values are categories of
     *                             {@code j:defaultCategory}, in row order
     * @param pageCategories       whether {@code j:defaultCategory} also receives the categories of the page the form was sent from
     * @param profileProperties    the reference properties filled from the submitter's profile
     */
    public record Parts(String titleField, List<Mappings.Entry> fieldMappings, List<Mappings.Entry> fileMappings,
                        List<Mappings.Entry> fileProperties, List<Mappings.Entry> fixedValues, String submitterProperty,
                        String organizationProperty, String pageProperty, List<String> categoryFields, boolean pageCategories,
                        List<String> profileProperties) {
        public Parts {
            categoryFields = List.copyOf(categoryFields);
            profileProperties = List.copyOf(profileProperties);
        }
    }

    static final String TITLE = "jcr:title";
    /** The property of {@code jmix:categorized}: valid on every content type, filled by the category sources only. */
    public static final String CATEGORIES = "j:defaultCategory";

    private ContentMapping() {
    }

    /**
     * Parses the stored document. A blank document has no row; a bare array of rows is accepted too.
     *
     * @param json the value of the {@code mapping} property, may be {@code null}
     * @return the rows in order
     * @throws ConfigException when the document is not JSON, or a row is not usable
     */
    public static List<Row> parse(String json) throws ConfigException {
        List<Row> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        JSONArray array;
        try {
            String trimmed = json.trim();
            array = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed).optJSONArray("rows");
        } catch (JSONException e) {
            throw new ConfigException("Setting 'mapping': not a JSON document (" + e.getMessage() + ")", e);
        }
        if (array == null) {
            return rows;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null) {
                throw new ConfigException("Setting 'mapping': row " + (i + 1) + " is not an object");
            }
            rows.add(row(raw, i + 1));
        }
        return rows;
    }

    private static Row row(JSONObject raw, int number) throws ConfigException {
        String property = raw.optString("property", "").trim();
        if (property.isEmpty()) {
            throw new ConfigException("Setting 'mapping': row " + number + " names no property");
        }
        Target target = parseEnum(Target.class, raw.optString("target", "content"), "target", number);
        Source source = parseEnum(Source.class, raw.optString("source", ""), "source", number);
        List<String> values = new ArrayList<>();
        JSONArray rawValues = raw.optJSONArray("values");
        if (rawValues != null) {
            for (int j = 0; j < rawValues.length(); j++) {
                String value = rawValues.optString(j, "");
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        } else if (raw.has("value") && !raw.isNull("value") && !raw.optString("value", "").isBlank()) {
            values.add(raw.optString("value"));
        }
        return new Row(target, property, source, raw.optString("nodeId", "").trim(), raw.optString("fieldKey", "").trim(),
                raw.optString("fieldName", "").trim(), values);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String what, int number) throws ConfigException {
        // "pageCategories" and "submitterProfile" are spelled in camel case in the document: PAGE_CATEGORIES, SUBMITTER_PROFILE
        String wanted = value.trim();
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().replace("_", "").equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        throw new ConfigException("Setting 'mapping': row " + number + " has the unknown " + what + " '" + value + "'");
    }

    /**
     * Turns the rows into the plan builder's settings. Every field row is resolved to the name its field is submitted
     * under; whether a property exists and takes the value is checked later, against the node types.
     *
     * @param rows      the parsed rows
     * @param fieldName resolves a field or files row to the submitted field name, empty when the field is gone
     * @return the parts
     * @throws ConfigException on a field that cannot be found, a source a target does not take, a property filled twice
     *                         by the submitter, organization or page sources, a constant row without a value, a source
     *                         {@value #CATEGORIES} does not take, or the category sources on another property
     */
    public static Parts toParts(List<Row> rows, Function<Row, Optional<String>> fieldName) throws ConfigException {
        String titleField = null;
        List<Mappings.Entry> fields = new ArrayList<>();
        List<Mappings.Entry> files = new ArrayList<>();
        List<Mappings.Entry> fileProperties = new ArrayList<>();
        List<Mappings.Entry> fixed = new ArrayList<>();
        String submitter = null;
        String organization = null;
        String page = null;
        List<String> categoryFields = new ArrayList<>();
        boolean pageCategories = false;
        List<String> profiles = new ArrayList<>();
        for (Row row : rows) {
            String field = row.readsField() ? fieldName.apply(row).orElseThrow(() -> new ConfigException(
                    "Setting 'mapping': the form field of '" + row.property() + "' (" + describe(row) + ") is not in the form any more"))
                    : null;
            if (row.target() == Target.FILE) {
                if (row.source() != Source.FIELD) {
                    throw new ConfigException("Setting 'mapping': the file property '" + row.property() + "' takes a form field only");
                }
                fileProperties.add(new Mappings.Entry(field, row.property()));
                continue;
            }
            if (CATEGORIES.equals(row.property()) && row.source() != Source.FIELD && row.source() != Source.PAGE_CATEGORIES) {
                throw new ConfigException("Setting 'mapping': " + CATEGORIES + " takes a choice field in category mode or the categories of the page, not '"
                        + row.source().name().toLowerCase(Locale.ROOT) + "'");
            }
            switch (row.source()) {
                case FIELD -> {
                    if (CATEGORIES.equals(row.property())) {
                        categoryFields.add(field);
                    } else if (TITLE.equals(row.property())) {
                        if (titleField != null) {
                            throw new ConfigException("Setting 'mapping': jcr:title is mapped from two fields");
                        }
                        titleField = field;
                    } else {
                        fields.add(new Mappings.Entry(field, row.property()));
                    }
                }
                case FILES -> files.add(new Mappings.Entry(field, row.property()));
                case CONSTANT -> {
                    if (row.values().isEmpty()) {
                        throw new ConfigException("Setting 'mapping': the fixed value of '" + row.property() + "' is empty");
                    }
                    for (String value : row.values()) {
                        fixed.add(new Mappings.Entry(row.property(), value));
                    }
                }
                case SUBMITTER -> submitter = once(submitter, row, "submitter");
                case ORGANIZATION -> organization = once(organization, row, "organization");
                case PAGE -> page = once(page, row, "page");
                case PAGE_CATEGORIES -> {
                    if (!CATEGORIES.equals(row.property())) {
                        throw new ConfigException("Setting 'mapping': the categories of the page fill " + CATEGORIES + " only, not '" + row.property() + "'");
                    }
                    pageCategories = true;
                }
                case SUBMITTER_PROFILE -> {
                    if (profiles.contains(row.property())) {
                        throw new ConfigException("Setting 'mapping': the profile of the submitter fills '" + row.property() + "' twice");
                    }
                    profiles.add(row.property());
                }
            }
        }
        return new Parts(titleField, fields, files, fileProperties, fixed, submitter, organization, page, categoryFields, pageCategories, profiles);
    }

    private static String once(String current, Row row, String what) throws ConfigException {
        if (current != null) {
            throw new ConfigException("Setting 'mapping': the " + what + " fills two properties ('" + current + "' and '" + row.property() + "')");
        }
        return row.property();
    }

    private static String describe(Row row) {
        return !row.fieldName().isEmpty() ? "'" + row.fieldName() + "'" : "node " + row.nodeId();
    }
}
