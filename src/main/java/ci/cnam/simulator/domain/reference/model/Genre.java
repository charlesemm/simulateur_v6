package ci.cnam.simulator.domain.reference.model;

/**
 * Genre, code identique au nom. {@code TB_TV_GENRES} connait aussi le code I (indetermine), que le
 * referentiel simule ne produit pas : un genre indetermine est une donnee de saisie incomplete, et
 * c'est a l'injecteur d'anomalies de la fabriquer, pas au generateur de donnees propres.
 */
public enum Genre {
    M, F;

    public String code() {
        return name();
    }
}
