package ci.cnam.simulator.quality;

/**
 * Sens d'un echange avec l'outil d'analyse, vu d'Echo. Echo ne pousse jamais : c'est le script
 * exterieur qui vient chercher le jeu puis depose son rapport.
 */
public enum SensEchange {

    /** Le script a retire l'export. */
    ENVOI,

    /** Le script a pousse un rapport, accepte ou refuse. */
    RECEPTION;

    public String libelle() {
        return this == ENVOI ? "Jeu retire par le script" : "Rapport pousse par le script";
    }
}
