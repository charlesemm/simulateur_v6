package ci.cnam.simulator.quality.planification;

/**
 * Ou en est une echeance d'analyse.
 *
 * <p>Aucun de ces statuts n'est un taux : un SILENCE ou un RAPPORT_REFUSE dit que l'echange n'a pas
 * abouti, jamais que l'outil a detecte 0 %.
 */
public enum StatutAnalyse {

    /** Ouverte, le script ne l'a pas encore prise. C'est ce que rend /api/analyses/attendues. */
    ATTENDUE("Attendue"),
    /** Le script a retire l'export : son rapport est attendu. */
    RETIREE("Jeu retire, rapport attendu"),
    /** Le script a pousse un rapport exploitable : la mesure est possible. */
    RAPPORT_RECU("Rapport recu"),
    /** Le jeu a ete retire, aucun rapport n'est arrive dans le delai regle. */
    SILENCE("Silence du script"),
    /** Le rapport a ete refuse, ou n'avait aucune detection exploitable. */
    RAPPORT_REFUSE("Rapport refuse"),
    /** L'heure est passee, aucune simulation terminee a viser. L'echeance le DIT, elle n'echoue pas. */
    SANS_CIBLE("Aucune simulation a analyser");

    private final String libelle;

    StatutAnalyse(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai tant que le script peut encore agir sur cette echeance. */
    public boolean ouverte() {
        return this == ATTENDUE || this == RETIREE;
    }

    /** Vrai quand l'echange n'a pas abouti : a ne jamais confondre avec un taux. */
    public boolean echec() {
        return this == SILENCE || this == RAPPORT_REFUSE;
    }
}
