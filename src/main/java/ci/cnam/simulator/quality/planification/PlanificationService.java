package ci.cnam.simulator.quality.planification;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.quality.AnalyseAttendue;
import ci.cnam.simulator.quality.AnalysesAttendues;
import ci.cnam.simulator.quality.SuiviEcheances;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le calendrier des analyses : creer des regles, ouvrir leurs echeances, et suivre pour chacune si
 * le script est venu et si son rapport est arrive.
 *
 * <p>Echo ne declenche RIEN : il ouvre une echeance, le script vient la lire par
 * {@code GET /api/analyses/attendues} et reste maitre de son execution. Ce service est la source de
 * verite de cet endpoint : il publie {@link AnalysesAttendues}, que le prompt 14 lisait deja par un
 * {@code ObjectProvider} — ni l'endpoint ni le script n'ont eu a changer.
 *
 * <p>L'heure vient d'une {@link Clock} injectee, jamais de {@code Instant.now()} : les tests
 * pilotent le temps sans attendre.
 */
@Service
public class PlanificationService implements AnalysesAttendues, SuiviEcheances {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificationService.class);

    /**
     * Au-dela, on ne rouvre pas le passe. Une application arretee trois jours ne doit pas, au
     * redemarrage, ouvrir trois jours d'echeances d'un coup : elle reprend la journee ecoulee.
     */
    static final Duration RATTRAPAGE_MAX = Duration.ofDays(1);

    /** Nombre d'echeances rendues aux ecrans par defaut. */
    public static final int HISTORIQUE_PAR_DEFAUT = 50;

    private final PlanificationStore store;
    private final Clock horloge;
    private final Duration delaiRapport;
    private final ZoneId fuseau;

    public PlanificationService(PlanificationStore store, Clock horloge, SimulateurProperties proprietes) {
        this.store = store;
        this.horloge = horloge;
        this.delaiRapport = Duration.ofMinutes(proprietes.qualite().delaiRapportMinutes());
        this.fuseau = ZoneId.of(proprietes.qualite().fuseau());
    }

    /** Le fuseau dans lequel l'heure saisie est comprise : c'est l'heure du serveur. */
    public ZoneId fuseau() {
        return fuseau;
    }

    /** Delai au-dela duquel une echeance retiree sans rapport devient un SILENCE. */
    public Duration delaiRapport() {
        return delaiRapport;
    }

    // ------------------------------------------------------------------ les regles

    /**
     * Cree une regle de calendrier. L'heure est celle du serveur, dans le fuseau {@link #fuseau()}.
     *
     * @throws IllegalArgumentException libelle vide, heure absente ou jour inconnu
     */
    @Transactional
    public Planification creer(String libelle, LocalTime heure, String joursSemaine, CibleAnalyse cible) {
        Instant maintenant = horloge.instant();
        Planification planification = new Planification(UUID.randomUUID(), libelle, heure,
                JoursSemaine.lire(joursSemaine), cible, true, maintenant, maintenant);
        store.creer(planification);
        return planification;
    }

    @Transactional
    public boolean activer(UUID id, boolean actif) {
        return store.changerActivite(id, actif, horloge.instant());
    }

    @Transactional
    public boolean supprimer(UUID id) {
        return store.supprimer(id);
    }

    public List<Planification> planifications() {
        return store.planifications();
    }

    public Optional<Planification> planification(UUID id) {
        return store.planification(id);
    }

    /** L'historique des echeances, de la plus recente a la plus ancienne. */
    public List<Echeance> historique() {
        return store.echeances(HISTORIQUE_PAR_DEFAUT);
    }

    // ------------------------------------------------------- ce que le script vient lire

    /**
     * Les echeances reellement ATTENDUE, avec la simulation visee et le chemin de retrait de son
     * export. Une echeance SANS_CIBLE n'y figure pas : il n'y a rien a retirer.
     */
    @Override
    public List<AnalyseAttendue> enAttente() {
        List<AnalyseAttendue> attendues = new ArrayList<>();
        for (Echeance e : store.echeancesAuStatut(StatutAnalyse.ATTENDUE)) {
            if (e.runId() == null) {
                continue;
            }
            attendues.add(new AnalyseAttendue(e.id(), e.runId(), e.dateEcheance(),
                    e.dateEcheance().plus(delaiRapport), e.statut().name(),
                    AnalyseAttendue.cheminExport(e.runId())));
        }
        return attendues;
    }

    // ------------------------------------------------------- le report du passage du script

    @Override
    @Transactional
    public void retraitConstate(UUID runId, String detail) {
        avancer(runId, StatutAnalyse.RETIREE, horloge.instant(), null, detail);
    }

    @Override
    @Transactional
    public void rapportConstate(UUID runId, String detail) {
        avancer(runId, StatutAnalyse.RAPPORT_RECU, null, horloge.instant(), detail);
    }

    @Override
    @Transactional
    public void refusConstate(UUID runId, String detail) {
        avancer(runId, StatutAnalyse.RAPPORT_REFUSE, null, horloge.instant(), detail);
    }

    private void avancer(UUID runId, StatutAnalyse statut, Instant retrait, Instant rapport, String detail) {
        Optional<Echeance> echeance = store.echeanceOuvertePour(runId);
        if (echeance.isEmpty()) {
            // Le script a le droit de travailler hors planification : ce n'est pas une anomalie.
            LOG.debug("Aucune echeance ouverte pour le run {} : {} non reporte sur le calendrier", runId, statut);
            return;
        }
        store.avancer(echeance.get().id(), statut, retrait, rapport, tronquer(detail));
    }

    // ------------------------------------------------------- l'ouverture des echeances

    /**
     * Ouvre les echeances dues et non encore ouvertes. Idempotent : l'unicite
     * {@code (planification_id, date_echeance)} fait que deux passages dans la meme minute n'ouvrent
     * qu'une echeance.
     *
     * @return le nombre d'echeances reellement ouvertes par cet appel
     */
    @Transactional
    public int ouvrirEcheancesDues() {
        Instant maintenant = horloge.instant();
        int ouvertes = 0;
        for (Planification p : store.planifications()) {
            if (!p.actif()) {
                continue;
            }
            for (Instant echeance : dues(p, maintenant)) {
                if (ouvrir(p, echeance)) {
                    ouvertes++;
                }
            }
        }
        return ouvertes;
    }

    /**
     * Les instants dus pour cette regle, strictement apres la derniere echeance ouverte (ou sa
     * creation) et jusqu'a maintenant inclus. Le rattrapage est borne a {@link #RATTRAPAGE_MAX}.
     */
    private List<Instant> dues(Planification p, Instant maintenant) {
        Instant depuis = store.derniereEcheance(p.id()).orElse(p.dateCreation());
        Instant plancher = maintenant.minus(RATTRAPAGE_MAX);
        if (depuis.isBefore(plancher)) {
            depuis = plancher;
        }
        List<Instant> dues = new ArrayList<>();
        LocalDate jour = depuis.atZone(fuseau).toLocalDate();
        LocalDate fin = maintenant.atZone(fuseau).toLocalDate();
        while (!jour.isAfter(fin)) {
            if (p.jours().contient(jour.getDayOfWeek())) {
                Instant instant = ZonedDateTime.of(jour, p.heure(), fuseau).toInstant();
                if (instant.isAfter(depuis) && !instant.isAfter(maintenant)) {
                    dues.add(instant);
                }
            }
            jour = jour.plusDays(1);
        }
        return dues;
    }

    /**
     * Ouvre une echeance pour cette regle a cet instant. Aucune simulation terminee a viser :
     * l'echeance s'ouvre QUAND MEME, au statut SANS_CIBLE, avec son explication. Elle ne tombe pas
     * en erreur et ne disparait pas silencieusement.
     */
    private boolean ouvrir(Planification p, Instant echeance) {
        Optional<UUID> cible = store.derniereSimulationTerminee();
        StatutAnalyse statut = cible.isPresent() ? StatutAnalyse.ATTENDUE : StatutAnalyse.SANS_CIBLE;
        String detail = cible.isPresent()
                ? "Echeance ouverte par « " + p.libelle() + " » sur la derniere simulation terminee"
                : "Aucune simulation terminee a l'heure dite : rien a analyser. L'echeance est "
                        + "conservee telle quelle, elle ne sera pas rouverte.";
        boolean ouverte = store.ouvrir(new Echeance(UUID.randomUUID(), p.id(), cible.orElse(null),
                echeance, null, null, statut, tronquer(detail)));
        if (ouverte) {
            LOG.info("Analyse planifiee ouverte : {} a {} ({}) -> {}", p.libelle(), echeance, statut,
                    cible.map(UUID::toString).orElse("aucune simulation"));
        }
        return ouverte;
    }

    /**
     * Passe en SILENCE les echeances retirees dont le delai de rapport est depasse. Un silence n'est
     * JAMAIS un taux de detection de 0 % : c'est un echec d'echange, et il se dit comme tel.
     *
     * @return le nombre d'echeances passees en SILENCE
     */
    @Transactional
    public int marquerSilences() {
        Instant maintenant = horloge.instant();
        int silences = 0;
        for (Echeance e : store.echeancesAuStatut(StatutAnalyse.RETIREE)) {
            Instant reference = e.dateRetrait() == null ? e.dateEcheance() : e.dateRetrait();
            if (!maintenant.isBefore(reference.plus(delaiRapport))) {
                store.avancer(e.id(), StatutAnalyse.SILENCE, null, null, tronquer(
                        "Le jeu a ete retire et aucun rapport n'est arrive dans les "
                                + delaiRapport.toMinutes() + " minutes : aucune mesure n'est possible."));
                silences++;
            }
        }
        return silences;
    }

    private static String tronquer(String detail) {
        if (detail == null) {
            return null;
        }
        String propre = detail.strip();
        return propre.length() <= Echeance.DETAIL_MAX ? propre : propre.substring(0, Echeance.DETAIL_MAX);
    }
}
