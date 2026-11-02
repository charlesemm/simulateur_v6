package ci.cnam.simulator.domain.prestations.tarification;

/** Type de prestation auquel s'applique un taux de prise en charge CNAM. */
public enum TypePrestation {
    CONSULTATION, EXAMEN, HOSPITALISATION, MEDICAMENT, DENTAIRE;

    /** Code de la ligne correspondante dans la table taux. */
    public String codeTaux() {
        return "CNAM_" + name();
    }
}
