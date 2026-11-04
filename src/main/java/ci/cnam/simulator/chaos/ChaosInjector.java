package ci.cnam.simulator.chaos;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Declenche et retire les actions de chaos d'un run. Regles de securite :
 * <ul>
 *   <li>toute action a une duree, plafonnee par {@code simulateur.chaos.duree-max-secondes} ;</li>
 *   <li>une action prend fin toute seule a l'echeance, ou des que le run n'est plus actif ;</li>
 *   <li>une seule action active par type ; {@link #retablirTout} les annule toutes immediatement ;</li>
 *   <li>chaque action laisse une ligne d'audit (qui, quand, quoi) dans chaos_action ;</li>
 *   <li>au demarrage du simulateur, Toxiproxy est remis a zero : un chaos ne survit pas a un redemarrage.</li>
 * </ul>
 * Le simulateur n'agit QUE par l'API de Toxiproxy et par son propre etat interne.
 */
@Service
public class ChaosInjector {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosInjector.class);
    private static final long TOP_MS = 250;
    private static final String MODE_DESACTIVER = "DESACTIVER";
    private static final String MODE_RESET_PEER = "RESET_PEER";

    private final ToxiproxyClient toxiproxy;
    private final LoadMultiplier charge;
    private final TroncatureMessages troncature;
    private final ChaosActionStore store;
    private final RunManager runs;
    private final Clock horloge;
    private final Duration dureeMax;
    private final int latenceMaxMs;
    private final Map<UUID, ChaosAction> actives = new LinkedHashMap<>();   // protege par this
    private final ScheduledExecutorService minuteur = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chaos-echeances");
        t.setDaemon(true);
        return t;
    });
    private long dernierAvertissement;

    public ChaosInjector(ToxiproxyClient toxiproxy, LoadMultiplier charge, TroncatureMessages troncature,
                         ChaosActionStore store, RunManager runs, Clock horloge, SimulateurProperties proprietes) {
        this.toxiproxy = toxiproxy;
        this.charge = charge;
        this.troncature = troncature;
        this.store = store;
        this.runs = runs;
        this.horloge = horloge;
        this.dureeMax = Duration.ofSeconds(proprietes.chaos().dureeMaxSecondes());
        this.latenceMaxMs = proprietes.chaos().latenceMaxMs();
        minuteur.scheduleWithFixedDelay(this::verifierEcheances, TOP_MS, TOP_MS, TimeUnit.MILLISECONDS);
    }

    /** Duree maximale imposee a une action. */
    public Duration dureeMax() {
        return dureeMax;
    }

    /**
     * Declenche une action sur un run actif.
     *
     * @throws IllegalArgumentException run inconnu, duree ou parametre invalide
     * @throws IllegalStateException    run inactif, ou une action du meme type est deja en cours
     * @throws ChaosIndisponibleException Toxiproxy injoignable
     */
    public synchronized ChaosAction declencher(UUID runId, ChaosType type, Duration duree,
                                               Map<String, Object> demande, String auteur) {
        if (type == null) {
            throw new IllegalArgumentException("type : obligatoire " + List.of(ChaosType.values()));
        }
        if (duree == null || duree.isZero() || duree.isNegative()) {
            throw new IllegalArgumentException("duree : obligatoire et strictement positive");
        }
        if (duree.compareTo(dureeMax) > 0) {
            throw new IllegalArgumentException("duree : " + duree.toSeconds() + " s depasse la duree maximale de "
                    + dureeMax.toSeconds() + " s");
        }
        Map<String, Object> parametres = normaliser(type, demande == null ? Map.of() : demande);
        RunInfo run = runs.info(runId);   // IllegalArgumentException "Run inconnu" -> 404
        if (!run.etat().actif()) {
            throw new IllegalStateException("Impossible de declencher un chaos : le run " + runId + " est "
                    + run.etat());
        }
        if (actives.values().stream().anyMatch(a -> a.type() == type)) {
            throw new IllegalStateException("Une action " + type + " est deja en cours : la retablir d'abord");
        }

        Instant debut = horloge.instant();
        ChaosAction action = new ChaosAction(UUID.randomUUID(), runId, type, parametres, debut, debut.plus(duree),
                null, null, auteur, 0);
        store.creer(action);   // la trace existe AVANT l'effet : jamais de chaos sans audit
        try {
            appliquer(action);
        } catch (RuntimeException e) {
            store.supprimer(action.id());
            throw e;
        }
        actives.put(action.id(), action);
        LOG.warn("CHAOS demarre : {} {} sur le run {} pour {} s (auteur : {})", type, parametres, runId,
                duree.toSeconds(), auteur);
        return action;
    }

    /**
     * Annule IMMEDIATEMENT toutes les actions actives du run et nettoie Toxiproxy (y compris des toxics poses a la
     * main). Utilisable meme si le run est termine.
     */
    public synchronized List<ChaosAction> retablirTout(UUID runId, String auteur) {
        runs.info(runId);   // 404 si le run n'existe pas
        LOG.warn("CHAOS : tout retablir demande pour le run {} (auteur : {})", runId, auteur);
        boolean toxiproxyUtilise = actives.values().stream().anyMatch(a -> a.runId().equals(runId)
                && (a.type() == ChaosType.COUPURE_BASE || a.type() == ChaosType.LATENCE));
        try {
            toxiproxy.retablirTout();
        } catch (ChaosIndisponibleException e) {
            if (toxiproxyUtilise) {
                throw e;   // la liaison est peut-etre encore coupee : ne pas pretendre le contraire
            }
            LOG.info("Toxiproxy injoignable pendant 'tout retablir' ({}) : aucune action ne l'utilisait", e.getMessage());
        }
        List<ChaosAction> terminees = new ArrayList<>();
        for (ChaosAction a : List.copyOf(actives.values())) {
            if (a.runId().equals(runId)) {
                terminees.add(terminer(a, "RETABLIE"));
            }
        }
        return terminees;
    }

    /** Historique et actions actives d'un run, de la plus ancienne a la plus recente. */
    public List<ChaosAction> actions(UUID runId) {
        runs.info(runId);
        return store.lister(runId);
    }

    public synchronized boolean actif(ChaosType type) {
        return actives.values().stream().anyMatch(a -> a.type() == type);
    }

    /** Au demarrage : un chaos ne survit pas a un redemarrage du simulateur (Toxiproxy, lui, a pu rester coupe). */
    @EventListener(ApplicationReadyEvent.class)
    void nettoyerAuDemarrage() {
        try {
            int n = store.interrompre(horloge.instant());
            if (n > 0) {
                LOG.warn("{} action(s) de chaos restees ouvertes marquees INTERROMPUE", n);
            }
        } catch (RuntimeException e) {
            LOG.warn("Nettoyage de chaos_action impossible au demarrage : {}", e.getMessage());
        }
        try {
            toxiproxy.retablirTout();
        } catch (RuntimeException e) {
            LOG.info("Toxiproxy non remis a zero au demarrage ({})", e.getMessage());
        }
    }

    @PreDestroy
    synchronized void arreter() {
        minuteur.shutdownNow();
        for (ChaosAction a : List.copyOf(actives.values())) {
            try {
                terminer(a, "RUN_TERMINE");
            } catch (RuntimeException e) {
                LOG.warn("Chaos {} non retire a l'extinction : {}", a.type(), e.getMessage());
            }
        }
    }

    private synchronized void verifierEcheances() {
        try {
            Instant maintenant = horloge.instant();
            for (ChaosAction a : List.copyOf(actives.values())) {
                String motif = null;
                if (!maintenant.isBefore(a.finPrevue())) {
                    motif = "EXPIREE";
                } else if (!runActif(a.runId())) {
                    motif = "RUN_TERMINE";
                }
                if (motif != null) {
                    try {
                        terminer(a, motif);
                    } catch (RuntimeException e) {
                        avertirSeulementParfois(a, e);   // reessaye au prochain top
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("Verification des echeances de chaos en erreur", t);
        }
    }

    private boolean runActif(UUID runId) {
        try {
            return runs.info(runId).etat().actif();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void avertirSeulementParfois(ChaosAction a, RuntimeException e) {
        long maintenant = System.nanoTime();
        if (maintenant - dernierAvertissement > TimeUnit.SECONDS.toNanos(10)) {
            dernierAvertissement = maintenant;
            LOG.error("Chaos {} NON retire (nouvel essai dans {} ms) : {}", a.type(), TOP_MS, e.getMessage());
        }
    }

    /** Retire l'effet puis ferme la ligne d'audit. L'ordre compte : la base doit etre revenue avant d'y ecrire. */
    private ChaosAction terminer(ChaosAction a, String motif) {
        long touches = a.evenementsTouches();
        switch (a.type()) {
            case COUPURE_BASE -> {
                if (MODE_RESET_PEER.equals(a.parametres().get("mode"))) {
                    toxiproxy.supprimerToxic(ToxiproxyClient.TOXIC_RESET);
                } else {
                    toxiproxy.activerProxy();
                }
            }
            case LATENCE -> toxiproxy.supprimerToxic(ToxiproxyClient.TOXIC_LATENCE);
            case SURCHARGE -> charge.retablir();
            case MESSAGE_TRONQUE -> touches = troncature.desactiver();
        }
        actives.remove(a.id());
        ChaosAction finie = a.terminee(horloge.instant(), motif, touches);
        fermerLaTrace(finie);
        LOG.warn("CHAOS termine : {} sur le run {} ({})", a.type(), a.runId(), motif);
        return finie;
    }

    /** La base vient d'etre rendue : ses connexions du pool peuvent etre mortes, on reessaye un peu. */
    private void fermerLaTrace(ChaosAction finie) {
        RuntimeException derniere = null;
        for (int essai = 0; essai < 20; essai++) {
            try {
                store.terminer(finie.id(), finie.fin(), finie.motifFin(), finie.evenementsTouches());
                return;
            } catch (RuntimeException e) {
                derniere = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOG.error("Trace d'audit du chaos {} non fermee : {}", finie.id(), derniere == null ? "interrompu"
                : derniere.getMessage());
    }

    private void appliquer(ChaosAction a) {
        Map<String, Object> p = a.parametres();
        switch (a.type()) {
            case COUPURE_BASE -> {
                if (MODE_RESET_PEER.equals(p.get("mode"))) {
                    toxiproxy.ajouterResetPeer();
                } else {
                    toxiproxy.desactiverProxy();
                }
            }
            case LATENCE -> toxiproxy.ajouterLatence((Integer) p.get("latenceMs"), (Integer) p.get("jitterMs"));
            case SURCHARGE -> charge.fixer((Integer) p.get("multiplicateur"));
            case MESSAGE_TRONQUE -> troncature.activer((Double) p.get("proportion"));
        }
    }

    /** Valide les parametres d'un type et les complete par leurs valeurs par defaut. Cles inconnues : refusees. */
    private Map<String, Object> normaliser(ChaosType type, Map<String, Object> demande) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        switch (type) {
            case COUPURE_BASE -> {
                refuserInconnus(type, demande, Set.of("mode"));
                Object mode = demande.getOrDefault("mode", MODE_DESACTIVER);
                if (!MODE_DESACTIVER.equals(mode) && !MODE_RESET_PEER.equals(mode)) {
                    throw new IllegalArgumentException("parametres.mode : " + MODE_DESACTIVER + " ou "
                            + MODE_RESET_PEER + " attendu");
                }
                resultat.put("mode", mode);
            }
            case LATENCE -> {
                refuserInconnus(type, demande, Set.of("latenceMs", "jitterMs"));
                int latence = entier(demande, "latenceMs", null);
                if (latence < 1 || latence > latenceMaxMs) {
                    throw new IllegalArgumentException("parametres.latenceMs : entre 1 et " + latenceMaxMs
                            + " attendu (recu " + latence + ")");
                }
                int jitter = entier(demande, "jitterMs", 0);
                if (jitter < 0 || jitter > latence) {
                    throw new IllegalArgumentException("parametres.jitterMs : entre 0 et latenceMs attendu");
                }
                resultat.put("latenceMs", latence);
                resultat.put("jitterMs", jitter);
            }
            case SURCHARGE -> {
                refuserInconnus(type, demande, Set.of("multiplicateur"));
                int facteur = entier(demande, "multiplicateur", null);
                if (!LoadMultiplier.PALIERS.contains(facteur)) {
                    throw new IllegalArgumentException("parametres.multiplicateur : " + new java.util.TreeSet<>(
                            LoadMultiplier.PALIERS) + " attendu (recu " + facteur + ")");
                }
                resultat.put("multiplicateur", facteur);
            }
            case MESSAGE_TRONQUE -> {
                refuserInconnus(type, demande, Set.of("proportion"));
                Object brut = demande.getOrDefault("proportion", 0.1);
                if (!(brut instanceof Number n) || !(n.doubleValue() > 0 && n.doubleValue() <= 1)) {
                    throw new IllegalArgumentException("parametres.proportion : nombre dans ]0 ; 1] attendu");
                }
                resultat.put("proportion", n(brut));
            }
        }
        return resultat;
    }

    private static Double n(Object brut) {
        return ((Number) brut).doubleValue();
    }

    private static void refuserInconnus(ChaosType type, Map<String, Object> demande, Set<String> permis) {
        for (String cle : demande.keySet()) {
            if (!permis.contains(cle)) {
                throw new IllegalArgumentException("parametres." + cle + " : inconnu pour " + type + " (attendus : "
                        + new java.util.TreeSet<>(permis) + ")");
            }
        }
    }

    private static int entier(Map<String, Object> demande, String cle, Integer parDefaut) {
        Object valeur = demande.get(cle);
        if (valeur == null) {
            if (parDefaut == null) {
                throw new IllegalArgumentException("parametres." + cle + " : obligatoire");
            }
            return parDefaut;
        }
        if (valeur instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                && Math.abs(n.doubleValue()) < Integer.MAX_VALUE) {
            return n.intValue();
        }
        throw new IllegalArgumentException("parametres." + cle + " : entier attendu");
    }
}
