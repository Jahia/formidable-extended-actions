package org.jahia.modules.formidable.content.platform;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link Categories} on Jahia's {@link JCRTemplate}.
 *
 * <p>The children are read in the {@code live} workspace with a system session: the categories the form offered to the
 * visitor (the engine lists them from live), so a category that is not published is not accepted, and the reference
 * that is written does not dangle once the content is published. The children are listed and compared by name, never
 * fetched by a path built from the submitted value.</p>
 *
 * <p>Threading: a stateless singleton; every session lives inside one call.</p>
 */
@Component(service = Categories.class, immediate = true)
public class JahiaCategories implements Categories {

    private static final String LIVE = "live";
    private static final String CATEGORY = "jnt:category";

    private final Supplier<JCRTemplate> jcr;

    /** Production constructor: the platform's own template. */
    public JahiaCategories() {
        this(JCRTemplate::getInstance);
    }

    JahiaCategories(Supplier<JCRTemplate> jcr) {
        this.jcr = jcr;
    }

    @Override
    public Map<String, String> childCategories(String rootIdentifier) throws ConfigException {
        if (rootIdentifier == null || rootIdentifier.isBlank()) {
            throw new ConfigException("A category field has no root category");
        }
        Optional<Map<String, String>> children;
        try {
            children = jcr.get().doExecuteWithSystemSessionAsUser(null, LIVE, null, (JCRCallback<Optional<Map<String, String>>>) session -> {
                JCRNodeWrapper root;
                try {
                    root = session.getNodeByIdentifier(rootIdentifier);
                } catch (ItemNotFoundException e) {
                    return Optional.empty();
                }
                if (!root.isNodeType(CATEGORY)) {
                    return Optional.empty();
                }
                Map<String, String> byName = new LinkedHashMap<>();
                for (JCRNodeWrapper child : root.getNodes()) {
                    if (child.isNodeType(CATEGORY)) {
                        byName.putIfAbsent(child.getName(), child.getIdentifier());
                    }
                }
                return Optional.of(byName);
            });
        } catch (RepositoryException e) {
            throw new ConfigException("The categories of the root " + rootIdentifier + " could not be read", e);
        }
        return children.orElseThrow(() -> new ConfigException("The root category " + rootIdentifier
                + " of a category field does not exist in live or is not a category (publish it)"));
    }
}
