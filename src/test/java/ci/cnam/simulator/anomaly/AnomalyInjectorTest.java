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
    private static final Set<String> CLES_REFERENCE = Set.of("medecinId", "centreSanteId", "pharmacieId");

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
                if (AnomalyType.valueOf(t.type()).doublon()) {
                    SimEvent original = parId.get(t.doublonDe());
                    assertThat(original).isNotNull();
                    assertThat(e.eventId()).isNotEqualTo(original.eventId());
                    assertThat(e.type()).isEqualTo(original.type());
                    assertThat(sortie).contains(original);
                    if (t.champ() == null) {
                        // Doublon exact : la ligne de trop est identique a l'originale.
                        assertThat(e.payload()).isEqualTo(original.payload());
                    } else {
                        // Doublon approximatif : un seul champ varie, et la trace dit lequel.
                        Map<String, Object> attenduDoublon = new java.util.LinkedHashMap<>(original.payload());
                        attenduDoublon.put(t.champ(), t.valeurCorrompue());
                        assertThat(e.payload()).isEqualTo(attenduDoublon);
                        assertThat(t.valeurOrigine()).isEqualTo(String.valueOf(original.payload().get(t.champ())));
                    }
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
                // Les cles de rattachement sont intactes -- sauf pour REFERENCE_BRISEE, dont c'est l'objet.
                if (!t.type().equals(AnomalyType.REFERENCE_BRISEE.name())) {
                    CLES.forEach(cle -> assertThat(e.payload().get(cle)).isEqualTo(propre.payload().get(cle)));
                }
            }
        }
        assertThat(verifiees).isGreaterThan(500);
    }

    @Test
    void tousLesTypesDeGenerationSontInjectesEtAucunTypeDeFichier() {
        List<SimEvent> sortie = injecter(EvenementsDeTest.propres(RUN, 8, 20_000), pourcent("global", 50));

        Set<String> vus = new java.util.TreeSet<>();
        traces(sortie).forEach(t -> vus.add(t.type()));
        Set<String> attendus = new java.util.TreeSet<>();
        // Les 15 types poses a la generation. Les deux anomalies de fichier n'arrivent qu'a l'export : si
        // elles apparaissaient ici, c'est que la base serait abimee, ce qui est exactement ce qu'on evite.
        EnumSet.allOf(AnomalyType.class).stream().filter(t -> !t.fichier()).forEach(t -> attendus.add(t.name()));
        assertThat(vus).isEqualTo(attendus);
    }

    @Test
    void leDoublonApproximatifAjouteUneLigneQuiPointeVersLOriginale() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 3, 2_000);

        List<SimEvent> sortie = injecter(propres, pourcent("doublonApproximatif", 100));

        List<AnomalieTracee> doublons = traces(sortie);
        assertThat(doublons).isNotEmpty();
        // Une ligne de plus par doublon, et pas une de moins : les originales sont toutes la, intactes.
        assertThat(sortie).hasSize(propres.size() + doublons.size()).containsAll(propres);
        Map<UUID, SimEvent> parId = new java.util.HashMap<>();
        propres.forEach(e -> parId.put(e.eventId(), e));
        for (AnomalieTracee t : doublons) {
            assertThat(t.type()).isEqualTo(AnomalyType.DOUBLON_APPROXIMATIF.name());
            assertThat(t.doublonDe()).as("le corrige renseigne doublon_de").isNotNull();
            assertThat(parId.get(t.doublonDe())).as("et il pointe vers l'originale").isNotNull();
            assertThat(t.champ()).isIn("nom", "prenoms");
            assertThat(t.valeurCorrompue()).isNotEqualTo(t.valeurOrigine());
        }
    }

    @Test
    void laReferenceBriseeViseUneCleAvecUnIdentifiantBienFormeEtInconnu() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 4, 2_000);
        Set<Object> referentiel = new java.util.HashSet<>();
        propres.forEach(e -> CLES_REFERENCE.forEach(cle -> {
            if (e.payload().get(cle) != null) {
                referentiel.add(e.payload().get(cle));
            }
        }));

        List<SimEvent> sortie = injecter(propres, pourcent("referenceBrisee", 100));

        List<AnomalieTracee> brisees = traces(sortie);
        assertThat(brisees).isNotEmpty();
        for (AnomalieTracee t : brisees) {
            assertThat(t.champ()).isIn("medecinId", "centreSanteId", "pharmacieId");
            // Bien forme : un UUID, comme les identifiants du referentiel. Et introuvable dans celui-ci.
            UUID invente = UUID.fromString(t.valeurCorrompue());
            assertThat(invente.version()).isEqualTo(4);
            assertThat(referentiel).doesNotContain(invente);
        }
    }

    @Test
    void memeGraineMemeProfilMemeCorrigePourLesSixNouveauxTypes() {
        for (String cle : List.of("doublonApproximatif", "referenceBrisee", "incoherenceTemporelle",
                "incoherenceCroisee", "caracteresCasses", "formatDateFichier")) {
            List<SimEvent> premier = injecter(EvenementsDeTest.propres(RUN, 77, 1_000), pourcent(cle, 40));
            List<SimEvent> second = injecter(EvenementsDeTest.propres(RUN, 77, 1_000), pourcent(cle, 40));

            assertThat(second).as("deux runs de meme graine, meme profil : " + cle).isEqualTo(premier);
        }
    }

    @Test
    void lesDeuxAnomaliesDeFichierNeTouchentJamaisLeFluxNiLaBase() {
        List<SimEvent> propres = EvenementsDeTest.propres(RUN, 6, 2_000);

        for (String cle : List.of("caracteresCasses", "formatDateFichier")) {
            // Taux a 100 % : si ces deux types pouvaient abimer un evenement, cela se verrait ici.
            assertThat(injecter(propres, pourcent(cle, 100))).isEqualTo(propres);
        }
    }

    @Test
    void lIncoherenceTemporelleSortLaDateDeSoinDeLaPeriodeDeDroits() {
        List<SimEvent> sortie = injecter(EvenementsDeTest.propres(RUN, 12, 1_000),
                pourcent("incoherenceTemporelle", 100));

        List<AnomalieTracee> traces = traces(sortie);
        assertThat(traces).isNotEmpty();
        for (AnomalieTracee t : traces) {
            assertThat(t.champ()).isEqualTo("dateSoin");
            java.time.LocalDate corrompue = java.time.LocalDate.parse(t.valeurCorrompue());
            assertThat(corrompue).isNotEqualTo(java.time.LocalDate.parse(t.valeurOrigine()));
            // Hors de la periode de droits que les evenements de test portent (2024-01-01 .. 2026-12-31),
            // ou alors dans le futur par rapport au soin : dans les deux cas, la relation est fausse.
            assertThat(corrompue.isBefore(java.time.LocalDate.of(2024, 1, 1))
                    || corrompue.isAfter(java.time.LocalDate.of(2026, 3, 10))).isTrue();
        }
    }

    @Test
    void lIncoherenceCroiseeFermeLaPeriodeAlorsQueLesDroitsRestentActifs() {
        List<SimEvent> sortie = injecter(EvenementsDeTest.propres(RUN, 13, 1_000),
                pourcent("incoherenceCroisee", 100));

        List<AnomalieTracee> traces = traces(sortie);
        assertThat(traces).isNotEmpty();
        for (SimEvent e : sortie) {
            for (AnomalieTracee t : e.anomalies()) {
                assertThat(t.champ()).isEqualTo("dateFinDroits");
                // Chaque champ reste valide seul ; c'est leur conjonction qui ne tient pas.
                assertThat(e.payload().get("droitsOuverts")).isEqualTo(true);
                assertThat(java.time.LocalDate.parse(t.valeurCorrompue()))
                        .isBefore(java.time.LocalDate.parse(String.valueOf(e.payload().get("dateSoin"))));
            }
        }
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
