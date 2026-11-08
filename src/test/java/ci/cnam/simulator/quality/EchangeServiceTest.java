package ci.cnam.simulator.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.engine.RunManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les trois echecs d'echange, sans base. Ce qui est verifie ici : aucun des trois ne produit de
 * chiffre de detection, et surtout pas un taux de 0 %.
 */
class EchangeServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");
    private static final int DELAI = 60;

    /** Journal en memoire : la seule chose que la base apporte en plus, c'est la durabilite. */
    private static final class Journal implements EchangeStore {
        private final List<Echange> lignes = new ArrayList<>();

        @Override
        public void tracer(Echange echange) {
            lignes.add(echange);
        }

        @Override
        public List<Echange> journal(UUID runId) {
            return lignes.stream().filter(e -> e.runId().equals(runId)).toList();
        }
    }

    private final Journal journal = new Journal();

    private EchangeService service(Instant maintenant) {
        return new EchangeService(journal, Clock.fixed(maintenant, ZoneOffset.UTC),
                new SimulateurProperties(42, "scenarios", null, null, null, null,
                        new SimulateurProperties.Qualite(DELAI)));
    }

    @Test
    @DisplayName("Aucun echange : rien a dire, ce n'est pas un echec")
    void aucunEchange() {
        assertThat(service(T0).etat(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Jeu retire, rapport attendu : aucun chiffre, et ce n'est pas encore un echec")
    void attente() {
        UUID run = UUID.randomUUID();
        service(T0).tracerRetrait(run, "csv");

        EtatEchange etat = service(T0.plus(Duration.ofMinutes(DELAI - 1))).etat(run).orElseThrow();

        assertThat(etat.statut()).isEqualTo(StatutEchange.ATTENTE_RAPPORT);
        assertThat(etat.echec()).isFalse();
        assertThat(etat.mesurable()).isFalse();
        assertThat(etat.libelle()).isEqualTo("En attente du script");
    }

    @Test
    @DisplayName("ECHEC 1 - Jeu retire, aucun rapport passe le delai : SILENCE, et AUCUN taux (pas meme 0 %)")
    void silence() {
        UUID run = UUID.randomUUID();
        service(T0).tracerRetrait(run, "Jeu retire au format CSV");

        EchangeService apres = service(T0.plus(Duration.ofMinutes(DELAI)));
        EtatEchange etat = apres.etat(run).orElseThrow();
        assertThat(etat.statut()).isEqualTo(StatutEchange.SILENCE);
        assertThat(etat.echec()).isTrue();
        assertThat(etat.detail()).contains("aucun rapport").contains("60 minutes");

        // 100 anomalies semees, aucune detection : le piege serait d'afficher 0 % de detection.
        ComparaisonQualite c = comparaison(apres, run);
        assertThat(c.mesure()).isFalse();
        assertThat(c.total()).isNull();
        assertThat(c.types()).isEmpty();
        assertThat(c.categories()).isEmpty();
        assertThat(c.echecEchange()).isNotNull();
        assertThat(c.echecEchange().statut()).isEqualTo(StatutEchange.SILENCE);
    }

    @Test
    @DisplayName("ECHEC 2 - Envoi refuse en bloc : RAPPORT_MALFORME, aucun chiffre produit")
    void rapportMalforme() {
        UUID run = UUID.randomUUID();
        EchangeService service = service(T0);
        service.tracerRetrait(run, "csv");
        service.tracerRapportMalforme(run, "outil-1.0", "Envoi refuse : 3 detection(s) invalide(s)");

        ComparaisonQualite c = comparaison(service, run);

        assertThat(c.echecEchange().statut()).isEqualTo(StatutEchange.RAPPORT_MALFORME);
        assertThat(c.mesure()).isFalse();
        assertThat(c.total()).isNull();
    }

    @Test
    @DisplayName("ECHEC 3 - Rapport sans detail exploitable : RAPPORT_SANS_DETAIL, aucun chiffre produit")
    void rapportSansDetail() {
        UUID run = UUID.randomUUID();
        EchangeService service = service(T0);
        service.tracerRetrait(run, "csv");
        service.tracerRapportSansDetail(run, "outil-1.0", "12 detection(s), 0 exploitable(s)");

        ComparaisonQualite c = comparaison(service, run);

        assertThat(c.echecEchange().statut()).isEqualTo(StatutEchange.RAPPORT_SANS_DETAIL);
        assertThat(c.mesure()).isFalse();
        assertThat(c.total()).isNull();
    }

    @Test
    @DisplayName("Rapport exploitable recu : la mesure reprend, meme apres un refus precedent")
    void rapportRecu() {
        UUID run = UUID.randomUUID();
        EchangeService service = service(T0);
        service.tracerRetrait(run, "csv");
        service.tracerRapportMalforme(run, "outil-1.0", "refus");
        service.tracerRapport(run, "outil-1.1", "100 detection(s), 100 exploitable(s)");

        ComparaisonQualite c = comparaison(service, run);

        assertThat(c.echange().statut()).isEqualTo(StatutEchange.RAPPORT_RECU);
        assertThat(c.echecEchange()).isNull();
        assertThat(c.mesure()).isTrue();
        assertThat(c.total().injectees()).isEqualTo(100);
    }

    @Test
    @DisplayName("Rapport pousse sans retrait prealable : accepte et trace, la mesure reste possible")
    void rapportSansRetrait() {
        UUID run = UUID.randomUUID();
        EchangeService service = service(T0);
        service.tracerRapport(run, "outil-1.0", "hors planification");

        assertThat(service.journal(run)).singleElement()
                .satisfies(e -> assertThat(e.sens()).isEqualTo(SensEchange.RECEPTION));
        assertThat(comparaison(service, run).mesure()).isTrue();
    }

    @Test
    @DisplayName("Le journal est horodate et garde l'ordre : l'envoi avant la reception")
    void ordreDuJournal() {
        UUID run = UUID.randomUUID();
        service(T0).tracerRetrait(run, "csv");
        service(T0.plusSeconds(90)).tracerRapport(run, "outil-1.0", "ok");

        List<Echange> lignes = service(T0).journal(run);

        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0).sens()).isEqualTo(SensEchange.ENVOI);
        assertThat(lignes.get(0).date()).isEqualTo(T0);
        assertThat(lignes.get(0).versionOutil()).isNull();
        assertThat(lignes.get(1).sens()).isEqualTo(SensEchange.RECEPTION);
        assertThat(lignes.get(1).date()).isEqualTo(T0.plusSeconds(90));
        assertThat(lignes.get(1).versionOutil()).isEqualTo("outil-1.0");
        assertThat(lignes.get(0).date()).isBefore(lignes.get(1).date());
    }

    @Test
    @DisplayName("Une detection sans champ n'est pas exploitable : le cahier exige ligne, champ et type")
    void exploitable() {
        UUID e = UUID.randomUUID();
        assertThat(QualityReportService.exploitable(
                new Detection(e, AnomalyType.CHAMP_VIDE, "nom", null, null))).isTrue();
        assertThat(QualityReportService.exploitable(
                new Detection(e, AnomalyType.CHAMP_VIDE, "  ", null, null))).isFalse();
        assertThat(QualityReportService.exploitable(
                new Detection(e, AnomalyType.DOUBLON_EXACT, null, null, null))).isFalse();
    }

    /** Une comparaison sur un run ou 100 anomalies ont ete semees et aucune detection n'est connue. */
    private static ComparaisonQualite comparaison(EchangeService echanges, UUID run) {
        Map<AnomalyType, Long> cent = new EnumMap<>(AnomalyType.class);
        cent.put(AnomalyType.CHAMP_VIDE, 100L);
        QualityStore store = new QualityStore() {
            @Override public Set<UUID> evenementsDuRun(UUID runId, Collection<UUID> ids) { return Set.of(); }
            @Override public int enregistrer(UUID r, UUID run, String v, List<Detection> d) { return 0; }
            @Override public Map<AnomalyType, Long> injectees(UUID runId) { return cent; }
            @Override public Map<AnomalyType, Long> detectees(UUID runId, String v) { return Map.of(); }
            @Override public Map<AnomalyType, Long> fauxPositifs(UUID runId, String v) { return Map.of(); }
        };
        return new QualityReportService(store, mock(RunManager.class), echanges).comparer(run, null);
    }
}
