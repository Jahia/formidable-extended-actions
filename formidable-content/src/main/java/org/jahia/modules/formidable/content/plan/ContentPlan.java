package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.convert.TypedValue;

import java.util.List;
import java.util.Locale;

/**
 * Everything that will be written for one submission, decided and validated before the first write: the type, the two
 * folders, the title and the properties (converted to their types), the files with the property that references them,
 * what is added to every file, whether to publish. Immutable; no JCR object in it, so it can be built and checked
 * without a repository.
 *
 * @param contentType    the primary type of the content node
 * @param parentPath     the folder the content node is created in (the subfolder of the submitter's group, when one applies)
 * @param filesPath      the folder the files are stored in, {@code null} when there is no file to store
 * @param title          the title in every language, or {@code null}: it also names the node
 * @param properties     the properties of the content node, the title excluded; later entries override earlier ones
 * @param fileGroups     the files to store, with the property that references each group
 * @param fileMixin      a mixin added to every stored file, or {@code null}
 * @param fileProperties the properties written on every stored file
 * @param publish        whether the content and its files are published
 * @param languages      the languages of the site, the default one first: what is translated is written in each
 * @param contentMixins  the mixins added to the content node before its properties are written (those of the properties
 *                       the type does not declare, {@code jmix:categorized} for the categories); none the node already is of
 */
public record ContentPlan(
        String contentType,
        String parentPath,
        String filesPath,
        String title,
        List<TypedValue> properties,
        List<FileGroup> fileGroups,
        String fileMixin,
        List<TypedValue> fileProperties,
        boolean publish,
        List<Locale> languages,
        List<String> contentMixins
) {

    /**
     * A plan with no extra mixin on the content node.
     *
     * @param contentType    the primary type of the content node
     * @param parentPath     the folder the content node is created in
     * @param filesPath      the folder the files are stored in, or {@code null}
     * @param title          the title, or {@code null}
     * @param properties     the properties of the content node
     * @param fileGroups     the files to store
     * @param fileMixin      a mixin added to every stored file, or {@code null}
     * @param fileProperties the properties written on every stored file
     * @param publish        whether to publish
     * @param languages      the languages of the site, the default first
     */
    public ContentPlan(String contentType, String parentPath, String filesPath, String title, List<TypedValue> properties,
                       List<FileGroup> fileGroups, String fileMixin, List<TypedValue> fileProperties, boolean publish,
                       List<Locale> languages) {
        this(contentType, parentPath, filesPath, title, properties, fileGroups, fileMixin, fileProperties, publish, languages, List.of());
    }

    public ContentPlan {
        contentMixins = contentMixins == null ? List.of() : List.copyOf(contentMixins);
        properties = List.copyOf(properties);
        fileGroups = List.copyOf(fileGroups);
        fileProperties = List.copyOf(fileProperties);
        languages = List.copyOf(languages);
    }
}
