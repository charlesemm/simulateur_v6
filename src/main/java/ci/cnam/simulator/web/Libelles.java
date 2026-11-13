package ci.cnam.simulator.web;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.quality.CategorieAnomalie;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Les mots de l'interface : UN seul endroit traduit les codes techniques (types d'evenement, types
 * d'anomalie, pannes, roles, actions du journal) en libelles lisibles par quelqu'un qui decouvre Echo.
 *
 * <p>Aucune regle metier : seulement de la mise en forme. Un code inconnu n'est jamais masque, il est
 * rendu « en clair » (minuscules, espaces) pour qu'un nouveau type ajoute cote moteur reste lisible sans
 * toucher a cette classe. Les gabarits y accedent par l'attribut de modele {@code libelles}
 * (voir {@link ModeleCommun}).
 */
public final class Libelles {

    static final Libelles INSTANCE = new Libelles();

    private static final Map<String, String> ETAPES = Map.ofEntries(
            Map.entry("PATIENT_ARRIVE", "Arrivée du patient"),
            Map.entry("DROITS_VERIFIES", "Droits vérifiés"),
            Map.entry("ACCES_REFUSE", "Accès refusé"),
            Map.entry("FACTURE_OUVERTE", "Feuille de soins ouverte"),
            Map.entry("CONSULTATION_REALISEE", "Consultation réalisée"),
            Map.entry("EXAMEN_REALISE", "Examen réalisé"),
            Map.entry("HOSPITALISATION_REALISEE", "Hospitalisation réalisée"),
            Map.entry("PRESCRIPTION_EMISE", "Prescription émise"),
            Map.entry("MEDICAMENTS_DELIVRES", "Médicaments délivrés"),
            Map.entry("FACTURE_FINALISEE", "Feuille de soins finalisée"));

    private static final Map<String, String> ANOMALIES = Map.ofEntries(
            Map.entry("CHAMP_VIDE", "Champ vidé"),
            Map.entry("FAUTE_ORTHOGRAPHE", "Faute d'orthographe"),
            Map.entry("ESPACES_PARASITES", "Espaces parasites"),
            Map.entry("CASSE_INCOHERENTE", "Casse incohérente"),
            Map.entry("CARACTERES_SPECIAUX", "Caractères spéciaux"),
            Map.entry("FORMAT_DATE_INVALIDE", "Format de date invalide"),
            Map.entry("FORMAT_NUMERIQUE_INVALIDE", "Format numérique invalide"),
            Map.entry("FORMAT_IDENTIFIANT_INVALIDE", "Format d'identifiant invalide"),
            Map.entry("VALEUR_NEGATIVE", "Valeur négative"),
            Map.entry("VALEUR_HORS_LISTE", "Valeur hors liste"),
            Map.entry("DOUBLON_EXACT", "Doublon exact"),
            Map.entry("DOUBLON_APPROXIMATIF", "Doublon approximatif"),
            Map.entry("REFERENCE_BRISEE", "Référence brisée"),
            Map.entry("INCOHERENCE_TEMPORELLE", "Incohérence de dates"),
            Map.entry("INCOHERENCE_CROISEE", "Incohérence entre champs"),
            Map.entry("CARACTERES_CASSES", "Caractères cassés à l'export"),
            Map.entry("FORMAT_DATE_FICHIER", "Format de date changé à l'export"));

    private static final Map<String, String> PANNES = Map.of(
            "COUPURE_BASE", "Coupure de la base",
            "LATENCE", "Liaison ralentie",
            "SURCHARGE", "Surcharge",
            "MESSAGE_TRONQUE", "Messages tronqués");

    private static final Map<String, String> FINS_DE_PANNE = Map.of(
            "EXPIREE", "Durée écoulée",
            "RETABLIE", "Rétablie à la main",
            "RUN_TERMINE", "Simulation terminée");

    private static final Map<String, String> ROLES = Map.of(
            "ADMIN", "Administrateur",
            "OPERATEUR", "Opérateur",
            "OBSERVATEUR", "Observateur");

    private static final Map<String, String> ROLES_EN_CLAIR = Map.of(
            "ADMIN", "Gère les comptes, en plus de tout ce que fait un opérateur.",
            "OPERATEUR", "Lance les simulations, provoque les pannes, programme les analyses.",
            "OBSERVATEUR", "Consulte tout, ne modifie rien.");

    private static final Map<String, String> CATEGORIES = Map.of(
            "STRUCTURELLE", "Structurelle",
            "SEMANTIQUE", "Sémantique",
            "RELATIONNELLE", "Relationnelle",
            "FICHIER", "De fichier");

    private static final Map<String, String> CATEGORIES_EN_CLAIR = Map.of(
            "STRUCTURELLE", "se voit par une règle sur la valeur seule (vide, format, signe, liste)",
            "SEMANTIQUE", "ne se voit qu'en comparant à un référentiel (orthographe, casse)",
            "RELATIONNELLE", "ne se voit qu'en regardant une autre ligne, une autre date ou un autre champ",
            "FICHIER", "n'existe que dans le fichier exporté, pas en base");

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("RUN_LANCE", "Simulation lancée"),
            Map.entry("RUN_PAUSE", "Simulation mise en pause"),
            Map.entry("RUN_REPRIS", "Simulation reprise"),
            Map.entry("RUN_ARRETE", "Simulation arrêtée"),
            Map.entry("RUN_REJOUE", "Simulation rejouée"),
            Map.entry("CHAOS_DECLENCHE", "Panne provoquée"),
            Map.entry("CHAOS_RETABLI", "Pannes rétablies"),
            Map.entry("UTILISATEUR_CREE", "Compte créé"),
            Map.entry("ROLE_MODIFIE", "Rôle modifié"),
            Map.entry("UTILISATEUR_ACTIVE", "Compte réactivé"),
            Map.entry("UTILISATEUR_DESACTIVE", "Compte désactivé"),
            Map.entry("COMPTE_VERROUILLE", "Compte verrouillé"),
            Map.entry("COMPTE_DEVERROUILLE", "Compte déverrouillé"),
            Map.entry("EMAIL_MODIFIE", "Adresse modifiée"),
            Map.entry("MOT_DE_PASSE_CHANGE", "Mot de passe changé"),
            Map.entry("MOT_DE_PASSE_REINITIALISE", "Mot de passe réinitialisé"),
            Map.entry("EXPORT_CORRIGE", "Corrigé téléchargé"),
            Map.entry("PLANIFICATION_CREEE", "Analyse programmée"),
            Map.entry("PLANIFICATION_ACTIVEE", "Analyse programmée réactivée"),
            Map.entry("PLANIFICATION_DESACTIVEE", "Analyse programmée désactivée"),
            Map.entry("PLANIFICATION_SUPPRIMEE", "Analyse programmée supprimée"),
            Map.entry("SCENARIO_ENREGISTRE", "Scénario enregistré"),
            Map.entry("EVENEMENT_REJOUE", "Événement rejoué"));

    private Libelles() {
    }

    /** Etape du parcours portee par un evenement ({@code FACTURE_OUVERTE} -> « Feuille de soins ouverte »). */
    public String etape(Object code) {
        return traduire(ETAPES, code);
    }

    public String anomalie(Object code) {
        return traduire(ANOMALIES, code);
    }

    public String panne(Object code) {
        return traduire(PANNES, code);
    }

    public String finDePanne(Object code) {
        return code == null ? "—" : traduire(FINS_DE_PANNE, code);
    }

    public String role(Object code) {
        return traduire(ROLES, code);
    }

    public String roleEnClair(Object code) {
        return code == null ? "" : ROLES_EN_CLAIR.getOrDefault(code.toString(), "");
    }

    public String action(Object code) {
        return traduire(ACTIONS, code);
    }

    public String categorie(Object code) {
        return traduire(CATEGORIES, code);
    }

    public String categorieEnClair(Object code) {
        return code == null ? "" : CATEGORIES_EN_CLAIR.getOrDefault(code.toString(), "");
    }

    /** Categorie d'un type d'anomalie, telle que le service de qualite la classe. Vide pour un type inconnu. */
    public String categorieDe(Object typeAnomalie) {
        if (typeAnomalie == null) {
            return "";
        }
        return AnomalyType.depuis(typeAnomalie.toString())
                .map(CategorieAnomalie::de).map(c -> categorie(c.name())).orElse("");
    }

    /** Duree lisible : « 45 s », « 12 min 05 s », « 2 h 03 min ». Tiret quand elle est inconnue. */
    public String duree(Duration duree) {
        if (duree == null || duree.isNegative()) {
            return "—";
        }
        long s = duree.toSeconds();
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
        }
        return String.format(Locale.ROOT, "%d h %02d min", s / 3600, (s % 3600) / 60);
    }

    private static String traduire(Map<String, String> table, Object code) {
        if (code == null) {
            return "—";
        }
        String cle = code.toString();
        String connu = table.get(cle);
        return connu != null ? connu : enClair(cle);
    }

    /** {@code UN_CODE_INCONNU} -> « Un code inconnu » : jamais masque, seulement rendu lisible. */
    private static String enClair(String code) {
        if (code.isBlank() || !code.equals(code.toUpperCase(Locale.ROOT))) {
            return code;
        }
        String bas = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(bas.charAt(0)) + bas.substring(1);
    }
}
