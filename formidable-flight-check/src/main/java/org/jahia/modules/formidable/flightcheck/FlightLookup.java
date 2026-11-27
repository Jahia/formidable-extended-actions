package org.jahia.modules.formidable.flightcheck;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Asks a flight data service about a flight number.
 *
 * <p>Implementations are called from the field action and from the lookup endpoint, possibly from several threads
 * at once: they must be thread-safe.</p>
 */
public interface FlightLookup {

    /** What the service said about a flight number. */
    enum Answer {
        /** The service knows flights under this number. */
        FOUND,
        /** The service answered and knows no flight under this number. */
        NOT_FOUND,
        /** The service did not judge the number: refused key, quota reached, server error. */
        NO_ANSWER
    }

    /**
     * Whether flights exist under this number, near today.
     *
     * @param flight the normalised flight number
     * @return the service's answer
     * @throws IOException when the service cannot be reached or does not answer in time
     */
    Answer find(FlightNumber flight) throws IOException;

    /**
     * The flight of that number departing on that local date.
     *
     * @param flight the normalised flight number
     * @param date   the local departure date
     * @return the flight, or a {@link FlightDetails.Status#NOT_FOUND} / {@link FlightDetails.Status#NO_ANSWER} result
     * @throws IOException when the service cannot be reached or does not answer in time
     */
    FlightDetails details(FlightNumber flight, LocalDate date) throws IOException;
}
