package org.jahia.modules.formidable.flightcheck;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightDataServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    void readsOwnFlightsWithAndWithoutRoutes() {
        var flights = FlightDataService.ownFlightsOf(" sl812:hkg-nrt ,SL813, nonsense, SL101:HKG");
        assertEquals("HKG", flights.get("SL812").from());
        assertEquals("NRT", flights.get("SL812").to());
        assertEquals("", flights.get("SL813").from());
        assertEquals("", flights.get("SL101").to());
        assertEquals(3, flights.size());
    }

    @Test
    void ownFlightDetailsComeFromTheSchedule() {
        FlightDataService service = FlightCheckFieldActionTest.service(false, null);
        FlightDetails found = service.details(FlightNumber.parse("SL812").orElseThrow(), DAY);
        assertEquals(FlightDetails.Status.FOUND, found.status());
        assertEquals("HKG", found.departure().iata());
        assertEquals("NRT", found.arrival().iata());
        assertEquals("Skylantern Airways", found.airline());
        assertEquals(FlightDetails.Status.NOT_FOUND, service.details(FlightNumber.parse("SL999").orElseThrow(), DAY).status());
    }

    @Test
    void otherFlightsAreAskedOnceThenCachedAndUnavailableOffline() {
        AtomicInteger calls = new AtomicInteger();
        FlightDataService service = FlightCheckFieldActionTest.service(false,
                FlightCheckFieldActionTest.answering(FlightLookup.Answer.FOUND, calls));
        FlightNumber cx = FlightNumber.parse("CX251").orElseThrow();
        service.details(cx, DAY);
        service.details(cx, DAY);
        assertEquals(1, calls.get());
        FlightDataService offline = FlightCheckFieldActionTest.service(true, null);
        assertEquals(FlightDetails.Status.NO_ANSWER, offline.details(cx, DAY).status());
    }

    @Test
    void limitsLookupsPerClient() {
        FlightDataService service = FlightCheckFieldActionTest.service(false, null);
        for (int i = 0; i < 20; i++) {
            assertTrue(service.allowLookup("10.0.0.1"));
        }
        assertFalse(service.allowLookup("10.0.0.1"));
        assertTrue(service.allowLookup("10.0.0.2"));
    }

    @Test
    void dropsTheSlashesThatEndTheUrl() {
        assertEquals("https://aerodatabox.p.rapidapi.com", FlightDataService.withoutTrailingSlashes("https://aerodatabox.p.rapidapi.com//"));
        assertEquals("https://h", FlightDataService.withoutTrailingSlashes("https://h"));
        assertEquals("", FlightDataService.withoutTrailingSlashes("///"));
    }
}
