package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.platform.TypeCatalog;

import javax.jcr.PropertyType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory stand-ins for the platform seams, shared by the tests. */
public final class Fakes {

    private Fakes() {
    }

    /** A node type registry with a content type of the shape the real project uses, and a mixin for the files. */
    public static class Catalog implements TypeCatalog {
        public static final String CONTENT_TYPE = "ctmd:submission";
        public static final String FILE_MIXIN = "ctmdmix:credited";
        public static final String CATEGORIZED = "jmix:categorized";

        private final Map<String, Map<String, PropertyShape>> types = new HashMap<>();
        private final Set<String> creatable = new HashSet<>();
        private final Set<String> mixins = new HashSet<>();
        private final Map<String, Set<String>> supertypes = new HashMap<>();
        /** The types that already are categorized (the mixin is then not added). */
        public final Set<String> categorizedTypes = new HashSet<>();

        public Catalog() {
            creatable.add(CONTENT_TYPE);
            Map<String, PropertyShape> submission = new HashMap<>();
            add(submission, "jcr:title", PropertyType.STRING, false, true);
            add(submission, "description", PropertyType.STRING, false, true);
            add(submission, "durationMinutes", PropertyType.LONG, false, false);
            add(submission, "budget", PropertyType.DOUBLE, false, false);
            add(submission, "approved", PropertyType.BOOLEAN, false, false);
            add(submission, "deadline", PropertyType.DATE, false, false);
            add(submission, "channels", PropertyType.STRING, true, false);
            add(submission, "submissionStatus", PropertyType.STRING, false, false);
            add(submission, "submittedBy", PropertyType.STRING, false, false);
            add(submission, "company", PropertyType.STRING, false, false);
            add(submission, "brief", PropertyType.WEAKREFERENCE, false, false);
            add(submission, "media", PropertyType.WEAKREFERENCE, true, false);
            add(submission, "captions", PropertyType.WEAKREFERENCE, true, false);
            add(submission, "cover", PropertyType.WEAKREFERENCE, false, false);
            add(submission, "score", PropertyType.NAME, false, false);
            // the story-like properties: rich text, a single image, the authors (profiles), a single editor
            submission.put("body", new PropertyShape("body", PropertyType.STRING, false, true, true, List.of()));
            submission.put("teaser", new PropertyShape("teaser", PropertyType.STRING, true, true, true, List.of()));
            submission.put("image", new PropertyShape("image", PropertyType.WEAKREFERENCE, false, false, false, List.of("jmix:image")));
            submission.put("authors", new PropertyShape("authors", PropertyType.WEAKREFERENCE, true, false, false, List.of("ctmd:author")));
            submission.put("editor", new PropertyShape("editor", PropertyType.WEAKREFERENCE, false, false, false, List.of("ctmd:author")));
            submission.put("anyone", new PropertyShape("anyone", PropertyType.REFERENCE, true, false, false, List.of()));
            types.put(CONTENT_TYPE, submission);
            Map<String, PropertyShape> categorized = new HashMap<>();
            categorized.put("j:defaultCategory", new PropertyShape("j:defaultCategory", PropertyType.WEAKREFERENCE, true, false, false, List.of("jnt:category")));
            types.put(CATEGORIZED, categorized);
            supertypes.put("jnt:file", Set.of("nt:file", "nt:hierarchyNode"));
            supertypes.put("ctmd:person", Set.of("ctmd:author"));
            Map<String, PropertyShape> file = new HashMap<>();
            add(file, "jcr:title", PropertyType.STRING, false, true);
            types.put("jnt:file", file);
            Map<String, PropertyShape> credited = new HashMap<>();
            add(credited, "ctmdCredit", PropertyType.STRING, false, true);
            add(credited, "ctmdYear", PropertyType.LONG, false, false);
            types.put(FILE_MIXIN, credited);
            mixins.add(FILE_MIXIN);
        }

        private static void add(Map<String, PropertyShape> into, String name, int type, boolean multiple, boolean i18n) {
            into.put(name, new PropertyShape(name, type, multiple, i18n));
        }

        @Override
        public boolean isCreatable(String typeName) {
            return creatable.contains(typeName);
        }

        @Override
        public boolean isMixin(String name) {
            return mixins.contains(name);
        }

        @Override
        public boolean isOfType(String typeName, String ofType) {
            if (CATEGORIZED.equals(ofType) && categorizedTypes.contains(typeName)) {
                return true;
            }
            return typeName.equals(ofType) || supertypes.getOrDefault(typeName, Set.of()).contains(ofType);
        }

        @Override
        public Optional<PropertyShape> property(List<String> typeNames, String propertyName) {
            for (String type : typeNames) {
                PropertyShape shape = types.getOrDefault(type, Map.of()).get(propertyName);
                if (shape != null) {
                    return Optional.of(shape);
                }
            }
            return Optional.empty();
        }
    }
}
