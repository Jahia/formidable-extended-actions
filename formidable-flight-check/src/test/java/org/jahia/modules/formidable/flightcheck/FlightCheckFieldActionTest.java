package org.jahia.modules.formidable.flightcheck;

import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult.Verdict;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightCheckFieldActionTest {

    /** A lookup answering {@code find} with a fixed answer, or throwing. */
    static FlightLookup answering(FlightLookup.Answer answer, AtomicInteger calls) {
        return new FlightLookup() {
            @Override
            public Answer find(FlightNumber flight) throws IOException {
                calls.incrementAndGet();
                if (answer == null) {
                    throw new IOException("down");
                }
                return answer;
            }

            @Override
            public FlightDetails details(FlightNumber flight, LocalDate date) {
                calls.incrementAndGet();
                return FlightDetails.without(FlightDetails.Status.NOT_FOUND, flight.code(), date.toString());
            }
        };
    }

    static FlightDataService service(boolean offline, FlightLookup lookup) {
        FlightDataService service = new FlightDataService();
        service.use(new FlightDataService.Settings("SL", "Skylantern Airways",
                FlightDataService.ownFlightsOf("SL812:HKG-NRT, SL813"), offline, lookup, 20));
        return service;
    }

    private static Verdict judge(FlightDataService service, String value) {
        FlightCheckFieldAction action = new FlightCheckFieldAction();
        action.setFlights(service);
        return action.execute(null, new FieldActionRequest("form", "flightNumber", value, Locale.ENGLISH)).verdict();
    }

    @Test
    void ownAirlineIsJudgedOnItsScheduleWithoutACall() {
        AtomicInteger calls = new AtomicInteger();
        FlightDataService service = service(false, answering(FlightLookup.Answer.FOUND, calls));
        assertEquals(Verdict.ACCEPT, judge(service, "sl 812"));
        assertEquals(Verdict.ACCEPT, judge(service, "SL813"));
        assertEquals(Verdict.REJECT, judge(service, "SL999"));
        assertEquals(0, calls.get());
    }

    @Test
    void otherAirlinesFollowTheService() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Verdict.ACCEPT, judge(service(false, answering(FlightLookup.Answer.FOUND, calls)), "CX251"));
        assertEquals(Verdict.REJECT, judge(service(false, answering(FlightLookup.Answer.NOT_FOUND, calls)), "CX9999"));
        assertEquals(Verdict.UNAVAILABLE, judge(service(false, answering(FlightLookup.Answer.NO_ANSWER, calls)), "CX251"));
        assertEquals(Verdict.UNAVAILABLE, judge(service(false, answering(null, calls)), "CX251"));
    }

    @Test
    void offlineOrNoKeyIsUnavailableAndBlankOrMalformedNeedsNoService() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(Verdict.UNAVAILABLE, judge(service(true, answering(FlightLookup.Answer.FOUND, calls)), "CX251"));
        assertEquals(Verdict.UNAVAILABLE, judge(service(false, null), "CX251"));
        assertEquals(Verdict.ACCEPT, judge(service(false, null), " "));
        assertEquals(Verdict.REJECT, judge(service(false, null), "not a flight"));
        assertEquals(0, calls.get());
    }
}
