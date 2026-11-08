package ci.cnam.simulator.quality;

import java.util.List;
import java.util.UUID;

/**
 * Injectees contre detectees pour un run.
 *
 * <p>Quand l'echange a echoue, il n'y a AUCUN chiffre : {@code total} est nul et les listes sont
 * vides. Un echec d'echange n'est pas une mauvaise performance de l'outil, et surtout pas un taux
 * de detection de 0 % (cahier 4.5) : {@link #echecEchange()} le dit, l'ecran l'affiche comme tel.
 *
 * @param reference    vrai si rien n'a ete injecte (run a 0 %) : toute detection y est un faux positif
 * @param versionOutil filtre applique, nul si tous les rapports du run sont pris ensemble
 * @param echange      ou en est l'echange avec le script, nul si aucun echange n'a eu lieu
 */
public record ComparaisonQualite(UUID runId, String versionOutil, boolean reference, EtatEchange echange,
                                 LigneQualite total, List<LigneQualite> categories, List<LigneQualite> types) {

    /**
     * Aucune mesure possible : l'echange n'a pas abouti. Rien n'est chiffre, volontairement, pour
     * qu'aucun ecran et aucun export ne puisse presenter un 0 % qui n'a pas de sens.
     */
    public static ComparaisonQualite sansMesure(UUID runId, String versionOutil, EtatEchange echange) {
        return new ComparaisonQualite(runId, versionOutil, false, echange, null, List.of(), List.of());
    }

    /** Vrai si des chiffres sont disponibles. Faux en cas d'echec ou d'attente d'echange. */
    public boolean mesure() {
        return total != null;
    }

    /** L'echec d'echange a afficher, ou nul s'il n'y en a pas (y compris en simple attente). */
    public EtatEchange echecEchange() {
        return echange != null && echange.echec() ? echange : null;
    }
}
