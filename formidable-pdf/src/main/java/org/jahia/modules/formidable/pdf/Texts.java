package org.jahia.modules.formidable.pdf;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The sentences the visitor reads, on the download button and in the document, from the module's own bundle
 * ({@code resources/formidable-pdf*.properties}) in the submission's language. A language the module does not ship
 * reads the English text of the base file, never the platform's default language.
 *
 * <p>Stateless; thread-safe.</p>
 */
public final class Texts {

    static final String BUNDLE = "resources.formidable-pdf";

    private Texts() {
    }

    /**
     * @param locale    the submission's locale; English when {@code null}
     * @param key       the key, for instance {@code pdf.reference}
     * @param arguments the values of the {@code {0}}, {@code {1}} placeholders, if any
     * @return the sentence, or the key when no bundle holds it
     */
    public static String in(Locale locale, String key, Object... arguments) {
        Locale language = locale == null ? Locale.ENGLISH : locale;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, language, Texts.class.getClassLoader(),
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            String text = bundle.containsKey(key) ? bundle.getString(key) : key;
            return arguments.length == 0 ? text : new MessageFormat(text, language).format(arguments);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
