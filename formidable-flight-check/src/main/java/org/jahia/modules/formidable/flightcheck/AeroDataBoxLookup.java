package org.jahia.modules.formidable.flightcheck;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

/**
 * {@link FlightLookup} on AeroDataBox through RapidAPI: {@code GET <url>/flights/number/<flight>} (the flights of
 * that number nearest to now) and {@code GET <url>/flights/number/<flight>/<yyyy-MM-dd>?dateLocalRole=Departure} (the
 * ones that leave that day, local time: by default the service also returns a flight that lands that day).
 *
 * <p>Calls Java's {@link HttpClient} itself rather than Formidable's {@code FieldActionGateway}: RapidAPI wants two
 * headers ({@code X-RapidAPI-Key} and {@code X-RapidAPI-Host}) and the gateway sends one. HTTPS only (plain HTTP on
 * localhost), no redirects, the key never logged. Thread-safe: the client is shared, the settings are final.</p>
 */
public final class AeroDataBoxLookup implements FlightLookup {

    private static final Logger log = LoggerFactory.getLogger(AeroDataBoxLookup.class);
    private static final String QUERY = "?withAircraftImage=false&withLocation=false";
    /** A boarding pass dates a flight by its departure: without it, the flight of the day before that lands that day comes first. */
    private static final String DEPARTING = "&dateLocalRole=Departure";

    private final URI base;
    private final String apiHost;
    private final String key;
    private final Duration requestTimeout;
    private final HttpClient client;

    /**
     * @param base           the service's base URL, without a trailing slash
     * @param apiHost        the value of {@code X-RapidAPI-Host}
     * @param key            the RapidAPI key, sent in {@code X-RapidAPI-Key}
     * @param connectTimeout time to open the connection
     * @param requestTimeout time for the whole call
     */
    public AeroDataBoxLookup(URI base, String apiHost, String key, Duration connectTimeout, Duration requestTimeout) {
        this.base = base;
        this.apiHost = apiHost;
        this.key = key;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Answer find(FlightNumber flight) throws IOException {
        HttpResponse<String> response = get("/flights/number/" + encode(flight.code()), "");
        Answer answer = answerOf(response.statusCode(), response.body());
        if (answer == Answer.NO_ANSWER && log.isInfoEnabled()) {
            log.info("AeroDataBox did not judge flight {}: HTTP {}", flight.code(), response.statusCode());
        }
        return answer;
    }

    @Override
    public FlightDetails details(FlightNumber flight, LocalDate date) throws IOException {
        HttpResponse<String> response = get("/flights/number/" + encode(flight.code()) + "/" + date, DEPARTING);
        FlightDetails details = detailsOf(flight.code(), date.toString(), response.statusCode(), response.body());
        if (details.status() == FlightDetails.Status.NO_ANSWER && log.isInfoEnabled()) {
            log.info("AeroDataBox gave no details of flight {} on {}: HTTP {}", flight.code(), date, response.statusCode());
        }
        return details;
    }

    private HttpResponse<String> get(String path, String extraQuery) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path + QUERY + extraQuery)).timeout(requestTimeout).GET()
                .header("Accept", "application/json")
                .header("X-RapidAPI-Key", key)
                .header("X-RapidAPI-Host", apiHost)
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Reads an AeroDataBox answer: a non-empty array of flights is {@link Answer#FOUND}; no content, an empty array,
     * 400 (a number the service cannot read) or 404 is {@link Answer#NOT_FOUND}; anything else (401, 403, 429, 5xx)
     * is {@link Answer#NO_ANSWER}.
     *
     * @param status the HTTP status
     * @param body   the body, may be {@code null}
     * @return the answer
     */
    static Answer answerOf(int status, String body) {
        if (status == 204 || status == 400 || status == 404) {
            return Answer.NOT_FOUND;
        }
        if (status != 200) {
            return Answer.NO_ANSWER;
        }
        if (body == null || body.isBlank()) {
            return Answer.NOT_FOUND;
        }
        try {
            return new JSONArray(body).isEmpty() ? Answer.NOT_FOUND : Answer.FOUND;
        } catch (JSONException e) {
            return Answer.NO_ANSWER;
        }
    }

    /**
     * Reads the flights of one day: the first one of the array that leaves on that date (local time), else the first
     * one, with the fields the page can use. Unknown or missing fields read as empty strings.
     *
     * @param flight the flight number asked for
     * @param date   the date asked for
     * @param status the HTTP status
     * @param body   the body, may be {@code null}
     * @return the details
     */
    static FlightDetails detailsOf(String flight, String date, int status, String body) {
        Answer answer = answerOf(status, body);
        if (answer != Answer.FOUND) {
            return FlightDetails.without(answer == Answer.NOT_FOUND ? FlightDetails.Status.NOT_FOUND : FlightDetails.Status.NO_ANSWER,
                    flight, date);
        }
        JSONObject first = departingOn(new JSONArray(body), date);
        if (first == null) {
            return FlightDetails.without(FlightDetails.Status.NO_ANSWER, flight, date);
        }
        FlightDetails.Leg departure = leg(first.optJSONObject("departure"));
        FlightDetails.Leg arrival = leg(first.optJSONObject("arrival"));
        if (departure.iata().isEmpty() && arrival.iata().isEmpty()) {
            return FlightDetails.without(FlightDetails.Status.NO_ANSWER, flight, date);
        }
        JSONObject airline = first.optJSONObject("airline");
        JSONObject aircraft = first.optJSONObject("aircraft");
        return new FlightDetails(FlightDetails.Status.FOUND, flight, date, airline == null ? "" : airline.optString("name", ""),
                departure, arrival, aircraft == null ? "" : aircraft.optString("model", ""));
    }

    /** The first flight whose scheduled local departure is on {@code date}; the first flight when none is. */
    private static JSONObject departingOn(JSONArray flights, String date) {
        for (int i = 0; i < flights.length(); i++) {
            JSONObject candidate = flights.optJSONObject(i);
            if (candidate != null && leg(candidate.optJSONObject("departure")).scheduledLocal().startsWith(date)) {
                return candidate;
            }
        }
        return flights.optJSONObject(0);
    }

    private static FlightDetails.Leg leg(JSONObject leg) {
        if (leg == null) {
            return new FlightDetails.Leg("", "", "", "", "", "");
        }
        JSONObject airport = leg.optJSONObject("airport");
        JSONObject scheduled = leg.optJSONObject("scheduledTime");
        String local = scheduled != null ? scheduled.optString("local", "") : leg.optString("scheduledTimeLocal", "");
        return new FlightDetails.Leg(
                airport == null ? "" : airport.optString("iata", "").toUpperCase(Locale.ROOT),
                airport == null ? "" : airport.optString("name", ""),
                airport == null ? "" : airport.optString("municipalityName", ""),
                local, leg.optString("terminal", ""), leg.optString("baggageBelt", ""));
    }
}
