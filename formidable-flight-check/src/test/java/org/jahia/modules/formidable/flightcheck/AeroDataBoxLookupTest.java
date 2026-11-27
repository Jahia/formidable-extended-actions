package org.jahia.modules.formidable.flightcheck;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeroDataBoxLookupTest {

    private static final String ONE_FLIGHT = "[{\"number\":\"CX 251\",\"status\":\"Arrived\","
            + "\"departure\":{\"airport\":{\"iata\":\"HKG\",\"name\":\"Hong Kong\",\"municipalityName\":\"Hong Kong\"},"
            + "\"scheduledTime\":{\"utc\":\"2026-10-20 15:00Z\",\"local\":\"2026-10-20 23:00+08:00\"},\"terminal\":\"1\"},"
            + "\"arrival\":{\"airport\":{\"iata\":\"lhr\",\"name\":\"London Heathrow\",\"municipalityName\":\"London\"},"
            + "\"scheduledTime\":{\"local\":\"2026-10-21 05:30+01:00\"},\"terminal\":\"3\",\"baggageBelt\":\"7\"},"
            + "\"airline\":{\"name\":\"Cathay Pacific\"},\"aircraft\":{\"model\":\"Airbus A350-1000\"}}]";

    @Test
    void readsTheServiceAnswers() {
        assertEquals(FlightLookup.Answer.FOUND, AeroDataBoxLookup.answerOf(200, ONE_FLIGHT));
        assertEquals(FlightLookup.Answer.NOT_FOUND, AeroDataBoxLookup.answerOf(200, "[]"));
        assertEquals(FlightLookup.Answer.NOT_FOUND, AeroDataBoxLookup.answerOf(204, ""));
        assertEquals(FlightLookup.Answer.NOT_FOUND, AeroDataBoxLookup.answerOf(404, null));
        assertEquals(FlightLookup.Answer.NO_ANSWER, AeroDataBoxLookup.answerOf(429, "{}"));
        assertEquals(FlightLookup.Answer.NO_ANSWER, AeroDataBoxLookup.answerOf(403, ""));
        assertEquals(FlightLookup.Answer.NO_ANSWER, AeroDataBoxLookup.answerOf(200, "<html>"));
    }

    /** The service also returns the flight of the day before that lands on the date asked, and lists it first. */
    private static final String TWO_DAYS = "[{\"departure\":{\"airport\":{\"iata\":\"HKG\"},\"scheduledTime\":{\"local\":\"2026-11-23 23:00+08:00\"}},"
            + "\"arrival\":{\"airport\":{\"iata\":\"LHR\"},\"scheduledTime\":{\"local\":\"2026-11-24 05:30+00:00\"}}},"
            + "{\"departure\":{\"airport\":{\"iata\":\"HKG\"},\"scheduledTime\":{\"local\":\"2026-11-24 23:00+08:00\"}},"
            + "\"arrival\":{\"airport\":{\"iata\":\"LHR\"},\"scheduledTime\":{\"local\":\"2026-11-25 05:30+00:00\"}}}]";

    @Test
    void picksTheFlightThatLeavesOnTheDateAsked() {
        assertEquals("2026-11-24 23:00+08:00", AeroDataBoxLookup.detailsOf("CX251", "2026-11-24", 200, TWO_DAYS).departure().scheduledLocal());
        // none leaves that day: the first one, as before
        assertEquals("2026-11-23 23:00+08:00", AeroDataBoxLookup.detailsOf("CX251", "2026-11-26", 200, TWO_DAYS).departure().scheduledLocal());
    }

    @Test
    void readsOneDayOfAFlight() {
        FlightDetails details = AeroDataBoxLookup.detailsOf("CX251", "2026-10-20", 200, ONE_FLIGHT);
        assertEquals(FlightDetails.Status.FOUND, details.status());
        assertEquals("HKG", details.departure().iata());
        assertEquals("LHR", details.arrival().iata());
        assertEquals("2026-10-20 23:00+08:00", details.departure().scheduledLocal());
        assertEquals("7", details.arrival().baggageBelt());
        assertEquals("Cathay Pacific", details.airline());
        assertEquals("found", details.toJson().getString("status"));
        assertEquals(FlightDetails.Status.NOT_FOUND, AeroDataBoxLookup.detailsOf("CX251", "2026-10-20", 204, "").status());
        assertEquals(FlightDetails.Status.NO_ANSWER, AeroDataBoxLookup.detailsOf("CX251", "2026-10-20", 200, "[{}]").status());
        assertEquals("unavailable", FlightDetails.without(FlightDetails.Status.NO_ANSWER, "CX251", "2026-10-20").toJson().getString("status"));
    }

    @Test
    void acceptsDatesWithinAYearOnly() {
        assertTrue(FlightLookupServlet.dateOf(LocalDate.now().minusMonths(2).toString()).isPresent());
        assertFalse(FlightLookupServlet.dateOf(LocalDate.now().minusYears(2).toString()).isPresent());
        assertFalse(FlightLookupServlet.dateOf("2026-13-40").isPresent());
        assertFalse(FlightLookupServlet.dateOf("../../x").isPresent());
        assertFalse(FlightLookupServlet.dateOf(null).isPresent());
    }
}
