package ci.cnam.simulator.quality;

import ci.cnam.simulator.anomaly.AnomalyType;

/**
 * Structurelle : detectable par une regle sur la valeur (vide, format, signe, liste, espaces, doublon exact).
 * Semantique : seulement par referentiel ou similarite (orthographe, casse, caracteres speciaux).
 * Relationnelle : seulement en regardant AUTRE CHOSE que la ligne -- une autre ligne, un referentiel, une
 * autre date, un autre champ. Ni une regle de valeur ni une similarite ne la voient.
 * Fichier : n'existe que dans les octets servis, pas en base ; se voit a la lecture du fichier.
 *
 * <p>DECISION (brique Q5) : les anomalies de relation ont recu leur propre categorie plutot que d'etre
 * forcees dans structurelle ou semantique. Les ranger en structurelle aurait laisse croire qu'une regle de
 * champ suffit a les trouver, ce qui est faux et fausserait la lecture du rappel par categorie. Les deux
 * anomalies de fichier ont, pour la meme raison, leur propre categorie : elles ne sont pas en base.
 */
public enum CategorieAnomalie {
    STRUCTURELLE,
    SEMANTIQUE,
    RELATIONNELLE,
    FICHIER;

    public static CategorieAnomalie de(AnomalyType type) {
        return switch (type) {
            case FAUTE_ORTHOGRAPHE, CASSE_INCOHERENTE, CARACTERES_SPECIAUX -> SEMANTIQUE;
            case DOUBLON_APPROXIMATIF, REFERENCE_BRISEE, INCOHERENCE_TEMPORELLE, INCOHERENCE_CROISEE
                    -> RELATIONNELLE;
            case CARACTERES_CASSES, FORMAT_DATE_FICHIER -> FICHIER;
            default -> STRUCTURELLE;
        };
    }
}
