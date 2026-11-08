package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncoherenceCroiseeMutatorTest {

    private final IncoherenceCroiseeMutator mutator = new IncoherenceCroiseeMutator();

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dateSoin", "2026-03-10");
        payload.put("dateDebutDroits", "2025-01-01");
        payload.put("dateFinDroits", "2026-12-31");
        payload.put("droitsOuverts", true);
        return payload;
    }

    private MutationRelation muter(Map<String, Object> payload, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        MutationRelation mutation = mutator.muter(payload, alea).orElseThrow();
        alea.verifierEpuise();
        return mutation;
    }

    @Test
    void laPeriodeSeFermeAvantLeSoinAlorsQueLesDroitsRestentActifs() {
        MutationRelation mutation = muter(payload(), 30);

        // Un seul champ bouge, et c'est la date de fin : droitsOuverts reste vrai, d'ou la contradiction.
        assertThat(mutation.champ()).isEqualTo("dateFinDroits");
        assertThat(mutation.valeur()).isEqualTo("2026-02-08");
    }

    @Test
    void laFermetureResteApresLOuvertureDesDroits() {
        Map<String, Object> payload = payload();
        payload.put("dateDebutDroits", "2026-03-09");

        // Reculer de 100 jours passerait avant l'ouverture : on se rabat sur la veille du soin, qui tient.
        assertThat(muter(payload, 100).valeur()).isEqualTo("2026-03-09");
    }

    @Test
    void rienAFaireSiLaPeriodeNePeutPasSeFermerSansDevenirAbsurde() {
        Map<String, Object> payload = payload();
        payload.put("dateDebutDroits", "2026-03-10");

        assertThat(mutator.muter(payload, new AleaScripte(5))).isEmpty();
    }

    @Test
    void rienAFaireSiLesDroitsNeSontPasActifs() {
        Map<String, Object> refuses = payload();
        refuses.put("droitsOuverts", false);

        assertThat(mutator.muter(refuses, new AleaScripte())).isEmpty();
    }

    @Test
    void rienAFaireSansDateDeFin() {
        Map<String, Object> sansFin = payload();
        sansFin.remove("dateFinDroits");

        assertThat(mutator.muter(sansFin, new AleaScripte())).isEmpty();
    }

    @Test
    void elleNommeLesChampsDontElleABesoin() {
        assertThat(mutator.champsRequis()).containsExactly("droitsOuverts", "dateSoin", "dateFinDroits");
    }
}
