/*
 * formidable-flight-check: fills a Formidable form's airport fields from its flight date and number.
 *
 * FlightAutofillFilter writes, after each form whose flight check has "Fill in the airports" on, a
 * <script type="application/json" data-fmdbflt-autofill="<form id>"> with the field names, the endpoint and the
 * labels in the page's language. When the visitor changes the date or the flight number and both are filled, this
 * script asks /modules/formidable-flight-check/lookup and writes the airports into the picked fields, the way the
 * jExperience prefill writes a field: the value, then bubbling "input" and "change" events, so Formidable's rules and
 * validation see it as typed. Each new flight or date writes its airports over the fields' values; a list that has
 * no option for the airport gets its "another airport" option and the airport goes into the matching other field
 * (shown by a rule of the form), or, with no such field, keeps the visitor's own choice. The result is said in a
 * polite live region under the flight number.
 */
(function () {
  "use strict";
  if (window.__fmdbfltAutofill) {
    return;
  }
  window.__fmdbfltAutofill = true;

  var FLIGHT = /^[A-Za-z0-9]{2}\s?\d{1,4}[A-Za-z]?$/;
  var DATE = /^\d{4}-\d{2}-\d{2}$/;

  function format(template, values) {
    return (template || "").replace(/\{(\w+)\}/g, function (all, key) {
      return values[key] == null ? "" : String(values[key]);
    });
  }

  // 2026-10-20 as the page's language writes a date ("20 October 2026", "20 octobre 2026").
  function dayOf(iso) {
    try {
      var parts = iso.split("-");
      return new Date(Date.UTC(+parts[0], +parts[1] - 1, +parts[2])).toLocaleDateString(document.documentElement.lang || undefined,
        {day: "numeric", month: "long", year: "numeric", timeZone: "UTC"});
    } catch (e) {
      return iso;
    }
  }

  function control(form, name) {
    if (!name) {
      return null;
    }
    var element = form.elements.namedItem(name);
    return element && (element.tagName === "INPUT" || element.tagName === "SELECT") ? element : null;
  }

  // A label such as "Hong Kong (HKG)" from any airport list of the form, for a code the service gave without a name.
  function labelOf(form, iata) {
    var options = form.querySelectorAll("select option");
    for (var i = 0; i < options.length; i++) {
      if (options[i].textContent.trim().toUpperCase().endsWith("(" + iata + ")")) {
        return options[i].textContent.trim();
      }
    }
    return iata;
  }

  function nameOf(form, leg) {
    if (leg.name) {
      return leg.name + " (" + leg.iata + ")";
    }
    return labelOf(form, leg.iata);
  }

  function write(element, value) {
    var proto = element.tagName === "SELECT" ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, "value").set.call(element, value);
    element.dataset.fmdbfltFilled = "true";
    element.dispatchEvent(new Event("input", {bubbles: true}));
    element.dispatchEvent(new Event("change", {bubbles: true}));
  }

  function hasOption(select, value) {
    for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value === value) {
        return true;
      }
    }
    return false;
  }

  // Empties a field only when this script had written it.
  function clear(element) {
    if (element && element.value && element.dataset.fmdbfltFilled === "true") {
      write(element, "");
    }
  }

  // Writes one airport. A lookup runs only when the visitor changed the flight or its date, so its airports replace
  // whatever the fields held: they belong to the flight just given (a change by hand stays until the next flight).
  // A list without the airport gets its "another airport" option and the airport goes into the other field; when the
  // form has neither, the visitor's own choice is kept. Answers the label of an airport no field could take.
  function fill(form, element, leg, other, otherValue) {
    if (!element || !leg || !leg.iata) {
      return null;
    }
    var label = nameOf(form, leg);
    if (element.tagName !== "SELECT") {
      write(element, label);
      return null;
    }
    var code = leg.iata.toLowerCase();
    for (var i = 0; i < element.options.length; i++) {
      var option = element.options[i];
      if (option.value.toLowerCase() === code || option.textContent.trim().toUpperCase().endsWith("(" + leg.iata + ")")) {
        write(element, option.value);
        clear(other);
        return null;
      }
    }
    if (other && otherValue && hasOption(element, otherValue)) {
      write(element, otherValue);
      write(other, label);
      return null;
    }
    clear(element);
    clear(other);
    return label;
  }

  // "2026-11-09 01:55+09:00" (the airport's local time) as "9 November 2026, 01:55".
  function timeOf(local) {
    var match = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(local || "");
    return match ? dayOf(match[1]) + ", " + match[2] : local;
  }

  function setup(script) {
    var config;
    try {
      config = JSON.parse(script.textContent);
    } catch (e) {
      return;
    }
    var form = document.querySelector('form[data-form-id="' + script.getAttribute("data-fmdbflt-autofill") + '"]');
    if (!form || !config.dateField) {
      return;
    }
    var messages = config.messages || {};
    var status = null;
    var last = "";
    var pending = 0;

    function say(text) {
      var flight = control(form, config.flightField);
      if (!flight) {
        return;
      }
      if (!status || !status.isConnected) {
        status = document.createElement("p");
        status.className = "fmdbflt-autofill-status";
        status.setAttribute("role", "status");
        status.setAttribute("aria-live", "polite");
        status.style.margin = "0.25rem 0 0";
        status.style.fontSize = "0.875em";
        var wrapper = flight.closest(".fmdb-form-element") || flight.parentElement;
        wrapper.insertAdjacentElement("afterend", status);
      }
      status.textContent = text || "";
      status.hidden = !text;
    }

    function lookup() {
      var flight = control(form, config.flightField);
      var date = control(form, config.dateField);
      if (!flight || !date) {
        return;
      }
      var number = flight.value.trim();
      var day = date.value;
      if (!FLIGHT.test(number) || !DATE.test(day)) {
        say("");
        last = "";
        return;
      }
      var key = number.replace(/\s/g, "").toUpperCase() + "|" + day;
      if (key === last) {
        return;
      }
      last = key;
      var ticket = ++pending;
      fetch(config.endpoint + "?flight=" + encodeURIComponent(number) + "&date=" + encodeURIComponent(day),
        {credentials: "same-origin", headers: {Accept: "application/json"}})
        .then(function (response) {
          return response.ok ? response.json() : {status: "unavailable"};
        })
        .catch(function () {
          return {status: "unavailable"};
        })
        .then(function (answer) {
          if (ticket !== pending) {
            return;
          }
          if (answer.status === "notFound") {
            say(format(messages.notFound, {flight: answer.flight, date: dayOf(day)}));
            return;
          }
          if (answer.status !== "found") {
            say(messages.unavailable || "");
            return;
          }
          var missing = [];
          var otherValue = config.otherOptionValue || "other";
          var lost = fill(form, control(form, config.departureField), answer.departure,
            control(form, config.departureOtherField), otherValue);
          var away = fill(form, control(form, config.arrivalField), answer.arrival,
            control(form, config.arrivalOtherField), otherValue);
          [lost, away].forEach(function (airport) {
            if (airport) {
              missing.push(format(messages.otherAirport, {airport: airport}));
            }
          });
          var text = format(messages.found, {
            airline: answer.airline || "",
            flight: answer.flight,
            from: nameOf(form, answer.departure),
            to: nameOf(form, answer.arrival)
          }).trim();
          if (answer.departure.scheduledLocal) {
            text += " " + format(messages.departure, {time: timeOf(answer.departure.scheduledLocal)});
          }
          say([text].concat(missing).join(" "));
        });
    }

    form.addEventListener("change", function (event) {
      var name = event.target && event.target.name;
      if (name === config.flightField || name === config.dateField) {
        lookup();
      }
    });
    // A visitor's own choice is no longer this script's value: a later airport missing from a list will not clear it.
    form.addEventListener("input", function (event) {
      if (event.isTrusted && event.target && event.target.dataset) {
        delete event.target.dataset.fmdbfltFilled;
      }
    });
    form.addEventListener("formidable:reset", function () {
      last = "";
      say("");
    });
  }

  function start() {
    document.querySelectorAll("script[data-fmdbflt-autofill]").forEach(setup);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
