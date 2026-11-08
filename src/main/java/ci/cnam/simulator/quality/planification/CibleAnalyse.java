package ci.cnam.simulator.quality.planification;

/**
 * Ce qu'une planification va analyser.
 *
 * <p>Une seule valeur aujourd'hui, et c'est volontaire : la cible retenue au plan est « la derniere
 * simulation terminee ». La colonne et l'enumeration existent quand meme, pour qu'une autre cible
 * n'impose pas une migration de plus. Generer une simulation depuis la planification a ete ecarte
 * au plan : Echo ne se declenche pas lui-meme.
 */
public enum CibleAnalyse {

    DERNIERE_SIMULATION_TERMINEE("La derniere simulation terminee");

    private final String libelle;

    CibleAnalyse(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
