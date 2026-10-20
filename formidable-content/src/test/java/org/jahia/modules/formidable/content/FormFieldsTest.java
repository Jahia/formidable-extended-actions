package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.mapping.FormFields;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which choice fields are in category mode, and the root of each: what a categories row needs from the form. */
class FormFieldsTest {

    private static JCRNodeIteratorWrapper iterator(List<JCRNodeWrapper> nodes) {
        return mock(JCRNodeIteratorWrapper.class, call -> "iterator".equals(call.getMethod().getName()) ? nodes.iterator() : RETURNS_DEFAULTS.answer(call));
    }

    private static JCRNodeWrapper field(String name, boolean categoryMode, String root, boolean nonSubmittable) throws Exception {
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.getName()).thenReturn(name);
        when(field.getIdentifier()).thenReturn("id-" + name);
        when(field.isNodeType("fmdbmix:formElement")).thenReturn(true);
        when(field.isNodeType("fmdbmix:nonSubmittable")).thenReturn(nonSubmittable);
        when(field.isNodeType("fmdbmix:categoryOptions")).thenReturn(categoryMode);
        when(field.getNodes()).thenReturn(iterator(List.of()));
        if (root != null) {
            when(field.hasProperty("optionsRootCategory")).thenReturn(true);
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(root);
            when(field.getProperty("optionsRootCategory")).thenReturn(property);
        }
        return field;
    }

    private static FormFields formOf(JCRNodeWrapper... fields) throws Exception {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        JCRNodeWrapper container = mock(JCRNodeWrapper.class);
        when(container.getNodes()).thenReturn(iterator(List.of(fields)));
        when(form.hasNode("fields")).thenReturn(true);
        when(form.getNode("fields")).thenReturn(container);
        return FormFields.of(form);
    }

    @Test
    void aChoiceFieldInCategoryModeNamesItsRootCategory() throws Exception {
        FormFields fields = formOf(field("topic", true, "root-topics", false), field("title", false, null, false));
        assertEquals(Optional.of("root-topics"), fields.categoryRootOf("topic"));
        assertEquals(Optional.empty(), fields.categoryRootOf("title"));
        assertEquals(Optional.empty(), fields.categoryRootOf("nosuch"));
    }

    @Test
    void aFieldWithTheMixinButNoRootOrARootButNoMixinIsNotInCategoryMode() throws Exception {
        FormFields fields = formOf(field("noRoot", true, null, false), field("blankRoot", true, "  ", false), field("noMixin", false, "root-x", false));
        assertTrue(fields.categoryRootOf("noRoot").isEmpty());
        assertTrue(fields.categoryRootOf("blankRoot").isEmpty());
        assertTrue(fields.categoryRootOf("noMixin").isEmpty());
    }

    @Test
    void theFirstFieldOfANameWinsAsInTheEngine() throws Exception {
        FormFields fields = formOf(field("topic", false, null, false), field("topic", true, "root-second", false));
        assertTrue(fields.categoryRootOf("topic").isEmpty());
    }
}
