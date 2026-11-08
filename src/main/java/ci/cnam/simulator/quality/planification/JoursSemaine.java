package ci.cnam.simulator.quality.planification;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Les jours ou une planification s'applique. Saisis en francais, stockes en texte :
 * {@code TOUS}, ou une liste comme {@code LUNDI,MARDI}.
 *
 * <p>Le texte reste lisible en base : un exploitant qui regarde la table comprend la regle sans
 * table de correspondance.
 */
public record JoursSemaine(Set<DayOfWeek> jours) {

    /** Mot-cle qui vaut les sept jours. */
    public static final String TOUS = "TOUS";

    private static final List<String> NOMS = List.of(
            "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");

    public JoursSemaine(Set<DayOfWeek> jours) {
        if (jours == null || jours.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucun jour retenu : une planification sans jour ne s'ouvrirait jamais");
        }
        this.jours = EnumSet.copyOf(jours);
    }

    /** Les sept jours. */
    public static JoursSemaine tous() {
        return new JoursSemaine(EnumSet.allOf(DayOfWeek.class));
    }

    /**
     * Lit la valeur stockee ou saisie. Tolere les espaces, la casse et les separateurs doubles.
     *
     * @throws IllegalArgumentException si un nom de jour est inconnu (les valeurs permises sont dans le message)
     */
    public static JoursSemaine lire(String valeur) {
        String propre = valeur == null ? "" : valeur.strip().toUpperCase(Locale.ROOT);
        if (propre.isEmpty() || TOUS.equals(propre)) {
            return tous();
        }
        Set<DayOfWeek> retenus = EnumSet.noneOf(DayOfWeek.class);
        for (String nom : propre.split("[,;\\s]+")) {
            if (nom.isEmpty()) {
                continue;
            }
            int index = NOMS.indexOf(nom);
            if (index < 0) {
                throw new IllegalArgumentException("Jour inconnu '" + nom + "' (valeurs permises : "
                        + TOUS + ", " + String.join(", ", NOMS) + ")");
            }
            retenus.add(DayOfWeek.of(index + 1));
        }
        return new JoursSemaine(retenus);
    }

    public boolean contient(DayOfWeek jour) {
        return jours.contains(jour);
    }

    /** Valeur a stocker : {@code TOUS} quand les sept jours sont retenus. */
    public String texte() {
        if (jours.size() == 7) {
            return TOUS;
        }
        return jours.stream().sorted().map(j -> NOMS.get(j.getValue() - 1))
                .reduce((a, b) -> a + "," + b).orElseThrow();
    }

    /** Pour les ecrans : « tous les jours », ou la liste en clair. */
    public String libelle() {
        return jours.size() == 7 ? "tous les jours" : texte().toLowerCase(Locale.ROOT).replace(",", ", ");
    }
}
