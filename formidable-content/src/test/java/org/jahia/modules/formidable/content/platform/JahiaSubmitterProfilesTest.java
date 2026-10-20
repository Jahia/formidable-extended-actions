package org.jahia.modules.formidable.content.platform;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.QueryManagerWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.Property;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The profile query: bound, under one site, as the system user in default, and checked again in code. */
class JahiaSubmitterProfilesTest {

    private JCRTemplate template;
    private JCRSessionWrapper session;
    private QueryManager manager;
    private Query query;
    private final List<Node> results = new ArrayList<>();
    private JahiaSubmitterProfiles profiles;

    @BeforeEach
    void setUp() throws Exception {
        template = mock(JCRTemplate.class);
        session = mock(JCRSessionWrapper.class);
        manager = mock(QueryManager.class);
        query = mock(Query.class);
        QueryResult result = mock(QueryResult.class);
        ValueFactory factory = mock(ValueFactory.class);
        when(factory.createValue(anyString())).thenReturn(mock(Value.class));
        when(session.getValueFactory()).thenReturn(factory);
        when(manager.createQuery(anyString(), eq(Query.JCR_SQL2))).thenReturn(query);
        when(query.execute()).thenReturn(result);
        when(result.getNodes()).thenAnswer(call -> iterator(results));
        when(template.doExecuteWithSystemSessionAsUser(isNull(), eq("default"), isNull(), any(JCRCallback.class)))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(session));
        profiles = new JahiaSubmitterProfiles(() -> template) {
            @Override
            QueryManager queryManager(JCRSessionWrapper wrapper) {
                return manager;
            }
        };
    }

    private static NodeIterator iterator(List<Node> nodes) {
        NodeIterator it = mock(NodeIterator.class);
        java.util.Iterator<Node> inner = nodes.iterator();
        when(it.hasNext()).thenAnswer(call -> inner.hasNext());
        when(it.nextNode()).thenAnswer(call -> inner.next());
        return it;
    }

    private Node profile(String id, String path, String user, String... types) throws Exception {
        Node node = mock(Node.class);
        when(node.getIdentifier()).thenReturn(id);
        when(node.getPath()).thenReturn(path);
        when(node.isNodeType("fmdbcntmix:submitterProfile")).thenReturn(true);
        when(node.hasProperty("submitterUser")).thenReturn(true);
        Property property = mock(Property.class);
        when(property.getString()).thenReturn(user);
        when(node.getProperty("submitterUser")).thenReturn(property);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        results.add(node);
        return node;
    }

    @Test
    void theUserNameIsBoundAndNeverPartOfTheStatement() throws Exception {
        profile("p1", "/sites/orvane/contents/authors/ada", "ada'; DROP", "ctmd:author");
        profiles.find("orvane", "ada'; DROP", List.of("ctmd:author"));

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(manager).createQuery(statement.capture(), eq(Query.JCR_SQL2));
        assertEquals("SELECT * FROM [fmdbcntmix:submitterProfile] AS p WHERE ISDESCENDANTNODE(p, '/sites/orvane') AND p.[submitterUser] = $user",
                statement.getValue());
        assertFalse(statement.getValue().contains("DROP"));
        verify(query).bindValue(eq("user"), any(Value.class));
        verify(session.getValueFactory()).createValue("ada'; DROP");
        verify(query).setLimit(JahiaSubmitterProfiles.MAX_CANDIDATES);
    }

    @Test
    void onlyTheProfilesThatFitThePropertysTypesAndBelongToTheUserInTheSiteAreKept() throws Exception {
        profile("author", "/sites/orvane/contents/authors/ada", "ada", "ctmd:author");
        profile("other-type", "/sites/orvane/contents/people/ada", "ada", "ctmd:visitor");
        profile("other-site", "/sites/rival/contents/authors/ada", "ada", "ctmd:author");
        profile("other-user", "/sites/orvane/contents/authors/bob", "bob", "ctmd:author");
        profile("author", "/sites/orvane/contents/authors/ada", "ada", "ctmd:author"); // the same node twice
        profile("lookalike", "/sites/orvane-extra/contents/ada", "ada", "ctmd:author");

        assertEquals(List.of("author"), profiles.find("orvane", "ada", List.of("ctmd:author")));
    }

    @Test
    void anyOneOfSeveralTypesIsEnoughAndNoTypeKeepsEveryProfile() throws Exception {
        profile("a", "/sites/orvane/x/a", "ada", "ctmd:author");
        profile("b", "/sites/orvane/x/b", "ada", "ctmd:guest");
        profile("c", "/sites/orvane/x/c", "ada");
        assertEquals(List.of("a", "b"), profiles.find("orvane", "ada", List.of("ctmd:author", "ctmd:guest")));
        assertEquals(List.of("a", "b", "c"), profiles.find("orvane", "ada", List.of()));
    }

    @Test
    void aSiteKeyThatIsNotAPlainKeyNeverReachesTheStatement() throws Exception {
        for (String bad : new String[]{null, "", "a b", "x'y", "x/y", "..", "a') OR ISDESCENDANTNODE(p, '/"}) {
            assertThrows(IllegalArgumentException.class, () -> profiles.find(bad, "ada", List.of()), String.valueOf(bad));
        }
        verify(manager, never()).createQuery(anyString(), anyString());
    }

    @Test
    void aBlankUserFindsNothingWithoutQuerying() throws Exception {
        assertTrue(profiles.find("orvane", " ", List.of()).isEmpty());
        assertTrue(profiles.find("orvane", null, List.of()).isEmpty());
        verify(manager, never()).createQuery(anyString(), anyString());
    }

    @Test
    void aRepositoryFailureReachesTheCaller() throws Exception {
        when(query.execute()).thenThrow(new RepositoryException("index down"));
        assertThrows(RepositoryException.class, () -> profiles.find("orvane", "ada", List.of()));
    }
}
