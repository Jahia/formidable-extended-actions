package org.jahia.modules.formidable.flightcheck;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A flight number as a visitor types it, normalised: the two-character IATA airline designator, one to four
 * digits, an optional operational suffix letter ({@code "cx 251"} and {@code "CX251"} are both {@code CX251}).
 *
 * <p>Immutable and thread-safe.</p>
 *
 * @param airline the airline designator, upper case
 * @param digits  the flight's digits, as typed (leading zeros kept)
 * @param suffix  the operational suffix letter, or an empty string
 */
public record FlightNumber(String airline, String digits, String suffix) {

    private static final Pattern SHAPE = Pattern.compile("([A-Z0-9]{2})(\\d{1,4})([A-Z]?)");

    /**
     * Reads a typed value.
     *
     * @param value the value the visitor typed, may be {@code null}
     * @return the flight number, or empty when the value is not shaped like one
     */
    public static Optional<FlightNumber> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String compact = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        Matcher matcher = SHAPE.matcher(compact);
        if (!matcher.matches() || matcher.group(1).chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return Optional.of(new FlightNumber(matcher.group(1), matcher.group(2), matcher.group(3)));
    }

    /**
     * @return the flight number as flight services read it, for example {@code CX251}
     */
    public String code() {
        return airline + digits + suffix;
    }
}
