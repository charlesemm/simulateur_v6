package ci.cnam.simulator.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Photo du simulateur envoyee au tableau de bord (une par seconde, en JSON, par le flux SSE).
 *
 * @param run                        run actif (en cours ou en pause), nul s'il n'y en a pas
 * @param debits                     debit mesure, rattache au domaine du scenario du run actif
 * @param evenementsEcritsParSeconde evenements reellement ecrits en base par seconde
 * @param lotsEnEchec                tentatives d'ecriture de lot en echec depuis le demarrage
 * @param fileTaille                 evenements en attente d'ecriture
 * @param latenceLotMs               duree moyenne recente d'ecriture d'un lot, en millisecondes
 * @param latenceLotMaxMs            duree maximale observee d'ecriture d'un lot, en millisecondes
 * @param base                       UP ou DOWN
 * @param proxy                      etat de la liaison avec la base (Toxiproxy)
 */
public record EtatTableauDeBord(
        Instant instant,
        RunVue run,
        List<DebitDomaine> debits,
        double evenementsEcritsParSeconde,
        long evenementsEcrits,
        long lotsEnEchec,
        long fileTaille,
        double latenceLotMs,
        double latenceLotMaxMs,
        String base,
        ProxyVue proxy) {

    public EtatTableauDeBord {
        debits = List.copyOf(debits);
    }

    public record RunVue(UUID id, String scenario, long seed, String etat, long parcoursGeneres,
                         long evenementsEmis, String erreur) {
    }

    public record DebitDomaine(String domaine, double parcoursParSeconde, double evenementsParSeconde) {
    }

    /** @param etat ACTIF, COUPE ou INJOIGNABLE ; {@code toxics} : perturbations posees sur la liaison */
    public record ProxyVue(String etat, List<String> toxics) {
        public ProxyVue {
            toxics = List.copyOf(toxics);
        }
    }
}
