package org.jahia.modules.formidable.pdf.model;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptReaderTest {

    private static final Locale FRENCH = Locale.FRENCH;

    /** A form element named {@code name}, titled in French, of the given mixins, with its children. */
    private static JCRNodeWrapper node(String name, String title, List<String> mixins, JCRNodeWrapper... children) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        for (String mixin : mixins) {
            when(node.isNodeType(mixin)).thenReturn(true);
        }
        Node translation = mock(Node.class);
        Property titleProperty = mock(Property.class);
        when(titleProperty.getString()).thenReturn(title);
        when(translation.hasProperty("jcr:title")).thenReturn(true);
        when(translation.getProperty("jcr:title")).thenReturn(titleProperty);
        when(node.hasI18N(FRENCH)).thenReturn(true);
        when(node.getI18N(FRENCH)).thenReturn(translation);
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        Iterator<JCRNodeWrapper> source = List.of(children).iterator();
        when(iterator.hasNext()).thenAnswer(call -> source.hasNext());
        when(iterator.nextNode()).thenAnswer(call -> source.next());
        when(node.getNodes()).thenReturn(iterator);
        return node;
    }

    private static void options(JCRNodeWrapper field, String... json) throws RepositoryException {
        Node translation = field.getI18N(FRENCH);
        Value[] values = new Value[json.length];
        for (int i = 0; i < json.length; i++) {
            values[i] = mock(Value.class);
            when(values[i].getString()).thenReturn(json[i]);
        }
        Property property = mock(Property.class);
        when(property.getValues()).thenReturn(values);
        when(translation.hasProperty("options")).thenReturn(true);
        when(translation.getProperty("options")).thenReturn(property);
    }

    @Test
    void theAnswersAreReadUnderTheVisitorsLabelsInTheFormsGroups() throws RepositoryException {
        // Verifies the content of a claim: option labels, yes/no, files, groups, and nothing for an unanswered field.
        List<String> element = List.of(FmdbMixin.FORM_ELEMENT);
        JCRNodeWrapper claimType = node("claimType", "Que s'est-il passé ?", List.of(FmdbMixin.FORM_ELEMENT, FmdbMixin.CHOICE_FIELD));
        options(claimType, "{\"value\":\"delayed\",\"label\":\"Mon bagage est arrivé en retard\"}", "{\"value\":\"damaged\",\"label\":\"Mon bagage a été endommagé\"}", "not json");
        JCRNodeWrapper photos = node("photos", "Photos des dommages", element);
        JCRNodeWrapper estimate = node("repairEstimate", "Devis de réparation (HKD)", element);
        JCRNodeWrapper damage = node("damage", "Les dommages", List.of(FmdbMixin.FORM_CONTAINER, FmdbMixin.FORM_ELEMENT), photos, estimate);
        JCRNodeWrapper bagBrand = node("bagBrand", "Marque", element);
        JCRNodeWrapper bag = node("bag", "Votre bagage", List.of(FmdbMixin.FORM_CONTAINER, FmdbMixin.FORM_ELEMENT), bagBrand);
        JCRNodeWrapper claim = node("claim", "Votre réclamation", List.of(FmdbMixin.FORM_CONTAINER), claimType, damage, bag);
        JCRNodeWrapper consent = node("consent", "Consentement", List.of(FmdbMixin.FORM_ELEMENT, FmdbMixin.BOOLEAN_FIELD));
        JCRNodeWrapper email = node("email", "E-mail", element);
        JCRNodeWrapper flightDate = node("flightDate", "Date du vol", List.of(FmdbMixin.FORM_ELEMENT, FmdbMixin.DATE_FIELD));
        JCRNodeWrapper airport = node("arrivalAirport", "Aéroport d'arrivée", List.of(FmdbMixin.FORM_ELEMENT, FmdbMixin.CHOICE_FIELD));
        JCRNodeWrapper airports = node("skylantern-airports", "Aéroports", List.of());
        JCRNodeWrapper hongKong = node("hkg", "Hong Kong (HKG)", List.of());
        when(airports.hasNode("hkg")).thenReturn(true);
        when(airports.getNode("hkg")).thenReturn(hongKong);
        JCRPropertyWrapper root = mock(JCRPropertyWrapper.class);
        when(root.getNode()).thenReturn(airports);
        when(airport.hasProperty("optionsRootCategory")).thenReturn(true);
        when(airport.getProperty("optionsRootCategory")).thenReturn(root);
        JCRNodeWrapper fields = node("fields", "Champs", List.of(), email, flightDate, airport, claim, consent);
        JCRNodeWrapper form = node("baggage-claim", "Réclamation bagage", List.of());
        when(form.getIdentifier()).thenReturn("form-uuid");
        when(form.hasNode("fields")).thenReturn(true);
        when(form.getNode("fields")).thenReturn(fields);

        Map<String, List<String>> parameters = Map.of("email", List.of("camille@example.com"), "claimType", List.of("damaged"),
                "repairEstimate", List.of("850"), "consent", List.of("true"), "bagBrand", List.of(" "), "unknown", List.of("ignored"),
                "flightDate", List.of("2026-11-15"), "arrivalAirport", List.of("hkg"));
        List<SubmittedFile> files = List.of(new SubmittedFile("photos", "poignée.jpg", "image/jpeg", new byte[46004]));
        FormActionContext context = new FormActionContext(mock(JCRNodeWrapper.class), form, mock(HttpServletRequest.class), null, null,
                FRENCH, "skylantern-forms", parameters, Map.of(), files);

        ReceiptContent content = ReceiptReader.read(context, null, "Une note", Instant.parse("2026-11-16T09:30:00Z"), ZoneId.of("Europe/Paris"));

        assertEquals("Réclamation bagage", content.title());
        assertEquals("Une note", content.note());
        assertEquals(List.of("", "Votre réclamation", "Votre réclamation / Les dommages"), content.sections().stream().map(ReceiptContent.Section::title).toList());
        assertEquals(List.of(new ReceiptContent.Entry("E-mail", List.of("camille@example.com")), new ReceiptContent.Entry("Date du vol", List.of("15 novembre 2026")),
                        new ReceiptContent.Entry("Aéroport d'arrivée", List.of("Hong Kong (HKG)")), new ReceiptContent.Entry("Consentement", List.of("Oui"))),
                content.sections().get(0).entries());
        assertEquals(List.of(new ReceiptContent.Entry("Que s'est-il passé ?", List.of("Mon bagage a été endommagé"))), content.sections().get(1).entries());
        assertEquals(List.of(new ReceiptContent.Entry("Photos des dommages", List.of("poignée.jpg (45 KB)")),
                new ReceiptContent.Entry("Devis de réparation (HKD)", List.of("850"))), content.sections().get(2).entries());
        assertEquals("Référence", content.meta().get(0).label());
        assertEquals(Fingerprint.reference(content.fingerprint()), content.meta().get(0).values().get(0));
        assertTrue(content.meta().get(1).values().get(0).contains("10:30") && content.meta().get(1).values().get(0).contains("Europe/Paris"),
                "the visitor's own time: " + content.meta().get(1).values());
        assertEquals(Fingerprint.of("form-uuid", parameters, List.of(new Fingerprint.FileFact("photos", "poignée.jpg", 46004))), content.fingerprint());
    }
}
