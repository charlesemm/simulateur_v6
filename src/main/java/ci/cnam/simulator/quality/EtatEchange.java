package ci.cnam.simulator.quality;

import java.time.Instant;

/**
 * Ou en est l'echange pour une simulation, deduit de son journal. Quand {@code statut} n'est pas
 * {@link StatutEchange#mesurable()}, la comparaison ne rend AUCUN taux : ni un chiffre, ni 0 %.
 *
 * @param date   horodatage de l'echange qui determine cet etat
 * @param detail message affichable, deja redige
 */
public record EtatEchange(StatutEchange statut, Instant date, String detail) {

    public boolean echec() {
        return statut.echec();
    }

    public boolean mesurable() {
        return statut.mesurable();
    }

    public String libelle() {
        return statut.libelle();
    }
}
