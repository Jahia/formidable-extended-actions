package org.jahia.modules.formidable.flightcheck;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The module's resource bundles are ASCII, every other character a {@code \\uXXXX} escape: the Jahia parent pom runs
 * native2ascii reading ISO-8859-1, so a UTF-8 "é" reaches jContent and the page as "Ã©" while the build passes.
 */
class ResourceBundlesTest {

    @Test
    void resourceBundlesAreAscii() throws IOException {
        List<Path> bundles;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/resources"))) {
            bundles = files.filter(p -> p.toString().endsWith(".properties")).toList();
        }
        assertTrue(bundles.size() >= 2, "the EN and FR bundles are there");
        for (Path bundle : bundles) {
            List<String> lines = Files.readAllLines(bundle, java.nio.charset.StandardCharsets.ISO_8859_1);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).chars().allMatch(c -> c < 128)) {
                    fail(bundle.getFileName() + ":" + (i + 1) + " is not ASCII: write \\uXXXX escapes");
                }
            }
        }
    }
}
