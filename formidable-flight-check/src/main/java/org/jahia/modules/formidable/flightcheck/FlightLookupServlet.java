package org.jahia.modules.formidable.flightcheck;

import org.jahia.services.securityfilter.PermissionService;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code GET /modules/formidable-flight-check/lookup?flight=CX251&date=2026-10-20}: the flight of that number on that
 * local date, for the page script that fills a form ({@code flight-autofill.js}). Answers {@code 200} with
 * {@link FlightDetails#toJson()} ({@code found}, {@code notFound}, {@code unavailable}).
 *
 * <p>An open door to a quota-limited service, so it is gated as Formidable's own field-action pre-check is: the
 * {@value #API} security-filter scope, auto-applied to the site's own pages ({@code origin: hosted}, shipped in
 * {@code org.jahia.bundles.api.authorization-formidable-flight-check.yml}), then a limit of lookups per client address
 * and minute ({@code 403} and {@code 429}); a malformed flight number or date, or a date more than a year away, is a
 * {@code 400} without any call. Read-only: a GET with no CSRF token, nothing written. Answers carry
 * {@code Cache-Control: no-store}, and only public flight data.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=/formidable-flight-check/lookup"}, immediate = true)
public class FlightLookupServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    static final String API = "formidable-flight-lookup";
    private static final String ERROR = "error";
    private static final Logger log = LoggerFactory.getLogger(FlightLookupServlet.class);

    /** Set once by Declarative Services before the servlet is registered; the holders publish them to every request thread. */
    private final transient AtomicReference<FlightDataService> flights = new AtomicReference<>();
    private final transient AtomicReference<PermissionService> permissions = new AtomicReference<>();

    /**
     * @param service the module's flight data
     */
    @Reference
    public void setFlights(FlightDataService service) {
        this.flights.set(service);
    }

    /**
     * @param service Jahia's security filter, which knows the scopes of the request
     */
    @Reference
    public void setPermissions(PermissionService service) {
        this.permissions.set(service);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            answer(req, resp);
        } catch (IOException | JSONException e) {
            // the browser went away, or an answer could not be written: nothing more to send
            log.debug("A flight lookup could not be answered: {}", e.getMessage());
        }
    }

    private void answer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!permissions.get().hasPermission(Map.of("api", API))) {
            send(resp, HttpServletResponse.SC_FORBIDDEN, new JSONObject().put(ERROR, "not allowed"));
            return;
        }
        Optional<FlightNumber> flight = FlightNumber.parse(req.getParameter("flight"));
        Optional<LocalDate> date = dateOf(req.getParameter("date"));
        if (flight.isEmpty() || date.isEmpty()) {
            send(resp, HttpServletResponse.SC_BAD_REQUEST, new JSONObject().put(ERROR, "flight or date malformed"));
            return;
        }
        FlightDataService service = flights.get();
        if (!service.allowLookup(req.getRemoteAddr())) {
            send(resp, 429, new JSONObject().put(ERROR, "too many lookups"));
            return;
        }
        try {
            send(resp, HttpServletResponse.SC_OK, service.details(flight.get(), date.get()).toJson());
        } catch (RuntimeException e) {
            log.warn("Flight lookup failed for {} on {}", flight.get().code(), date.get(), e);
            send(resp, HttpServletResponse.SC_OK, FlightDetails.without(FlightDetails.Status.NO_ANSWER,
                    flight.get().code(), date.get().toString()).toJson());
        }
    }

    /**
     * Reads a {@code yyyy-MM-dd} date no more than a year before or after today.
     *
     * @param value the parameter, may be {@code null}
     * @return the date, or empty
     */
    static Optional<LocalDate> dateOf(String value) {
        if (value == null || value.length() != 10) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(value);
            LocalDate today = LocalDate.now();
            return date.isBefore(today.minusYears(1)) || date.isAfter(today.plusYears(1)) ? Optional.empty() : Optional.of(date);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static void send(HttpServletResponse resp, int status, JSONObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(body.toString());
    }
}
