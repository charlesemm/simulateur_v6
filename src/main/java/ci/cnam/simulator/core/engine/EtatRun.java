package ci.cnam.simulator.core.engine;

/** Etats d'un run. ARRETE et ECHOUE sont terminaux. */
public enum EtatRun {
    CREE, EN_COURS, EN_PAUSE, ARRETE, ECHOUE;

    public boolean actif() {
        return this == EN_COURS || this == EN_PAUSE;
    }

    public boolean terminal() {
        return this == ARRETE || this == ECHOUE;
    }
}
