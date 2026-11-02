package ci.cnam.simulator.domain.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.NumeroAssure;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceGeneratorTest {

    private static List<Assure> assures(long graine, int nb) {
        return new ReferenceGenerator(new SeededRandom(graine)).assures(nb);
    }

    @Test
    void numerosToujoursValidesEtUniques() {
        List<Assure> assures = assures(42, 5000);
        assertThat(assures).allMatch(a -> NumeroAssure.estValide(a.numeroAssure()));
        assertThat(assures.stream().map(Assure::numeroAssure).distinct()).hasSize(5000);
    }

    @Test
    void memeGraineMemes100PremiersAssures() {
        assertThat(assures(42, 100)).isEqualTo(assures(42, 100));
    }

    @Test
    void les100PremiersAssuresNeDependentPasDuVolume() {
        assertThat(assures(42, 100)).isEqualTo(assures(42, 2000).subList(0, 100));
    }

    @Test
    void graineDifferenteDonneesDifferentes() {
        assertThat(assures(1, 100)).isNotEqualTo(assures(2, 100));
    }

    @Test
    void droitsCoherents() {
        assertThat(assures(42, 2000)).allMatch(a ->
                a.dateFinDroits() == null || !a.dateFinDroits().isBefore(a.dateDebutDroits()));
    }

    @Test
    void chaqueCentreADesMedecinsEtLeReferentielEstReproductible() {
        ReferenceGenerator g1 = new ReferenceGenerator(new SeededRandom(5));
        var centres = g1.centres(20);
        var medecins = g1.medecins(centres);
        assertThat(centres).allMatch(c -> medecins.stream().anyMatch(m -> m.centreSanteId().equals(c.id())));

        ReferenceGenerator g2 = new ReferenceGenerator(new SeededRandom(5));
        assertThat(g2.centres(20)).isEqualTo(centres);
    }
}
