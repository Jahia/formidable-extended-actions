# Formidable demo forms (site `skylantern-forms`)

Three forms for a Formidable demo, with sample answers, on a site of their own for the fictional Skylantern
Airways, built on [classic-templates](https://github.com/Jahia/classic-templates). They show the core form
features, jExperience prefill, and two modules of this repository: the [PDF copy](../../docs/pdf.md) and the
[Flight number check](../../docs/flight-check.md). Every name, address and flight is invented (`@skylantern.example`).
Back to the [repository README](../../README.md).

| Page (live) | Form | Shows |
|---|---|---|
| `/home/contact-us` | none: an empty free zone | building a form live in Page Builder |
| `/home/contact-us-ready` (hidden from the menu) | Contact us | the finished copy of that form, the fallback of the live build |
| `/home/partner-day` | Partner day registration | jExperience prefill (email read-only), sessions read from content items, "Other" dietary need on demand, rating, consent |
| `/home/baggage-claim` | Baggage claim | flight number checked as the visitor leaves the field, three steps, rules on a choice (claim type, contact channel) and on a URL parameter (`?member=1` shows the Lantern Club number), airports from a category, flight date within the last 6 months, photo upload, CAPTCHA |
| `/home/privacy` (hidden) | none | the consent fields' terms page |
| `/home/baggage-claim-restyled` (hidden) | Baggage claim (a copy) | the same form restyled as a boarding pass by its Custom CSS: tokens first, then a few layout rules (the styling scene) |

## Requirements

- Jahia 8.2 with classic-templates (its form styling: Jahia/classic-templates#74), formidable-elements,
  formidable-extended-inputs, jExperience and formidable-jexperience-engine deployed.
- Optional, enabled on the site when deployed: `formidable-pdf` and `formidable-flight-check` from this repository.
- Python 3 with Pillow (the sample pictures), and a checkout of classic-templates next to this repository's
  checkout: its `scripts/seed-demo.py` is the GraphQL helper library (`CLASSIC_TEMPLATES` to point elsewhere).

## Run

```bash
cd demo/skylantern-forms
JAHIA_URL=http://localhost:8080 python3 -I seed-forms-demo.py
```

`JAHIA_USER` defaults to `root:root1234`. Add-only and idempotent, like the site's own seeder: a second run
creates nothing.

It creates the site (EN + FR) and enables formidable-elements, formidable-extended-inputs, jexperience and
formidable-jexperience-engine; writes the site's jExperience settings (copied from the instance's global settings,
`JXP_*` to override); the categories `/sites/systemsite/categories/skylantern-airports` (6 airports); four session
items under `contents/event-sessions`; the forms under `contents/forms`; the pages; then publishes.

After a first run, publish the site once more (Formidable writes the fields' keys after the first publication,
which leaves the pages "Modified"):

```graphql
mutation { jcr { mutateNode(pathOrId: "/sites/skylantern-forms") {
  publish(languages: ["en", "fr"], publishSubNodes: true, includeSubTree: true) } } }
```

## Styling scene

`baggage-claim-restyled.css` is the Custom CSS (`css` property, Style section of Content Editor) of the copy
`contents/forms/baggage-claim-restyled`, placed on the hidden page `/home/baggage-claim-restyled`. Part 1 sets
tokens only, on `.fmdb-form`: Formidable's `--fmdb-*` and `--fmdbext-*` variables (`docs/styling/css-variables.md`
in Jahia/formidable) and the classic-templates `--ctpl-*` tokens its form rules read, plus `color-scheme: light` so
the site's `light-dark()` tokens resolve light inside the pass in either theme. Part 2 changes the layout on
Formidable's class hooks: two columns per step, pill steps, underlined fields, the flight check's route line as a
row of its own. Every colour pair meets WCAG 2.1 AA, and so does the live change (`--pass-accent: #1f6f4a`).

The copy keeps the real claim form, its answers and its analytics out of the scene. The seed writes the file into
the copy's Custom CSS again whenever they differ and publishes it, so a run after the scene undoes the live edit.

## Sample submissions

```bash
JAHIA_URL=http://localhost:8080 python3 -I seed-submissions.py     # --dry-run only draws the pictures
```

Posts ten Baggage claim submissions and fourteen Partner day registrations as a visitor would (the forms' own
endpoint, the CAPTCHA test token), then spreads them over the last four weeks, so the Results page and its
Analytics tab have something to show.

- **Claims**: the three claim types, both contact channels, English and French pages, one visit with
  `?member=1`, and damage claims with one to three pictures (one without any). The pictures are drawn by the
  script with Pillow: nothing is a photo.
- **Registrations**: one to three sessions each, a dietary need ("Other" with its precision twice), a rating
  for most, a company for all but two.
- **Dates**: a submission is stamped when it is posted and its creation date is protected, so a provisioning
  script creates each one again in the folder of its day and removes the one posted today (`--backdate` does
  only that, on submissions already posted).

Each run adds twenty-four submissions and sends the forms' e-mails.

## Around the forms (set up on the instance)

- **Mail catcher**: the e-mail actions write to `sales@`, `events@` and `baggage@skylantern.example`. Point Jahia's
  mail server at a catcher such as Mailpit (`smtp://mailpit:1025` from a container on the same network).
- **CAPTCHA**: Cloudflare Turnstile's published test keys (always pass), in `org.jahia.modules.formidable.captcha`.
  `seed-submissions.py` posts with their test token.
- **PDF copy**: with `formidable-pdf` deployed, the Baggage claim and Partner day forms get the action: the success
  message offers the visitor's copy as a PDF.
- **Flight number check and airport autofill**: with `formidable-flight-check` deployed, the Baggage claim flight
  number gets the check. Add the three lines of `flightcheck-skylantern.cfg` to
  `karaf/etc/org.jahia.modules.formidable.flightcheck.cfg`: SL flights are then judged on Skylantern's schedule
  (SL812 passes, SL999 is refused). With the flight date given first, the page fills Departure airport (text) and
  Arrival airport (list) and says the route under the number; an airport outside Skylantern's six makes the list
  "Another airport" and fills the "Your arrival airport" field a rule shows for that option. Other airlines go
  through AeroDataBox with your RapidAPI key (`.credential` in the same file, [setup](../../docs/flight-check.md#configuration));
  without it they are accepted unchecked and nothing is filled. A file copied into `karaf/etc` with `docker cp`
  keeps the host's owner: `chown` it to the Jahia user, or the configuration stays read-only.

## Before each demo

- Prefill shows only for a returning visitor: submit the Partner day form once in the browser you will present
  from, then reload.
- Delete the rehearsal answers on the Results page if they should not show.
- The browser's own "Please fill in this field" follows the browser's language: only the fields with a custom
  message (first name of Contact us) are translated by the form.
- Each AeroDataBox check spends quota of the RapidAPI plan.
