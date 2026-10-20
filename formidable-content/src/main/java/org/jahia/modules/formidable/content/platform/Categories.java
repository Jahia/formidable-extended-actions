package org.jahia.modules.formidable.content.platform;

import org.jahia.modules.formidable.content.mapping.ConfigException;

import java.util.Map;

/**
 * What the action asks the platform about categories: the direct children of a root category, by node name. A seam over
 * Jahia's repository, so the plan of a submission can be built and tested without one.
 *
 * <p>Implementations are stateless singletons and thread-safe.</p>
 */
public interface Categories {

    /**
     * The categories a choice field in category mode offers: the direct {@code jnt:category} children of its root,
     * read with a system session. The root is chosen by the author of the form (never by a submitted value), and a
     * submitted value only selects one entry of the map: it is never used as a path.
     *
     * @param rootIdentifier the identifier of the field's root category
     * @return node name to identifier of every direct {@code jnt:category} child, in the repository's order
     * @throws ConfigException when the root does not exist, is not a category, or cannot be read: the field's
     *                         configuration is broken
     */
    Map<String, String> childCategories(String rootIdentifier) throws ConfigException;
}
