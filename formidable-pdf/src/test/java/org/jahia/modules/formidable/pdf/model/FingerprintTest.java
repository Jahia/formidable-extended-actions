package org.jahia.modules.formidable.pdf.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FingerprintTest {

    private static final String FORM = "80329d0d-7ab3-43d1-9199-12b61d37b466";

    @Test
    void theOrderOfTheFieldsDoesNotChangeIt() {
        // Verifies what lets the organisation compute it again from a stored submission, whatever order it reads in.
        Map<String, List<String>> typed = new LinkedHashMap<>();
        typed.put("lastName", List.of("Tanaka"));
        typed.put("email", List.of("aiko@example.com"));
        Map<String, List<String>> stored = new LinkedHashMap<>();
        stored.put("email", List.of("aiko@example.com"));
        stored.put("lastName", List.of("Tanaka"));

        assertEquals(Fingerprint.of(FORM, typed), Fingerprint.of(FORM, stored));
        assertEquals(64, Fingerprint.of(FORM, typed).length());
    }

    @Test
    void aBlankValueCountsForNothingAndAnyOtherChangeCounts() {
        String base = Fingerprint.of(FORM, Map.of("email", List.of("aiko@example.com")));

        assertEquals(base, Fingerprint.of(FORM, Map.of("email", List.of("aiko@example.com"), "phone", List.of(" "))));
        assertNotEquals(base, Fingerprint.of(FORM, Map.of("email", List.of("aiko@example.org"))));
        assertNotEquals(base, Fingerprint.of("another-form", Map.of("email", List.of("aiko@example.com"))));
        assertNotEquals(base, Fingerprint.of(FORM, Map.of("email", List.of("aiko@example.com")),
                List.of(new Fingerprint.FileFact("photos", "crack.jpg", 45926))));
    }

    @Test
    void theReferenceIsItsFirstTwelveCharactersInGroupsOfFour() {
        String reference = Fingerprint.reference(Fingerprint.of(FORM, Map.of("email", List.of("aiko@example.com"))));

        assertTrue(reference.matches("[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}"), reference);
    }
}
