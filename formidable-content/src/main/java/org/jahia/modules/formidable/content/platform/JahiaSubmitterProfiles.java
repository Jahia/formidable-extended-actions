package org.jahia.modules.formidable.content.platform;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@link SubmitterProfiles} on a bound JCR-SQL2 query.
 *
 * <p>The query runs as the system user in the {@code default} workspace, so it ignores ACLs: what keeps it harmless is
 * what it can reach. It reads only the nodes that carry {@code fmdbcntmix:submitterProfile} under
 * {@code /sites/<siteKey>} (the site of the folder the author configured, validated as a plain key before it enters the
 * statement, where a path cannot be a bind variable), matched on the user name bound as a variable, and answers only
 * identifiers. Each candidate is checked again in code (path under the site, exact user name) because the index may
 * match more than the equality. At most {@value #MAX_CANDIDATES} candidates are read.</p>
 *
 * <p>Threading: a stateless singleton; every session lives inside one call.</p>
 */
@Component(service = SubmitterProfiles.class, immediate = true)
public class JahiaSubmitterProfiles implements SubmitterProfiles {

    static final String MIXIN = "fmdbcntmix:submitterProfile";
    static final String USER_PROPERTY = "submitterUser";
    static final int MAX_CANDIDATES = 50;
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final String DEFAULT = "default";

    private final Supplier<JCRTemplate> jcr;

    /** Production constructor: the platform's own template. */
    public JahiaSubmitterProfiles() {
        this(JCRTemplate::getInstance);
    }

    JahiaSubmitterProfiles(Supplier<JCRTemplate> jcr) {
        this.jcr = jcr;
    }

    @Override
    public List<String> find(String siteKey, String username, List<String> nodeTypes) throws RepositoryException {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            throw new IllegalArgumentException("Not a site key");
        }
        if (username == null || username.isBlank()) {
            return List.of();
        }
        String sitePath = "/sites/" + siteKey;
        List<String> constraints = nodeTypes == null ? List.of() : nodeTypes;
        return jcr.get().doExecuteWithSystemSessionAsUser(null, DEFAULT, null, (JCRCallback<List<String>>) session -> {
            Query query = queryManager(session).createQuery(
                    "SELECT * FROM [" + MIXIN + "] AS p WHERE ISDESCENDANTNODE(p, '" + sitePath + "') AND p.[" + USER_PROPERTY + "] = $user",
                    Query.JCR_SQL2);
            query.bindValue("user", session.getValueFactory().createValue(username));
            query.setLimit(MAX_CANDIDATES);
            Set<String> identifiers = new LinkedHashSet<>();
            NodeIterator nodes = query.execute().getNodes();
            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                if (isProfileOf(node, sitePath, username) && satisfies(node, constraints)) {
                    identifiers.add(node.getIdentifier());
                }
            }
            return new ArrayList<>(identifiers);
        });
    }

    /** The query manager of the session, through the plain JCR interface (Jahia's wrappers add nothing this query needs). */
    QueryManager queryManager(JCRSessionWrapper session) throws RepositoryException {
        return session.getWorkspace().getQueryManager();
    }

    private static boolean isProfileOf(Node node, String sitePath, String username) throws RepositoryException {
        return node.getPath().startsWith(sitePath + "/") && node.isNodeType(MIXIN) && node.hasProperty(USER_PROPERTY)
                && username.equals(node.getProperty(USER_PROPERTY).getString());
    }

    private static boolean satisfies(Node node, List<String> constraints) throws RepositoryException {
        if (constraints.isEmpty()) {
            return true;
        }
        for (String type : constraints) {
            if (node.isNodeType(type)) {
                return true;
            }
        }
        return false;
    }
}
