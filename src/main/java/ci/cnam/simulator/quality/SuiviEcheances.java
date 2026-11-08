package ci.cnam.simulator.quality;

import java.util.UUID;

/**
 * Ce que le passage du script fait a l'echeance qui l'attendait. Implemente par la planification
 * (prompt 15) ; {@link EchangeService} s'en sert s'il est present, et s'en passe sinon.
 *
 * <p>Le journal des echanges reste la trace de reference : ce suivi n'est que le report de ces
 * memes faits sur le calendrier, pour que l'ecran puisse dire « cette analyse de 3 h a bien eu lieu ».
 */
public interface SuiviEcheances {

    /** Le script est venu chercher le jeu de cette simulation. */
    void retraitConstate(UUID runId, String detail);

    /** Son rapport est arrive et il est exploitable. */
    void rapportConstate(UUID runId, String detail);

    /** Son rapport a ete refuse, ou n'avait aucune detection exploitable : ce n'est pas un taux. */
    void refusConstate(UUID runId, String detail);
}
