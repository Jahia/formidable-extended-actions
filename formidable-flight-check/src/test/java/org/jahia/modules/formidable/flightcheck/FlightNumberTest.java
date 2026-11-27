package org.jahia.modules.formidable.flightcheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightNumberTest {

    @Test
    void normalisesSpacesAndCase() {
        assertEquals("CX251", FlightNumber.parse(" cx 251 ").orElseThrow().code());
        assertEquals("SL812", FlightNumber.parse("SL812").orElseThrow().code());
        assertEquals("U2123A", FlightNumber.parse("u2 123a").orElseThrow().code());
    }

    @Test
    void refusesWhatIsNotAFlightNumber() {
        assertTrue(FlightNumber.parse("hello").isEmpty());
        assertTrue(FlightNumber.parse("CX").isEmpty());
        assertTrue(FlightNumber.parse("CX12345").isEmpty());
        assertTrue(FlightNumber.parse("12345").isEmpty());
        assertTrue(FlightNumber.parse(null).isEmpty());
    }
}
