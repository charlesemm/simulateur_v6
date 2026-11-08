package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.AnomalyType;
import org.junit.jupiter.api.Test;

class CaracteresCassesMutatorTest {

    private static final String PAYLOAD = "{\"nom\": \"N'Guessan\", \"prenoms\": \"Awa\"}";

    private final CaracteresCassesMutator mutator = new CaracteresCassesMutator();

    private MutationFichier muter(String cellule, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        MutationFichier mutation = mutator.muter(cellule, alea).orElseThrow();
        alea.verifierEpuise();
        return mutation;
    }

    @Test
    void entiteHtmlNonDecodee() {
        MutationFichier mutation = muter(PAYLOAD, 0);

        assertThat(mutation.cellule()).isEqualTo("{\"nom\": \"N&#39;Guessan\", \"prenoms\": \"Awa\"}");
        // Le corrige garde la valeur SAINE, celle qui reste en base.
        assertThat(mutation.valeurOrigine()).isEqualTo("N'Guessan");
        assertThat(mutation.valeurCorrompue()).isEqualTo("N&#39;Guessan");
    }

    @Test
    void caracterePerduALaConversion() {
        assertThat(muter(PAYLOAD, 1).valeurCorrompue()).isEqualTo("N?Guessan");
    }

    @Test
    void utf8ReluEnLatin1() {
        assertThat(muter(PAYLOAD, 2).valeurCorrompue()).isEqualTo("Nâ€™Guessan");
    }

    @Test
    void seulLeNomEstTouche() {
        assertThat(muter(PAYLOAD, 0).cellule()).contains("\"prenoms\": \"Awa\"");
    }

    @Test
    void rienAFaireSurUnNomSansApostrophe() {
        assertThat(mutator.muter("{\"nom\": \"Kouassi\"}", new AleaScripte())).isEmpty();
        assertThat(mutator.muter("{\"quantite\": 2}", new AleaScripte())).isEmpty();
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
    }

    @Test
    void elleViseLaCelluleDuPayloadEtSInscritSurLeNom() {
        assertThat(mutator.type()).isEqualTo(AnomalyType.CARACTERES_CASSES);
        assertThat(mutator.colonne()).isEqualTo("payload");
        assertThat(mutator.champ()).isEqualTo("payload.nom");
    }
}
