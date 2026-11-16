package org.jahia.modules.formidable.pdf.model;

import java.util.List;

/**
 * What the document says, ready to be laid out: every text is already in the visitor's language. Immutable.
 *
 * @param title       the heading: the action's document title, or the form's own
 * @param subtitle    the line under it ("Copy of your submission")
 * @param meta        the facts about the submission: its reference, when it was received
 * @param sections    the answers, grouped as the form groups them (a step, a fieldset); a section without a title
 *                    holds the fields that sit directly in the form
 * @param note        the line the contributor adds at the end, or {@code null}
 * @param footer      the line at the foot of every page; {@code {page}} and {@code {pages}} are replaced
 * @param fingerprint the SHA-256 of the submitted values ({@link Fingerprint}), in hexadecimal
 */
public record ReceiptContent(String title, String subtitle, List<Entry> meta, List<Section> sections, String note, String footer,
                             String fingerprint) {

    public ReceiptContent {
        meta = List.copyOf(meta);
        sections = List.copyOf(sections);
    }

    /** A group of answers under a heading; the heading is empty for the fields outside any step or fieldset. */
    public record Section(String title, List<Entry> entries) {
        public Section {
            entries = List.copyOf(entries);
        }
    }

    /** One line of the document: a label and its values, each value on its own line. */
    public record Entry(String label, List<String> values) {
        public Entry {
            values = List.copyOf(values);
        }
    }
}
