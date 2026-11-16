package org.jahia.modules.formidable.pdf.store;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a visitor's copy waits for its download: in memory, for a while, then gone. Nothing is written to the
 * repository, so a copy of personal answers never outlives the time the visitor has to fetch it.
 *
 * <p>A copy goes through two states. The action {@link #hold holds} it under the fingerprint of the submitted
 * values while the other actions run; once the submission is accepted, the response enricher {@link #release
 * releases} it and gets the random token of its download link. A copy nobody releases (a later action failed) is
 * dropped after {@link #PENDING}. A released copy is served by {@link #find} until the configured minutes pass.</p>
 *
 * <p>Per JVM: on a cluster, the download must reach the node that took the submission (a sticky session), and a
 * restart drops the copies. Thread-safe: every method is synchronised on the store.</p>
 */
@Component(service = ReceiptStore.class, configurationPid = ReceiptConfig.PID, immediate = true)
@Designate(ocd = ReceiptConfig.class)
public class ReceiptStore {

    /** How long a held copy waits for the end of its submission: the other actions of a form run within seconds. */
    static final Duration PENDING = Duration.ofMinutes(2);

    /** A copy: the document, the name of the file the browser saves, the label of its button. */
    public record Receipt(byte[] pdf, String fileName, String buttonLabel) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Receipt receipt && Arrays.equals(pdf, receipt.pdf)
                    && Objects.equals(fileName, receipt.fileName) && Objects.equals(buttonLabel, receipt.buttonLabel);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(fileName, buttonLabel) + Arrays.hashCode(pdf);
        }

        @Override
        public String toString() {
            return "Receipt[" + fileName + ", " + (pdf == null ? 0 : pdf.length) + " bytes]";
        }
    }

    private record Timed(Receipt receipt, Instant expires) {
    }

    private final Map<String, Deque<Timed>> held = new HashMap<>();
    private final Map<String, Timed> released = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private Duration download = Duration.ofMinutes(30);
    private int max = 500;

    public ReceiptStore() {
        this(Clock.systemUTC());
    }

    ReceiptStore(Clock clock) {
        this.clock = clock;
    }

    @Activate
    @Modified
    public synchronized void configure(ReceiptConfig config) {
        download = Duration.ofMinutes(Math.max(1, Math.min(1440, config.downloadMinutes())));
        max = Math.max(1, Math.min(10000, config.maxReceipts()));
    }

    /**
     * @param key     the fingerprint of the submitted values
     * @param receipt the copy
     */
    public synchronized void hold(String key, Receipt receipt) {
        purge();
        held.computeIfAbsent(key, k -> new ArrayDeque<>()).add(new Timed(receipt, clock.instant().plus(PENDING)));
    }

    /**
     * @param key the fingerprint of the accepted values
     * @return the token of the download link and the copy, when a copy was held for these values
     */
    public synchronized Optional<Map.Entry<String, Receipt>> release(String key) {
        purge();
        Deque<Timed> queue = held.get(key);
        if (queue == null) {
            return Optional.empty();
        }
        Timed first = queue.poll();
        if (queue.isEmpty()) {
            held.remove(key);
        }
        if (first == null) {
            return Optional.empty();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        released.put(token, new Timed(first.receipt(), clock.instant().plus(download)));
        while (released.size() > max) {
            Iterator<String> oldest = released.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return Optional.of(Map.entry(token, first.receipt()));
    }

    /**
     * @param token the token of a download link
     * @return the copy, while its time has not passed
     */
    public synchronized Optional<Receipt> find(String token) {
        purge();
        return token == null ? Optional.empty() : Optional.ofNullable(released.get(token)).map(Timed::receipt);
    }

    private void purge() {
        Instant now = clock.instant();
        released.values().removeIf(timed -> !timed.expires().isAfter(now));
        held.values().forEach(queue -> queue.removeIf(timed -> !timed.expires().isAfter(now)));
        held.values().removeIf(Deque::isEmpty);
    }
}
