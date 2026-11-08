package ci.cnam.simulator.anomaly.mutators;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.anomaly.AnomalyType;
import org.junit.jupiter.api.Test;

class FormatDateFichierMutatorTest {

    /** La date de soin telle que l'export l'ecrit sur TOUTES les autres lignes. */
    private static final String ISO = "2026-03-10T08:30:00.000000";

    private final FormatDateFichierMutator mutator = new FormatDateFichierMutator();

    private String muter(String cellule, int... tirages) {
        AleaScripte alea = new AleaScripte(tirages);
        String ecrite = mutator.muter(cellule, alea).orElseThrow().cellule();
        alea.verifierEpuise();
        return ecrite;
    }

    @Test
    void jourMoisAnnee() {
        assertThat(muter(ISO, 0)).isEqualTo("10/03/2026 08:30");
    }

    @Test
    void formatAmericainDoncAmbigu() {
        assertThat(muter(ISO, 1)).isEqualTo("03/10/2026 08:30");
    }

    @Test
    void formatCompact() {
        assertThat(muter(ISO, 2)).isEqualTo("20260310083000");
    }

    @Test
    void tiretsEtOrdreInverse() {
        assertThat(muter(ISO, 3)).isEqualTo("10-03-2026 08:30:00");
    }

    @Test
    void laValeurDOrigineInscriteAuCorrigeEstLaFormeIsoDeLaBase() {
        MutationFichier mutation = mutator.muter(ISO, new AleaScripte(0)).orElseThrow();

        assertThat(mutation.valeurOrigine()).isEqualTo(ISO);
        assertThat(mutation.valeurCorrompue()).isEqualTo("10/03/2026 08:30");
    }

    @Test
    void accepteUneDateSansFractionDeSeconde() {
        assertThat(muter("2026-03-10T08:30:00", 0)).isEqualTo("10/03/2026 08:30");
    }

    @Test
    void rienAFaireSiLaCelluleNEstPasUneDateIso() {
        assertThat(mutator.muter("10/03/2026", new AleaScripte())).isEmpty();
        assertThat(mutator.muter("", new AleaScripte())).isEmpty();
        assertThat(mutator.muter(null, new AleaScripte())).isEmpty();
    }

    @Test
    void elleViseLaDateDeSoinDeLaTableDesEvenements() {
        assertThat(mutator.type()).isEqualTo(AnomalyType.FORMAT_DATE_FICHIER);
        assertThat(mutator.colonne()).isEqualTo("timestamp_simule");
        assertThat(mutator.champ()).isEqualTo("timestamp_simule");
    }
}
