package ci.cnam.simulator.quality.planification;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Une regle de calendrier : « a telle heure, tels jours, analyse telle cible ».
 *
 * @param heure heure du SERVEUR (le fuseau est fixe en configuration, et affiche dans l'aide du champ)
 * @param jours jours ou la regle s'applique
 * @param actif une regle inactive n'ouvre plus aucune echeance, sans perdre son historique
 */
public record Planification(UUID id, String libelle, LocalTime heure, JoursSemaine jours,
                            CibleAnalyse cible, boolean actif, Instant dateCreation, Instant dateMaj) {

    /** Longueur de la colonne. */
    public static final int LIBELLE_MAX = 120;

    public Planification {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException(
                    "Libelle obligatoire : c'est ce qui identifie l'analyse a l'ecran");
        }
        if (libelle.strip().length() > LIBELLE_MAX) {
            throw new IllegalArgumentException("Libelle trop long (" + LIBELLE_MAX + " caracteres au plus)");
        }
        libelle = libelle.strip();
        if (heure == null) {
            throw new IllegalArgumentException("Heure obligatoire");
        }
        // La minute est la granularite de la tache : une seconde saisie ne serait jamais honoree.
        heure = heure.withSecond(0).withNano(0);
        if (jours == null) {
            throw new IllegalArgumentException("Jours obligatoires");
        }
        if (cible == null) {
            cible = CibleAnalyse.DERNIERE_SIMULATION_TERMINEE;
        }
    }

    /** Pour les ecrans : « 03:00, tous les jours ». */
    public String libelleCalendrier() {
        return heure + ", " + jours.libelle();
    }
}
