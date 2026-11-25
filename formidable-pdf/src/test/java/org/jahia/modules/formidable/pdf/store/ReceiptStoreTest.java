package org.jahia.modules.formidable.pdf.store;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptStoreTest {

    /** A clock the test moves by hand. */
    private static final class Hand extends Clock {
        private Instant now = Instant.parse("2026-11-16T10:00:00Z");

        void forward(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Hand clock = new Hand();
    private final ReceiptStore store = new ReceiptStore(clock);

    private static ReceiptStore.Receipt receipt(String content) {
        return new ReceiptStore.Receipt(content.getBytes(), content + ".pdf", "Download");
    }

    private static ReceiptConfig config(int minutes, int max) {
        return new ReceiptConfig() {
            @Override
            public Class<ReceiptConfig> annotationType() {
                return ReceiptConfig.class;
            }

            @Override
            public int downloadMinutes() {
                return minutes;
            }

            @Override
            public int maxReceipts() {
                return max;
            }
        };
    }

    @Test
    void aHeldCopyIsServedOnlyOnceReleasedAndOnlyByItsToken() {
        // Verifies the two states: a copy the submission has not finished for has no link yet.
        store.hold("key", receipt("a"));
        assertTrue(store.find("key").isEmpty(), "the fingerprint is not a token");

        Optional<Map.Entry<String, ReceiptStore.Receipt>> released = store.release("key");

        assertTrue(released.isPresent());
        assertTrue(released.get().getKey().length() >= 43, "256 random bits, base64url");
        assertArrayEquals("a".getBytes(), store.find(released.get().getKey()).orElseThrow().pdf());
        assertTrue(store.release("key").isEmpty(), "one link per held copy");
        assertTrue(store.find(null).isEmpty());
        assertTrue(store.find("unknown").isEmpty());
    }

    @Test
    void twoSubmissionsWithTheSameAnswersEachGetTheirOwnLink() {
        // Verifies the queue under one fingerprint: the second visitor is not left without a copy.
        store.hold("key", receipt("first"));
        store.hold("key", receipt("second"));

        String first = store.release("key").orElseThrow().getKey();
        String second = store.release("key").orElseThrow().getKey();

        assertNotEquals(first, second);
        assertEquals("first.pdf", store.find(first).orElseThrow().fileName());
        assertEquals("second.pdf", store.find(second).orElseThrow().fileName());
    }

    @Test
    void aCopyNobodyReleasesIsDropped() {
        // Verifies the fate of a submission a later action refused: its copy never gets a link.
        store.hold("key", receipt("a"));
        clock.forward(ReceiptStore.PENDING.plusSeconds(1));

        assertTrue(store.release("key").isEmpty());
    }

    @Test
    void aReleasedCopyExpiresAfterTheConfiguredMinutes() {
        store.configure(config(10, 500));
        store.hold("key", receipt("a"));
        String token = store.release("key").orElseThrow().getKey();

        clock.forward(Duration.ofMinutes(9));
        assertTrue(store.find(token).isPresent());
        clock.forward(Duration.ofMinutes(1));
        assertFalse(store.find(token).isPresent(), "gone at the minute it expires");
    }

    @Test
    void pastTheMaximumTheOldestCopyIsDropped() {
        store.configure(config(30, 2));
        String[] tokens = new String[3];
        for (int i = 0; i < 3; i++) {
            store.hold("key" + i, receipt("copy" + i));
            tokens[i] = store.release("key" + i).orElseThrow().getKey();
        }

        assertTrue(store.find(tokens[0]).isEmpty());
        assertTrue(store.find(tokens[1]).isPresent());
        assertTrue(store.find(tokens[2]).isPresent());
    }

    @Test
    void outOfRangeSettingsAreBroughtBackInRange() {
        // Verifies the clamps: zero minutes would make every link dead on arrival.
        store.configure(config(0, 0));
        store.hold("key", receipt("a"));
        String token = store.release("key").orElseThrow().getKey();

        clock.forward(Duration.ofSeconds(59));
        assertTrue(store.find(token).isPresent(), "at least one minute, at least one copy");
    }
}
