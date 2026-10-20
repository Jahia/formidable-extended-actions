package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.content.plan.CategoryField;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.plan.PlanBuilder;
import org.jahia.modules.formidable.content.plan.Settings;
import org.jahia.modules.formidable.content.plan.ValueException;
import org.jahia.modules.formidable.content.platform.Categories;
import org.jahia.modules.formidable.content.platform.SubmitterProfiles;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanBuilderTest {

    private static final Fakes.Catalog CATALOG = new Fakes.Catalog();
    private static final PlanBuilder.Target TARGET = new PlanBuilder.Target("/sites/orvane/contents/briefs/northlight", "/sites/orvane/files/briefs/northlight");
    private static final PlanBuilder.Identity WHO = new PlanBuilder.Identity("ada@northlight.example", "Northlight Studio");
    private static final List<Locale> LANGUAGES = List.of(Locale.ENGLISH, Locale.FRENCH);

    /** The settings of the Orvane supplier form, adjusted by the test. */
    private static final class S {
        String type = Fakes.Catalog.CONTENT_TYPE;
        String titleField = "title";
        List<String> fields = new ArrayList<>(List.of("summary=description", "minutes=durationMinutes", "budget=budget",
                "agreed=approved", "due=deadline", "channels=channels"));
        List<String> fileMappings = new ArrayList<>(List.of("media=media", "captions=captions"));
        String fileMixin = Fakes.Catalog.FILE_MIXIN;
        List<String> fileProperties = new ArrayList<>(List.of("credit=ctmdCredit"));
        List<String> fixed = new ArrayList<>(List.of("submissionStatus=submitted"));
        String submitter = "submittedBy";
        String organization = "company";
        String page = "brief";
        List<String> pairs = new ArrayList<>(List.of("captions<-media:video/*"));
        boolean publish = true;
        List<CategoryField> categoryFields = new ArrayList<>();
        boolean pageCategories;
        List<String> profiles = new ArrayList<>();

        Settings build() throws ConfigException {
            return new Settings(type, "orvane-supplier-", titleField, Mappings.parse("fieldMappings", fields),
                    Mappings.parse("fileMappings", fileMappings), fileMixin, Mappings.parse("fileProperties", fileProperties),
                    Mappings.parse("fixedValues", fixed), submitter, organization, page, PairRule.parse(pairs), publish,
                    categoryFields, pageCategories, profiles);
        }
    }

    private static SubmittedFile file(String field, String name, String mime) {
        return new SubmittedFile(field, name, mime, new byte[]{1, 2, 3});
    }

    private static Map<String, List<String>> values() {
        Map<String, List<String>> values = new HashMap<>();
        values.put("title", List.of("Summer launch film"));
        values.put("summary", List.of("A 30 second film"));
        values.put("minutes", List.of("2"));
        values.put("budget", List.of("1250.5"));
        values.put("agreed", List.of("true"));
        values.put("due", List.of("2026-11-01"));
        values.put("channels", List.of("web", "social"));
        values.put("credit", List.of("Northlight Studio"));
        return values;
    }

    private static List<SubmittedFile> films() {
        return List.of(file("media", "film.mp4", "video/mp4"), file("captions", "film.vtt", "text/vtt"));
    }

    private static ContentPlan build(S s, Map<String, List<String>> values, List<SubmittedFile> files, String pageId)
            throws ConfigException, ValueException {
        return PlanBuilder.build(s.build(), new PlanBuilder.Submission(values, files), WHO, pageId, TARGET, LANGUAGES, CATALOG);
    }

    private static TypedValue property(ContentPlan plan, String name) {
        return plan.properties().stream().filter(value -> value.shape().name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void theWholeSubmissionBecomesOnePlan() throws Exception {
        ContentPlan plan = build(new S(), values(), films(), "page-uuid");

        assertEquals("ctmd:submission", plan.contentType());
        assertEquals(TARGET.parentPath(), plan.parentPath());
        assertEquals(TARGET.filesPath(), plan.filesPath());
        assertEquals("Summer launch film", plan.title());
        assertEquals(List.of("A 30 second film"), property(plan, "description").values());
        assertEquals(List.of(2L), property(plan, "durationMinutes").values());
        assertEquals(List.of(1250.5), property(plan, "budget").values());
        assertEquals(List.of(true), property(plan, "approved").values());
        assertEquals(List.of("web", "social"), property(plan, "channels").values());
        assertEquals(List.of("submitted"), property(plan, "submissionStatus").values());
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values());
        assertEquals(List.of("Northlight Studio"), property(plan, "company").values());
        assertEquals(List.of(new Reference("page-uuid")), property(plan, "brief").values());
        assertEquals(2, plan.fileGroups().size());
        assertEquals("media", plan.fileGroups().get(0).property().name());
        assertEquals("film.mp4", plan.fileGroups().get(0).files().get(0).originalName());
        assertEquals(Fakes.Catalog.FILE_MIXIN, plan.fileMixin());
        assertEquals(List.of("Northlight Studio"), plan.fileProperties().get(0).values());
        assertTrue(plan.publish());
        assertEquals(LANGUAGES, plan.languages());
    }

    @Test
    void theTitleIsNotAPropertyOfThePlanAndItsMappingIsNotRequired() throws Exception {
        ContentPlan plan = build(new S(), values(), films(), null);
        assertEquals("Summer launch film", plan.title());
        assertNull(property(plan, "jcr:title"));
    }

    @Test
    void noTitleFieldMeansNoTitle() throws Exception {
        S s = new S();
        s.titleField = null;
        assertNull(build(s, values(), films(), null).title());
        Map<String, List<String>> blank = values();
        blank.put("title", List.of("  "));
        assertNull(build(new S(), blank, films(), null).title());
    }

    @Test
    void aVeryLongTitleIsCut() throws Exception {
        Map<String, List<String>> long_ = values();
        long_.put("title", List.of("x".repeat(5000)));
        assertEquals(PlanBuilder.MAX_TITLE, build(new S(), long_, films(), null).title().length());
    }

    @Test
    void whatThePlatformKnowsOverridesWhatTheFormSaid() throws Exception {
        S s = new S();
        s.fields.add("rogue=submissionStatus");
        s.fields.add("rogueUser=submittedBy");
        Map<String, List<String>> values = values();
        values.put("rogue", List.of("approved"));
        values.put("rogueUser", List.of("someone-else@example.com"));
        ContentPlan plan = build(s, values, films(), null);
        assertEquals(List.of("submitted"), property(plan, "submissionStatus").values(), "the fixed value wins over a mapped field");
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values(), "the submitter wins over a mapped field");
    }

    @Test
    void aFieldLeftBlankIsNotWritten() throws Exception {
        Map<String, List<String>> values = values();
        values.remove("budget");
        values.put("due", List.of(""));
        ContentPlan plan = build(new S(), values, films(), null);
        assertNull(property(plan, "budget"));
        assertNull(property(plan, "deadline"));
    }

    @Test
    void theOrganizationAndThePageAreLeftOutWhenUnknown() throws Exception {
        PlanBuilder.Identity nobody = new PlanBuilder.Identity("ada@northlight.example", null);
        ContentPlan plan = PlanBuilder.build(new S().build(), new PlanBuilder.Submission(values(), films()), nobody, null, TARGET, LANGUAGES, CATALOG);
        assertNull(property(plan, "company"));
        assertNull(property(plan, "brief"));
        assertEquals(List.of("ada@northlight.example"), property(plan, "submittedBy").values());
    }

    @Test
    void aValueThatDoesNotFitIsAValueErrorThatNamesTheFieldButNotTheValue() throws Exception {
        Map<String, List<String>> values = values();
        values.put("minutes", List.of("two hours"));
        ValueException error = assertThrows(ValueException.class, () -> build(new S(), values, films(), null));
        assertEquals(ValueException.Kind.BAD_VALUE, error.kind());
        assertTrue(error.getMessage().contains("minutes"));
        assertFalse(error.getMessage().contains("two hours"));
    }

    @Test
    void aVideoWithoutCaptionsIsAPairErrorBeforeAnythingElseIsChecked() throws Exception {
        S s = new S();
        s.type = "nope:missing";
        ValueException error = assertThrows(ValueException.class,
                () -> build(s, values(), List.of(file("media", "film.mp4", "video/mp4")), null));
        assertEquals(ValueException.Kind.PAIR, error.kind());
        assertEquals("captions", error.rule().dependentField());
    }

    @Test
    void anImageNeedsNoCaptions() throws Exception {
        ContentPlan plan = build(new S(), values(), List.of(file("media", "cover.png", "image/png")), null);
        assertEquals(1, plan.fileGroups().size());
    }

    @Test
    void filesOfAFieldThatIsNotMappedAreNotStored() throws Exception {
        ContentPlan plan = build(new S(), values(), List.of(file("other", "x.pdf", "application/pdf")), null);
        assertTrue(plan.fileGroups().isEmpty());
    }

    @Test
    void severalFilesForASingleValuedPropertyAreAValueError() throws Exception {
        S s = new S();
        s.fileMappings.add("covers=cover");
        List<SubmittedFile> files = List.of(file("covers", "a.png", "image/png"), file("covers", "b.png", "image/png"));
        ValueException error = assertThrows(ValueException.class, () -> build(s, values(), files, null));
        assertEquals(ValueException.Kind.BAD_VALUE, error.kind());
        assertEquals(1, build(s, values(), List.of(file("covers", "a.png", "image/png")), null).fileGroups().size());
    }

    @Test
    void twoFieldsMappedToTheSameMultiplePropertyShareOneGroup() throws Exception {
        S s = new S();
        s.fileMappings = new ArrayList<>(List.of("photos=media", "videos=media"));
        s.pairs = new ArrayList<>();
        ContentPlan plan = build(s, values(), List.of(file("photos", "a.png", "image/png"), file("videos", "b.mp4", "video/mp4")), null);
        assertEquals(1, plan.fileGroups().size());
        assertEquals(2, plan.fileGroups().get(0).files().size());
    }

    @Test
    void anUnknownTypeIsAConfigurationError() {
        S s = new S();
        s.type = "ctmd:unknown";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void anUnknownPropertyIsAConfigurationErrorInEverySetting() {
        for (String setting : List.of("fields", "fixed", "fileMappings", "fileProperties")) {
            S s = new S();
            switch (setting) {
                case "fields" -> s.fields.add("summary=nosuch");
                case "fixed" -> s.fixed.add("nosuch=1");
                case "fileMappings" -> s.fileMappings.add("x=nosuch");
                default -> s.fileProperties.add("credit=nosuch");
            }
            ConfigException error = assertThrows(ConfigException.class, () -> build(s, values(), films(), null), setting);
            assertTrue(error.getMessage().contains("nosuch"), setting);
        }
        S s = new S();
        s.submitter = "nosuch";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aReferencePropertyCannotBeFilledFromTextAndAFileMappingNeedsAReference() {
        S text = new S();
        text.fields.add("summary=brief");
        assertThrows(ConfigException.class, () -> build(text, values(), films(), null));
        S fixed = new S();
        fixed.fixed.add("brief=x");
        assertThrows(ConfigException.class, () -> build(fixed, values(), films(), null));
        S files = new S();
        files.fileMappings.add("media=description");
        assertThrows(ConfigException.class, () -> build(files, values(), films(), null));
    }

    @Test
    void aTypeThatTextCannotFillIsAConfigurationError() {
        S s = new S();
        s.fields.add("summary=score");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aSingleValuedPropertyMappedFromTwoFieldsIsAConfigurationError() {
        S s = new S();
        s.fields.add("summary=durationMinutes");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aBadFixedValueIsAConfigurationErrorNotTheVisitorsFault() {
        S s = new S();
        s.fixed.add("durationMinutes=many");
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void theSubmitterAndPagePropertiesMustHaveTheRightShape() {
        S submitter = new S();
        submitter.submitter = "durationMinutes";
        assertThrows(ConfigException.class, () -> build(submitter, values(), films(), null));
        S page = new S();
        page.page = "media";
        assertThrows(ConfigException.class, () -> build(page, values(), films(), null), "a multiple reference is not a page");
        S text = new S();
        text.page = "description";
        assertThrows(ConfigException.class, () -> build(text, values(), films(), null));
    }

    @Test
    void theFileMixinMustBeAMixin() {
        S s = new S();
        s.fileMixin = "ctmd:submission";
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
        S missing = new S();
        missing.fileMixin = "nope:mixin";
        assertThrows(ConfigException.class, () -> build(missing, values(), films(), null));
    }

    @Test
    void aTitleFieldNeedsATypeThatHasATitle() {
        Fakes.Catalog bare = new Fakes.Catalog() {
            @Override
            public java.util.Optional<org.jahia.modules.formidable.content.convert.PropertyShape> property(List<String> types, String name) {
                return name.equals("jcr:title") && types.contains(Fakes.Catalog.CONTENT_TYPE) ? java.util.Optional.empty() : super.property(types, name);
            }
        };
        S s = new S();
        assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(s), new PlanBuilder.Submission(values(), films()), WHO, null, TARGET, LANGUAGES, bare));
    }

    private static Settings settings(S s) {
        try {
            return s.build();
        } catch (ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void filesWithoutAFilesFolderAreAConfigurationError() {
        PlanBuilder.Target noFiles = new PlanBuilder.Target(TARGET.parentPath(), null);
        assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(new S()), new PlanBuilder.Submission(values(), films()), WHO, null, noFiles,
                LANGUAGES, CATALOG));
    }

    @Test
    void publishFollowsTheSetting() throws Exception {
        S s = new S();
        s.publish = false;
        assertFalse(build(s, values(), films(), null).publish());
    }

    // ----- categories, rich text, the submitter's profile, single references (the story mapping) -----

    /** The categories of the site: two roots, each with its direct children by node name. */
    private static final class FakeCategories implements Categories {
        final Map<String, Map<String, String>> roots = new HashMap<>(Map.of(
                "root-topics", new java.util.LinkedHashMap<>(Map.of("sports", "id-sports", "culture", "id-culture")),
                "root-regions", new java.util.LinkedHashMap<>(Map.of("europe", "id-europe"))));
        final List<String> asked = new ArrayList<>();

        @Override
        public Map<String, String> childCategories(String rootIdentifier) throws ConfigException {
            asked.add(rootIdentifier);
            Map<String, String> children = roots.get(rootIdentifier);
            if (children == null) {
                throw new ConfigException("no such root");
            }
            return children;
        }
    }

    /** The profiles of the site: what is found for a user, and what was asked. */
    private static final class FakeProfiles implements SubmitterProfiles {
        List<String> found = List.of();
        RepositoryException failure;
        final List<String> asked = new ArrayList<>();
        List<String> constraints;

        @Override
        public List<String> find(String siteKey, String username, List<String> nodeTypes) throws RepositoryException {
            if (failure != null) {
                throw failure;
            }
            asked.add(siteKey + "|" + username);
            constraints = nodeTypes;
            return found;
        }
    }

    private final FakeCategories categories = new FakeCategories();
    private final FakeProfiles profiles = new FakeProfiles();

    private ContentPlan buildWith(S s, Map<String, List<String>> values, List<SubmittedFile> files, PlanBuilder.Page page)
            throws ConfigException, ValueException {
        return PlanBuilder.build(s.build(), new PlanBuilder.Submission(values, files), WHO, page, TARGET, LANGUAGES, CATALOG,
                new PlanBuilder.Lookups(categories, profiles));
    }

    private static S withTopic() {
        S s = new S();
        s.categoryFields.add(new CategoryField("topic", "root-topics"));
        return s;
    }

    private static List<Object> refs(String... ids) {
        List<Object> refs = new ArrayList<>();
        for (String id : ids) {
            refs.add(new Reference(id));
        }
        return refs;
    }

    @Test
    void theNodeNamesOfACategoryFieldBecomeReferencesToThoseCategoriesAndTheMixinIsAdded() throws Exception {
        Map<String, List<String>> values = values();
        values.put("topic", List.of("culture", "sports"));
        ContentPlan plan = buildWith(withTopic(), values, films(), PlanBuilder.Page.NONE);

        TypedValue categories = property(plan, "j:defaultCategory");
        assertEquals(refs("id-culture", "id-sports"), categories.values());
        assertTrue(categories.shape().isWeak() && categories.shape().multiple());
        assertEquals(List.of("jmix:categorized"), plan.contentMixins());
    }

    @Test
    void aCategoryValueThatTheRootDoesNotOfferRefusesTheSubmissionAndNeverCreatesOne() throws Exception {
        for (String bad : new String[]{"unknown", "SPORTS", "../culture", "/sites/systemsite/categories/topics/sports", "topics/sports",
                "sports/..", "sports[2]", "*", "jcr:content"}) {
            Map<String, List<String>> values = values();
            values.put("topic", List.of("sports", bad));
            ValueException error = assertThrows(ValueException.class, () -> buildWith(withTopic(), values, films(), PlanBuilder.Page.NONE), bad);
            assertEquals(ValueException.Kind.BAD_VALUE, error.kind(), bad);
            assertTrue(error.getMessage().contains("topic"), "the log names the field");
            assertFalse(error.getMessage().contains(bad), "and never what was typed: " + bad);
        }
    }

    @Test
    void aCategoryNameIsTrimmedAndBlankValuesAreIgnored() throws Exception {
        Map<String, List<String>> values = values();
        values.put("topic", List.of(" sports ", "", "  "));
        assertEquals(refs("id-sports"), property(buildWith(withTopic(), values, films(), PlanBuilder.Page.NONE), "j:defaultCategory").values());
    }

    @Test
    void nothingSubmittedForTheCategoriesWritesNoPropertyAndNoMixin() throws Exception {
        ContentPlan plan = buildWith(withTopic(), values(), films(), PlanBuilder.Page.NONE);
        assertNull(property(plan, "j:defaultCategory"));
        assertTrue(plan.contentMixins().isEmpty());
    }

    @Test
    void severalCategoryFieldsAndThePagesCategoriesAreUnitedWithoutDuplicates() throws Exception {
        S s = withTopic();
        s.categoryFields.add(new CategoryField("region", "root-regions"));
        s.categoryFields.add(new CategoryField("topic2", "root-topics"));
        s.pageCategories = true;
        Map<String, List<String>> values = values();
        values.put("topic", List.of("sports"));
        values.put("region", List.of("europe"));
        values.put("topic2", List.of("sports", "culture"));
        ContentPlan plan = buildWith(s, values, films(), new PlanBuilder.Page("page-uuid", List.of("id-culture", "id-page-only")));

        assertEquals(refs("id-sports", "id-europe", "id-culture", "id-page-only"), property(plan, "j:defaultCategory").values());
        assertEquals(List.of("root-topics", "root-regions"), categories.asked, "each root is read once, whatever the number of fields");
    }

    @Test
    void thePagesCategoriesAreCopiedAsTheyAreAndNoPageOrNoCategoryMeansNothing() throws Exception {
        S s = new S();
        s.pageCategories = true;
        ContentPlan plan = buildWith(s, values(), films(), new PlanBuilder.Page("page-uuid", List.of("id-a", "id-b")));
        assertEquals(refs("id-a", "id-b"), property(plan, "j:defaultCategory").values());
        assertEquals(List.of("jmix:categorized"), plan.contentMixins());

        for (PlanBuilder.Page none : new PlanBuilder.Page[]{PlanBuilder.Page.NONE, new PlanBuilder.Page("page-uuid", List.of())}) {
            ContentPlan empty = buildWith(s, values(), films(), none);
            assertNull(property(empty, "j:defaultCategory"));
            assertTrue(empty.contentMixins().isEmpty());
        }
        assertTrue(categories.asked.isEmpty(), "the page's categories never go through a root");
    }

    @Test
    void aTypeThatIsAlreadyCategorizedGetsNoMixin() throws Exception {
        Fakes.Catalog categorized = new Fakes.Catalog();
        categorized.categorizedTypes.add(Fakes.Catalog.CONTENT_TYPE);
        S s = new S();
        s.pageCategories = true;
        ContentPlan plan = PlanBuilder.build(s.build(), new PlanBuilder.Submission(values(), films()), WHO,
                new PlanBuilder.Page("page-uuid", List.of("id-a")), TARGET, LANGUAGES, categorized, new PlanBuilder.Lookups(categories, profiles));
        assertEquals(refs("id-a"), property(plan, "j:defaultCategory").values());
        assertTrue(plan.contentMixins().isEmpty());
    }

    @Test
    void aBrokenRootIsAConfigurationError() {
        S s = new S();
        s.categoryFields.add(new CategoryField("topic", "root-gone"));
        assertThrows(ConfigException.class, () -> buildWith(s, values(), films(), PlanBuilder.Page.NONE));
    }

    @Test
    void theCategoriesNeedTheCategorizedMixinToBeInstalled() {
        Fakes.Catalog without = new Fakes.Catalog() {
            @Override
            public java.util.Optional<org.jahia.modules.formidable.content.convert.PropertyShape> property(List<String> types, String name) {
                return name.equals("j:defaultCategory") ? java.util.Optional.empty() : super.property(types, name);
            }
        };
        S s = new S();
        s.pageCategories = true;
        assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(s), new PlanBuilder.Submission(values(), films()), WHO,
                new PlanBuilder.Page("p", List.of("c")), TARGET, LANGUAGES, without, new PlanBuilder.Lookups(categories, profiles)));
    }

    @Test
    void withoutLookupsTheCategorySourcesAreAConfigurationError() {
        S s = withTopic();
        assertThrows(ConfigException.class, () -> build(s, values(), films(), null));
    }

    @Test
    void aRichTextPropertyReceivesTheTextOfAFieldAsEscapedParagraphsAndTheFixedValueAsTyped() throws Exception {
        S s = new S();
        s.fields.add("story=body");
        s.fields.add("tease=teaser");
        Map<String, List<String>> values = values();
        values.put("story", List.of("First <script>alert('x')</script> & more\nsame paragraph\n\nSecond \"one\""));
        values.put("tease", List.of("one", "two"));
        ContentPlan plan = build(s, values, films(), null);

        assertEquals(List.of("<p>First &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; more<br>same paragraph</p><p>Second &quot;one&quot;</p>"),
                property(plan, "body").values());
        assertEquals(List.of("<p>one</p>", "<p>two</p>"), property(plan, "teaser").values());
        assertEquals(List.of("A 30 second film"), property(plan, "description").values(), "an ordinary string stays plain text");

        S fixed = new S();
        fixed.fixed.add("body=<b>typed by the author</b>");
        assertEquals(List.of("<b>typed by the author</b>"), property(build(fixed, values(), films(), null), "body").values(),
                "an author's fixed value is not a visitor's text: it is not touched");
    }

    @Test
    void aBlankRichTextFieldWritesNothing() throws Exception {
        S s = new S();
        s.fields.add("story=body");
        Map<String, List<String>> values = values();
        values.put("story", List.of(" \n \r\n\n "));
        assertNull(property(build(s, values, films(), null), "body"));
    }

    @Test
    void theProfilesOfTheSubmitterAreReferencedWithTheTypesThePropertyAccepts() throws Exception {
        S s = new S();
        s.profiles.add("authors");
        profiles.found = List.of("id-ada", "id-bob");
        ContentPlan plan = buildWith(s, values(), films(), PlanBuilder.Page.NONE);

        assertEquals(refs("id-ada", "id-bob"), property(plan, "authors").values());
        assertEquals(List.of("orvane|ada@northlight.example"), profiles.asked, "the site of the content folder, the platform's user name");
        assertEquals(List.of("ctmd:author"), profiles.constraints);
    }

    @Test
    void aSingleReferenceTakesTheOnlyProfileAndNothingWhenSeveralFitOrNoneDoes() throws Exception {
        S s = new S();
        s.profiles.add("editor");
        profiles.found = List.of("id-ada");
        assertEquals(refs("id-ada"), property(buildWith(s, values(), films(), PlanBuilder.Page.NONE), "editor").values());
        profiles.found = List.of("id-ada", "id-bob");
        assertNull(property(buildWith(s, values(), films(), PlanBuilder.Page.NONE), "editor"), "several for a single property: none, never a guess");
        profiles.found = List.of();
        assertNull(property(buildWith(s, values(), films(), PlanBuilder.Page.NONE), "editor"));
    }

    @Test
    void noProfileWritesNothingAndIsNotAnError() throws Exception {
        S s = new S();
        s.profiles.add("authors");
        ContentPlan plan = buildWith(s, values(), films(), PlanBuilder.Page.NONE);
        assertNull(property(plan, "authors"));
    }

    @Test
    void theProfileSourceFillsReferencePropertiesOnlyAndAloneAndInAKnownSite() {
        S text = new S();
        text.profiles.add("description");
        assertThrows(ConfigException.class, () -> buildWith(text, values(), films(), PlanBuilder.Page.NONE));
        S unknown = new S();
        unknown.profiles.add("nosuch");
        assertThrows(ConfigException.class, () -> buildWith(unknown, values(), films(), PlanBuilder.Page.NONE));
        S twice = new S();
        twice.profiles.add("brief");
        assertThrows(ConfigException.class, () -> buildWith(twice, values(), films(), PlanBuilder.Page.NONE), "the page property is filled by the page source");
        S files = new S();
        files.profiles.add("media");
        assertThrows(ConfigException.class, () -> buildWith(files, values(), films(), PlanBuilder.Page.NONE), "the files fill media");
    }

    @Test
    void theProfilesAreLookedUpOnlyInTheSiteOfTheFolderAndOnlyIfItIsAPlainKey() {
        S s = new S();
        s.profiles.add("authors");
        for (String path : new String[]{"/content/briefs", "/sites", "/sites/", "/sites/or vane/x", "/sites/x'y/z", "/sites/..", "/sitesx/a/b"}) {
            PlanBuilder.Target odd = new PlanBuilder.Target(path, null);
            assertThrows(ConfigException.class, () -> PlanBuilder.build(settings(s), new PlanBuilder.Submission(values(), List.of()), WHO,
                    PlanBuilder.Page.NONE, odd, LANGUAGES, CATALOG, new PlanBuilder.Lookups(categories, profiles)), path);
        }
        assertTrue(profiles.asked.isEmpty(), "no query ran");
    }

    @Test
    void aRepositoryFailureWhileLookingUpTheProfileRefusesRatherThanWriteWithoutAuthors() {
        S s = new S();
        s.profiles.add("authors");
        profiles.failure = new RepositoryException("down");
        assertThrows(ConfigException.class, () -> buildWith(s, values(), films(), PlanBuilder.Page.NONE));
    }

    @Test
    void aFileForASingleWeakReferenceIsKeptWhenItSatisfiesTheConstraintAndItsGroupHoldsIt() throws Exception {
        S s = new S();
        s.fileMappings.add("cover=image");
        for (String mime : new String[]{"image/png", "image/jpeg", "image/webp", "image/svg+xml"}) {
            ContentPlan plan = build(s, values(), List.of(file("cover", "cover.x", mime)), null);
            FileGroupHolder group = new FileGroupHolder(plan, "image");
            assertEquals(1, group.files(), mime);
        }
    }

    /** The group of a plan for a property. */
    private record FileGroupHolder(ContentPlan plan, String property) {
        int files() {
            return plan.fileGroups().stream().filter(group -> group.property().name().equals(property)).mapToInt(group -> group.files().size()).sum();
        }
    }

    @Test
    void aFileThatCannotBeTheTargetsTypeIsABadValueNotAServerError() {
        S s = new S();
        s.fileMappings.add("cover=image");
        for (String mime : new String[]{"application/pdf", "video/mp4", "text/plain"}) {
            ValueException error = assertThrows(ValueException.class, () -> build(s, values(), List.of(file("cover", "cover.x", mime)), null), mime);
            assertEquals(ValueException.Kind.BAD_VALUE, error.kind());
            assertTrue(error.getMessage().contains("image"));
        }
    }

    @Test
    void twoFilesForTheSingleImageAreStillRefused() {
        S s = new S();
        s.fileMappings.add("cover=image");
        assertThrows(ValueException.class, () -> build(s, values(),
                List.of(file("cover", "a.png", "image/png"), file("cover", "b.png", "image/png")), null));
    }

    @Test
    void aPropertyWithoutConstraintTakesAnyFile() throws Exception {
        S s = new S();
        s.fileMappings.add("cover=anyone");
        assertEquals(1, new FileGroupHolder(build(s, values(), List.of(file("cover", "a.pdf", "application/pdf")), null), "anyone").files(),
                "no constraint: any file");
    }
}
