package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.domain.prestations.droits.ControleDroitsService;
import ci.cnam.simulator.domain.prestations.etapes.ConsultationEtape;
import ci.cnam.simulator.domain.prestations.etapes.Etape;
import ci.cnam.simulator.domain.prestations.etapes.ExamensEtape;
import ci.cnam.simulator.domain.prestations.etapes.FinalisationEtape;
import ci.cnam.simulator.domain.prestations.etapes.HospitalisationEtape;
import ci.cnam.simulator.domain.prestations.etapes.OuvertureFactureEtape;
import ci.cnam.simulator.domain.prestations.etapes.PharmacieEtape;
import ci.cnam.simulator.domain.prestations.etapes.PrescriptionEtape;
import ci.cnam.simulator.domain.prestations.etapes.VerificationDroitsEtape;
import ci.cnam.simulator.domain.prestations.facture.GestionFacture;
import ci.cnam.simulator.domain.prestations.tarification.RegleTaux;
import ci.cnam.simulator.domain.prestations.tarification.TarificationService;
import ci.cnam.simulator.domain.prestations.tarification.TypePrestation;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Genre;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import ci.cnam.simulator.domain.reference.model.TypeCentreSante;
import ci.cnam.simulator.sink.EventSink;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acteurs fictifs et assemblage d'un orchestrateur sans Spring, pour les tests de parcours. */
final class ParcoursFixtures {

    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
    static final CentreSante CENTRE = new CentreSante(new UUID(1, 1), "CS-001", "Centre de test",
            TypeCentreSante.values()[0], "Abidjan");
    static final Medecin MEDECIN = new Medecin(new UUID(2, 2), "MED-001", "Kone", "Awa", "Generaliste",
            CENTRE.id());
    static final Pharmacie PHARMACIE = new Pharmacie(new UUID(3, 3), "PH-001", "Pharmacie de test", "Abidjan");
    static final Catalogue CATALOGUE = new CatalogueEnMemoire();
    /** Taux de test, propres aux tests (jamais ceux de la base). */
    static final TarificationService TARIFICATION = new TarificationService(new RegleTaux(
            Map.of(TypePrestation.CONSULTATION, new BigDecimal("0.70"), TypePrestation.EXAMEN, new BigDecimal("0.80"),
                    TypePrestation.HOSPITALISATION, new BigDecimal("0.90"),
                    TypePrestation.MEDICAMENT, new BigDecimal("0.65"), TypePrestation.DENTAIRE, new BigDecimal("0.50")),
            new BigDecimal("0.50")));

    private ParcoursFixtures() {
    }

    static Assure assure(StatutCotisation cotisation, LocalDate debut, LocalDate fin) {
        return new Assure(new UUID(4, 4), "A-0001", "Traore", "Moussa", LocalDate.of(1990, 5, 17), Genre.M,
                cotisation, debut, fin);
    }

    static Assure assureValide() {
        return assure(StatutCotisation.A_JOUR, LocalDate.of(2020, 1, 1), null);
    }

    static ParcoursOrchestrator orchestrateur(GestionFacture factures, EventSink sink, ParcoursProperties proprietes) {
        List<Etape> etapes = List.of(
                new VerificationDroitsEtape(new ControleDroitsService(), factures),
                new OuvertureFactureEtape(factures),
                new ConsultationEtape(factures, CATALOGUE, TARIFICATION),
                new ExamensEtape(factures, CATALOGUE, TARIFICATION),
                new HospitalisationEtape(factures, CATALOGUE, TARIFICATION),
                new PrescriptionEtape(factures, CATALOGUE, TARIFICATION),
                new PharmacieEtape(factures, CATALOGUE, TARIFICATION),
                new FinalisationEtape(factures));
        return new ParcoursOrchestrator(etapes, new ParcoursGraph(proprietes), sink, HORLOGE);
    }
}
