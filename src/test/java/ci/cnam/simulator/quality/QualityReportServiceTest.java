package ci.cnam.simulator.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.engine.RunManager;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Logique du rapport sans base : calcul des taux, categories, refus d'un envoi invalide. */
class QualityReportServiceTest {

    private static Map<AnomalyType, Long> map(Object... paires) {
        Map<AnomalyType, Long> m = new EnumMap<>(AnomalyType.class);
        for (int i = 0; i < paires.length; i += 2) {
            m.put((AnomalyType) paires[i], ((Number) paires[i + 1]).longValue());
        }
        return m;
    }

    @Test
    void jeuConnu_100Injectees_80Detectees_20Ratees_10FauxPositifs_parType() {
        var injectees = map(AnomalyType.CHAMP_VIDE, 40, AnomalyType.FORMAT_DATE_INVALIDE, 30,
                AnomalyType.FAUTE_ORTHOGRAPHE, 30);
        var detectees = map(AnomalyType.CHAMP_VIDE, 35, AnomalyType.FORMAT_DATE_INVALIDE, 25,
                AnomalyType.FAUTE_ORTHOGRAPHE, 20);
        var fp = map(AnomalyType.CHAMP_VIDE, 4, AnomalyType.FORMAT_DATE_INVALIDE, 3, AnomalyType.FAUTE_ORTHOGRAPHE, 3);

        ComparaisonQualite c = QualityReportService.construire(UUID.randomUUID(), null, null, injectees, detectees, fp);

        assertThat(c.total()).isEqualTo(new LigneQualite("TOTAL", 100, 80, 20, 10, 0.8, 0.8889));
        assertThat(c.reference()).isFalse();
        assertThat(c.types()).hasSize(AnomalyType.values().length);
        assertThat(ligne(c, "CHAMP_VIDE")).isEqualTo(new LigneQualite("CHAMP_VIDE", 40, 35, 5, 4, 0.875, 0.8974));
        assertThat(ligne(c, "FORMAT_DATE_INVALIDE").ratees()).isEqualTo(5);
        assertThat(ligne(c, "FAUTE_ORTHOGRAPHE")).isEqualTo(
                new LigneQualite("FAUTE_ORTHOGRAPHE", 30, 20, 10, 3, 0.6667, 0.8696));
        assertThat(ligne(c, "DOUBLON_EXACT")).isEqualTo(new LigneQualite("DOUBLON_EXACT", 0, 0, 0, 0, null, null));
    }

    @Test
    void structurellesEtSemantiquesSontSeparees() {
        var injectees = map(AnomalyType.CHAMP_VIDE, 10, AnomalyType.CASSE_INCOHERENTE, 10);
        var detectees = map(AnomalyType.CHAMP_VIDE, 10, AnomalyType.CASSE_INCOHERENTE, 4);

        ComparaisonQualite c = QualityReportService.construire(UUID.randomUUID(), null, null, injectees, detectees, map());

        LigneQualite structurelles = c.categories().get(0);
        LigneQualite semantiques = c.categories().get(1);
        assertThat(structurelles.libelle()).isEqualTo("STRUCTURELLE");
        assertThat(structurelles.tauxDetection()).isEqualTo(1.0);
        assertThat(semantiques.libelle()).isEqualTo("SEMANTIQUE");
        assertThat(semantiques.tauxDetection()).isEqualTo(0.4);
    }

    @Test
    void runDeReference_toutCeQuiEstDetecteEstUnFauxPositif() {
        var fp = map(AnomalyType.CHAMP_VIDE, 7);

        ComparaisonQualite c = QualityReportService.construire(UUID.randomUUID(), null, null, map(), map(), fp);

        assertThat(c.reference()).isTrue();
        assertThat(c.total().fauxPositifs()).isEqualTo(7);
        assertThat(c.total().tauxDetection()).isNull();
        assertThat(c.total().precision()).isEqualTo(0.0);
    }

    @Test
    void evenementInconnu_envoiRefuseEnBlocAvecUnMessageClair() {
        UUID connu = UUID.randomUUID();
        UUID inconnu = UUID.randomUUID();
        QualityStore store = new QualityStore() {
            @Override public Set<UUID> evenementsDuRun(UUID runId, java.util.Collection<UUID> ids) {
                return ids.contains(connu) ? Set.of(connu) : Set.of();
            }
            @Override public int enregistrer(UUID r, UUID run, String v, List<Detection> d) {
                throw new AssertionError("rien ne doit etre enregistre");
            }
            @Override public Map<AnomalyType, Long> injectees(UUID runId) { return map(); }
            @Override public Map<AnomalyType, Long> detectees(UUID runId, String v) { return map(); }
            @Override public Map<AnomalyType, Long> fauxPositifs(UUID runId, String v) { return map(); }
        };
        var service = new QualityReportService(store, mock(RunManager.class), mock(EchangeService.class));

        assertThatThrownBy(() -> service.recevoir(UUID.randomUUID(), "v1", List.of(
                new Detection(connu, AnomalyType.CHAMP_VIDE, "nom", null, null),
                new Detection(inconnu, AnomalyType.CHAMP_VIDE, "nom", null, null))))
                .isInstanceOfSatisfying(DetectionInvalideException.class, e -> {
                    assertThat(e.getMessage()).contains("rien n'a ete enregistre").contains("1 detection");
                    assertThat(e.erreurs()).containsExactly(
                            "detections[1] : evenement inconnu pour ce run : " + inconnu);
                });
    }

    @Test
    void champObligatoireSaufPourUnDoublon() {
        UUID e = UUID.randomUUID();
        QualityStore store = new QualityStore() {
            @Override public Set<UUID> evenementsDuRun(UUID runId, java.util.Collection<UUID> ids) { return Set.of(e); }
            @Override public int enregistrer(UUID r, UUID run, String v, List<Detection> d) { return d.size(); }
            @Override public Map<AnomalyType, Long> injectees(UUID runId) { return map(); }
            @Override public Map<AnomalyType, Long> detectees(UUID runId, String v) { return map(); }
            @Override public Map<AnomalyType, Long> fauxPositifs(UUID runId, String v) { return map(); }
        };
        var service = new QualityReportService(store, mock(RunManager.class), mock(EchangeService.class));

        assertThat(service.recevoir(UUID.randomUUID(), "v1",
                List.of(new Detection(e, AnomalyType.DOUBLON_EXACT, null, null, null))).nouvelles()).isEqualTo(1);
        assertThatThrownBy(() -> service.recevoir(UUID.randomUUID(), "v1",
                List.of(new Detection(e, AnomalyType.CHAMP_VIDE, " ", null, null))))
                .isInstanceOf(DetectionInvalideException.class)
                .satisfies(ex -> assertThat(((DetectionInvalideException) ex).erreurs().get(0))
                        .contains("champ obligatoire"));
    }

    private static LigneQualite ligne(ComparaisonQualite c, String type) {
        return c.types().stream().filter(l -> l.libelle().equals(type)).findFirst().orElseThrow();
    }
}
