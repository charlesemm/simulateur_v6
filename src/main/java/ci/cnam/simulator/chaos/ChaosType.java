package ci.cnam.simulator.chaos;

/** Les quatre crash tests pilotables pendant un run. */
public enum ChaosType {
    /** Toxiproxy coupe la liaison avec PostgreSQL (proxy desactive ou connexions reinitialisees). */
    COUPURE_BASE,
    /** Toxiproxy ajoute un delai sur chaque reponse de PostgreSQL. */
    LATENCE,
    /** Le debit du scenario est multiplie (x5, x20, x100). */
    SURCHARGE,
    /** Une proportion des evenements perd une partie de ses champs avant l'ecriture. */
    MESSAGE_TRONQUE
}
