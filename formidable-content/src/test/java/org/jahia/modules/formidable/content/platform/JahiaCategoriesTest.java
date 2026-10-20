package org.jahia.modules.formidable.content.platform;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The categories a field offers: the direct jnt:category children of its root, by name, read in live as the system user. */
class JahiaCategoriesTest {

    private JCRTemplate template;
    private JCRSessionWrapper session;
    private JahiaCategories categories;

    @BeforeEach
    void setUp() throws Exception {
        template = mock(JCRTemplate.class);
        session = mock(JCRSessionWrapper.class);
        when(template.doExecuteWithSystemSessionAsUser(isNull(), eq("live"), isNull(), any(JCRCallback.class)))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(session));
        categories = new JahiaCategories(() -> template);
    }

    private static JCRNodeIteratorWrapper iterator(List<JCRNodeWrapper> nodes) {
        return mock(JCRNodeIteratorWrapper.class, call -> "iterator".equals(call.getMethod().getName()) ? nodes.iterator() : RETURNS_DEFAULTS.answer(call));
    }

    private static JCRNodeWrapper category(String name, String id, boolean isCategory) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getIdentifier()).thenReturn(id);
        when(node.isNodeType("jnt:category")).thenReturn(isCategory);
        return node;
    }

    @Test
    void theDirectCategoryChildrenOfTheRootAreListedByNodeName() throws Exception {
        JCRNodeWrapper root = category("topics", "root", true);
        List<JCRNodeWrapper> children = List.of(category("sports", "id-sports", true), category("not-a-category", "id-x", false),
                category("culture", "id-culture", true));
        when(root.getNodes()).thenReturn(iterator(children));
        when(session.getNodeByIdentifier("root")).thenReturn(root);

        assertEquals(Map.of("sports", "id-sports", "culture", "id-culture"), categories.childCategories("root"));
        verify(session, never()).getNode(anyString());
    }

    @Test
    void aRootThatIsMissingOrNotACategoryIsAConfigurationError() throws Exception {
        when(session.getNodeByIdentifier("gone")).thenThrow(new ItemNotFoundException("gone"));
        assertThrows(ConfigException.class, () -> categories.childCategories("gone"));
        JCRNodeWrapper page = category("home", "page", false);
        when(session.getNodeByIdentifier("page")).thenReturn(page);
        assertThrows(ConfigException.class, () -> categories.childCategories("page"), "a page is not a category root");
        assertThrows(ConfigException.class, () -> categories.childCategories(" "));
        assertThrows(ConfigException.class, () -> categories.childCategories(null));
    }

    @Test
    void aRepositoryFailureIsAConfigurationErrorWithItsCause() throws Exception {
        when(session.getNodeByIdentifier("root")).thenThrow(new RepositoryException("down"));
        ConfigException error = assertThrows(ConfigException.class, () -> categories.childCategories("root"));
        assertEquals(RepositoryException.class, error.getCause().getClass());
    }
}
