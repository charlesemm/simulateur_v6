package ci.cnam.simulator.core.event;

/**
 * Statut d'ecriture d'un evenement en base (colonne {@code statut} de TB_ECHO_EVENEMENT).
 *
 * <p>La contrainte {@code ck_evenement_statut} (V22) enumere les memes valeurs : un test
 * d'integration verifie qu'ils disent la meme chose. Ajouter une valeur ici sans migration
 * fait echouer ce test, pas l'ecriture en production.
 */
public enum StatutEcriture {
    /** Ecrit normalement par le DatabaseSink. */
    ECRIT,
    /** Evenement en echec, que l'explorateur peut rejouer (il repasse alors a ECRIT). */
    ECHOUE
}
