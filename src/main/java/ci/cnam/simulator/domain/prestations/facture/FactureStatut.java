package ci.cnam.simulator.domain.prestations.facture;

import java.util.EnumSet;
import java.util.Set;

/**
 * Statut d'une facture et machine a etats. Seules les transitions listees sont acceptees :
 * OUVERTE -> EN_COURS -> PRESCRITE -> MEDICAMENTS_DELIVRES -> FINALISEE, abandon possible depuis
 * OUVERTE, EN_COURS ou PRESCRITE. REFUSEE n'est jamais atteinte par transition : la facture est creee ainsi.
 */
public enum FactureStatut {
    OUVERTE,
    EN_COURS,
    PRESCRITE,
    MEDICAMENTS_DELIVRES,
    FINALISEE,
    REFUSEE,
    ABANDONNEE;

    public Set<FactureStatut> suivants() {
        return switch (this) {
            case OUVERTE -> EnumSet.of(EN_COURS, ABANDONNEE);
            case EN_COURS -> EnumSet.of(PRESCRITE, ABANDONNEE);
            case PRESCRITE -> EnumSet.of(MEDICAMENTS_DELIVRES, ABANDONNEE);
            case MEDICAMENTS_DELIVRES -> EnumSet.of(FINALISEE);
            case FINALISEE, REFUSEE, ABANDONNEE -> EnumSet.noneOf(FactureStatut.class);
        };
    }

    public boolean peutPasserA(FactureStatut cible) {
        return suivants().contains(cible);
    }

    public boolean estTerminal() {
        return suivants().isEmpty();
    }

    /** Leve {@link TransitionInterditeException} si la transition n'est pas autorisee. */
    public void verifierTransitionVers(FactureStatut cible) {
        if (!peutPasserA(cible)) {
            throw new TransitionInterditeException(this, cible);
        }
    }
}
