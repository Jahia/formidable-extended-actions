package org.jahia.modules.formidable.pdf.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The fingerprint of a submission: the SHA-256 of the form and of the values it accepted, written in a fixed order.
 * Two submissions with the same answers to the same form have the same fingerprint, so the organisation can compute
 * it again from the stored submission and compare it with the one printed on a visitor's copy.
 *
 * <p>The text hashed, in UTF-8, one line per item, each ended by a line feed:</p>
 * <pre>
 * form=&lt;UUID of the form&gt;
 * &lt;field name&gt;=&lt;value&gt;            one line per value, fields by name, values in the order submitted
 * file:&lt;field name&gt;/&lt;file name&gt;:&lt;size in bytes&gt;    files by field name then file name
 * </pre>
 *
 * <p>Stateless; thread-safe.</p>
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    /** A file as the fingerprint reads it: where it was attached, its name and its size. */
    public record FileFact(String fieldName, String fileName, long size) {
    }

    /**
     * @param formUuid   the form's identifier
     * @param parameters the accepted values, by field name; blank values are left out
     * @param files      the attached files, or an empty list
     * @return 64 hexadecimal characters
     */
    public static String of(String formUuid, Map<String, List<String>> parameters, List<FileFact> files) {
        StringBuilder text = new StringBuilder("form=").append(formUuid).append('\n');
        new TreeMap<>(parameters).forEach((name, values) -> values.stream().filter(value -> value != null && !value.isBlank())
                .forEach(value -> text.append(name).append('=').append(value).append('\n')));
        List<String> lines = new ArrayList<>();
        files.forEach(file -> lines.add("file:" + file.fieldName() + "/" + file.fileName() + ":" + file.size()));
        lines.stream().sorted().forEach(line -> text.append(line).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }

    /** The fingerprint of the values alone: what the action and the response enricher both know of a submission. */
    public static String of(String formUuid, Map<String, List<String>> parameters) {
        return of(formUuid, parameters, List.of());
    }

    /** The first twelve characters, grouped by four: short enough to read out, as {@code 3F9A-12BC-77D0}. */
    public static String reference(String fingerprint) {
        String start = fingerprint.substring(0, Math.min(12, fingerprint.length())).toUpperCase(java.util.Locale.ROOT);
        return String.join("-", start.split("(?<=\\G.{4})"));
    }
}
