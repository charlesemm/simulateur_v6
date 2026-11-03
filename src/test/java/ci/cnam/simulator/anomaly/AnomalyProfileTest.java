package ci.cnam.simulator.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnomalyProfileTest {

    @Test
    void lesNomsDuScenarioSontLesTypesEnCamelCase() {
        assertThat(AnomalyType.CHAMP_VIDE.cle()).isEqualTo("champVide");
        assertThat(AnomalyType.FORMAT_IDENTIFIANT_INVALIDE.cle()).isEqualTo("formatIdentifiantInvalide");
        assertThat(AnomalyType.DOUBLON_EXACT.cle()).isEqualTo("doublonExact");
        assertThat(AnomalyType.depuis("fauteOrthographe")).contains(AnomalyType.FAUTE_ORTHOGRAPHE);
        assertThat(AnomalyType.depuis("FAUTE_ORTHOGRAPHE")).contains(AnomalyType.FAUTE_ORTHOGRAPHE);
        assertThat(AnomalyType.depuis("n_importe_quoi")).isEmpty();
    }

    @Test
    void lesPourcentagesDevenantDesProbabilites() {
        AnomalyProfile p = AnomalyProfile.depuis(Map.of("champVide", 3.0, "fauteOrthographe", 2.0));

        assertThat(p.taux(AnomalyType.CHAMP_VIDE)).isEqualTo(0.03);
        assertThat(p.taux(AnomalyType.FAUTE_ORTHOGRAPHE)).isEqualTo(0.02);
        assertThat(p.taux(AnomalyType.DOUBLON_EXACT)).isZero();
        assertThat(p.tauxGlobal()).isEqualTo(0.05);
        assertThat(p.actif()).isTrue();
    }

    @Test
    void sansTauxOuAvecDesZerosLeProfilEstInactif() {
        assertThat(AnomalyProfile.depuis(Map.of()).actif()).isFalse();
        assertThat(AnomalyProfile.depuis(null).actif()).isFalse();
        assertThat(AnomalyProfile.depuis(Map.of("champVide", 0.0)).actif()).isFalse();
        assertThat(AnomalyProfile.depuis(Map.of("global", 0.0)).tauxGlobal()).isZero();
    }

    @Test
    void globalSeRepartitEgalementEntreLesOnzeTypes() {
        AnomalyProfile p = AnomalyProfile.depuis(Map.of("global", 11.0));

        for (AnomalyType type : AnomalyType.values()) {
            assertThat(p.taux(type)).isEqualTo(0.01);
        }
        assertThat(p.tauxGlobal()).isCloseTo(0.11, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void refuseUnTypeInconnuEnLeNommant() {
        assertThatThrownBy(() -> AnomalyProfile.depuis(Map.of("champVidee", 3.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("champVidee")
                .hasMessageContaining("champVide");
    }

    @Test
    void refuseUnMelangeGlobalEtTypesOuUneSommeAuDessusDeCent() {
        assertThatThrownBy(() -> AnomalyProfile.depuis(Map.of("global", 3.0, "champVide", 1.0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("global");
        assertThatThrownBy(() -> AnomalyProfile.depuis(Map.of("champVide", 60.0, "fauteOrthographe", 50.0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100");
    }
}
