package ci.cnam.simulator.anomaly;

import java.util.Arrays;
import java.util.Optional;

/** Les types d'anomalies simples de qualite de donnees (cahier des charges, section 6). */
public enum AnomalyType {
    CHAMP_VIDE,
    FAUTE_ORTHOGRAPHE,
    ESPACES_PARASITES,
    CASSE_INCOHERENTE,
    CARACTERES_SPECIAUX,
    FORMAT_DATE_INVALIDE,
    FORMAT_NUMERIQUE_INVALIDE,
    FORMAT_IDENTIFIANT_INVALIDE,
    VALEUR_NEGATIVE,
    VALEUR_HORS_LISTE,
    DOUBLON_EXACT;

    /** Nom du type dans un scenario YAML : CHAMP_VIDE s'ecrit champVide. */
    public String cle() {
        StringBuilder sb = new StringBuilder();
        boolean majuscule = false;
        for (char c : name().toCharArray()) {
            if (c == '_') {
                majuscule = true;
            } else {
                sb.append(majuscule ? c : Character.toLowerCase(c));
                majuscule = false;
            }
        }
        return sb.toString();
    }

    /** Retrouve un type par sa cle YAML (champVide) ou par son nom (CHAMP_VIDE). */
    public static Optional<AnomalyType> depuis(String texte) {
        return Arrays.stream(values()).filter(t -> t.cle().equals(texte) || t.name().equals(texte)).findFirst();
    }
}
