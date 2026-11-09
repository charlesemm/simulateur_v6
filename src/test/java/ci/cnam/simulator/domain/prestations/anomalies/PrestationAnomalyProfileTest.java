package ci.cnam.simulator.domain.prestations.anomalies;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.anomaly.ChampsEligibles.ChampEligible;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrestationAnomalyProfileTest {

    /**
     * Les six types qui ne nomment aucun champ, et pourquoi : les deux doublons visent l'evenement entier,
     * les deux incoherences nomment elles-memes les champs qu'elles croisent, et les deux anomalies de
     * fichier sont posees a l'ecriture de l'export, donc hors de la base.
     */
    private static final Set<AnomalyType> SANS_CHAMP_ELIGIBLE = EnumSet.of(
            AnomalyType.DOUBLON_EXACT, AnomalyType.DOUBLON_APPROXIMATIF,
            AnomalyType.INCOHERENCE_TEMPORELLE, AnomalyType.INCOHERENCE_CROISEE,
            AnomalyType.CARACTERES_CASSES, AnomalyType.FORMAT_DATE_FICHIER);

    private final PrestationAnomalyProfile profil = new PrestationAnomalyProfile();

    @Test
    void toutTypeVisantUnChampADesChampsEligiblesSaufCeuxQuiNenVisentAucun() {
        for (AnomalyType type : AnomalyType.values()) {
            if (SANS_CHAMP_ELIGIBLE.contains(type)) {
                assertThat(profil.champs(type)).as(type.name()).isEmpty();
            } else {
                assertThat(profil.champs(type)).as(type.name()).isNotEmpty();
            }
        }
    }

    @Test
    void lesClesDeRattachementNeSontEligiblesQuePourLaReferenceBrisee() {
        List<String> cles = List.of("factureId", "assureId", "centreSanteId", "medecinId", "pharmacieId");
        for (AnomalyType type : AnomalyType.values()) {
            List<String> noms = profil.champs(type).stream().map(ChampEligible::nom).toList();
            if (type == AnomalyType.REFERENCE_BRISEE) {
                // Seule exception : casser une reference, c'est son objet meme. Mais jamais factureId ni
                // assureId, qui rattachent l'evenement a son parcours : l'orphelin serait ininterpretable.
                assertThat(noms).as(type.name()).isNotEmpty()
                        .isSubsetOf(cles).doesNotContain("factureId", "assureId");
            } else {
                assertThat(noms).as(type.name()).doesNotContainAnyElementsOf(cles);
            }
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
