package org.jahia.modules.formidable.content.platform;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * What the action asks the platform about the submitter's profile content: the nodes that carry the mixin
 * {@code fmdbcntmix:submitterProfile} with the submitter's user name. A seam over Jahia's repository.
 *
 * <p>Implementations are stateless singletons and thread-safe.</p>
 */
public interface SubmitterProfiles {

    /**
     * Finds the profiles of a user in a site. The query is bound (the user name is never part of the statement), reads
     * only under {@code /sites/<siteKey>} and runs as the system user in the {@code default} workspace.
     *
     * @param siteKey   the key of the site, as validated by the caller: letters, digits, {@code -} and {@code _}
     * @param username  the submitter's Jahia user name
     * @param nodeTypes the node types of the property the profiles will be written to: a profile is kept when it is of
     *                  any one of them; empty keeps every profile
     * @return the identifiers of the matching profiles, never {@code null}; empty when there is none
     * @throws RepositoryException when the repository cannot be read: the caller refuses rather than write a story
     *                             without its authors
     */
    List<String> find(String siteKey, String username, List<String> nodeTypes) throws RepositoryException;
}
