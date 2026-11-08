package ci.cnam.simulator.export;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.anomaly.mutators.CaracteresCassesMutator;
import ci.cnam.simulator.anomaly.mutators.FormatDateFichierMutator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Les deux anomalies qui n'existent que dans le fichier servi : posees a l'ecriture, la base restant propre. */
class InjecteurFichierTest {

    private static final UUID RUN = new UUID(7, 7);
    private static final List<String> COLONNES = List.of("event_id", "facture_id", "domaine", "type",
            "timestamp_simule", "timestamp_reel", "statut", "payload");

    private final InjecteurFichier injecteur =
            new InjecteurFichier(List.of(new CaracteresCassesMutator(), new FormatDateFichierMutator()));

    private static List<List<String>> lignesSaines(int nombre) {
        List<List<String>> lignes = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            lignes.add(Arrays.asList(new UUID(4, i).toString(), new UUID(8, i).toString(), "prestations",
                    "FACTURE_OUVERTE", "2026-03-10T08:30:00.000000", "2026-03-10T09:00:00.000000Z", "ECRIT",
                    "{\"nom\": \"N'Guessan\", \"prenoms\": \"Awa\"}"));
        }
        return lignes;
    }

    private static TableExport table(List<List<String>> lignes) {
        return new TableExport("evenements", COLONNES, ligne -> lignes.forEach(ligne::accept));
    }

    private static AnomalyProfile profil(double pourcentFichier) {
        return AnomalyProfile.depuis(Map.of("caracteresCasses", pourcentFichier / 2,
                "formatDateFichier", pourcentFichier / 2));
    }

    private Resultat servir(List<List<String>> saines, AnomalyProfile profil) {
        List<AnomalyRecord> corrige = new ArrayList<>();
        List<List<String>> servies = new ArrayList<>();
        injecteur.pieger(table(saines), RUN, Map.of("prestations", profil), corrige::add)
                .lignes().pour(servies::add);
        return new Resultat(servies, corrige);
    }

    private record Resultat(List<List<String>> servies, List<AnomalyRecord> corrige) {
    }

    @Test
    void lesDeuxAnomaliesApparaissentDansLeFichierEtDansLeCorrige() {
        List<List<String>> saines = lignesSaines(400);

        Resultat r = servir(saines, profil(100));

        assertThat(r.corrige()).isNotEmpty();
        assertThat(r.corrige()).extracting(AnomalyRecord::type)
                .contains(AnomalyType.CARACTERES_CASSES, AnomalyType.FORMAT_DATE_FICHIER);
        for (AnomalyRecord a : r.corrige()) {
            assertThat(a.runId()).isEqualTo(RUN);
            assertThat(a.eventId()).isNotNull();
            assertThat(a.doublonDe()).isNull();
            // La valeur d'origine est la valeur SAINE, celle qui est restee en base.
            if (a.type() == AnomalyType.CARACTERES_CASSES) {
                assertThat(a.champ()).isEqualTo("payload.nom");
                assertThat(a.valeurOrigine()).isEqualTo("N'Guessan");
            } else {
                assertThat(a.champ()).isEqualTo("timestamp_simule");
                assertThat(a.valeurOrigine()).isEqualTo("2026-03-10T08:30:00.000000");
            }
            assertThat(a.valeurCorrompue()).isNotEqualTo(a.valeurOrigine());
            // Et la ligne servie porte bien le piege.
            List<String> servie = r.servies().stream()
                    .filter(l -> l.get(0).equals(a.eventId().toString())).findFirst().orElseThrow();
            assertThat(String.join("|", servie)).contains(a.valeurCorrompue());
        }
    }

    @Test
    void laBaseResteProprePuisqueSeulesLesLignesServiesSontReecrites() {
        List<List<String>> saines = lignesSaines(200);
        List<List<String>> copie = saines.stream().map(ArrayList::new).map(l -> (List<String>) l).toList();

        servir(saines, profil(100));

        assertThat(saines).isEqualTo(copie);
    }

    @Test
    void deuxTelechargementsDonnentExactementLesMemesOctets() {
        List<List<String>> saines = lignesSaines(300);

        Resultat premier = servir(saines, profil(60));
        Resultat second = servir(saines, profil(60));

        assertThat(second.servies()).isEqualTo(premier.servies());
        assertThat(second.corrige()).isEqualTo(premier.corrige());
    }

    @Test
    void sansTauxDeFichierRienNEstPiege() {
        List<List<String>> saines = lignesSaines(100);

        for (AnomalyProfile profil : List.of(AnomalyProfile.AUCUNE,
                AnomalyProfile.depuis(Map.of("champVide", 100.0)))) {
            Resultat r = servir(saines, profil);
            assertThat(r.servies()).isEqualTo(saines);
            assertThat(r.corrige()).isEmpty();
        }
    }

    @Test
    void unDomaineSansProfilNEstPasPiege() {
        List<List<String>> saines = lignesSaines(100);
        List<AnomalyRecord> corrige = new ArrayList<>();
        List<List<String>> servies = new ArrayList<>();

        injecteur.pieger(table(saines), RUN, Map.of("affiliation", profil(100)), corrige::add)
                .lignes().pour(servies::add);

        assertThat(servies).isEqualTo(saines);
        assertThat(corrige).isEmpty();
    }
}
