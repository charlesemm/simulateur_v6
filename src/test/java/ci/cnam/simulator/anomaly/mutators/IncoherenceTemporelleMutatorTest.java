package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncoherenceTemporelleMutatorTest {

    private final IncoherenceTemporelleMutator mutator = new IncoherenceTemporelleMutator();

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nom", "Kouassi");
        payload.put("dateSoin", "2026-03-10");
        payload.put("dateDebutDroits", "2025-01-01");
        payload.put("dateFinDroits", "2026-12-31");
        return payload;
    }

    private MutationRelation muter(Map<String, Object> payload, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        MutationRelation mutation = mutator.muter(payload, alea).orElseThrow();
        alea.verifierEpuise();
        return mutation;
    }

    @Test
    void dateAntidateeAvantLOuvertureDesDroits() {
        MutationRelation mutation = muter(payload(), 0, 10);

        assertThat(mutation.champ()).isEqualTo("dateSoin");
        assertThat(mutation.valeur()).isEqualTo("2024-12-22");
    }

    @Test
    void dateApresLaFermetureDesDroits() {
        assertThat(muter(payload(), 1, 20).valeur()).isEqualTo("2027-01-20");
    }

    @Test
    void dateDansLeFutur() {
        assertThat(muter(payload(), 2, 100).valeur()).isEqualTo("2026-06-18");
    }

    @Test
    void laDateResteUneDateValideEtBienFormatee() {
        // Seule la RELATION est fausse : le format reste ISO, le mois et le jour existent.
        assertThat(muter(payload(), 0, 1).valeur().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void rienAFaireSansLaPeriodeDeDroits() {
        Map<String, Object> sansFin = payload();
        sansFin.remove("dateFinDroits");
        assertThat(mutator.muter(sansFin, new AleaScripte())).isEmpty();

        Map<String, Object> sansSoin = payload();
        sansSoin.put("dateSoin", "pas une date");
        assertThat(mutator.muter(sansSoin, new AleaScripte())).isEmpty();
    }

    @Test
    void elleNommeLesChampsDontElleABesoin() {
        assertThat(mutator.champsRequis()).containsExactly("dateSoin", "dateDebutDroits", "dateFinDroits");
    }
}
