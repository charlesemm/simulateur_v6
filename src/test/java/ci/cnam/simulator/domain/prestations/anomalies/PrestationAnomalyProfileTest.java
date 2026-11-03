package ci.cnam.simulator.domain.prestations.anomalies;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.anomaly.ChampsEligibles.ChampEligible;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrestationAnomalyProfileTest {

    private final PrestationAnomalyProfile profil = new PrestationAnomalyProfile();

    @Test
    void toutTypeVisantUnChampADesChampsEligiblesSaufLeDoublon() {
        for (AnomalyType type : AnomalyType.values()) {
            if (type == AnomalyType.DOUBLON_EXACT) {
                assertThat(profil.champs(type)).isEmpty();
            } else {
                assertThat(profil.champs(type)).as(type.name()).isNotEmpty();
            }
        }
    }

    @Test
    void lesClesDeRattachementNeSontJamaisEligibles() {
        List<String> cles = List.of("factureId", "assureId", "centreSanteId", "medecinId", "pharmacieId");
        for (AnomalyType type : AnomalyType.values()) {
            assertThat(profil.champs(type).stream().map(ChampEligible::nom)).doesNotContainAnyElementsOf(cles);
        }
    }

    @Test
    void lesChampsSaisisParLAgentOntLePlusFortPoids() {
        List<ChampEligible> champs = profil.champs(AnomalyType.CHAMP_VIDE);
        int poidsNom = champs.stream().filter(c -> c.nom().equals("nom")).findFirst().orElseThrow().poids();
        int poidsMotif = champs.stream().filter(c -> c.nom().equals("motif")).findFirst().orElseThrow().poids();

        assertThat(poidsNom).isGreaterThan(poidsMotif);
        assertThat(Arrays.stream(AnomalyType.values()).flatMap(t -> profil.champs(t).stream())
                .mapToInt(ChampEligible::poids).max()).hasValue(poidsNom);
    }

    @Test
    void ledomaineEstPrestations() {
        assertThat(profil.domaine()).isEqualTo("prestations");
    }
}
