package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.random.SeededRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReferenceBriseeMutatorTest {

    private final ReferenceBriseeMutator mutator = new ReferenceBriseeMutator();

    @Test
    void identifiantRemplaceParUnAutreUuidBienForme() {
        UUID medecin = UUID.fromString("11111111-1111-4111-8111-111111111111");

        Object brisee = mutator.muter(medecin, new SeededRandom(7)).orElseThrow().valeur();

        assertThat(brisee).isInstanceOf(UUID.class)
                .isEqualTo(new SeededRandom(7).nextUuid())
                .isNotEqualTo(medecin);
        // Bien forme : c'est bien un UUID version 4, comme ceux du referentiel. Seul le lien est rompu.
        assertThat(((UUID) brisee).version()).isEqualTo(4);
    }

    @Test
    void identifiantEcritEnTexteResteDuTexte() {
        String medecin = "11111111-1111-4111-8111-111111111111";

        Object brisee = mutator.muter(medecin, new SeededRandom(7)).orElseThrow().valeur();

        assertThat(brisee).isEqualTo(new SeededRandom(7).nextUuid().toString());
        assertThat(UUID.fromString((String) brisee)).isNotNull();
    }

    @Test
    void codeDeReferentielGardeSonPrefixeEtSaLargeur() {
        AleaScripte alea = new AleaScripte(3);

        Object brisee = mutator.muter("CS-0042", alea).orElseThrow().valeur();

        assertThat(brisee).isEqualTo("CS-9993");
        alea.verifierEpuise();
    }

    @Test
    void rienAFaireSiCeNEstPasUnIdentifiant() {
        assertThat(mutator.muter("Kouassi", new SeededRandom(7))).isEmpty();
        assertThat(mutator.muter("", new SeededRandom(7))).isEmpty();
        assertThat(mutator.muter(12, new SeededRandom(7))).isEmpty();
        assertThat(mutator.muter(null, new SeededRandom(7))).isEmpty();
    }
}
