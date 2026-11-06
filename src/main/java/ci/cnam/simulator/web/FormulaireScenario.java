package ci.cnam.simulator.web;

import java.util.List;

/**
 * Formulaire de l'editeur de scenarios. Les phases sont des colonnes paralleles (une ligne par phase).
 *
 * @param debutSimule date-heure ISO (ex. 2026-03-10T08:00:00), vide = valeur par defaut du chargeur
 * @param debit       parcours demarres par seconde reelle
 * @param examens     pourcentage de parcours avec examens
 * @param hospitalisation pourcentage de parcours avec hospitalisation
 * @param direct      pourcentage de prescription directe
 * @param anomaliesGlobal taux global d'anomalies en pourcent, vide ou 0 = flux propre
 */
public record FormulaireScenario(
        String nom,
        Long seed,
        Double accelerationTemps,
        String debutSimule,
        Double debit,
        Double examens,
        Double hospitalisation,
        Double direct,
        Double anomaliesGlobal,
        List<String> phaseDe,
        List<String> phaseA,
        List<String> phaseCharge) {

    public FormulaireScenario {
        phaseDe = phaseDe == null ? List.of() : List.copyOf(phaseDe);
        phaseA = phaseA == null ? List.of() : List.copyOf(phaseA);
        phaseCharge = phaseCharge == null ? List.of() : List.copyOf(phaseCharge);
    }
}
