package org.jahia.modules.formidable.content.convert;

import javax.jcr.PropertyType;
import java.util.List;

/**
 * What the node type says about one property, reduced to what the conversion and the write need. Immutable.
 *
 * @param name             the property name as the definition spells it ({@code ctmd:title}, {@code submissionStatus})
 * @param type             a {@link PropertyType} constant
 * @param multiple         whether the property holds several values
 * @param i18n             whether the value is translated (written once per language of the site)
 * @param richText         whether the property is a string edited as rich text (selector {@code richtext}): a value typed
 *                         in a form field is then converted to safe HTML
 * @param valueConstraints the value constraints of the definition; for a reference, the node types the target must
 *                         be of (any one of them), empty when unconstrained
 */
public record PropertyShape(String name, int type, boolean multiple, boolean i18n, boolean richText, List<String> valueConstraints) {

    public PropertyShape {
        valueConstraints = valueConstraints == null ? List.of() : List.copyOf(valueConstraints);
    }

    /**
     * A shape with no rich text selector and no value constraint.
     *
     * @param name     the property name
     * @param type     a {@link PropertyType} constant
     * @param multiple whether the property holds several values
     * @param i18n     whether the value is translated
     */
    public PropertyShape(String name, int type, boolean multiple, boolean i18n) {
        this(name, type, multiple, i18n, false, List.of());
    }

    /** @return whether the property holds references to nodes ({@code reference} or {@code weakreference}) */
    public boolean isReference() {
        return type == PropertyType.REFERENCE || type == PropertyType.WEAKREFERENCE;
    }

    /** @return whether a reference of this property is weak */
    public boolean isWeak() {
        return type == PropertyType.WEAKREFERENCE;
    }
}
