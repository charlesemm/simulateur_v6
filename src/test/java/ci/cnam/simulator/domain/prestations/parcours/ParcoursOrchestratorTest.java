package ci.cnam.simulator.domain.prestations.parcours;

import static ci.cnam.simulator.domain.prestations.parcours.ParcoursFixtures.CENTRE;
import static ci.cnam.simulator.domain.prestations.parcours.ParcoursFixtures.MEDECIN;
import static ci.cnam.simulator.domain.prestations.parcours.ParcoursFixtures.PHARMACIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.facture.Facture;
import ci.cnam.simulator.domain.prestations.facture.FactureStatut;
import ci.cnam.simulator.domain.prestations.facture.TypeBon;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import ci.cnam.simulator.sink.InMemoryEventSink;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParcoursOrchestratorTest {

    private static final UUID RUN = new UUID(9, 9);
    private static final ParcoursProperties PAR_DEFAUT = new ParcoursProperties(0.60, 0.10, 0.30);

    private ParcoursOrchestrator.Resultat lancer(Assure assure, ParcoursProperties proprietes, long graine) {
        FactureEnMemoire factures = new FactureEnMemoire(graine);
        ParcoursOrchestrator orchestrateur = ParcoursFixtures.orchestrateur(factures, new InMemoryEventSink(), proprietes);
        return orchestrateur.executer(RUN, assure, CENTRE, MEDECIN, PHARMACIE, new SeededRandom(graine));
    }

    private static List<String> types(List<SimEvent> evenements) {
        return evenements.stream().map(SimEvent::type).toList();
    }

    @Test
    void voieDirecteFinitFinaliseeAvecLaSuiteAttendue() {
        var resultat = lancer(ParcoursFixtures.assureValide(), new ParcoursProperties(0, 0, 1), 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(resultat.facture().typeBon()).isEqualTo(TypeBon.AMBULATOIRE);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "DROITS_VERIFIES",
                "FACTURE_OUVERTE", "CONSULTATION_REALISEE", "PRESCRIPTION_EMISE", "MEDICAMENTS_DELIVRES",
                "FACTURE_FINALISEE");
    }

    @Test
    void voieExamensPasseParLesExamensEtPrendUnBonBiologieImagerie() {
        var resultat = lancer(ParcoursFixtures.assureValide(), new ParcoursProperties(1, 0, 0), 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(resultat.facture().typeBon()).isEqualTo(TypeBon.BIOLOGIE_IMAGERIE);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "DROITS_VERIFIES",
                "FACTURE_OUVERTE", "CONSULTATION_REALISEE", "EXAMEN_REALISE", "PRESCRIPTION_EMISE",
                "MEDICAMENTS_DELIVRES", "FACTURE_FINALISEE");
    }

    @Test
    void voieHospitalisationPasseParLeSejourEtPrendUnBonHospitalisation() {
        var resultat = lancer(ParcoursFixtures.assureValide(), new ParcoursProperties(0, 1, 0), 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.FINALISEE);
        assertThat(resultat.facture().typeBon()).isEqualTo(TypeBon.HOSPITALISATION);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "DROITS_VERIFIES",
                "FACTURE_OUVERTE", "CONSULTATION_REALISEE", "HOSPITALISATION_REALISEE", "PRESCRIPTION_EMISE",
                "MEDICAMENTS_DELIVRES", "FACTURE_FINALISEE");
    }

    @Test
    void lesEvenementsApresLOuvertureNommentLaFactureEtLeTempsSimuleNeReculeJamais() {
        var resultat = lancer(ParcoursFixtures.assureValide(), PAR_DEFAUT, 5);
        UUID factureId = resultat.facture().id();

        List<SimEvent> apresOuverture = resultat.evenements().subList(2, resultat.evenements().size());
        assertThat(apresOuverture).allSatisfy(e -> assertThat(e.payload()).containsEntry("factureId", factureId));
        assertThat(resultat.evenements()).allSatisfy(e -> {
            assertThat(e.domaine()).isEqualTo("prestations");
            assertThat(e.runId()).isEqualTo(RUN);
            assertThat(e.anomalies()).isEmpty();
        });
        for (int i = 1; i < resultat.evenements().size(); i++) {
            assertThat(resultat.evenements().get(i).timestampSimule())
                    .isAfterOrEqualTo(resultat.evenements().get(i - 1).timestampSimule());
        }
        assertThat(resultat.facture().dateFinalisation()).isNotNull();
    }

    @Test
    void laFactureFinaleAdditionneSesLignesEtLesTroisPartsFontLeTotal() {
        Facture f = lancer(ParcoursFixtures.assureValide(), PAR_DEFAUT, 3).facture();

        assertThat(f.montantTotal()).isPositive();
        assertThat(f.partCnam().add(f.partAssuranceComplementaire()).add(f.partAssure()))
                .isEqualByComparingTo(f.montantTotal());
    }

    @Test
    void droitsFermesDonnentRefuseeEtAccesRefuse() {
        // Droits termines avant la date de soin (2026-03-10).
        Assure expire = ParcoursFixtures.assure(StatutCotisation.A_JOUR, LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 1, 31));
        var resultat = lancer(expire, PAR_DEFAUT, 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.REFUSEE);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "ACCES_REFUSE");
        SimEvent refus = resultat.evenements().get(1);
        assertThat(refus.payload()).containsEntry("motif", "DROITS_FERMES")
                .containsEntry("factureId", resultat.facture().id());
    }

    @Test
    void droitsPasEncoreOuvertsDonnentRefusee() {
        Assure futur = ParcoursFixtures.assure(StatutCotisation.A_JOUR, LocalDate.of(2026, 6, 1), null);
        var resultat = lancer(futur, PAR_DEFAUT, 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.REFUSEE);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "ACCES_REFUSE");
    }

    @Test
    void cotisationsEnRetardDonnentRefuseeEtAccesRefuse() {
        Assure retard = ParcoursFixtures.assure(StatutCotisation.EN_RETARD, LocalDate.of(2020, 1, 1), null);
        var resultat = lancer(retard, PAR_DEFAUT, 1);

        assertThat(resultat.facture().statut()).isEqualTo(FactureStatut.REFUSEE);
        assertThat(types(resultat.evenements())).containsExactly("PATIENT_ARRIVE", "ACCES_REFUSE");
        assertThat(resultat.evenements().get(1).payload()).containsEntry("motif", "COTISATIONS_EN_RETARD");
    }

    @Test
    void surDixMillePatientsLesVoiesRespectentSoixanteDixTrente() {
        FactureEnMemoire factures = new FactureEnMemoire(11);
        InMemoryEventSink sink = new InMemoryEventSink();
        ParcoursOrchestrator orchestrateur = ParcoursFixtures.orchestrateur(factures, sink, PAR_DEFAUT);
        SeededRandom random = new SeededRandom(2024);
        int examens = 0;
        int hospitalisations = 0;
        int directs = 0;
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            var resultat = orchestrateur.executer(RUN, ParcoursFixtures.assureValide(), CENTRE, MEDECIN, PHARMACIE,
                    random);
            List<String> types = types(resultat.evenements());
            if (types.contains("EXAMEN_REALISE")) {
                examens++;
            } else if (types.contains("HOSPITALISATION_REALISEE")) {
                hospitalisations++;
            } else {
                directs++;
            }
        }
        assertThat(examens + hospitalisations + directs).isEqualTo(total);
        assertThat(100.0 * examens / total).isCloseTo(60.0, within(3.0));
        assertThat(100.0 * hospitalisations / total).isCloseTo(10.0, within(3.0));
        assertThat(100.0 * directs / total).isCloseTo(30.0, within(3.0));
    }

    @Test
    void memeGraineMemesEvenementsEtGraineDifferenteAutreSuite() {
        assertThat(suiteComplete(42)).isEqualTo(suiteComplete(42));
        assertThat(suiteComplete(42)).isNotEqualTo(suiteComplete(43));
    }

    private List<SimEvent> suiteComplete(long graine) {
        FactureEnMemoire factures = new FactureEnMemoire(graine);
        InMemoryEventSink sink = new InMemoryEventSink();
        ParcoursOrchestrator orchestrateur = ParcoursFixtures.orchestrateur(factures, sink, PAR_DEFAUT);
        SeededRandom random = new SeededRandom(graine);
        List<SimEvent> tout = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tout.addAll(orchestrateur.executer(RUN, ParcoursFixtures.assureValide(), CENTRE, MEDECIN, PHARMACIE,
                    random).evenements());
        }
        assertThat(sink.evenements()).isEqualTo(tout); // le sink recoit exactement ce que le parcours renvoie
        return tout;
    }
}
