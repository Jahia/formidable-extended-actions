package org.jahia.modules.formidable.flightcheck;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Formidable field action {@code fmdbflt:flightCheckAction}: is the value a flight that exists?
 *
 * <ul>
 *   <li>A value not shaped like a flight number is refused without a call.</li>
 *   <li>A flight of the site's own airline is judged on its schedule, with no call.</li>
 *   <li>Any other flight is asked of AeroDataBox; when no source can answer, the check is unavailable and the
 *       contributor's <i>If the check cannot run</i> setting decides.</li>
 * </ul>
 *
 * <p>It judges the number alone: a field action receives one field's value, never the flight date beside it. The
 * autofill of the same node ({@code autofill} and the field pickers) is the page's business, see
 * {@link FlightAutofillFilter}. Thread-safe: it keeps no state of its own.</p>
 */
@Component(service = FieldAction.class)
public class FlightCheckFieldAction implements FieldAction {

    static final String NODE_TYPE = "fmdbflt:flightCheckAction";
    private static final Logger log = LoggerFactory.getLogger(FlightCheckFieldAction.class);

    private FlightDataService flights;

    /**
     * @param service the module's flight data
     */
    @Reference
    public void setFlights(FlightDataService service) {
        this.flights = service;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    @Override
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        if (request.value() == null || request.value().isBlank()) {
            return FieldActionResult.accept();
        }
        Optional<FlightNumber> parsed = FlightNumber.parse(request.value());
        if (parsed.isEmpty()) {
            return FieldActionResult.reject("not a flight number");
        }
        try {
            return switch (flights.exists(parsed.get())) {
                case FOUND -> FieldActionResult.accept();
                case NOT_FOUND -> FieldActionResult.reject("unknown flight");
                case NO_ANSWER -> FieldActionResult.unavailable("no source could judge it");
            };
        } catch (IOException e) {
            log.info("AeroDataBox could not be asked about flight {}: {}", parsed.get().code(), e.getClass().getSimpleName());
            return FieldActionResult.unavailable("AeroDataBox did not answer");
        }
    }
}
