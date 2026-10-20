package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.convert.ConversionException;
import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.convert.ValueConverter;
import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.ContentMapping;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.content.names.ImageSize;
import org.jahia.modules.formidable.content.platform.Categories;
import org.jahia.modules.formidable.content.platform.SubmitterProfiles;
import org.jahia.modules.formidable.content.platform.TypeCatalog;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the settings of the action and one submission into a {@link ContentPlan}, checking every mapping against the
 * node types before anything is written. Pure apart from the {@link TypeCatalog} and the {@link Lookups} it asks.
 * Stateless and thread-safe.
 *
 * <p>Two kinds of failure, kept apart because the visitor is told different things: a {@link ConfigException} means
 * the author's settings are wrong (an unknown property, a type that cannot be created: logged, the form is
 * "unavailable"); a {@link ValueException} means the submission is wrong (a bad value, a video without captions).</p>
 *
 * <p>Precedence when several settings fill the same property, later wins: the form's fields, then the fixed values,
 * then the submitter, the organization and the page. What the visitor typed can never override what the platform knows.</p>
 */
public final class PlanBuilder {

    /** The longest title kept, which is also what names the node. */
    public static final int MAX_TITLE = 512;

    private static final String FILE_TYPE = "jnt:file";
    private static final String TITLE = "jcr:title";
    private static final String CATEGORIZED = "jmix:categorized";
    private static final String TITLE_MIXIN = "mix:title";
    private static final String IMAGE_MIXIN = "jmix:image";
    private static final Pattern SITE_PATH = Pattern.compile("/sites/([A-Za-z0-9_-]{1,100})(?:/.*)?");
    private static final Logger log = LoggerFactory.getLogger(PlanBuilder.class);

    /**
     * What was submitted.
     *
     * @param parameters the accepted values of the non-sensitive fields, by field name
     * @param files      the validated uploads
     */
    public record Submission(Map<String, List<String>> parameters, List<SubmittedFile> files) {
        public Submission {
            parameters = parameters == null ? Map.of() : parameters;
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    /**
     * Who submitted.
     *
     * @param username     the signed-in user's name
     * @param organization the user's organization, may be {@code null}
     */
    public record Identity(String username, String organization) {
    }

    /**
     * Where it goes, after the submitter's subfolder was applied.
     *
     * @param parentPath the folder for the content
     * @param filesPath  the folder for the files, {@code null} when the action stores none
     */
    public record Target(String parentPath, String filesPath) {
    }

    /**
     * The page the form was sent from, as the action found it.
     *
     * @param identifier          the page's identifier, or {@code null} when no readable page was found
     * @param categoryIdentifiers its {@code j:defaultCategory} values (identifiers) in live, empty when it has none or when
     *                            they were not read
     */
    public record Page(String identifier, List<String> categoryIdentifiers) {
        public Page {
            categoryIdentifiers = categoryIdentifiers == null ? List.of() : List.copyOf(categoryIdentifiers);
        }

        /** No page. */
        public static final Page NONE = new Page(null, List.of());
    }

    /**
     * What the repository is asked while a plan is built.
     *
     * @param categories the children of a root category
     * @param profiles   the profiles of a submitter
     */
    public record Lookups(Categories categories, SubmitterProfiles profiles) {

        /** For settings that use neither source: asking either one is a configuration error. */
        public static final Lookups NONE = new Lookups(
                root -> {
                    throw new ConfigException("No category lookup is available");
                },
                (site, user, types) -> {
                    throw new RepositoryException("No profile lookup is available");
                });
    }

    private PlanBuilder() {
    }

    /**
     * Builds the plan.
     *
     * @param settings       the parsed settings of the action
     * @param submission     the values and files
     * @param who            the submitter
     * @param pageIdentifier the identifier of the page the form was submitted from, or {@code null}
     * @param target         the folders
     * @param languages      the site's languages, the default first
     * @param catalog        the node types
     * @return the plan
     * @throws ConfigException when the settings do not fit the node types
     * @throws ValueException  when the submission does not fit the settings
     */
    public static ContentPlan build(Settings settings, Submission submission, Identity who, String pageIdentifier, Target target,
                                    List<Locale> languages, TypeCatalog catalog) throws ConfigException, ValueException {
        return build(settings, submission, who, new Page(pageIdentifier, List.of()), target, languages, catalog, Lookups.NONE);
    }

    /**
     * Builds the plan, with the categories of the page and the repository lookups the category and profile sources need.
     *
     * @param settings   the parsed settings of the action
     * @param submission the values and files
     * @param who        the submitter
     * @param page       the page the form was submitted from
     * @param target     the folders
     * @param languages  the site's languages, the default first
     * @param catalog    the node types
     * @param lookups    the categories and the profiles
     * @return the plan
     * @throws ConfigException when the settings do not fit the node types or the repository
     * @throws ValueException  when the submission does not fit the settings
     */
    public static ContentPlan build(Settings settings, Submission submission, Identity who, Page page, Target target,
                                    List<Locale> languages, TypeCatalog catalog, Lookups lookups) throws ConfigException, ValueException {
        String pageIdentifier = page.identifier();
        Optional<PairRule> broken = PairRule.firstBroken(settings.pairs(), submission.files());
        if (broken.isPresent()) {
            PairRule rule = broken.get();
            throw new ValueException(ValueException.Kind.PAIR,
                    "a file of '" + rule.field() + "' matching " + rule.mimePattern() + " has no companion in '" + rule.dependentField() + "'", rule);
        }
        String type = settings.contentType();
        if (!catalog.isCreatable(type)) {
            throw new ConfigException("The content type '" + type + "' does not exist or cannot be created (abstract or a mixin)");
        }
        if (settings.fileMixin() != null && !catalog.isMixin(settings.fileMixin())) {
            throw new ConfigException("The file mixin '" + settings.fileMixin() + "' does not exist or is not a mixin");
        }
        List<String> contentTypes = List.of(type);
        List<String> fileTypes = settings.fileMixin() == null ? List.of(FILE_TYPE) : List.of(FILE_TYPE, settings.fileMixin());

        String title = title(settings, submission, catalog, contentTypes);
        Map<String, TypedValue> properties = new LinkedHashMap<>();
        fieldValues(settings, submission, catalog, contentTypes, properties);
        fixedValues(settings, catalog, contentTypes, properties);
        submitterValues(settings, who, pageIdentifier, catalog, contentTypes, properties);
        List<String> contentMixins = new ArrayList<>();
        categoryValues(settings, submission, page, catalog, lookups.categories(), contentTypes, properties, contentMixins);
        profileValues(settings, who, target, catalog, lookups.profiles(), contentTypes, properties);
        List<FileGroup> groups = fileGroups(settings, submission, target, catalog, contentTypes);
        List<TypedValue> fileProperties = fileProperties(settings, submission, catalog, fileTypes);

        return new ContentPlan(type, target.parentPath(), target.filesPath(), title,
                new ArrayList<>(properties.values()), groups, settings.fileMixin(), fileProperties, settings.publish(), languages, contentMixins);
    }

    private static String title(Settings settings, Submission submission, TypeCatalog catalog, List<String> types) throws ConfigException {
        if (settings.titleField() == null) {
            return null;
        }
        if (catalog.property(types, TITLE).isEmpty()) {
            throw new ConfigException("The content type '" + settings.contentType() + "' has no jcr:title: a title field cannot be used (add mix:title to the type)");
        }
        String value = first(submission.parameters().get(settings.titleField()));
        if (value == null) {
            return null;
        }
        return value.length() > MAX_TITLE ? value.substring(0, MAX_TITLE) : value;
    }

    private static void fieldValues(Settings settings, Submission submission, TypeCatalog catalog, List<String> types,
                                    Map<String, TypedValue> into) throws ConfigException, ValueException {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fieldMappings()) {
            PropertyShape shape = shapeOf(catalog, types, entry.right(), "fieldMappings");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fieldMappings': '" + entry.right() + "' holds references: fill it with a file mapping or the page setting");
            }
            requireFillable(shape, "fieldMappings");
            if (!shape.multiple() && seen.put(entry.right(), entry.left()) != null) {
                throw new ConfigException("Setting 'fieldMappings': the single-valued property '" + entry.right() + "' is mapped from two fields");
            }
            try {
                List<Object> values = ValueConverter.convertSubmitted(shape, submission.parameters().get(entry.left()));
                if (!values.isEmpty()) {
                    merge(into, shape, values);
                }
            } catch (ConversionException e) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "field '" + entry.left() + "': " + e.getMessage());
            }
        }
    }

    private static void fixedValues(Settings settings, TypeCatalog catalog, List<String> types, Map<String, TypedValue> into)
            throws ConfigException {
        Map<String, TypedValue> fixed = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fixedValues()) {
            PropertyShape shape = shapeOf(catalog, types, entry.left(), "fixedValues");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fixedValues': '" + entry.left() + "' holds references and cannot take a fixed text");
            }
            requireFillable(shape, "fixedValues");
            try {
                List<Object> values = ValueConverter.convert(shape, List.of(entry.right()));
                if (!shape.multiple() && fixed.containsKey(entry.left())) {
                    throw new ConfigException("Setting 'fixedValues': the single-valued property '" + entry.left() + "' is given two values");
                }
                merge(fixed, shape, values);
            } catch (ConversionException e) {
                throw new ConfigException("Setting 'fixedValues': " + e.getMessage(), e);
            }
        }
        into.putAll(fixed);
    }

    private static void submitterValues(Settings settings, Identity who, String pageIdentifier, TypeCatalog catalog, List<String> types,
                                        Map<String, TypedValue> into) throws ConfigException {
        if (settings.submitterProperty() != null) {
            PropertyShape shape = singleString(catalog, types, settings.submitterProperty(), "submitterProperty");
            into.put(shape.name(), new TypedValue(shape, List.of(who.username())));
        }
        if (settings.organizationProperty() != null) {
            PropertyShape shape = singleString(catalog, types, settings.organizationProperty(), "organizationProperty");
            if (who.organization() != null && !who.organization().isBlank()) {
                into.put(shape.name(), new TypedValue(shape, List.of(who.organization())));
            }
        }
        if (settings.pageProperty() != null) {
            PropertyShape shape = shapeOf(catalog, types, settings.pageProperty(), "pageProperty");
            if (!shape.isReference() || shape.multiple()) {
                throw new ConfigException("Setting 'pageProperty': '" + settings.pageProperty() + "' must be a single reference or weakreference property");
            }
            if (pageIdentifier != null) {
                into.put(shape.name(), new TypedValue(shape, List.of(new Reference(pageIdentifier))));
            }
        }
    }

    private static List<FileGroup> fileGroups(Settings settings, Submission submission, Target target, TypeCatalog catalog,
                                              List<String> types) throws ConfigException, ValueException {
        Map<String, PropertyShape> shapes = new LinkedHashMap<>();
        Map<String, List<SubmittedFile>> files = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fileMappings()) {
            PropertyShape shape = shapeOf(catalog, types, entry.right(), "fileMappings");
            if (!shape.isReference()) {
                throw new ConfigException("Setting 'fileMappings': '" + entry.right() + "' must be a reference or weakreference property");
            }
            shapes.putIfAbsent(entry.right(), shape);
            for (SubmittedFile file : submission.files()) {
                if (entry.left().equals(file.fieldName())) {
                    files.computeIfAbsent(entry.right(), key -> new ArrayList<>()).add(file);
                }
            }
        }
        List<FileGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<SubmittedFile>> group : files.entrySet()) {
            PropertyShape shape = shapes.get(group.getKey());
            if (!shape.multiple() && group.getValue().size() > 1) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "property '" + shape.name() + "' holds one file and the form sent " + group.getValue().size());
            }
            for (SubmittedFile file : group.getValue()) {
                requireSatisfies(shape, file, settings.fileMixin(), catalog);
            }
            groups.add(new FileGroup(shape, group.getValue()));
        }
        if (!groups.isEmpty() && target.filesPath() == null) {
            throw new ConfigException("Files were uploaded but the action has no filesFolder");
        }
        return groups;
    }

    private static List<TypedValue> fileProperties(Settings settings, Submission submission, TypeCatalog catalog, List<String> fileTypes)
            throws ConfigException, ValueException {
        Map<String, TypedValue> values = new LinkedHashMap<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fileProperties()) {
            PropertyShape shape = shapeOf(catalog, fileTypes, entry.right(), "fileProperties");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fileProperties': '" + entry.right() + "' holds references and cannot take a field's text");
            }
            requireFillable(shape, "fileProperties");
            if (!shape.multiple() && seen.put(entry.right(), entry.left()) != null) {
                throw new ConfigException("Setting 'fileProperties': the single-valued property '" + entry.right() + "' is mapped from two fields");
            }
            try {
                List<Object> converted = ValueConverter.convertSubmitted(shape, submission.parameters().get(entry.left()));
                if (!converted.isEmpty()) {
                    merge(values, shape, converted);
                }
            } catch (ConversionException e) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "field '" + entry.left() + "' for a file property: " + e.getMessage());
            }
        }
        return new ArrayList<>(values.values());
    }

    /**
     * {@code j:defaultCategory}: the union of the categories every categories row gives, in order, without duplicates. A
     * choice field in category mode submits the node names of the direct children of its root; each is looked up among
     * those children and an unknown one refuses the submission (a category is never created, a name that is not exactly a
     * child's, a path included, matches nothing). The categories of the page are copied as they are. Nothing to write
     * adds nothing, not even the mixin.
     */
    private static void categoryValues(Settings settings, Submission submission, Page page, TypeCatalog catalog, Categories categories,
                                       List<String> types, Map<String, TypedValue> into, List<String> mixins)
            throws ConfigException, ValueException {
        if (settings.categoryFields().isEmpty() && !settings.pageCategories()) {
            return;
        }
        List<String> withMixin = new ArrayList<>(types);
        withMixin.add(CATEGORIZED);
        PropertyShape shape = catalog.property(withMixin, ContentMapping.CATEGORIES).orElseThrow(() ->
                new ConfigException("Setting 'mapping': the property " + ContentMapping.CATEGORIES + " is not available (is " + CATEGORIZED + " installed?)"));
        if (!shape.isReference() || !shape.multiple()) {
            throw new ConfigException("Setting 'mapping': " + ContentMapping.CATEGORIES + " must be a multiple reference or weakreference property");
        }
        Set<String> identifiers = new LinkedHashSet<>();
        Map<String, Map<String, String>> childrenByRoot = new HashMap<>();
        for (CategoryField field : settings.categoryFields()) {
            Map<String, String> children = childrenByRoot.get(field.rootIdentifier());
            if (children == null) {
                children = categories.childCategories(field.rootIdentifier());
                childrenByRoot.put(field.rootIdentifier(), children);
            }
            for (String raw : submission.parameters().getOrDefault(field.fieldName(), List.of())) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String identifier = children.get(raw.trim());
                if (identifier == null) {
                    // the value is the visitor's: the log names the field, never what was typed
                    throw new ValueException(ValueException.Kind.BAD_VALUE,
                            "field '" + field.fieldName() + "': a value is not a category offered by the field");
                }
                identifiers.add(identifier);
            }
        }
        if (settings.pageCategories()) {
            identifiers.addAll(page.categoryIdentifiers());
        }
        if (identifiers.isEmpty()) {
            return;
        }
        if (!catalog.isOfType(settings.contentType(), CATEGORIZED)) {
            mixins.add(CATEGORIZED);
        }
        List<Object> references = new ArrayList<>();
        identifiers.forEach(identifier -> references.add(new Reference(identifier)));
        into.put(shape.name(), new TypedValue(shape, references));
    }

    /**
     * The properties filled from the submitter's profile: references to the nodes under the site of the content folder
     * that carry {@code fmdbcntmix:submitterProfile} with the submitter's user name, restricted to the node types the
     * property accepts. None found writes nothing; several for a single property write nothing too (the plan never
     * guesses which one is meant).
     */
    private static void profileValues(Settings settings, Identity who, Target target, TypeCatalog catalog, SubmitterProfiles profiles,
                                      List<String> types, Map<String, TypedValue> into) throws ConfigException {
        if (settings.profileProperties().isEmpty()) {
            return;
        }
        String siteKey = siteKeyOf(target.parentPath());
        for (String property : settings.profileProperties()) {
            PropertyShape shape = shapeOf(catalog, types, property, "mapping");
            if (!shape.isReference()) {
                throw new ConfigException("Setting 'mapping': the profile of the submitter fills '" + property + "', which must be a reference or weakreference property");
            }
            if (property.equals(settings.pageProperty()) || settings.fileMappings().stream().anyMatch(entry -> entry.right().equals(property))) {
                throw new ConfigException("Setting 'mapping': '" + property + "' is filled by the profile of the submitter and by another source");
            }
            List<String> found;
            try {
                found = profiles.find(siteKey, who.username(), shape.valueConstraints());
            } catch (RepositoryException e) {
                throw new ConfigException("The profile of the submitter could not be looked up for '" + property + "'", e);
            }
            if (found.isEmpty()) {
                log.info("[formidable-content] {} has no profile in the site {} that fits '{}': the property is left empty.", who.username(), siteKey, property);
                continue;
            }
            if (!shape.multiple() && found.size() > 1) {
                log.warn("[formidable-content] {} has {} profiles in the site {} that fit '{}', which holds one: none is written.",
                        who.username(), found.size(), siteKey, property);
                continue;
            }
            List<Object> references = new ArrayList<>();
            found.forEach(identifier -> references.add(new Reference(identifier)));
            into.put(shape.name(), new TypedValue(shape, references));
        }
    }

    /** The key of the site of the configured content folder; only a plain key may enter the profile query. */
    static String siteKeyOf(String path) throws ConfigException {
        Matcher matcher = path == null ? null : SITE_PATH.matcher(path);
        if (matcher == null || !matcher.matches()) {
            throw new ConfigException("Setting 'mapping': the profile of the submitter is looked up in the site of the content folder, "
                    + "which is not under /sites/<site key> (" + path + ")");
        }
        return matcher.group(1);
    }

    /**
     * A file stored for a reference property that constrains its target ({@code < jmix:image}) must satisfy the
     * constraint as the writer will store it: {@code jnt:file}, {@code mix:title}, the file mixin and, for an
     * {@code image/*} file, {@code jmix:image}. A file that cannot is a bad value, refused before anything is written.
     */
    private static void requireSatisfies(PropertyShape shape, SubmittedFile file, String fileMixin, TypeCatalog catalog) throws ValueException {
        if (shape.valueConstraints().isEmpty()) {
            return;
        }
        List<String> stored = new ArrayList<>(List.of(FILE_TYPE, TITLE_MIXIN));
        if (fileMixin != null) {
            stored.add(fileMixin);
        }
        if (ImageSize.looksLikeImage(file.mimeType())) {
            stored.add(IMAGE_MIXIN);
        }
        for (String constraint : shape.valueConstraints()) {
            for (String type : stored) {
                if (type.equals(constraint) || catalog.isOfType(type, constraint)) {
                    return;
                }
            }
        }
        throw new ValueException(ValueException.Kind.BAD_VALUE,
                "a file of '" + file.fieldName() + "' does not fit the property '" + shape.name() + "', which takes " + shape.valueConstraints() + " only");
    }

    private static PropertyShape shapeOf(TypeCatalog catalog, List<String> types, String property, String setting) throws ConfigException {
        return catalog.property(types, property).orElseThrow(() ->
                new ConfigException("Setting '" + setting + "': the property '" + property + "' is not declared by " + types
                        + " (or it is protected): check its name and prefix"));
    }

    private static void requireFillable(PropertyShape shape, String setting) throws ConfigException {
        if (!ValueConverter.supports(shape.type())) {
            throw new ConfigException("Setting '" + setting + "': the property '" + shape.name() + "' has the type "
                    + PropertyType.nameFromValue(shape.type()) + ", which cannot be filled from text");
        }
    }

    private static PropertyShape singleString(TypeCatalog catalog, List<String> types, String property, String setting) throws ConfigException {
        PropertyShape shape = shapeOf(catalog, types, property, setting);
        if (shape.type() != PropertyType.STRING || shape.multiple()) {
            throw new ConfigException("Setting '" + setting + "': '" + property + "' must be a single-valued string property");
        }
        return shape;
    }

    private static void merge(Map<String, TypedValue> into, PropertyShape shape, List<Object> values) {
        TypedValue existing = into.get(shape.name());
        if (existing == null || !shape.multiple()) {
            into.put(shape.name(), new TypedValue(shape, values));
            return;
        }
        List<Object> all = new ArrayList<>(existing.values());
        all.addAll(values);
        into.put(shape.name(), new TypedValue(shape, all));
    }

    private static String first(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).findFirst().orElse(null);
    }
}
