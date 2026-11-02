package ci.cnam.simulator.domain.prestations.facture;

/** Les quatre types de bons de prise en charge. */
public enum TypeBon {
    /** Soins ambulatoires, consultations et medicaments (ER-01-GPR). */
    AMBULATOIRE,
    /** Hospitalisation et soins (ER-02-GPR). */
    HOSPITALISATION,
    /** Biologie, imagerie et autres (ER-03-GPR). */
    BIOLOGIE_IMAGERIE,
    /** Soins et protheses dentaires. */
    DENTAIRE
}
