package ci.cnam.simulator.quality.planification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acces aux tables {@code planification_analyse} et {@code analyse_attendue}. Implemente dans persistence/. */
public interface PlanificationStore {

    void creer(Planification planification);

    /**
     * Active ou desactive une regle.
     *
     * @return vrai si la regle existait
     */
    boolean changerActivite(UUID id, boolean actif, Instant maintenant);

    /**
     * Supprime une regle. L'historique de ses echeances survit ({@code planification_id} passe a nul) :
     * ce qui a eu lieu reste tracable.
     *
     * @return vrai si la regle existait
     */
    boolean supprimer(UUID id);

    List<Planification> planifications();

    Optional<Planification> planification(UUID id);

    /**
     * Ouvre une echeance. Ne fait RIEN si une echeance existe deja pour cette planification a cette
     * date : c'est ce qui rend la tache idempotente.
     *
     * @return vrai si l'echeance a bien ete ouverte par cet appel
     */
    boolean ouvrir(Echeance echeance);

    /** Les echeances, de la plus recente a la plus ancienne. */
    List<Echeance> echeances(int limite);

    /** Les echeances a un statut donne, de la plus ancienne a la plus recente. */
    List<Echeance> echeancesAuStatut(StatutAnalyse statut);

    /** La derniere echeance ouverte par cette planification, pour ne pas rouvrir le passe. */
    Optional<Instant> derniereEcheance(UUID planificationId);

    /**
     * L'echeance ouverte (ATTENDUE ou RETIREE) la plus ancienne qui vise ce run, s'il y en a une.
     * C'est elle que le passage du script fait avancer.
     */
    Optional<Echeance> echeanceOuvertePour(UUID runId);

    /** Fait avancer une echeance. Une date nulle n'efface pas celle qui est deja inscrite. */
    void avancer(UUID id, StatutAnalyse statut, Instant dateRetrait, Instant dateRapport, String detail);

    /** Le dernier run au statut ARRETE, c'est-a-dire « la derniere simulation terminee ». */
    Optional<UUID> derniereSimulationTerminee();
}
