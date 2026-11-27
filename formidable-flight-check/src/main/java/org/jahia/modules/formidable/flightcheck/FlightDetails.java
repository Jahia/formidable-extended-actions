package org.jahia.modules.formidable.flightcheck;

import org.json.JSONObject;

/**
 * One flight on one day, as the lookup endpoint returns it to the page: only public flight data, never anything
 * of the visitor or of the service's account.
 *
 * @param status    whether the flight was found
 * @param flight    the normalised flight number
 * @param date      the local departure date asked for, {@code yyyy-MM-dd}
 * @param airline   the airline's name, may be empty
 * @param departure where and when it leaves, {@code null} unless found
 * @param arrival   where and when it lands, {@code null} unless found
 * @param aircraft  the aircraft model, may be empty
 */
public record FlightDetails(Status status, String flight, String date, String airline, Leg departure, Leg arrival,
                            String aircraft) {

    /** The outcome of a lookup. */
    public enum Status {
        /** The flight operates that day. */
        FOUND,
        /** The source answered and has no such flight that day. */
        NOT_FOUND,
        /** No source could answer (offline, no key, quota, error). */
        NO_ANSWER
    }

    /**
     * One end of the flight.
     *
     * @param iata           the airport's IATA code, upper case
     * @param name           the airport's name, may be empty
     * @param city           the city, may be empty
     * @param scheduledLocal the scheduled local time as the source writes it, may be empty
     * @param terminal       the terminal, may be empty
     * @param baggageBelt    the baggage belt (arrival only), may be empty
     */
    public record Leg(String iata, String name, String city, String scheduledLocal, String terminal, String baggageBelt) {

        JSONObject toJson() {
            return new JSONObject().put("iata", iata).put("name", name).put("city", city)
                    .put("scheduledLocal", scheduledLocal).put("terminal", terminal).put("baggageBelt", baggageBelt);
        }
    }

    /**
     * A result without flight data.
     *
     * @param status the outcome, {@link Status#NOT_FOUND} or {@link Status#NO_ANSWER}
     * @param flight the flight number asked for
     * @param date   the date asked for
     * @return the result
     */
    public static FlightDetails without(Status status, String flight, String date) {
        return new FlightDetails(status, flight, date, "", null, null, "");
    }

    /**
     * @return the JSON the lookup endpoint sends: {@code status} is {@code found}, {@code notFound} or
     * {@code unavailable}
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("status", switch (status) {
            case FOUND -> "found";
            case NOT_FOUND -> "notFound";
            case NO_ANSWER -> "unavailable";
        });
        json.put("flight", flight).put("date", date);
        if (status == Status.FOUND) {
            json.put("airline", airline).put("aircraft", aircraft)
                    .put("departure", departure.toJson()).put("arrival", arrival.toJson());
        }
        return json;
    }
}
