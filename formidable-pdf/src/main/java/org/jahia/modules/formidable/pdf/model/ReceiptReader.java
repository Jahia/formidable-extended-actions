package org.jahia.modules.formidable.pdf.model;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.pdf.Texts;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a submission into what the document says: each field of the form that received an answer, under the label
 * the visitor saw, in the form's order and under the form's own groups. A choice shows the label of its option, a
 * yes/no field says yes or no, an attached file shows its name and size. A field the visitor did not answer, or did
 * not see, is left out. A sensitive field (a password) never reaches an action's parameters, so it cannot be printed.
 *
 * <p>Stateless; thread-safe.</p>
 */
public final class ReceiptReader {

    static final String FIELDS = "fields";
    /** The weak references of the two option sources that list nodes: categories under a root, content under a root. */
    private static final List<String> SOURCE_ROOTS = List.of("optionsRootCategory", "optionsRootNode");

    private ReceiptReader() {
    }

    /**
     * @param context  the submission
     * @param title    the action's document title, or {@code null} for the form's own
     * @param note     the action's closing line, or {@code null}
     * @param received when the submission was received
     * @param zone     the visitor's time zone, as their browser gave it, or UTC
     * @return the content of the document
     * @throws RepositoryException when the form cannot be read
     */
    public static ReceiptContent read(FormActionContext context, String title, String note, Instant received, ZoneId zone)
            throws RepositoryException {
        Locale locale = context.locale();
        JCRNodeWrapper form = context.formNode();
        Map<String, List<SubmittedFile>> files = new LinkedHashMap<>();
        context.files().forEach(file -> files.computeIfAbsent(file.fieldName(), name -> new ArrayList<>()).add(file));

        List<ReceiptContent.Section> sections = new ArrayList<>();
        List<ReceiptContent.Entry> direct = new ArrayList<>();
        if (form.hasNode(FIELDS)) {
            collect(form.getNode(FIELDS), "", context.parameters(), files, locale, direct, sections);
        }
        if (!direct.isEmpty()) {
            sections.add(0, new ReceiptContent.Section("", direct));
        }

        String fingerprint = Fingerprint.of(form.getIdentifier(), context.parameters(), context.files().stream()
                .map(file -> new Fingerprint.FileFact(file.fieldName(), file.originalName(), file.size())).toList());
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
                .format(received.atZone(zone)) + " (" + zone.getId() + ")";
        List<ReceiptContent.Entry> meta = List.of(
                new ReceiptContent.Entry(Texts.in(locale, "pdf.reference"), List.of(Fingerprint.reference(fingerprint))),
                new ReceiptContent.Entry(Texts.in(locale, "pdf.submittedOn"), List.of(when)));
        return new ReceiptContent(title == null || title.isBlank() ? titleOf(form, locale) : title,
                Texts.in(locale, "pdf.subtitle"), meta, sections, note, Texts.in(locale, "pdf.footer", Fingerprint.reference(fingerprint)),
                fingerprint);
    }

    /** Walks one level of the form: a step or a fieldset opens a section of its own, a field adds its answer. */
    private static void collect(JCRNodeWrapper parent, String heading, Map<String, List<String>> parameters,
                                Map<String, List<SubmittedFile>> files, Locale locale, List<ReceiptContent.Entry> into,
                                List<ReceiptContent.Section> sections) throws RepositoryException {
        JCRNodeIteratorWrapper children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) children.nextNode();
            if (node.isNodeType(FmdbMixin.FORM_CONTAINER)) {
                String path = heading.isEmpty() ? titleOf(node, locale) : heading + " / " + titleOf(node, locale);
                List<ReceiptContent.Entry> entries = new ArrayList<>();
                // the place of this group, before the groups inside it that the walk below adds
                int place = sections.size();
                collect(node, path, parameters, files, locale, entries, sections);
                if (!entries.isEmpty()) {
                    sections.add(place, new ReceiptContent.Section(path, entries));
                }
            } else if (node.isNodeType(FmdbMixin.FORM_ELEMENT)) {
                List<String> values = valuesOf(node, parameters.get(node.getName()), files.get(node.getName()), locale);
                if (!values.isEmpty()) {
                    into.add(new ReceiptContent.Entry(titleOf(node, locale), values));
                }
            }
        }
    }

    private static List<String> valuesOf(JCRNodeWrapper field, List<String> submitted, List<SubmittedFile> attached, Locale locale)
            throws RepositoryException {
        List<String> values = new ArrayList<>();
        if (attached != null) {
            attached.forEach(file -> values.add(file.originalName() + " (" + size(file.size()) + ")"));
        }
        if (submitted == null) {
            return values;
        }
        boolean choice = field.isNodeType(FmdbMixin.CHOICE_FIELD);
        Map<String, String> labels = choice ? optionLabels(field, locale) : Map.of();
        boolean yesNo = field.isNodeType(FmdbMixin.BOOLEAN_FIELD);
        for (String value : submitted) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (yesNo) {
                values.add(Texts.in(locale, "false".equalsIgnoreCase(value.trim()) ? "pdf.no" : "pdf.yes"));
            } else if (choice) {
                values.add(labels.containsKey(value) ? labels.get(value) : sourcedLabel(field, value, locale));
            } else {
                values.add(dated(field, value, locale));
            }
        }
        return values;
    }

    /** A date or a date and time, as the visitor's language writes it; any other value, and one that does not parse, as typed. */
    private static String dated(JCRNodeWrapper field, String value, Locale locale) throws RepositoryException {
        try {
            if (field.isNodeType(FmdbMixin.DATE_FIELD)) {
                return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(LocalDate.parse(value.trim()));
            }
            if (field.isNodeType(FmdbMixin.DATETIME_LOCAL_FIELD)) {
                return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale).format(LocalDateTime.parse(value.trim()));
            }
        } catch (DateTimeException e) {
            // not the format the date inputs post: printed as stored
        }
        return value;
    }

    /**
     * The label of an option that comes from a source: the title of the category, or of the content item, the value
     * names under the root the field points at. A declared source, or a value that names nothing, prints as stored.
     */
    private static String sourcedLabel(JCRNodeWrapper field, String value, Locale locale) {
        try {
            for (String root : SOURCE_ROOTS) {
                if (field.hasProperty(root)) {
                    Node parent = field.getProperty(root).getNode();
                    if (parent.hasNode(value) && parent.getNode(value) instanceof JCRNodeWrapper option) {
                        return titleOf(option, locale);
                    }
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            // a root that is gone, a value that is no node name: the copy keeps the stored value
        }
        return value;
    }

    /** The options typed in the form, value to label; empty for a list fed by a source, whose values print as stored. */
    private static Map<String, String> optionLabels(JCRNodeWrapper field, Locale locale) {
        Map<String, String> labels = new LinkedHashMap<>();
        try {
            // the translation node is a plain JCR node, not a Jahia wrapper
            Node translated = field.hasI18N(locale) ? field.getI18N(locale) : field;
            if (!translated.hasProperty("options")) {
                return labels;
            }
            for (Value option : translated.getProperty("options").getValues()) {
                JSONObject json = new JSONObject(option.getString());
                String value = json.optString("value", "");
                if (!value.isEmpty()) {
                    labels.put(value, json.optString("label", value).isBlank() ? value : json.optString("label", value));
                }
            }
        } catch (RepositoryException | JSONException e) {
            // an option that cannot be read prints as its stored value: the copy is still true to the submission
        }
        return labels;
    }

    static String titleOf(JCRNodeWrapper node, Locale locale) {
        try {
            if (node.hasI18N(locale) && node.getI18N(locale).hasProperty("jcr:title")) {
                String title = node.getI18N(locale).getProperty("jcr:title").getString();
                if (!title.isBlank()) {
                    return title;
                }
            }
            return node.getName();
        } catch (RepositoryException e) {
            return node.getName();
        }
    }

    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return bytes < 1024 * 1024 ? String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0) : String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
