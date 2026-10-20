package org.jahia.modules.formidable.content.plan;

/**
 * A choice field in category mode that fills {@code j:defaultCategory}: the name it is submitted under and the root
 * category its options come from. Its submitted values are the node names of direct children of that root. Immutable.
 *
 * @param fieldName      the name the field is submitted under
 * @param rootIdentifier the identifier of the root category ({@code optionsRootCategory} of the field)
 */
public record CategoryField(String fieldName, String rootIdentifier) {
}
