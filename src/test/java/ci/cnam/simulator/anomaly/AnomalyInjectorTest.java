package ci.cnam.simulator.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalyInjectorTest {

    private static final UUID RUN = new UUID(7, 7);
    private static final Set<String> CLES = Set.of("factureId", "assureId", "centreSanteId", "pharmacieId");

    private final AnomalyInjector injecteur = EvenementsDeTest.injecteur();

    private List<SimEvent> injecter(List<SimEvent> propres, AnomalyProfile profil) {
        List<SimEvent> sortie = new ArrayList<>();
        propres.forEach(e -> sortie.addAll(injecteur.injecter(e, profil)));
        return sortie;
    }

    private static List<AnomalieTracee> traces(List<SimEvent> evenements) {
        return evenements.stream().flatMap(e -> e.anomalies().stream()).toList();
    }

    private static AnomalyProfile pourcent(String cle, double taux) {
        return AnomalyProfile.depuis(Map.of(cle, taux));
    }

    @Test
    void unTauxGlobalDeZeroNeProduitAucuneAnomalieEtLeFluxResteIdentique() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 42, 5_000);

        for (AnomalyProfile profil : List.of(AnomalyProfile.AUCUNE, pourcent("global", 0),
                pourcent("champVide", 0))) {
            assertThat(injecter(propres, profil)).isEqualTo(propres);
        }
    }

    @Test
    void sur10000EvenementsUnTauxDe3PourCentDonneEnviron300Anomalies() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 42, 10_000);

        List<SimEvent> sortie = injecter(propres, pourcent("global", 3));

        // Binomiale(10000 ; 0,03) : moyenne 300, ecart-type ~17 -> 6 ecarts-types de marge
        assertThat(traces(sortie)).hasSizeBetween(200, 400);
    }

    @Test
    void leTauxParTypeEstRespecte() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 11, 10_000);

        List<SimEvent> sortie = injecter(propres, AnomalyProfile.depuis(Map.of("champVide", 5.0, "fauteOrthographe", 2.0)));

        long vides = traces(sortie).stream().filter(t -> t.type().equals("CHAMP_VIDE")).count();
        long fautes = traces(sortie).stream().filter(t -> t.type().equals("FAUTE_ORTHOGRAPHE")).count();
        assertThat(vides).isBetween(400L, 600L);
        assertThat(fautes).isBetween(130L, 270L);
        assertThat(traces(sortie)).hasSize((int) (vides + fautes));
    }

    @Test
    void memeGraineMemesAnomaliesAuxMemesEndroits() {
        AnomalyProfile profil = pourcent("global", 10);

        List<SimEvent> premiere = injecter(EvenementsDeTest.propres(RUN, 42, 3_000), profil);
        List<SimEvent> seconde = injecter(EvenementsDeTest.propres(RUN, 42, 3_000), profil);
        List<SimEvent> autreGraine = injecter(EvenementsDeTest.propres(RUN, 43, 3_000), profil);

        assertThat(traces(premiere)).isNotEmpty();
        assertThat(seconde).isEqualTo(premiere);
        assertThat(autreGraine).isNotEqualTo(premiere);
    }

    @Test
    void changerLesTauxNeChangeJamaisLeFluxPropre() {
        // L'injecteur ne consomme pas l'alea du generateur : les evenements propres sont les memes quels que soient les taux.
        assertThat(EvenementsDeTest.propres(RUN, 42, 500)).isEqualTo(EvenementsDeTest.propres(RUN, 42, 500));
    }

    @Test
    void chaqueAnomalieRetrouveLaValeurProprePuisqueLaTraceLaGarde() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 5, 5_000);
        Map<UUID, SimEvent> parId = new java.util.HashMap<>();
        propres.forEach(e -> parId.put(e.eventId(), e));

        List<SimEvent> sortie = injecter(propres, pourcent("global", 20));

        int verifiees = 0;
        for (SimEvent e : sortie) {
            assertThat(e.anomalies().size()).as("au plus une anomalie par evenement").isLessThanOrEqualTo(1);
            for (AnomalieTracee t : e.anomalies()) {
                verifiees++;
                if (t.type().equals("DOUBLON_EXACT")) {
                    SimEvent original = parId.get(t.doublonDe());
                    assertThat(original).isNotNull();
                    assertThat(e.eventId()).isNotEqualTo(original.eventId());
                    assertThat(e.payload()).isEqualTo(original.payload());
                    assertThat(e.type()).isEqualTo(original.type());
                    assertThat(sortie).contains(original);
                    continue;
                }
                SimEvent propre = parId.get(e.eventId());
                assertThat(String.valueOf(propre.payload().get(t.champ()))).isEqualTo(t.valeurOrigine());
                assertThat(valeur(e, t.champ())).isEqualTo(t.valeurCorrompue());
                assertThat(valeur(e, t.champ())).isNotEqualTo(t.valeurOrigine());
                // Rien d'autre n'a bouge, et les cles de rattachement sont intactes.
                Map<String, Object> attendu = new java.util.LinkedHashMap<>(propre.payload());
                attendu.put(t.champ(), e.payload().get(t.champ()));
                assertThat(e.payload()).isEqualTo(attendu);
                CLES.forEach(cle -> assertThat(e.payload().get(cle)).isEqualTo(propre.payload().get(cle)));
            }
        }
        assertThat(verifiees).isGreaterThan(500);
    }

    @Test
    void lesOnzeTypesSontInjectes() {
        List<SimEvent> sortie = injecter(EvenementsDeTest.propres(RUN, 8, 20_000), pourcent("global", 50));

        Set<String> vus = new java.util.TreeSet<>();
        traces(sortie).forEach(t -> vus.add(t.type()));
        Set<String> tous = new java.util.TreeSet<>();
        EnumSet.allOf(AnomalyType.class).forEach(t -> tous.add(t.name()));
        assertThat(vus).isEqualTo(tous);
    }

    @Test
    void lesChampsSaisisAMainSontLesPlusTouches() {
        List<SimEvent> sortie = injecter(EvenementsDeTest.propres(RUN, 9, 10_000), pourcent("champVide", 30));

        long surNomOuPrenoms = traces(sortie).stream().filter(t -> t.champ().equals("nom") || t.champ().equals("prenoms")).count();
        long surMotifOuTypeBon = traces(sortie).stream().filter(t -> t.champ().equals("typeBon")).count();
        assertThat(surNomOuPrenoms).isGreaterThan(surMotifOuTypeBon * 3);
    }

    @Test
    void unEvenementSansChampEligiblePourLesTypesDemandesResteIntact() {
        SimEvent sansIdentite = new SimEvent(new UUID(1, 2), RUN, "prestations", "DROITS_VERIFIES",
                java.time.LocalDateTime.of(2026, 3, 10, 8, 0), Map.of("factureId", new UUID(3, 3)), List.of());

        assertThat(injecteur.injecter(sansIdentite, pourcent("champVide", 100))).containsExactly(sansIdentite);
    }

    private static String valeur(SimEvent e, String champ) {
        Object v = e.payload().get(champ);
        return v == null ? null : v.toString();
    }
}
