package ci.cnam.simulator.anomaly;

import java.util.Arrays;
import java.util.Optional;

/**
 * Les types d'anomalies de qualite de donnees.
 *
 * <p>Trois familles, et elles ne se posent pas au meme endroit :
 * <ul>
 *   <li>les 11 premieres abiment UN CHAMP D'UNE LIGNE (cahier des charges, section 6) ;
 *   <li>les quatre anomalies DE RELATION se jugent contre autre chose : une seconde ligne, un referentiel,
 *       une autre date, un autre champ. Elles sont posees a la generation, comme les autres ;
 *   <li>les deux anomalies DE FICHIER ne peuvent pas exister dans une colonne typee : elles sont posees a
 *       l'ECRITURE DE L'EXPORT. La base reste propre, c'est le fichier qui est piege.
 * </ul>
 */
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
    DOUBLON_EXACT,

    // --- Anomalies de relation (brique Q5) ---
    /** Une SECONDE ligne proche de l'originale : « N'Guessan » contre « Nguessan ». L'originale reste propre. */
    DOUBLON_APPROXIMATIF,
    /** Un identifiant bien forme mais absent de tout referentiel : c'est le lien qui est rompu, pas le format. */
    REFERENCE_BRISEE,
    /** Date de soin hors periode de droits, antidatee ou dans le futur : se juge contre une autre date. */
    INCOHERENCE_TEMPORELLE,
    /** Droits actifs alors que la periode de droits est close : chaque champ est valide seul. */
    INCOHERENCE_CROISEE,

    // --- Anomalies de fichier (posees a l'ecriture de l'export, jamais en base) ---
    /** « N'Guessan » ecrit N&#39;Guessan ou N?Guessan : encodage casse dans le fichier servi. */
    CARACTERES_CASSES,
    /** Date ecrite dans un format incoherent avec les autres lignes du meme fichier. */
    FORMAT_DATE_FICHIER;

    /** Vrai si l'anomalie est posee a l'ecriture de l'export et non a la generation. */
    public boolean fichier() {
        return this == CARACTERES_CASSES || this == FORMAT_DATE_FICHIER;
    }

    /** Vrai si l'anomalie emet une seconde ligne et renseigne doublon_de. */
    public boolean doublon() {
        return this == DOUBLON_EXACT || this == DOUBLON_APPROXIMATIF;
    }

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
