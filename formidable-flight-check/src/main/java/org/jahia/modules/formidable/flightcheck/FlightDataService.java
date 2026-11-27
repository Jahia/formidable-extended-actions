package org.jahia.modules.formidable.flightcheck;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The flight data of the module, shared by the field action and the lookup endpoint: the site's own airline judged on
 * its schedule (no call, the demo airline is fictional), every other airline asked of AeroDataBox, answers kept for a
 * while so a free quota lasts.
 *
 * <p>Configuration {@code org.jahia.modules.formidable.flightcheck.cfg}, read at activation and on every change.
 * Thread-safe: the settings are swapped as one immutable object, the cache is concurrent.</p>
 */
@Component(service = FlightDataService.class, configurationPid = FlightDataService.PID, immediate = true)
@Designate(ocd = FlightDataService.Config.class)
public class FlightDataService {

    static final String PID = "org.jahia.modules.formidable.flightcheck";
    private static final Logger log = LoggerFactory.getLogger(FlightDataService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int CACHE_MAX = 500;

    /** The settings, from the configuration file. */
    @ObjectClassDefinition(name = "Formidable - Field actions - Flight number check")
    public @interface Config {
        @AttributeDefinition(description = "AeroDataBox base URL, HTTPS, without a trailing slash")
        String url() default "";

        @AttributeDefinition(description = "Value of the X-RapidAPI-Host header")
        String apiHost() default "";

        /** The file's {@code .credential}: a private property, never published with the service. */
        @AttributeDefinition(type = AttributeType.PASSWORD, description = "RapidAPI key")
        String _credential() default "";

        @AttributeDefinition(description = "IATA code of the site's own airline, judged on ownFlights")
        String ownAirlineCode() default "";

        @AttributeDefinition(description = "Name of the own airline, shown with its flights")
        String ownAirlineName() default "";

        @AttributeDefinition(description = "Own flights, comma-separated: AB123 or AB123:HKG-NRT (with its route)")
        String ownFlights() default "";

        @AttributeDefinition(description = "true: never call AeroDataBox")
        boolean offline() default false;

        @AttributeDefinition(description = "Lookups accepted per client address and minute on the endpoint")
        int lookupsPerMinute() default 20;

        int connectTimeoutSeconds() default 5;

        int requestTimeoutSeconds() default 10;
    }

    /** A flight of the own airline, with its route when the configuration gives one. */
    record OwnFlight(String code, String from, String to) {
    }

    /** One consistent view of the configuration. */
    record Settings(String ownAirline, String ownAirlineName, Map<String, OwnFlight> ownFlights, boolean offline,
                    FlightLookup lookup, int lookupsPerMinute) {
    }

    private record Cached(FlightDetails details, long at) {
    }

    private final AtomicReference<Settings> settings = new AtomicReference<>(new Settings("", "", Map.of(), true, null, 20));
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final RateLimiter rateLimiter = new RateLimiter();

    /**
     * Reads the configuration.
     *
     * @param config the configuration of {@link #PID}
     */
    @Activate
    @Modified
    public void activate(Config config) {
        Map<String, OwnFlight> flights = ownFlightsOf(config.ownFlights());
        FlightLookup lookup = config.offline() ? null : lookupOf(config);
        Settings configured = new Settings(config.ownAirlineCode().trim().toUpperCase(Locale.ROOT), config.ownAirlineName().trim(),
                flights, config.offline(), lookup, Math.max(0, config.lookupsPerMinute()));
        settings.set(configured);
        cache.clear();
        String others;
        if (config.offline()) {
            others = "offline";
        } else if (lookup == null) {
            others = "AeroDataBox not configured";
        } else {
            others = "AeroDataBox on " + config.url();
        }
        log.info("Flight data configured: own airline '{}' with {} flight(s), {}", configured.ownAirline(), flights.size(), others);
    }

    /** For the tests: settings without a configuration file. */
    void use(Settings newSettings) {
        settings.set(newSettings);
        cache.clear();
    }

    static Map<String, OwnFlight> ownFlightsOf(String list) {
        Map<String, OwnFlight> flights = new LinkedHashMap<>();
        for (String entry : list.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            Optional<FlightNumber> number = FlightNumber.parse(parts[0]);
            if (number.isEmpty()) {
                continue;
            }
            String from = "";
            String to = "";
            String[] route = parts.length == 2 ? parts[1].split("-", -1) : new String[0];
            if (route.length == 2 && isAirportCode(route[0].trim()) && isAirportCode(route[1].trim())) {
                from = route[0].trim().toUpperCase(Locale.ROOT);
                to = route[1].trim().toUpperCase(Locale.ROOT);
            }
            flights.put(number.get().code(), new OwnFlight(number.get().code(), from, to));
        }
        return Collections.unmodifiableMap(flights);
    }

    /** The URL without the slashes that end it. */
    static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    /** Three letters, an IATA airport code in either case. */
    private static boolean isAirportCode(String value) {
        return value.length() == 3 && value.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
    }

    private static FlightLookup lookupOf(Config config) {
        if (config._credential().isBlank() || config.apiHost().isBlank()) {
            return null;
        }
        try {
            URI base = new URI(withoutTrailingSlashes(config.url().trim()));
            boolean local = "localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost());
            if (!"https".equals(base.getScheme()) && !("http".equals(base.getScheme()) && local)) {
                log.warn("Flight data: the AeroDataBox URL must use HTTPS; other airlines are not checked");
                return null;
            }
            return new AeroDataBoxLookup(base, config.apiHost().trim(), config._credential().trim(),
                    Duration.ofSeconds(Math.max(1, config.connectTimeoutSeconds())), Duration.ofSeconds(Math.max(1, config.requestTimeoutSeconds())));
        } catch (URISyntaxException e) {
            log.warn("Flight data: the AeroDataBox URL is malformed at character {}", e.getIndex());
            return null;
        }
    }

    /**
     * Whether flights exist under this number.
     *
     * @param flight the normalised flight number
     * @return the answer; {@link FlightLookup.Answer#NO_ANSWER} when no source can judge it
     * @throws IOException when AeroDataBox cannot be reached
     */
    public FlightLookup.Answer exists(FlightNumber flight) throws IOException {
        Settings current = settings.get();
        if (isOwn(current, flight)) {
            return current.ownFlights().containsKey(flight.code()) ? FlightLookup.Answer.FOUND : FlightLookup.Answer.NOT_FOUND;
        }
        if (current.offline() || current.lookup() == null) {
            return FlightLookup.Answer.NO_ANSWER;
        }
        return current.lookup().find(flight);
    }

    /**
     * The flight of that number on that day: from the own schedule (every day, with its route) or from AeroDataBox,
     * kept {@value #CACHE_MAX} answers for six hours.
     *
     * @param flight the normalised flight number
     * @param date   the local departure date
     * @return the details; {@link FlightDetails.Status#NO_ANSWER} when no source can answer or AeroDataBox fails
     */
    public FlightDetails details(FlightNumber flight, LocalDate date) {
        Settings current = settings.get();
        if (isOwn(current, flight)) {
            OwnFlight own = current.ownFlights().get(flight.code());
            if (own == null) {
                return FlightDetails.without(FlightDetails.Status.NOT_FOUND, flight.code(), date.toString());
            }
            return new FlightDetails(FlightDetails.Status.FOUND, flight.code(), date.toString(), current.ownAirlineName(),
                    new FlightDetails.Leg(own.from(), "", "", "", "", ""), new FlightDetails.Leg(own.to(), "", "", "", "", ""), "");
        }
        if (current.offline() || current.lookup() == null) {
            return FlightDetails.without(FlightDetails.Status.NO_ANSWER, flight.code(), date.toString());
        }
        String key = flight.code() + "|" + date;
        Cached cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at() < CACHE_TTL.toMillis()) {
            return cached.details();
        }
        FlightDetails details;
        try {
            details = current.lookup().details(flight, date);
        } catch (IOException e) {
            log.info("AeroDataBox could not be asked about flight {} on {}: {}", flight.code(), date, e.getClass().getSimpleName());
            return FlightDetails.without(FlightDetails.Status.NO_ANSWER, flight.code(), date.toString());
        }
        if (details.status() != FlightDetails.Status.NO_ANSWER) {
            if (cache.size() >= CACHE_MAX) {
                cache.clear();
            }
            cache.put(key, new Cached(details, now));
        }
        return details;
    }

    /**
     * Counts one lookup of a client against {@code lookupsPerMinute}.
     *
     * @param client the client's address
     * @return {@code false} when the client is over the limit (or the endpoint is switched off with 0)
     */
    public boolean allowLookup(String client) {
        int limit = settings.get().lookupsPerMinute();
        return limit > 0 && rateLimiter.allow(client, limit);
    }

    private static boolean isOwn(Settings current, FlightNumber flight) {
        return !current.ownAirline().isEmpty() && current.ownAirline().equals(flight.airline());
    }

    /** Calls per client and fixed one-minute window; forgets everything past 10,000 clients. */
    static final class RateLimiter {
        private record Window(long minute, int count) {
        }

        private final Map<String, Window> windows = new ConcurrentHashMap<>();

        boolean allow(String client, int limit) {
            long minute = System.currentTimeMillis() / 60_000;
            if (windows.size() > 10_000) {
                windows.clear();
            }
            Window window = windows.compute(client == null ? "" : client,
                    (k, w) -> w == null || w.minute() != minute ? new Window(minute, 1) : new Window(minute, w.count() + 1));
            return window.count() <= limit;
        }
    }
}
