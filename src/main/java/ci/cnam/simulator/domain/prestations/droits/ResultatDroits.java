package ci.cnam.simulator.domain.prestations.droits;

/** Resultat du controle des droits : accorde, ou refuse avec un motif. */
public record ResultatDroits(boolean accorde, MotifRefus motif) {

    public enum MotifRefus {
        /** Pas de droits ouverts a la date de soin. */
        DROITS_FERMES,
        /** Droits ouverts mais cotisations en retard. */
        COTISATIONS_EN_RETARD
    }

    public static ResultatDroits accepte() {
        return new ResultatDroits(true, null);
    }

    public static ResultatDroits refuse(MotifRefus motif) {
        return new ResultatDroits(false, motif);
    }
}
