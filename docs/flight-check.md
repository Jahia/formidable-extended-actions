# Flight number check (formidable-flight-check)

Flight data for Formidable forms. Two features on one node, the **Flight number check** a contributor adds to a
field (Field actions). The [Skylantern Airways forms demo](../demo/skylantern-forms/README.md) uses both on its
baggage claim form. Back to the [documentation index](README.md).

1. **The check** (a Formidable field action): is the flight number real? While the visitor fills the form (on
   leaving the field) and again at submission.
2. **Fill in the airports** (an option of the same check): once the visitor has given the flight date and number,
   the page looks the flight up and writes its departure and arrival airports into the fields the contributor
   picked, then says what it found under the flight number ("Cathay Pacific CX251: Hong Kong (HKG) to London
   Heathrow (LHR).", "We found no flight CX999 on 20 October 2026...").

| Flight number | Judged and filled from |
|---|---|
| Not shaped like one (`hello`, `CX12345`) | refused, no call |
| The site's own airline, if configured (`ownAirlineCode`) | its schedule, `ownFlights` in the configuration (with routes, `AB123:HKG-NRT`), every day, no call |
| Any other airline (`CX251`, `BA 27`) | [AeroDataBox](https://aerodatabox.com/) through RapidAPI |

The check judges the **number only**: a Formidable field action receives one field's value, never the date
beside it. The autofill, on the page, uses both (that day's flight). When AeroDataBox cannot answer (no key,
`offline=true`, quota spent, timeout, server error), the check is **unavailable** (the contributor's *If the check
cannot run* setting decides) and the page fills nothing.

## For the contributor

In the field's **Field actions**, add **Flight number check**, then:

| Setting | |
|---|---|
| Message, when to check, block or warn, if the check cannot run | Formidable's own settings of every field action |
| **Fill in the airports** | on: the page fills the airports |
| **Flight date field** | the form's date field (only date fields are listed) |
| **Departure airport field**, **Arrival airport field** | a text field (receives "Hong Kong (HKG)") or a drop-down list (gets the option whose value is the code, `hkg`, or whose label ends with `(HKG)`) |
| **... field, other airports** | for a list: the text field that receives an airport the list does not hold, while the list gets its "another airport" option. Show that field with a rule of the form on that option. Without it, an airport missing from the list is left to the visitor, and said |
| **Value of the "another airport" option** | `other` by default (a category named `other`) |

Each new flight or date replaces the airports in both fields, whatever they held: they belong to the flight just given. The visitor can still change them afterwards, until the next flight or date. A list without the airport keeps the visitor's own choice.
Page Builder shows the form without the autofill; preview and live have it.

## Install

Built for Formidable 0.6 (`formidable-engine=0.6` in `Jahia-Depends`), Jahia 8.2, Java 17.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install -pl formidable-flight-check
curl -u root:<password> -F 'script=[{"installOrUpgradeBundle":"formidable-flight-check-0.1.0-SNAPSHOT.jar","autoStart":true},{"enable":"formidable-flight-check","site":"<site key>"}]' \
  -F "file=@formidable-flight-check/target/formidable-flight-check-0.1.0-SNAPSHOT.jar" http://localhost:8080/modules/api/provisioning
```

Enable it on every site whose forms use it: the field's **Field actions** zone offers only the checks of modules
enabled on the site. The demo's seeder, `demo/skylantern-forms/seed-forms-demo.py`, adds the check, autofill on, to
the Baggage claim flight number when the module is deployed.

## Configuration

`karaf/etc/org.jahia.modules.formidable.flightcheck.cfg`, copied there at the first start and **never overwritten**
(a key added in a later build must be added to the deployed file by hand); a change applies without a restart.

| Key | Meaning |
|---|---|
| `url`, `apiHost` | AeroDataBox on RapidAPI (`https://aerodatabox.p.rapidapi.com`, `aerodatabox.p.rapidapi.com`) |
| `.credential` | Your RapidAPI key. Empty in the shipped file: subscribe to AeroDataBox's free Basic plan on RapidAPI and paste the key here yourself. Never logged |
| `ownAirlineCode`, `ownAirlineName`, `ownFlights` | The site's own airline, if it has one: its IATA code, its name, and its flights, comma-separated, each with its route when the airports should be filled: `AB123:HKG-NRT`. Empty in the shipped file: every flight goes to AeroDataBox |
| `offline` | `true`: never call AeroDataBox (a demo without network, a spent quota) |
| `lookupsPerMinute` | Lookups a visitor's address may ask per minute (default 20; 0 switches the lookup off) |
| `connectTimeoutSeconds`, `requestTimeoutSeconds` | 5 and 10 by default |

The free plan has a small monthly quota: the check's verdicts are kept 5 minutes by Formidable, the lookups 6 hours
by the module (500 at most).

## The lookup endpoint

`GET /modules/formidable-flight-check/lookup?flight=CX251&date=2026-10-20` answers `{"status": "found" | "notFound" |
"unavailable", ...}` with the airline, the aircraft model and, per leg, the airport code, name and city, scheduled
local time, terminal and (arrival) baggage belt. Gated as Formidable's own field-action endpoint: the API scope
`formidable-flight-lookup` (`org.jahia.bundles.api.authorization-formidable-flight-check.yml`), auto-applied to the
site's own pages, so another origin gets `403`; `429` past the per-address limit; `400` for a malformed flight number
or a date more than a year away, without any call. Read-only GET, no CSRF token, `Cache-Control: no-store`.

## Why it calls AeroDataBox itself

Formidable's `FieldActionGateway` sends one credential header. RapidAPI wants two (`X-RapidAPI-Key` and
`X-RapidAPI-Host`), so `AeroDataBoxLookup` uses Java's `HttpClient` with the same timeouts, HTTPS only (plain HTTP on
localhost), no redirects, and the key kept out of the logs. The AeroDataBox fields read (`departure.airport.iata`,
`scheduledTime.local`, `arrival.baggageBelt`...) are checked against a sample answer in the tests, not yet against a
live one, then confirmed on a real answer (CX251, 2026-11-09): airline, aircraft model, airport code, name and
city, local scheduled time, terminal, baggage belt. AeroDataBox matches the date on departure **or** arrival: asked
for 8 November, CX251 came back as the flight leaving Hong Kong at 23:00 on the 7th and landing on the 8th.

## Files

- `FlightDataService`: the configuration, the own schedule, the cache and the rate limit, shared by the two features
- `FlightCheckFieldAction`: the OSGi `FieldAction`, bound to `fmdbflt:flightCheckAction`
- `FlightLookupServlet`: the lookup endpoint
- `FlightAutofillFilter`: adds the JSON configuration and `javascript/flight-autofill.js` after each form whose check
  has autofill on
- `FormFieldsInitializer`: the pickers' list of the form's fields (`fmdbfltFormFields`, `date` or `place`)
- `AeroDataBoxLookup`, `FlightNumber`, `FlightDetails`: the call, a typed flight number, one flight on one day
- `src/test`: verdicts, schedule, cache, rate limit, answer parsing, dates, and ASCII resource bundles (the Jahia
  parent reads them as ISO-8859-1: every other character is a `\uXXXX` escape)
