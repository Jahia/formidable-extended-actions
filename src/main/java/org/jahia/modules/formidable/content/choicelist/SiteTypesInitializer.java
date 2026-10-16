package org.jahia.modules.formidable.content.choicelist;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeTypeIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Feeds the two type settings of the Create content action from the modules installed on the action's site, so a
 * contributor picks a type instead of typing its name: {@code choicelist[formidableContentTypes]} lists the content
 * types a node can be created with (primary, not abstract, a {@code jnt:content}), {@code
 * choicelist[formidableContentFileMixins]} the mixins those modules declare (what a stored file can carry). Labels
 * read "Label (prefix:name)". The stored value is the type's name, which the action checks again at every submission.
 *
 * <p>Threading: stateless singletons; the registry is read per call.</p>
 */
public abstract class SiteTypesInitializer implements ModuleChoiceListInitializer {

    private static final Logger log = LoggerFactory.getLogger(SiteTypesInitializer.class);

    /** Content types a node can be created with. */
    @Component(service = ModuleChoiceListInitializer.class)
    public static class ContentTypes extends SiteTypesInitializer {
        public static final String KEY = "formidableContentTypes";

        @Override
        protected NodeTypeIterator candidates(List<String> modules) throws RepositoryException {
            return NodeTypeRegistry.getInstance().getPrimaryNodeTypes(modules);
        }

        @Override
        protected boolean accepts(ExtendedNodeType type) {
            return !type.isAbstract() && !type.isMixin() && type.isNodeType("jnt:content");
        }

        @Override
        public String getKey() {
            return KEY;
        }
    }

    /** Mixins a stored file can carry. */
    @Component(service = ModuleChoiceListInitializer.class)
    public static class FileMixins extends SiteTypesInitializer {
        public static final String KEY = "formidableContentFileMixins";

        @Override
        protected NodeTypeIterator candidates(List<String> modules) throws RepositoryException {
            return NodeTypeRegistry.getInstance().getMixinNodeTypes(modules);
        }

        @Override
        protected boolean accepts(ExtendedNodeType type) {
            return type.isMixin();
        }

        @Override
        public String getKey() {
            return KEY;
        }
    }

    /** @return the types of these modules (their system ids) */
    protected abstract NodeTypeIterator candidates(List<String> modules) throws RepositoryException;

    /** @return whether a type is offered */
    protected abstract boolean accepts(ExtendedNodeType type);

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values,
                                                     Locale locale, Map<String, Object> context) {
        Object node = context == null ? null : context.get("contextNode");
        if (!(node instanceof JCRNodeWrapper)) {
            node = context == null ? null : context.get("contextParent");
        }
        if (!(node instanceof JCRNodeWrapper contextNode)) {
            return List.of();
        }
        List<ChoiceListValue> out = new ArrayList<>();
        try {
            JCRSiteNode site = contextNode.getResolveSite();
            if (site == null) {
                return List.of();
            }
            Set<String> modules = site.getInstalledModulesWithAllDependencies();
            NodeTypeIterator types = candidates(new ArrayList<>(modules));
            while (types.hasNext()) {
                ExtendedNodeType type = (ExtendedNodeType) types.nextNodeType();
                if (accepts(type)) {
                    String label = type.getLabel(locale);
                    out.add(new ChoiceListValue(label == null || label.isBlank() || label.equals(type.getName())
                            ? type.getName() : label + " (" + type.getName() + ")", type.getName()));
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[formidable-content] The types of the site could not be listed.", e);
        }
        out.sort(Comparator.comparing(ChoiceListValue::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public void setKey(String key) {
        // fixed key
    }
}
