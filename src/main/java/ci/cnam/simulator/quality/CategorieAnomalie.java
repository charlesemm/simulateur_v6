package ci.cnam.simulator.quality;

import ci.cnam.simulator.anomaly.AnomalyType;

/**
 * Structurelle : detectable par une regle (vide, format, signe, liste, espaces, doublon).
 * Semantique : seulement par referentiel ou similarite (orthographe, casse, caracteres speciaux).
 */
public enum CategorieAnomalie {
    STRUCTURELLE,
    SEMANTIQUE;

    public static CategorieAnomalie de(AnomalyType type) {
        return switch (type) {
            case FAUTE_ORTHOGRAPHE, CASSE_INCOHERENTE, CARACTERES_SPECIAUX -> SEMANTIQUE;
            default -> STRUCTURELLE;
        };
    }
}
