package ci.cnam.simulator.quality;

/**
 * Etat d'un echange. Les trois echecs d'echange (cahier 4.5) ont chacun leur statut et leur
 * libelle : aucun ne produit de chiffre de detection, et aucun ne doit etre lu comme un taux de 0 %.
 *
 * <p>Seul {@link #RAPPORT_RECU} autorise une mesure : tant qu'aucun rapport exploitable n'est
 * arrive, la comparaison ne rend aucun taux.
 */
public enum StatutEchange {

    /** Le script a retire l'export ; le rapport est attendu. */
    EXPORT_RETIRE("Jeu retire, rapport attendu", false, false),

    /** Deduit a la lecture : l'export a ete retire mais le rapport n'est pas encore attendu comme perdu. */
    ATTENTE_RAPPORT("En attente du script", false, false),

    /** Echec 1 : l'export a ete retire, aucun rapport n'est arrive dans le delai regle. */
    SILENCE("Silence du script", true, false),

    /** Echec 2 : l'envoi a ete refuse en bloc (evenement inconnu du run, champ manquant, type inconnu). */
    RAPPORT_MALFORME("Rapport malforme", true, false),

    /** Echec 3 : rapport accepte, mais aucune detection ne porte a la fois un type et un champ exploitables. */
    RAPPORT_SANS_DETAIL("Rapport sans detail exploitable", true, false),

    /** Le seul cas mesurable : un rapport exploitable est arrive. */
    RAPPORT_RECU("Rapport recu", false, true);

    private final String libelle;
    private final boolean echec;
    private final boolean mesurable;

    StatutEchange(String libelle, boolean echec, boolean mesurable) {
        this.libelle = libelle;
        this.echec = echec;
        this.mesurable = mesurable;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai pour les trois echecs d'echange : l'ecran dit « echec d'echange », jamais « 0 % detecte ». */
    public boolean echec() {
        return echec;
    }

    /** Vrai seulement si un chiffre de detection a un sens. */
    public boolean mesurable() {
        return mesurable;
    }

    /** {@link #ATTENTE_RAPPORT} n'est jamais ecrit en base : il se deduit du journal et de l'heure. */
    public boolean stockable() {
        return this != ATTENTE_RAPPORT;
    }
}
