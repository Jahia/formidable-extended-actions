#!/usr/bin/env python3
"""Sample submissions of the Baggage claim and Partner day forms, with pictures of the damage on the claims.

    JAHIA_URL=http://localhost:8080 python3 -I seed-submissions.py [--dry-run | --backdate]

Posts ten claims and fourteen registrations as an anonymous visitor would (the form's own endpoint, with the stack's CAPTCHA test
token), so that the Results page and its Analytics tab have something to show: the three claim types,
both contact channels, English and French pages, one visit with ?member=1, and damage claims with one to
three pictures (one without any, so "Photos of the damage" is sometimes left empty). The registrations pick
one to three sessions, a dietary need ("Other" with its precision twice) and often a rating.

The pictures are drawn here with Pillow, like the site's artwork (classic-templates,
scripts/skylantern/images.py): a suitcase with a crack,
a dent, a missing wheel or a torn handle. Nothing is a photo and nothing is downloaded.

The claims are then spread over the last four weeks (a submission is stamped when it is posted): a provisioning script
creates each one again on its own day, with its pictures; --backdate does only that, on claims already posted.

Not idempotent: each run adds twenty-four submissions and sends the form's e-mails to the mail catcher. Delete the
rehearsal answers from the Results page (Delete, a date range) before a demo.
"""
import base64
import datetime
import io
import json
import os
import random
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

JAHIA = os.environ.get("JAHIA_URL", "http://localhost:8080").rstrip("/")
USER = os.environ.get("JAHIA_USER", "root")
PASSWORD = os.environ.get("JAHIA_PASSWORD", "root1234")
SITE = "skylantern-forms"
# form name (its node, and its entry on the Results page) -> the page it sits on
PAGES = {"baggage-claim": "baggage-claim", "event-registration": "partner-day"}
# Cloudflare Turnstile's test secret accepts any token; this is its documented dummy one.
CAPTCHA_TOKEN = "XXXX.DUMMY.TOKEN.XXXX"

# ---- Pictures ------------------------------------------------------------------------------------
SHELLS = {"navy": (26, 54, 93), "red": (158, 42, 43), "teal": (20, 108, 120), "grey": (92, 98, 108), "sand": (196, 170, 128)}


def shade(colour, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in colour)


def suitcase(colour, damage, seed):
    """A hard-shell suitcase on an arrivals-hall floor, 1200x900, with the given damage drawn on it."""
    rng = random.Random(seed)
    w, h = 1200, 900
    img = Image.new("RGB", (w, h), (214, 210, 204))
    d = ImageDraw.Draw(img)
    for y in range(h):  # wall, then floor
        t = y / h
        d.line([(0, y), (w, y)], fill=shade((226, 222, 214), 1 - 0.18 * t) if y < 560 else shade((168, 160, 150), 1.1 - 0.35 * (t - 0.62)))
    for x in range(-200, w + 200, 150):  # floor tiles
        d.line([(x, 560), (x - 260, h)], fill=(150, 142, 132), width=2)
    d.ellipse([330, 770, 900, 850], fill=(120, 113, 104))  # shadow
    x0, y0, x1, y1 = 380, 170, 830, 790
    d.rounded_rectangle([x0, y0, x1, y1], radius=46, fill=colour, outline=shade(colour, 0.6), width=5)
    for i in range(1, 6):  # ribs of the shell
        x = x0 + i * (x1 - x0) / 6
        d.line([(x, y0 + 40), (x, y1 - 40)], fill=shade(colour, 0.78), width=7)
        d.line([(x + 6, y0 + 40), (x + 6, y1 - 40)], fill=shade(colour, 1.18), width=2)
    d.rounded_rectangle([x0 + 150, y0 - 70, x1 - 150, y0 + 6], radius=14, outline=(48, 48, 52), width=14)  # trolley handle
    d.rounded_rectangle([x0 - 26, 400, x0 + 6, 560], radius=10, fill=(40, 40, 44))  # side handle
    wheels = [(x0 + 60, y1 + 16), (x1 - 60, y1 + 16)]
    if damage == "wheel":
        wheels.pop()
        d.ellipse([930, 800, 990, 860], fill=(30, 30, 34), outline=(90, 90, 96), width=5)  # the wheel, on the floor
        d.rectangle([x1 - 84, y1 - 4, x1 - 36, y1 + 12], fill=(70, 70, 76))
    for cx, cy in wheels:
        d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=(30, 30, 34), outline=(90, 90, 96), width=5)
    # a baggage tag, in the airline's orange
    d.line([(x0 + 150, y0 - 30), (x0 + 96, y0 + 120)], fill=(60, 60, 60), width=3)
    d.rounded_rectangle([x0 + 56, y0 + 116, x0 + 138, y0 + 250], radius=8, fill=(248, 244, 236), outline=(180, 65, 15), width=5)
    d.rectangle([x0 + 66, y0 + 130, x0 + 128, y0 + 150], fill=(180, 65, 15))
    if damage == "crack":
        pts = [(x0 + 250, y0 + 90)]
        while pts[-1][1] < y1 - 150:
            pts.append((pts[-1][0] + rng.randint(-55, 70), pts[-1][1] + rng.randint(40, 85)))
        d.line(pts, fill=(18, 18, 20), width=12, joint="curve")
        d.line([(x + 7, y - 3) for x, y in pts], fill=shade(colour, 1.5), width=3)
        for px, py in pts[1:-1:2]:
            d.line([(px, py), (px + rng.randint(-90, 90), py + rng.randint(-30, 60))], fill=(24, 24, 26), width=5)
    elif damage == "dent":
        cx, cy = x0 + 270, y0 + 360
        for r, f in ((150, 0.86), (115, 0.72), (78, 0.58), (40, 0.46)):
            d.ellipse([cx - r, cy - r * 0.8, cx + r, cy + r * 0.8], fill=shade(colour, f))
        d.arc([cx - 150, cy - 120, cx + 150, cy + 120], 200, 330, fill=shade(colour, 1.4), width=6)
    elif damage == "handle":
        for y in range(y0 - 80, y0 - 8):  # the handle is gone: the wall shows again...
            d.line([(x0 + 140, y), (x1 - 140, y)], fill=shade((226, 222, 214), 1 - 0.18 * y / h))
        d.line([(x0 + 157, y0 + 4), (x0 + 150, y0 - 44)], fill=(48, 48, 52), width=14)  # ...behind two stumps
        d.line([(x1 - 157, y0 + 4), (x1 - 170, y0 - 22)], fill=(48, 48, 52), width=14)
        d.line([(900, 700), (1080, 760)], fill=(48, 48, 52), width=14)  # the bar, on the floor
    for _ in range(26):  # scuffs
        sx, sy = rng.randint(x0 + 20, x1 - 60), rng.randint(y0 + 30, y1 - 30)
        d.line([(sx, sy), (sx + rng.randint(12, 60), sy + rng.randint(-10, 10))], fill=shade(colour, rng.choice((0.7, 1.3))), width=2)
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    noise = Image.effect_noise((w, h), 14).convert("RGB")  # a little grain, as a phone picture has
    img = Image.blend(img, noise, 0.06)
    out = io.BytesIO()
    img.save(out, "JPEG", quality=84)
    return out.getvalue()


# ---- Claims --------------------------------------------------------------------------------------
def contact(first, last, email, channel="email", phone=None):
    fields = {"firstName": first, "lastName": last, "email": email, "channel": channel, "consent": "true"}
    if phone:
        fields["phone"] = phone
    return fields


def trip(reference, days_ago, number, departure, arrival):
    """The claim is filed `days_ago` days before today (see backdate), for a flight of the day before."""
    FILED[reference] = days_ago
    flight = datetime.date.today() - datetime.timedelta(days=days_ago + 1)
    return {"bookingReference": reference, "flightDate": flight.isoformat(), "flightNumber": number, "departureAirport": departure, "arrivalAirport": arrival}


# booking reference -> days before today the claim is filed: the ten claims spread over four weeks
FILED = {}


HKG, NRT, KIX, TPE, BKK, SIN = ("Hong Kong (HKG)", "Tokyo Narita (NRT)", "Osaka Kansai (KIX)", "Taipei (TPE)", "Bangkok (BKK)", "Singapore (SIN)")

# lang, query of the page, fields, pictures as (file name, shell colour, damage)
CLAIMS = [
    ("en", "", {**trip("K7Q2ZP", 27, "SL812", HKG, "nrt"), "claimType": "damaged", "repairEstimate": "850",
                **contact("Aiko", "Tanaka", "aiko.tanaka@example.com")},
     [("shell-crack.jpg", "navy", "crack"), ("shell-crack-close.jpg", "navy", "crack")]),
    ("en", "?member=1", {**trip("M4T9XB", 24, "SL331", HKG, "sin"), "claimType": "damaged", "repairEstimate": "2400",
                         **contact("Wei Ling", "Tan", "weiling.tan@example.com", "phone", "+65 0000 0000")},
     [("missing-wheel.jpg", "red", "wheel"), ("wheel-on-floor.jpg", "red", "wheel"), ("dent-side.jpg", "red", "dent")]),
    ("fr", "", {**trip("B2H8LC", 21, "SL102", TPE, "hkg"), "claimType": "damaged",
                **contact("Camille", "Laurent", "camille.laurent@example.com")},
     [("poignee-arrachee.jpg", "teal", "handle")]),
    ("en", "", {**trip("R9D3WQ", 19, "SL418", HKG, "kix"), "claimType": "damaged", "repairEstimate": "430",
                **contact("Daniel", "Cheung", "daniel.cheung@example.com", "phone", "+852 0000 0000")},
     [("dent-front.jpg", "grey", "dent"), ("dent-detail.jpg", "grey", "dent")]),
    ("en", "", {**trip("T6N1YE", 16, "SL205", HKG, "bkk"), "claimType": "damaged", "repairEstimate": "1500",
                **contact("Somchai", "Prasert", "somchai.prasert@example.com")}, []),
    ("fr", "", {**trip("H3P7KA", 13, "SL813", NRT, "hkg"), "claimType": "damaged", "repairEstimate": "3100",
                **contact("Julien", "Moreau", "julien.moreau@example.com")},
     [("coque-fissuree.jpg", "sand", "crack"), ("roue-manquante.jpg", "sand", "wheel")]),
    ("en", "", {**trip("C8V5ZM", 11, "SL507", HKG, "nrt"), "claimType": "delayed", "street": "2-1-1 Nihonbashi, Chuo-ku", "city": "Tokyo",
                **contact("Hiro", "Sato", "hiro.sato@example.com")}, []),
    ("fr", "", {**trip("L5G2RN", 8, "SL613", NRT, "tpe"), "claimType": "delayed", "street": "No. 7, Section 5, Xinyi Road",
                **contact("Mei-Hua", "Lin", "meihua.lin@example.com", "phone", "+886 0000 0000")}, []),
    ("en", "", {**trip("W1J6TD", 5, "SL901", SIN, "bkk"), "claimType": "lost", "bagColour": "#1a365d", "bagBrand": "Northwind",
                "bagDescription": "Hard shell, a yellow strap around it and a lantern sticker on the front.",
                **contact("Priya", "Nair", "priya.nair@example.com")}, []),
    ("en", "", {**trip("Z4S8FU", 2, "SL206", BKK, "hkg"), "claimType": "lost", "bagColour": "#9e2a2b", "bagBrand": "Harbourline",
                **contact("Marcus", "Lee", "marcus.lee@example.com", "phone", "+852 0000 0000")}, []),
]


def registration(days_ago, lang, first, last, email, company, sessions, dietary=None, other=None, rating=None):
    fields = {"email": email, "firstName": first, "lastName": last, "sessions": sessions, "consent": "true"}
    for name, value in (("company", company), ("dietary", dietary), ("dietaryOther", other), ("lastTime", rating)):
        if value:
            fields[name] = value
    return lang, days_ago, fields


# Partner day registrations: days before today, language of the page, then the answers
REGISTRATIONS = [
    registration(28, "en", "Grace", "Leung", "grace.leung@harbourline.example", "Harbourline Hotels", ["keynote", "lounge"], "none", rating="5"),
    registration(26, "en", "Kenji", "Mori", "kenji.mori@nipponrail.example", "Nippon Rail Travel", ["keynote", "cabin", "loyalty"], "none", rating="4"),
    registration(25, "fr", "Sophie", "Bernard", "sophie.bernard@voyagesazur.example", "Voyages Azur", ["keynote"], "vegetarian", rating="4"),
    registration(23, "en", "Arjun", "Mehta", "arjun.mehta@lotustours.example", "Lotus Tours", ["cabin", "loyalty"], "vegetarian"),
    registration(22, "en", "Siti", "Rahman", "siti.rahman@straitsholidays.example", "Straits Holidays", ["keynote", "lounge"], "halal", rating="5"),
    registration(20, "en", "Tom", "Fraser", "tom.fraser@example.com", None, ["lounge"], rating="3"),
    registration(18, "fr", "Antoine", "Girard", "antoine.girard@cielbleu.example", "Ciel Bleu Voyages", ["keynote", "cabin"], "other", "Sans fruits à coque", "4"),
    registration(15, "en", "Hye-jin", "Park", "hyejin.park@hanriver.example", "Han River Travel", ["keynote", "loyalty"], "gluten", rating="5"),
    registration(12, "en", "Nattaya", "Suksai", "nattaya.suksai@siamsky.example", "Siam Sky", ["cabin"], "none"),
    registration(10, "en", "Oliver", "Wright", "oliver.wright@meridiancorp.example", "Meridian Corporate Travel", ["keynote", "lounge", "cabin"], "none", rating="4"),
    registration(7, "fr", "Claire", "Dubois", "claire.dubois@example.com", None, ["keynote", "loyalty"], "vegetarian", rating="2"),
    registration(5, "en", "Yuki", "Hayashi", "yuki.hayashi@sakuratravel.example", "Sakura Travel", ["lounge", "cabin"], "other", "No shellfish", "5"),
    registration(3, "en", "Farid", "Karimi", "farid.karimi@gulfwings.example", "Gulf Wings Agency", ["keynote"], "halal", rating="4"),
    registration(1, "en", "Emma", "Ng", "emma.ng@pearlriver.example", "Pearl River Events", ["keynote", "lounge", "loyalty"], "none", rating="5"),
]


def form_uuid(form):
    query = {"query": '{ jcr(workspace: LIVE) { nodeByPath(path: "/sites/%s/contents/forms/%s") { uuid } } }' % (SITE, form)}
    credentials = base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()
    request = urllib.request.Request(f"{JAHIA}/modules/graphql", json.dumps(query).encode(),
                                     {"Content-Type": "application/json", "Origin": JAHIA, "Authorization": f"Basic {credentials}"})
    return json.load(urllib.request.urlopen(request))["data"]["jcr"]["nodeByPath"]["uuid"]


def post(uuid, form, lang, query, fields, pictures, folder):
    """One submission, sent as the browser sends it: multipart, no session, the page as Referer."""
    command = ["curl", "-s", "-w", "\n%{http_code}", "-X", "POST", f"{JAHIA}/modules/formidable-engine/form-submit?fid={uuid}&lang={lang}",
               "-H", f"Origin: {JAHIA}", "-H", f"Referer: {JAHIA}/{lang}/sites/{SITE}/home/{PAGES[form]}.html{query}",
               "-H", f"X-Formidable-Captcha-Token: {CAPTCHA_TOKEN}", "-H", "X-Formidable-Time-Zone: Asia/Hong_Kong"]
    for name, value in fields.items():
        for one in (value if isinstance(value, list) else [value]):  # a list is a multiple choice: one part per answer
            command += ["--form-string", f"{name}={one}"]
    for index, (file_name, colour, damage) in enumerate(pictures):
        path = Path(folder) / file_name
        path.write_bytes(suitcase(SHELLS[colour], damage, seed=f"{file_name}-{index}"))
        command += ["-F", f"photos=@{path};type=image/jpeg"]
    body, _, status = subprocess.run(command, capture_output=True, text=True, check=True).stdout.rpartition("\n")
    return int(status), body


BACKDATE = """
import org.jahia.services.content.JCRTemplate
import org.jahia.services.content.JCRCallback

def filed = %s   // visitor's e-mail -> [created (ISO, UTC), values to set on the copy]
def utc = TimeZone.getTimeZone("UTC")
def part = { Calendar date, String pattern -> def f = new java.text.SimpleDateFormat(pattern); f.setTimeZone(utc); f.format(date.getTime()) }
JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", null, { session ->
    def root = session.getNode("/sites/%s/formidable-results/%s/submissions")
    def found = session.getWorkspace().getQueryManager().createQuery(
        "SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '" + root.getPath() + "')", "JCR-SQL2").execute().getNodes()
    def nodes = []
    while (found.hasNext()) { nodes << found.nextNode() }
    int moved = 0
    nodes.each { node ->
        def data = node.getNode("data")
        def entry = data.hasProperty("email") ? filed[data.getProperty("email").getString()] : null
        if (entry == null) { return }
        def created = Calendar.getInstance(utc)
        created.setTime(Date.from(java.time.Instant.parse(entry[0])))
        if (part(created, "yyyyMMdd") == part(node.getProperty("jcr:created").getDate(), "yyyyMMdd")) { return }   // already on its day
        def folder = root
        ["yyyy", "MM", "dd"].each { pattern ->
            def name = part(created, pattern)
            folder = folder.hasNode(name) ? folder.getNode(name) : folder.addNode(name, "fmdb:splittedSubmission")
        }
        // jcr:created is protected: a node only takes a creation date when it is created, so the submission is created again
        def copy = folder.addNode("submission-" + part(created, "yyyyMMdd-HHmmss") + "-" + node.getName().tokenize("-").last(),
                "fmdb:formSubmission", null, created, node.getProperty("jcr:createdBy").getString(), created, "")
        ["origin", "locale", "referer", "timeZone"].each { name -> if (node.hasProperty(name)) { copy.setProperty(name, node.getProperty(name).getString()) } }
        def copyData = copy.hasNode("data") ? copy.getNode("data") : copy.addNode("data", "fmdb:submissionData")
        def properties = data.getProperties()
        while (properties.hasNext()) {
            def property = properties.nextProperty()
            def name = property.getName()
            if (name.startsWith("jcr:") || name.startsWith("j:")) { continue }
            if (property.isMultiple()) { copyData.setProperty(name, property.getValues()) } else { copyData.setProperty(name, property.getString()) }
        }
        entry[1].each { name, value -> copyData.setProperty(name, value) }
        session.save()
        if (node.hasNode("files")) { session.move(node.getPath() + "/files", copy.getPath() + "/files") }
        node.remove()
        session.save()
        moved++
    }
    log.info("%s submissions moved to their day: " + moved)
    return null
} as JCRCallback)
"""


def backdate(form, filed_days, rng):
    """Gives each seeded submission its own day: one is stamped when it is posted, so all would read today.

    The creation date of a node is protected, so a provisioning script creates each submission again in the folder
    of its day (submissions/yyyy/MM/dd), with its values and its pictures, and removes the one posted today.
    Submissions are matched on the visitor's e-mail; one already on its day is left. `filed_days` maps the e-mail
    to the days before today and to the values that follow the date (a claim's flight date).
    """
    today = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    entries = []
    for email, (days_ago, values) in filed_days.items():
        created = (today - datetime.timedelta(days=days_ago)).replace(hour=rng.randint(0, 14), minute=rng.randint(0, 59), second=rng.randint(0, 59))
        dated = {name: value(created.date()) for name, value in values.items()}
        overrides = "[" + ", ".join(f"'{name}': '{value}'" for name, value in dated.items()) + "]" if dated else "[:]"
        entries.append(f"'{email}': ['{created.strftime('%Y-%m-%dT%H:%M:%SZ')}', {overrides}]")
    groovy = BACKDATE % ("[" + ", ".join(entries) + "]", SITE, form, form)
    with tempfile.TemporaryDirectory() as folder:
        script = Path(folder) / f"backdate-{form}.groovy"
        script.write_text(groovy)
        out = subprocess.run(["curl", "-s", "-u", f"{USER}:{PASSWORD}", "-F", 'script=[{"executeScript":"%s"}]' % script.name,
                              "-F", f"file=@{script}", f"{JAHIA}/modules/api/provisioning"], capture_output=True, text=True, check=True).stdout
    print(f"{form}: spread over the last four weeks" if ".installed" in out else f"{form}: BACKDATE FAILED {out[:300]}")
    return ".installed" in out


def backdate_all():
    rng = random.Random(7)
    day_before = lambda created: (created - datetime.timedelta(days=1)).isoformat()
    claims = {fields["email"]: (FILED[fields["bookingReference"]], {"flightDate": day_before}) for _, _, fields, _ in CLAIMS}
    registrations = {fields["email"]: (days_ago, {}) for _, days_ago, fields in REGISTRATIONS}
    return backdate("baggage-claim", claims, rng) & backdate("event-registration", registrations, rng)


def main():
    if "--dry-run" in sys.argv:
        out = Path(tempfile.mkdtemp(prefix="claim-pictures-"))
        for _, _, _, pictures in CLAIMS:
            for index, (file_name, colour, damage) in enumerate(pictures):
                (out / file_name).write_bytes(suitcase(SHELLS[colour], damage, seed=f"{file_name}-{index}"))
        print(f"pictures written to {out}, nothing posted")
        return 0
    if "--backdate" in sys.argv:  # only spread the submissions already posted
        return 0 if backdate_all() else 1
    failures = 0
    with tempfile.TemporaryDirectory() as folder:
        posts = [("baggage-claim", lang, query, fields, pictures) for lang, query, fields, pictures in CLAIMS]
        posts += [("event-registration", lang, "", fields, []) for lang, _, fields in REGISTRATIONS]
        uuids = {form: form_uuid(form) for form in PAGES}
        for form, lang, query, fields, pictures in posts:
            status, body = post(uuids[form], form, lang, query, fields, pictures, folder)
            ok = status == 200 and '"success":true' in body.replace(" ", "")
            failures += 0 if ok else 1
            print(("ok  " if ok else "FAIL"), form, fields["email"], f"{len(pictures)} picture(s)" if pictures else "", "" if ok else f"{status} {body[:200]}")
    if not backdate_all():
        failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
