#!/usr/bin/env python3
"""Seeds the Formidable demo site "skylantern-forms" (Skylantern Airways, fictional) with the three
forms of the Formidable demo rundown:

  A  Contact us          the page /home/contact-us is left EMPTY for the live build; a finished copy
                         sits on the hidden page /home/contact-us-ready as the fallback
  B  Event registration  fields mapped to the jCustomer profile and prefilled (email read-only),
                         sessions read from content items, "Other" dietary need shown on demand
  C  Baggage claim       three steps, rules on a field value and on a URL parameter (?member=1),
                         airports from a category, flight date within the last 6 months, photo upload,
                         CAPTCHA (the instance's Turnstile test keys)

Add-only and idempotent, on the model of the site's seeder (classic-templates,
scripts/skylantern/seed-airline.py): an existing node is never changed, a
second run creates nothing. It publishes (EN + FR) what it created.

Needs: classic-templates, formidable-elements, formidable-extended-inputs, formidable-jexperience-engine
and jExperience deployed; the checkout of classic-templates next to this repository's checkout (its
scripts/seed-demo.py is the GraphQL helper library).

Environment: JAHIA_URL (default http://localhost:8080), JAHIA_USER (default root:root1234),
CLASSIC_TEMPLATES (default ../../../classic-templates). jExperience per-site settings are copied from the
instance's global settings (JXP_* variables override them).
"""
import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CTPL = Path(os.environ.get("CLASSIC_TEMPLATES", HERE.parent.parent.parent / "classic-templates"))
spec = importlib.util.spec_from_file_location("seed_demo", CTPL / "scripts" / "seed-demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)
gql, exists, i18n, set_props, uuid_at, provisioning = (
    demo.gql, demo.exists, demo.i18n, demo.set_props, demo.uuid_at, demo.provisioning)

SITE_KEY = "skylantern-forms"
MODULES = ["formidable-elements", "formidable-extended-inputs", "jexperience", "formidable-jexperience-engine"]
# enabled when deployed: module -> a node type only it declares (both modules of this repository)
OPTIONAL_MODULES = {"formidable-flight-check": "fmdbflt:flightCheckAction", "formidable-pdf": "fmdbpdf:pdfCopyAction"}
CATEGORY_ROOT = "/sites/systemsite/categories"
AIRPORTS = "skylantern-airports"
TEAM = {"contact": "sales@skylantern.example", "events": "events@skylantern.example", "claims": "baggage@skylantern.example"}

CREATED = []


def T(en, fr):
    return {"en": en, "fr": fr}


def add(parent, name, node_type, props, mixins=None, children=None):
    """Adds a node (with an optional subtree) when it is missing; never changes an existing one."""
    path = f"{parent}/{name}"
    if exists(path):
        return path
    gql("mutation($p:String!,$n:String!,$t:String!,$m:[String],$props:[InputJCRProperty],$c:[InputJCRNode]){"
        "jcr{addNode(parentPathOrId:$p,name:$n,primaryNodeType:$t,mixins:$m,properties:$props,children:$c){uuid}}}",
        {"p": parent, "n": name, "t": node_type, "m": mixins or [], "props": props, "c": children or []})
    CREATED.append(path)
    print(f"created {path}")
    return path


def prop_value(path, name):
    p = gql("query($p:String!,$n:String!){jcr{nodeByPath(path:$p){property(name:$n){value values}}}}",
            {"p": path, "n": name})["jcr"]["nodeByPath"]["property"]
    return None if not p else (p["value"] if p["value"] is not None else p["values"])


def publish(path):
    gql('mutation($p:String!){jcr{mutateNode(pathOrId:$p){publish(languages:["en","fr"],publishSubNodes:true,includeSubTree:true)}}}',
        {"p": path})
    print(f"published {path}")


# ---- Field builders (property names of formidable 0.6, as the Formidable Cypress fixtures write them) ----

def b(v):
    return "true" if v else "false"


def opts(choices, lang):
    return [json.dumps({"value": v, "label": labels[lang], "selected": False}) for v, labels in choices]


def base(name, node_type, title, required=None, help_=None, mixins=None, extra=None):
    props = i18n("jcr:title", title)
    if required is not None:
        props.append({"name": "required", "value": b(required), "type": "BOOLEAN"})
    if help_:
        props += i18n("helpText", {k: f"<p>{v}</p>" for k, v in help_.items()})
    props += extra or []
    return {"name": name, "primaryNodeType": node_type, "mixins": mixins or [], "properties": props}


def text(name, title, **kw):
    extra = []
    for key, typ in (("minLength", "LONG"), ("maxLength", "LONG")):
        if key in kw:
            extra.append({"name": key, "value": str(kw[key]), "type": typ})
    for key in ("pattern", "mask", "autocomplete"):
        if key in kw:
            extra.append({"name": key, "value": kw[key]})
    if "placeholder" in kw:
        extra += i18n("placeholder", kw["placeholder"])
    if "patternMessage" in kw:
        extra += i18n("msgPatternMismatch", kw["patternMessage"])
    if "missingMessage" in kw:
        extra += i18n("msgValueMissing", kw["missingMessage"])
    return base(name, kw.get("type", "fmdb:inputText"), title, kw.get("required"), kw.get("help"), extra=extra)


def email(name, title, **kw):
    return text(name, title, type="fmdb:inputEmail", autocomplete="email", **kw)


def textarea(name, title, **kw):
    node = base(name, "fmdb:textarea", title, kw.get("required"), kw.get("help"))
    node["properties"].append({"name": "rows", "value": str(kw.get("rows", 5)), "type": "LONG"})
    return node


def manual_choice(name, node_type, title, choices, required=None, help_=None, empty=None, multiple=None):
    extra = [{"name": "optionsMode", "value": "manual"}]
    extra += [{"name": "options", "values": opts(choices, lang), "language": lang} for lang in ("en", "fr")]
    if empty:
        extra += i18n("optionsEmptyLabel", empty)
    if multiple is not None:
        extra.append({"name": "multiple", "value": b(multiple), "type": "BOOLEAN"})
    return base(name, node_type, title, required, help_, ["fmdbmix:manualOptions"], extra)


def category_choice(name, node_type, title, root_uuid, required=None, empty=None):
    extra = [{"name": "optionsMode", "value": "category"},
             {"name": "optionsRootCategory", "value": root_uuid, "type": "WEAKREFERENCE"}]
    if empty:
        extra += i18n("optionsEmptyLabel", empty)
    return base(name, node_type, title, required, None, ["fmdbmix:categoryOptions"], extra)


def content_choice(name, node_type, title, root_uuid, item_type, required=None, help_=None):
    extra = [{"name": "optionsMode", "value": "content"},
             {"name": "optionsRootNode", "value": root_uuid, "type": "WEAKREFERENCE"},
             {"name": "optionsNodeType", "value": item_type}]
    return base(name, node_type, title, required, help_, ["fmdbmix:contentOptions"], extra)


def date_field(name, title, required=None, help_=None, min_relative=None, max_today=False):
    node = base(name, "fmdb:inputDate", title, required, help_)
    if min_relative:
        node["properties"] += [{"name": "minBoundMode", "value": "relative"},
                               {"name": "minRelativeAmount", "value": str(min_relative[0]), "type": "LONG"},
                               {"name": "minRelativeUnit", "value": min_relative[1]}]
        node["mixins"].append("fmdbmix:relativeMinDate")
    if max_today:
        node["properties"].append({"name": "maxBoundMode", "value": "today"})
    return node


def number(name, title, min_value=None, step=None, help_=None):
    node = base(name, "fmdb:inputNumber", title, None, help_)
    if min_value is not None:
        node["properties"].append({"name": "minValue", "value": str(min_value), "type": "DOUBLE"})
    if step is not None:
        node["properties"].append({"name": "step", "value": str(step), "type": "DOUBLE"})
    return node


def file_field(name, title, accept, multiple=False, required=None, help_=None):
    node = base(name, "fmdb:inputFile", title, required, help_)
    node["properties"] += [{"name": "multiple", "value": b(multiple), "type": "BOOLEAN"}, {"name": "accept", "values": accept}]
    return node


def consent(name, statement, terms_uuid, link_label):
    props = i18n("jcr:title", T("Consent", "Consentement")) + i18n("statement", statement) + i18n("termsLinkLabel", link_label)
    props += [{"name": "required", "value": "true", "type": "BOOLEAN"},
              {"name": "termsTarget", "value": terms_uuid, "type": "WEAKREFERENCE"}]
    return {"name": name, "primaryNodeType": "fmdbext:consent", "properties": props}


def rating(name, title, min_label, max_label):
    props = i18n("jcr:title", title) + i18n("minLabel", min_label) + i18n("maxLabel", max_label)
    props += [{"name": "icon", "value": "star"}, {"name": "maxValue", "value": "5", "type": "LONG"}]
    return {"name": name, "primaryNodeType": "fmdbext:rating", "properties": props}


def fieldset(name, title, children):
    return {"name": name, "primaryNodeType": "fmdb:fieldset", "properties": i18n("jcr:title", title), "children": children}


def step(name, title, children):
    return {"name": name, "primaryNodeType": "fmdb:step",
            "properties": i18n("jcr:title", title) + i18n("label", title), "children": children}


def when_choice(node, logic_id, source_name, source_type, values):
    rule = {"logicId": logic_id, "sourceFieldName": source_name, "sourceFieldType": source_type,
            "valueKind": "choice", "operator": "in", "values": values}
    node["properties"].append({"name": "logics", "values": [json.dumps(rule)]})
    return node


def when_url_param(node, logic_id, param, value):
    rule = {"logicId": logic_id, "sourceType": "urlParam", "param": param, "operator": "equals", "value": value}
    node["properties"].append({"name": "logics", "values": [json.dumps(rule)]})
    return node


def mapped(node, profile_property, prefill=True, then=None, strategy="alwaysSet"):
    node["mixins"] = node.get("mixins", []) + ["fmdbmix:jExperienceProfileMapping"]
    node["properties"] += [{"name": "jExperienceProfileProperty", "value": profile_property},
                           {"name": "jExperienceSetStrategy", "value": strategy}]
    if prefill:
        node["properties"].append({"name": "jExperiencePrefill", "value": "true", "type": "BOOLEAN"})
        if then:
            node["properties"].append({"name": "jExperiencePrefillThen", "value": then})
    return node


def node_type_exists(name):
    try:
        return bool(gql("query($n:String!){jcr{nodeTypeByName(name:$n){name}}}", {"n": name})["jcr"]["nodeTypeByName"])
    except RuntimeError:
        return False


def with_flight_check(node):
    """The flight number check of formidable-flight-check (this repository), when that module is deployed."""
    if not node_type_exists("fmdbflt:flightCheckAction"):
        print("formidable-flight-check is not deployed: the flight number has no check")
        return node
    check = {"name": "flightCheck", "primaryNodeType": "fmdbflt:flightCheckAction",
             "properties": [{"name": "trigger", "value": "blur"}, {"name": "severity", "value": "block"},
                            {"name": "whenUnavailable", "value": "accept"},
                            # once date and number are given, the page fills the airports (FlightAutofillFilter)
                            {"name": "autofill", "value": "true", "type": "BOOLEAN"}, {"name": "dateField", "value": "flightDate"},
                            {"name": "departureField", "value": "departureAirport"}, {"name": "arrivalField", "value": "arrivalAirport"},
                            {"name": "arrivalOtherField", "value": "arrivalOther"}, {"name": "otherOptionValue", "value": "other"}]
             + i18n("jcr:title", T("Flight number check", "Vérification du numéro de vol"))
             + i18n("rejectionMessage", T("<p>We found no flight <b>${value}</b>. Check the number on your boarding pass.</p>",
                                          "<p>Nous ne trouvons aucun vol <b>${value}</b>. Vérifiez le numéro sur votre carte d'embarquement.</p>"))}
    node["mixins"] = node.get("mixins", []) + ["fmdbmix:fieldActions"]
    node["children"] = [{"name": "actions", "primaryNodeType": "fmdb:fieldActionList", "properties": [], "children": [check]}]
    return node


def save_action():
    return {"name": "store", "primaryNodeType": "fmdb:save2jcrAction",
            "properties": i18n("jcr:title", T("Save the answers", "Enregistrer les réponses"))}


def notify_action(to, subject, body):
    return {"name": "notifyTeam", "primaryNodeType": "fmdb:emailNotificationAction",
            "properties": i18n("jcr:title", T("Notify the team", "Prévenir l'équipe")) + [{"name": "to", "value": to}]
            + i18n("subject", subject) + i18n("templateMessage", body)}


def content_action(to):
    return {"name": "sendAnswers", "primaryNodeType": "fmdb:emailContentAction",
            "properties": i18n("jcr:title", T("Send the answers with the files", "Envoyer les réponses et les fichiers"))
            + [{"name": "to", "value": to}, {"name": "attachFiles", "value": "true", "type": "BOOLEAN"}]}


def pdf_copy_action(note):
    """The visitor's PDF copy (formidable-pdf of formidable-extended-actions); nothing when the module is not deployed."""
    if not node_type_exists("fmdbpdf:pdfCopyAction"):
        return []
    return [{"name": "pdfCopy", "primaryNodeType": "fmdbpdf:pdfCopyAction",
             "properties": i18n("jcr:title", T("Give the visitor a PDF copy", "Remettre une copie PDF au visiteur")) + i18n("closingNote", note)}]


def form(folder, name, title, intro, submit, done, fields, actions, mixins=None, extra=None):
    props = (i18n("jcr:title", title) + i18n("intro", {k: f"<p>{v}</p>" for k, v in intro.items()})
             + i18n("submitBtnLabel", submit) + i18n("submissionMessage", {k: f"<p>{v}</p>" for k, v in done.items()})
             + (extra or []))
    children = [{"name": "fields", "primaryNodeType": "fmdb:fieldList",
                 "properties": i18n("jcr:title", T("Form fields", "Champs du formulaire")), "children": fields},
                {"name": "actions", "primaryNodeType": "fmdb:actionList",
                 "properties": i18n("jcr:title", T("Form actions", "Actions du formulaire")), "children": actions}]
    path = add(folder, name, "fmdb:form", props, mixins, children)
    if path not in CREATED:
        repair(f"{path}/fields", fields)
        repair(f"{path}/actions", actions)
    return path


def repair(parent, nodes):
    """Adds the fields an existing form has lost, at any depth, at the end of their step or fieldset.

    A field type that leaves the platform for a while takes its nodes with it: the rating and consent fields were
    gone from both forms after formidable-extended-inputs was replaced by the 0.6.0 release (2026-11-16).
    """
    for node in nodes:
        if exists(f"{parent}/{node['name']}"):
            repair(f"{parent}/{node['name']}", node.get("children", []))
        else:
            add(parent, node["name"], node["primaryNodeType"], node["properties"], node.get("mixins"), node.get("children"))


def page(home, name, title, description, hidden=False):
    props = i18n("jcr:title", title) + i18n("jcr:description", description) + [{"name": "j:templateName", "value": "content"}]
    mixins = []
    if hidden:
        mixins = ["ctplmix:pageOptions"]
        props += [{"name": "ctplHideFromNav", "value": "true"}, {"name": "ctplHideTitle", "value": "false"}]
    path = add(home, name, "jnt:page", props, mixins)
    add(path, "main", "ctpl:pageArea", [])
    # classic-templates areas take page components only: a form goes into a free zone (reading width).
    add(f"{path}/main", "form-zone", "ctpl:freeZone", [{"name": "width", "value": "reading"}])
    return path


def place_form(page_path, form_path):
    add(f"{page_path}/main/form-zone", "form", "fmdb:formReference",
        [{"name": "j:node", "value": uuid_at(form_path), "type": "WEAKREFERENCE"}])


# ---- Site ----------------------------------------------------------------------------------------

def ensure_site():
    site = f"/sites/{SITE_KEY}"
    if not exists(site):
        provisioning(f'- createSite: ""\n  siteKey: {SITE_KEY}\n  title: "Skylantern Airways Forms"\n  defaultLanguage: en\n'
                     "  serverName: localhost\n  templateSet: classic-templates\n", "application/yaml")
        if not exists(f"{site}/home"):
            sys.exit(f"site {SITE_KEY} was not created")
        set_props(site, [{"name": "j:languages", "values": ["en", "fr"]}, {"name": "j:mandatoryLanguages", "values": []},
                         {"name": "j:inactiveLanguages", "values": []}, {"name": "j:inactiveLiveLanguages", "values": []}])
        CREATED.append(site)
        print(f"created site {SITE_KEY}")
    installed = prop_value(site, "j:installedModules") or []
    for module in MODULES + [m for m, node_type in OPTIONAL_MODULES.items() if node_type_exists(node_type)]:
        if module not in installed:
            provisioning(f'- enable: "{module}"\n  site: "{SITE_KEY}"\n', "application/yaml")
            print(f"enabled {module}")
    return site


def ensure_jexperience_settings():
    """The per-site jExperience settings (what the site settings screen writes), from the global ones."""
    keys = {"jexperience.jCustomerURL": os.environ.get("JXP_URL", "https://jcustomer:9443"),
            "jexperience.jCustomerUsername": os.environ.get("JXP_USER", "karaf"),
            "jexperience.jCustomerPassword": os.environ.get("JXP_PASSWORD", "karaf"),
            "jexperience.jCustomerKey": os.environ.get("JXP_KEY", "670c26d1cc413346c3b2fd9ce65dab41"),
            "jexperience.jCustomerTrustAllCertificates": "true",
            "jexperience.jCustomerUsePublicAddressesForAdmin": "false",
            "jexperience.jCustomerPublicURL": "$WEBAPP_CONTEXT/modules/jexperience/proxy/$CURRENT_SITE",
            "jexperience.siteKey": SITE_KEY}
    lines = "\n".join(f'    {k}: "{v}"' for k, v in keys.items())
    provisioning(f'- editConfiguration: "org.jahia.modules.jexperience.settings"\n  configIdentifier: "{SITE_KEY}"\n'
                 f"  properties:\n{lines}\n", "application/yaml")


def ensure_airports():
    root = add(CATEGORY_ROOT, AIRPORTS, "jnt:category", i18n("jcr:title", T("Skylantern airports", "Aéroports Skylantern")))
    for code, en, fr in (("hkg", "Hong Kong (HKG)", "Hong Kong (HKG)"), ("nrt", "Tokyo Narita (NRT)", "Tokyo Narita (NRT)"),
                         ("kix", "Osaka Kansai (KIX)", "Osaka Kansai (KIX)"), ("tpe", "Taipei (TPE)", "Taipei (TPE)"),
                         ("bkk", "Bangkok (BKK)", "Bangkok (BKK)"), ("sin", "Singapore (SIN)", "Singapour (SIN)"),
                         # last: the option a flight to any other airport gets (the claim's "Your arrival airport" field)
                         ("other", "Another airport", "Un autre a\u00e9roport")):
        add(root, code, "jnt:category", i18n("jcr:title", T(en, fr)))
    if root in CREATED or any(p.startswith(root + "/") for p in CREATED):
        publish(root)
    return root


def ensure_sessions(site):
    folder = add(f"{site}/contents", "event-sessions", "jnt:contentFolder", i18n("jcr:title", T("Event sessions", "Sessions de l'événement")))
    for name, en, fr in (("keynote", "Keynote: the 2027 network", "Keynote : le réseau 2027"),
                         ("lounge", "Tour of the new Harbour lounge", "Visite du nouveau salon Harbour"),
                         ("cabin", "Hands-on: the A321neo cabin", "Découverte : la cabine A321neo"),
                         ("loyalty", "Lantern Club: what changes", "Lantern Club : ce qui change")):
        add(folder, name, "jnt:text", i18n("jcr:title", T(en, fr)) + i18n("text", T(en, fr)), ["mix:title"])
    return folder


# ---- The three forms ------------------------------------------------------------------------------

TOPICS = [("sales", T("Group and corporate sales", "Ventes groupes et entreprises")),
          ("support", T("Customer support", "Service client")),
          ("partnership", T("Partnership", "Partenariat"))]


def contact_fields(privacy_uuid):
    return [
        text("firstName", T("First name", "Prénom"), required=True, autocomplete="given-name",
             missingMessage=T("Please tell us your first name", "Merci d'indiquer votre prénom")),
        text("lastName", T("Last name", "Nom"), required=True, autocomplete="family-name"),
        email("email", T("Email", "E-mail"), required=True),
        manual_choice("topic", "fmdb:select", T("Topic", "Sujet"), TOPICS, required=True,
                      empty=T("Choose a topic", "Choisissez un sujet")),
        textarea("message", T("Message", "Message"), required=True),
        consent("consent", T("I agree that Skylantern Airways uses my details to answer my request.",
                             "J'accepte que Skylantern Airways utilise mes coordonnées pour répondre à ma demande."),
                privacy_uuid, T("Privacy notice", "Politique de confidentialité")),
    ]


def seed_contact(folder, privacy_uuid):
    return form(folder, "contact-us-ready", T("Contact us", "Nous contacter"),
                T("A question about a flight, a group booking or a partnership? Write to us.",
                  "Une question sur un vol, une réservation de groupe ou un partenariat ? Écrivez-nous."),
                T("Send", "Envoyer"),
                T("Thank you. Our team answers within two working days.", "Merci. Notre équipe vous répond sous deux jours ouvrés."),
                contact_fields(privacy_uuid),
                [save_action(), notify_action(TEAM["contact"], T("New contact request from ${email}", "Nouvelle demande de ${email}"),
                                              T("<p>${firstName} ${lastName} (${email}) wrote about ${topic}:</p><p>${message}</p>",
                                                "<p>${firstName} ${lastName} (${email}) a écrit au sujet de ${topic} :</p><p>${message}</p>"))])


DIETARY = [("none", T("No particular need", "Aucun besoin particulier")), ("vegetarian", T("Vegetarian", "Végétarien")),
           ("halal", T("Halal", "Halal")), ("gluten", T("Gluten free", "Sans gluten")), ("other", T("Other", "Autre"))]


def seed_event(folder, sessions_uuid, privacy_uuid):
    fields = [
        mapped(email("email", T("Email", "E-mail"), required=True), "email", then="readOnly", strategy="setIfMissing"),
        mapped(text("firstName", T("First name", "Prénom"), required=True, autocomplete="given-name"), "firstName"),
        mapped(text("lastName", T("Last name", "Nom"), required=True, autocomplete="family-name"), "lastName"),
        mapped(text("company", T("Company", "Entreprise"), autocomplete="organization"), "company", strategy="setIfMissing"),
        content_choice("sessions", "fmdb:checkbox", T("Sessions you will attend", "Sessions auxquelles vous assisterez"),
                       sessions_uuid, "jnt:text", required=True,
                       help_=T("The list follows the sessions the events team publishes.",
                               "La liste suit les sessions publiées par l'équipe événements.")),
        manual_choice("dietary", "fmdb:select", T("Dietary needs", "Régime alimentaire"), DIETARY,
                      empty=T("Choose one", "Choisissez")),
        when_choice(text("dietaryOther", T("Please specify", "Précisez")), "evt-dietary-other", "dietary", "fmdb:select", ["other"]),
        rating("lastTime", T("How did you like our last event?", "Comment avez-vous trouvé notre dernier événement ?"),
               T("Not at all", "Pas du tout"), T("Very much", "Beaucoup")),
        consent("consent", T("I agree to receive the practical information for this event.",
                             "J'accepte de recevoir les informations pratiques de cet événement."),
                privacy_uuid, T("Privacy notice", "Politique de confidentialité")),
    ]
    return form(folder, "event-registration", T("Partner day registration", "Inscription à la journée partenaires"),
                T("Join the Skylantern partner day at our Hong Kong base. Places are limited.",
                  "Rejoignez la journée partenaires Skylantern à notre base de Hong Kong. Places limitées."),
                T("Register", "S'inscrire"),
                T("You are registered. The practical details follow by e-mail.", "Vous êtes inscrit. Les informations pratiques suivent par e-mail."),
                fields, [save_action(), content_action(TEAM["events"])]
                + pdf_copy_action(T("Keep this copy: it is your proof of registration. Questions: events@skylantern.example.",
                                    "Conservez cette copie : c'est votre preuve d'inscription. Questions : events@skylantern.example.")))


CLAIM_TYPES = [("delayed", T("My bag arrived late", "Mon bagage est arrivé en retard")),
               ("damaged", T("My bag was damaged", "Mon bagage a été endommagé")),
               ("lost", T("My bag did not arrive", "Mon bagage n'est pas arrivé"))]
CHANNELS = [("email", T("By e-mail", "Par e-mail")), ("phone", T("By phone", "Par téléphone"))]


def seed_claim(folder, airports_uuid, privacy_uuid, name="baggage-claim", title=None, extra=None):
    trip = step("trip", T("Your trip", "Votre voyage"), [
        text("bookingReference", T("Booking reference", "Référence de réservation"), required=True, minLength=6, maxLength=6,
             pattern="[A-Z0-9]{6}", placeholder=T("e.g. K7Q2ZP", "ex. K7Q2ZP"),
             help=T("Six letters and digits, on your boarding pass.", "Six lettres et chiffres, sur votre carte d'embarquement."),
             patternMessage=T("Six capital letters or digits, e.g. K7Q2ZP", "Six majuscules ou chiffres, ex. K7Q2ZP")),
        date_field("flightDate", T("Flight date", "Date du vol"), required=True, min_relative=(-6, "months"), max_today=True,
                   help_=T("Claims are accepted up to six months after the flight.", "Les réclamations sont acceptées jusqu'à six mois après le vol.")),
        with_flight_check(text("flightNumber", T("Flight number", "Numéro de vol"), required=True,
                               pattern="[A-Za-z0-9]{2} ?[0-9]{1,4}[A-Za-z]?", placeholder=T("e.g. SL812 or CX251", "ex. SL812 ou CX251"),
                               help=T("As on your boarding pass: the airline's two characters, then the digits.",
                                      "Comme sur votre carte d'embarquement : les deux caractères de la compagnie, puis les chiffres."),
                               patternMessage=T("Two letters or digits, then 1 to 4 digits, e.g. SL812",
                                                "Deux lettres ou chiffres, puis 1 à 4 chiffres, ex. SL812"))),
        text("departureAirport", T("Departure airport", "Aéroport de départ"),
             help=T("Filled in from your flight once its date and number are given.",
                    "Rempli d'après votre vol une fois sa date et son numéro saisis.")),
        category_choice("arrivalAirport", "fmdb:select", T("Arrival airport", "Aéroport d'arrivée"), airports_uuid, required=True,
                        empty=T("Choose an airport", "Choisissez un aéroport")),
        when_choice(text("arrivalOther", T("Your arrival airport", "Votre aéroport d'arrivée"),
                         placeholder=T("e.g. London Heathrow (LHR)", "ex. Londres Heathrow (LHR)")),
                    "claim-arrival-other", "arrivalAirport", "fmdb:select", ["other"]),
        when_url_param(text("memberNumber", T("Lantern Club number", "Numéro Lantern Club"),
                            help=T("Members get their claim handled first.", "Les membres sont traités en priorité.")),
                       "claim-member", "member", "1"),
    ])
    claim = step("claim", T("Your claim", "Votre réclamation"), [
        manual_choice("claimType", "fmdb:radio", T("What happened?", "Que s'est-il passé ?"), CLAIM_TYPES, required=True),
        when_choice(fieldset("damage", T("The damage", "Les dommages"), [
            file_field("photos", T("Photos of the damage", "Photos des dommages"), ["image/jpeg", "image/png", "application/pdf"],
                       multiple=True, help_=T("JPEG, PNG or PDF, up to 10 files.", "JPEG, PNG ou PDF, jusqu'à 10 fichiers.")),
            number("repairEstimate", T("Repair estimate (HKD)", "Devis de réparation (HKD)"), min_value=0, step=1),
        ]), "claim-damaged", "claimType", "fmdb:radio", ["damaged"]),
        when_choice(fieldset("bag", T("Your bag", "Votre bagage"), [
            base("bagColour", "fmdb:inputColor", T("Colour", "Couleur")),
            text("bagBrand", T("Brand", "Marque")),
            textarea("bagDescription", T("Anything that sets it apart", "Ce qui le distingue"), rows=3),
        ]), "claim-lost", "claimType", "fmdb:radio", ["lost"]),
        when_choice(fieldset("delivery", T("Where should we deliver it?", "Où devons-nous le livrer ?"), [
            text("street", T("Address", "Adresse"), autocomplete="street-address"),
            text("city", T("City", "Ville"), autocomplete="address-level2"),
        ]), "claim-delayed", "claimType", "fmdb:radio", ["delayed"]),
    ])
    contact = step("contact", T("Your details", "Vos coordonnées"), [
        text("firstName", T("First name", "Prénom"), required=True, autocomplete="given-name"),
        text("lastName", T("Last name", "Nom"), required=True, autocomplete="family-name"),
        email("email", T("Email", "E-mail"), required=True),
        manual_choice("channel", "fmdb:radio", T("How should we reach you?", "Comment vous joindre ?"), CHANNELS, required=True),
        when_choice(text("phone", T("Phone number", "Numéro de téléphone"), autocomplete="tel", placeholder=T("+852 0000 0000", "+852 0000 0000")),
                    "claim-phone", "channel", "fmdb:radio", ["phone"]),
        consent("consent", T("I agree that Skylantern Airways uses these details to handle my claim.",
                             "J'accepte que Skylantern Airways utilise ces informations pour traiter ma réclamation."),
                privacy_uuid, T("Privacy notice", "Politique de confidentialité")),
    ])
    return form(folder, name, title or T("Baggage claim", "Réclamation bagage"),
                T("Tell us what happened to your bag. It takes about three minutes.",
                  "Dites-nous ce qui est arrivé à votre bagage. Comptez environ trois minutes."),
                T("Submit my claim", "Envoyer ma réclamation"),
                T("Your claim is registered. Our baggage team answers within three working days.",
                  "Votre réclamation est enregistrée. Notre service bagages vous répond sous trois jours ouvrés."),
                [trip, claim, contact],
                [save_action(), notify_action(TEAM["claims"], T("Baggage claim ${bookingReference}: ${claimType}", "Réclamation bagage ${bookingReference} : ${claimType}"),
                                              T("<p>${firstName} ${lastName} (${email}), flight ${flightNumber} on ${flightDate}.</p>",
                                                "<p>${firstName} ${lastName} (${email}), vol ${flightNumber} du ${flightDate}.</p>")),
                 content_action(TEAM["claims"])]
                + pdf_copy_action(T("Keep this copy and quote its reference when you contact our baggage team: baggage@skylantern.example.",
                                    "Conservez cette copie et citez sa référence en contactant notre service bagages : baggage@skylantern.example.")),
                ["fmdbmix:captcha"], extra)


RESTYLED_CSS = HERE / "baggage-claim-restyled.css"


def seed_restyled_claim(home, folder, airports_uuid, privacy_uuid):
    """A copy of the claim form whose Custom CSS restyles it as a boarding pass, on a hidden page of its own.

    The copy keeps the real claim form, its answers and its analytics untouched during the styling scene. Its CSS is
    baggage-claim-restyled.css, written again on every run when the file changed (the demo owns that value).
    """
    css = RESTYLED_CSS.read_text(encoding="utf-8")
    restyled = page(home, "baggage-claim-restyled", T("Baggage claim (restyled)", "Réclamation bagage (restylée)"),
                    T("The baggage claim form restyled through its CSS variables, for the styling scene of the Formidable demo.",
                      "Le formulaire de réclamation restylé par ses variables CSS, pour la scène de style de la démo Formidable."),
                    hidden=True)
    path = seed_claim(folder, airports_uuid, privacy_uuid, "baggage-claim-restyled",
                      T("Baggage claim", "Réclamation bagage"), [{"name": "css", "value": css}])
    place_form(restyled, path)
    if path not in CREATED and prop_value(path, "css") != css:
        set_props(path, [{"name": "css", "value": css}])
        publish(path)
        print(f"updated the Custom CSS of {path}")


def main():
    argparse.ArgumentParser(description=__doc__.splitlines()[0]).parse_args()
    site = ensure_site()
    ensure_jexperience_settings()
    home = f"{site}/home"
    airports = ensure_airports()
    sessions = ensure_sessions(site)
    privacy = page(home, "privacy", T("Privacy notice", "Politique de confidentialité"),
                   T("How Skylantern Airways, a fictional airline, handles the details you give in its forms.",
                     "Comment Skylantern Airways, compagnie fictive, traite les informations saisies dans ses formulaires."), hidden=True)
    folder = add(f"{site}/contents", "forms", "jnt:contentFolder", i18n("jcr:title", T("Forms", "Formulaires")))
    privacy_uuid = uuid_at(privacy)

    page(home, "contact-us", T("Contact us", "Nous contacter"),
         T("Write to Skylantern Airways about a flight, a group booking or a partnership.",
           "Écrivez à Skylantern Airways au sujet d'un vol, d'une réservation de groupe ou d'un partenariat."))
    ready = page(home, "contact-us-ready", T("Contact us (ready)", "Nous contacter (prêt)"),
                 T("The finished contact form, kept as the fallback of the live build in the Formidable demo.",
                   "Le formulaire de contact terminé, gardé en secours de la construction en direct de la démo."), hidden=True)
    place_form(ready, seed_contact(folder, privacy_uuid))
    event = page(home, "partner-day", T("Partner day", "Journée partenaires"),
                 T("Register for the Skylantern partner day: sessions, lounge tour and the new cabin.",
                   "Inscrivez-vous à la journée partenaires Skylantern : sessions, visite du salon et nouvelle cabine."))
    place_form(event, seed_event(folder, uuid_at(sessions), privacy_uuid))
    claim = page(home, "baggage-claim", T("Baggage claim", "Réclamation bagage"),
                 T("Report a delayed, damaged or lost bag to Skylantern Airways and follow up your claim.",
                   "Signalez à Skylantern Airways un bagage retardé, endommagé ou perdu et suivez votre réclamation."))
    place_form(claim, seed_claim(folder, uuid_at(airports), privacy_uuid))
    seed_restyled_claim(home, folder, uuid_at(airports), privacy_uuid)

    if CREATED:
        publish(site)
    print(f"{len(CREATED)} node(s) created" if CREATED else "nothing to create")


if __name__ == "__main__":
    main()
