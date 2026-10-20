package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.ContentMapping;
import org.jahia.modules.formidable.content.mapping.ContentMapping.Row;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMappingTest {

    /** The form of the tests: node ids and the names their fields are submitted under now. */
    private static final Map<String, String> FIELDS = Map.of("id-title", "title", "id-caption", "caption", "id-media", "media",
            "id-credit", "credit");
    private static final Function<Row, Optional<String>> BY_ID = row -> Optional.ofNullable(FIELDS.get(row.nodeId()));

    private static final String DOCUMENT = "{\"version\":1,\"rows\":["
            + "{\"target\":\"content\",\"property\":\"jcr:title\",\"source\":\"field\",\"nodeId\":\"id-title\",\"fieldName\":\"title\"},"
            + "{\"target\":\"content\",\"property\":\"caption\",\"source\":\"field\",\"nodeId\":\"id-caption\",\"fieldName\":\"oldName\"},"
            + "{\"target\":\"content\",\"property\":\"photos\",\"source\":\"files\",\"nodeId\":\"id-media\",\"fieldName\":\"media\"},"
            + "{\"target\":\"content\",\"property\":\"status\",\"source\":\"constant\",\"values\":[\"submitted\"]},"
            + "{\"target\":\"content\",\"property\":\"tags\",\"source\":\"constant\",\"values\":[\"a\",\"b\",\"  \"]},"
            + "{\"target\":\"content\",\"property\":\"submittedBy\",\"source\":\"submitter\"},"
            + "{\"target\":\"content\",\"property\":\"company\",\"source\":\"organization\"},"
            + "{\"target\":\"content\",\"property\":\"brief\",\"source\":\"page\"},"
            + "{\"target\":\"file\",\"property\":\"credit\",\"source\":\"field\",\"nodeId\":\"id-credit\",\"fieldName\":\"credit\",\"future\":true}"
            + "]}";

    @Test
    void everySourceBecomesThePartThePlanBuilderTakesAndARenamedFieldStillResolves() throws Exception {
        // Verifies the translation of each source, the title row set apart, constants split per value (blank dropped),
        // a field row resolved by its node id although its stored name is stale, and an unknown key ignored.
        ContentMapping.Parts parts = ContentMapping.toParts(ContentMapping.parse(DOCUMENT), BY_ID);

        assertEquals("title", parts.titleField());
        assertEquals(List.of(new Mappings.Entry("caption", "caption")), parts.fieldMappings());
        assertEquals(List.of(new Mappings.Entry("media", "photos")), parts.fileMappings());
        assertEquals(List.of(new Mappings.Entry("credit", "credit")), parts.fileProperties());
        assertEquals(List.of(new Mappings.Entry("status", "submitted"), new Mappings.Entry("tags", "a"), new Mappings.Entry("tags", "b")),
                parts.fixedValues());
        assertEquals("submittedBy", parts.submitterProperty());
        assertEquals("company", parts.organizationProperty());
        assertEquals("brief", parts.pageProperty());
    }

    @Test
    void aBlankDocumentMapsNothing() throws Exception {
        // Verifies an action saved without a mapping: no row, every part empty.
        for (String blank : new String[]{null, "", "  ", "{\"version\":1}"}) {
            ContentMapping.Parts parts = ContentMapping.toParts(ContentMapping.parse(blank), BY_ID);
            assertNull(parts.titleField());
            assertTrue(parts.fieldMappings().isEmpty() && parts.fixedValues().isEmpty() && parts.fileMappings().isEmpty());
        }
    }

    @Test
    void aDocumentTheActionCannotTrustIsAConfigurationError() {
        // Verifies the refusals: not JSON, a row without property, an unknown source or target.
        for (String bad : new String[]{
                "summary=description",
                "{\"rows\":[{\"source\":\"field\",\"nodeId\":\"id-title\"}]}",
                "{\"rows\":[{\"property\":\"x\",\"source\":\"guess\"}]}",
                "{\"rows\":[{\"property\":\"x\",\"target\":\"parent\",\"source\":\"submitter\"}]}",
                "{\"rows\":[42]}"}) {
            assertThrows(ConfigException.class, () -> ContentMapping.parse(bad), bad);
        }
    }

    @Test
    void aRowTheFormNoLongerHasAFieldForIsAConfigurationError() throws Exception {
        // Verifies a deleted field is refused, never silently skipped: the author must fix the mapping.
        List<Row> rows = ContentMapping.parse("{\"rows\":[{\"property\":\"caption\",\"source\":\"field\",\"nodeId\":\"gone\",\"fieldName\":\"caption2\"}]}");
        ConfigException error = assertThrows(ConfigException.class, () -> ContentMapping.toParts(rows, BY_ID));
        assertTrue(error.getMessage().contains("caption2"));
    }

    @Test
    void sourcesThatFillOnePropertyAreUsedOnceAndFileRowsTakeFieldsOnly() throws Exception {
        // Verifies the rules the editor also enforces: one submitter, organization, page property and title field;
        // a constant needs a value; a file property is filled from a form field only.
        for (String bad : new String[]{
                "{\"rows\":[{\"property\":\"a\",\"source\":\"submitter\"},{\"property\":\"b\",\"source\":\"submitter\"}]}",
                "{\"rows\":[{\"property\":\"a\",\"source\":\"page\"},{\"property\":\"b\",\"source\":\"page\"}]}",
                "{\"rows\":[{\"property\":\"jcr:title\",\"source\":\"field\",\"nodeId\":\"id-title\"},{\"property\":\"jcr:title\",\"source\":\"field\",\"nodeId\":\"id-caption\"}]}",
                "{\"rows\":[{\"property\":\"a\",\"source\":\"constant\",\"values\":[]}]}",
                "{\"rows\":[{\"target\":\"file\",\"property\":\"credit\",\"source\":\"constant\",\"values\":[\"x\"]}]}"}) {
            List<Row> rows = ContentMapping.parse(bad);
            assertThrows(ConfigException.class, () -> ContentMapping.toParts(rows, BY_ID), bad);
        }
    }

    private static final String STORY = "{\"version\":1,\"rows\":["
            + "{\"target\":\"content\",\"property\":\"j:defaultCategory\",\"source\":\"field\",\"nodeId\":\"id-topic\",\"fieldName\":\"topic\"},"
            + "{\"target\":\"content\",\"property\":\"j:defaultCategory\",\"source\":\"field\",\"nodeId\":\"id-region\",\"fieldName\":\"region\"},"
            + "{\"target\":\"content\",\"property\":\"j:defaultCategory\",\"source\":\"pageCategories\"},"
            + "{\"target\":\"content\",\"property\":\"authors\",\"source\":\"submitterProfile\"},"
            + "{\"target\":\"content\",\"property\":\"editor\",\"source\":\"submitterprofile\"},"
            + "{\"target\":\"content\",\"property\":\"image\",\"source\":\"files\",\"nodeId\":\"id-media\",\"fieldName\":\"media\"}"
            + "]}";
    private static final Function<Row, Optional<String>> STORY_FIELDS = row -> Optional.ofNullable(Map.of("id-topic", "topic",
            "id-region", "region", "id-media", "media").get(row.nodeId()));

    @Test
    void theCategoryAndProfileSourcesAreReadInTheirCamelCaseSpelling() throws Exception {
        // Verifies the document shapes of the story mapping: category fields in row order (they never become ordinary
        // field mappings), the page's categories, the profile properties, and the existing rows unchanged.
        ContentMapping.Parts parts = ContentMapping.toParts(ContentMapping.parse(STORY), STORY_FIELDS);

        assertEquals(List.of("topic", "region"), parts.categoryFields());
        assertTrue(parts.pageCategories());
        assertEquals(List.of("authors", "editor"), parts.profileProperties());
        assertTrue(parts.fieldMappings().isEmpty(), "j:defaultCategory is not an ordinary field mapping");
        assertEquals(List.of(new Mappings.Entry("media", "image")), parts.fileMappings());
        assertEquals(ContentMapping.Source.PAGE_CATEGORIES, ContentMapping.parse(STORY).get(2).source());
        assertEquals(ContentMapping.Source.SUBMITTER_PROFILE, ContentMapping.parse(STORY).get(3).source());
    }

    @Test
    void withoutTheNewRowsTheNewPartsAreEmpty() throws Exception {
        ContentMapping.Parts parts = ContentMapping.toParts(ContentMapping.parse(DOCUMENT), BY_ID);
        assertTrue(parts.categoryFields().isEmpty() && parts.profileProperties().isEmpty());
        assertFalse(parts.pageCategories());
    }

    @Test
    void theCategoriesTakeOnlyAFieldOrThePagesCategoriesAndThePagesCategoriesOnlyFillTheCategories() throws Exception {
        // Verifies the refusals: any other source on j:defaultCategory, the page's categories on another property, the
        // profile twice on one property, a file property filled from the profile.
        for (String bad : new String[]{
                "{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"constant\",\"values\":[\"x\"]}]}",
                "{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"submitter\"}]}",
                "{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"page\"}]}",
                "{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"files\",\"nodeId\":\"id-media\"}]}",
                "{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"submitterProfile\"}]}",
                "{\"rows\":[{\"property\":\"tags\",\"source\":\"pageCategories\"}]}",
                "{\"rows\":[{\"property\":\"authors\",\"source\":\"submitterProfile\"},{\"property\":\"authors\",\"source\":\"submitterProfile\"}]}",
                "{\"rows\":[{\"target\":\"file\",\"property\":\"credit\",\"source\":\"submitterProfile\"}]}",
                "{\"rows\":[{\"target\":\"file\",\"property\":\"credit\",\"source\":\"pageCategories\"}]}"}) {
            List<Row> rows = ContentMapping.parse(bad);
            assertThrows(ConfigException.class, () -> ContentMapping.toParts(rows, BY_ID), bad);
        }
    }

    @Test
    void aCategoryRowWhoseFieldLeftTheFormIsAConfigurationError() throws Exception {
        List<Row> rows = ContentMapping.parse("{\"rows\":[{\"property\":\"j:defaultCategory\",\"source\":\"field\",\"nodeId\":\"gone\",\"fieldName\":\"topic\"}]}");
        assertThrows(ConfigException.class, () -> ContentMapping.toParts(rows, STORY_FIELDS));
    }
}
