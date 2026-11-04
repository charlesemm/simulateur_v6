package ci.cnam.simulator.sink;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.monitoring.MetricsService;
import ci.cnam.simulator.persistence.IndisponibiliteBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ecrit les evenements en base par lots JDBC, via une file BORNEE.
 *
 * <ul>
 *   <li>Contre-pression : {@link #emettre} bloque quand la file est pleine (le generateur ralentit) ; jamais de
 *       file sans limite, jamais de perte silencieuse.</li>
 *   <li>Un lot = une transaction : insertion {@code ON CONFLICT (event_id) DO NOTHING} + mise a jour du
 *       checkpoint du run. Une erreur annule tout le lot. Rejouer un lot ne cree aucun doublon.</li>
 *   <li>Reprise : apres un arret brutal, {@link #checkpoint} donne le dernier etat valide du run ; ce qui etait
 *       encore dans la file (jamais ecrit) est a regenerer a partir de la.</li>
 *   <li>Un lot qui echoue est retente ; au-dela du nombre d'essais le sink passe en echec : {@link #emettre} et
 *       {@link #flush} levent une exception, donc le run s'arrete en ECHOUE au lieu de perdre des donnees.</li>
 * </ul>
 */
public class DatabaseSink implements EventSink, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSink.class);
    private static final long ATTENTE_OFFRE_MS = 100;

    private static final String INSERT = """
            insert into evenement (event_id, run_id, facture_id, domaine, type, timestamp_simule, timestamp_reel,
                                   payload, statut)
            values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'ECRIT')
            on conflict (event_id) do nothing""";

    private static final String INSERT_ANOMALIE = """
            insert into anomaly_injected (run_id, event_id, facture_id, type, champ, valeur_origine,
                                          valeur_corrompue, doublon_de)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing""";

    private static final String CHECKPOINT = """
            insert into checkpoint_evenement (run_id, dernier_event_id, compteur, date_maj)
            values (?, ?, ?, now())
            on conflict (run_id) do update
               set dernier_event_id = excluded.dernier_event_id,
                   compteur = checkpoint_evenement.compteur + excluded.compteur,
                   date_maj = now()""";

    /** Dernier point valide d'un run. */
    public record Checkpoint(UUID runId, UUID dernierEventId, long compteur) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final Clock horloge;
    private final MetricsService metriques;
    private final int tailleLot;
    private final long delaiMaxMs;
    private final int tentatives;
    private final long flushTimeoutMs;
    private final long indisponibiliteMaxMs;
    private final BlockingQueue<Object> file;   // SimEvent ou EcritureAnnexe, dans l ordre d emission
    private final Map<Class<?>, EcrivainAnnexe<?>> ecrivains = new HashMap<>();
    private final AtomicLong enAttente = new AtomicLong();   // emis mais pas encore valides en base
    private final Object signal = new Object();

    private volatile Throwable echec;
    private volatile boolean arret;
    private volatile boolean enPanne;   // un lot attend le retour de la base
    private Thread fil;

    public DatabaseSink(JdbcTemplate jdbc, PlatformTransactionManager tx, ObjectMapper json, Clock horloge,
                        MetricsService metriques, SimulateurProperties.Ecriture reglages) {
        this(jdbc, tx, json, horloge, metriques, reglages, List.of());
    }

    /** @param annexes les ecrivains des ecritures annexes (ex. factures) validees avec chaque lot */
    public DatabaseSink(JdbcTemplate jdbc, PlatformTransactionManager tx, ObjectMapper json, Clock horloge,
                        MetricsService metriques, SimulateurProperties.Ecriture reglages,
                        List<? extends EcrivainAnnexe<?>> annexes) {
        annexes.forEach(e -> this.ecrivains.put(e.type(), e));
        if (reglages.tailleLot() < 1 || reglages.fileAttenteMax() < 1 || reglages.tentatives() < 1) {
            throw new IllegalArgumentException("simulateur.ecriture : taille-lot, file-attente-max et tentatives "
                    + "doivent etre >= 1");
        }
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(tx);
        this.json = json;
        this.horloge = horloge;
        this.metriques = metriques;
        this.tailleLot = reglages.tailleLot();
        this.delaiMaxMs = reglages.delaiMaxMs();
        this.tentatives = reglages.tentatives();
        this.flushTimeoutMs = reglages.flushTimeoutMs();
        this.indisponibiliteMaxMs = reglages.indisponibiliteMaxMs();
        this.file = new ArrayBlockingQueue<>(reglages.fileAttenteMax());
        metriques.surveillerFile(file::size);
    }

    /** Lance le fil d'ecriture. */
    public synchronized void demarrer() {
        if (fil != null) {
            return;
        }
        fil = new Thread(this::boucle, "ecriture-evenements");
        fil.setDaemon(true);
        fil.start();
    }

    @Override
    public void emettre(SimEvent evenement) {
        deposer(evenement);
    }

    /** Une ecriture annexe prend sa place dans la file, comme un evenement : meme contre-pression, meme lot. */
    @Override
    public void joindre(EcritureAnnexe annexe) {
        if (!ecrivains.containsKey(annexe.getClass())) {
            throw new IllegalArgumentException("Aucun ecrivain pour " + annexe.getClass().getName());
        }
        deposer(annexe);
    }

    private void deposer(Object element) {
        verifierSain();
        enAttente.incrementAndGet();
        try {
            // Bloque tant que la file est pleine : c'est la contre-pression.
            while (!file.offer(element, ATTENTE_OFFRE_MS, TimeUnit.MILLISECONDS)) {
                verifierSain();
            }
        } catch (InterruptedException e) {
            enAttente.decrementAndGet();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompu pendant l'emission d'un evenement", e);
        } catch (RuntimeException e) {
            enAttente.decrementAndGet();
            throw e;
        }
    }

    /** Attend que tout ce qui a ete emis soit valide en base. */
    @Override
    public void flush() {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushTimeoutMs);
        synchronized (signal) {
            while (enAttente.get() > 0) {
                verifierSain();
                long reste = limite - System.nanoTime();
                if (reste <= 0) {
                    throw new IllegalStateException("flush : " + enAttente.get() + " evenements non ecrits apres "
                            + flushTimeoutMs + " ms");
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(signal, Math.min(reste, TimeUnit.MILLISECONDS.toNanos(50)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("flush interrompu", e);
                }
            }
        }
        verifierSain();
    }

    /** Dernier point valide du run, s'il y en a un (base de la reprise). */
    public Optional<Checkpoint> checkpoint(UUID runId) {
        return jdbc.query("select run_id, dernier_event_id, compteur from checkpoint_evenement where run_id = ?",
                (rs, n) -> new Checkpoint(rs.getObject("run_id", UUID.class),
                        rs.getObject("dernier_event_id", UUID.class), rs.getLong("compteur")), runId)
                .stream().findFirst();
    }

    public int tailleFile() {
        return file.size();
    }

    /** Ecrit ce qui reste puis arrete le fil. Ce qui n'est pas ecrit apres le delai est signale, pas ignore. */
    @Override
    public void close() {
        try {
            if (enPanne) {
                // Base injoignable depuis un moment : attendre ici ne ferait que retarder l'extinction.
                LOG.error("Arret du sink pendant une panne de la base : {} evenements non ecrits", enAttente.get());
            } else if (echec == null && fil != null) {
                flush();
            }
        } catch (RuntimeException e) {
            LOG.error("Arret du sink : evenements non ecrits ({} restants)", enAttente.get(), e);
        } finally {
            arret = true;
            Thread f = fil;
            if (f != null) {
                f.interrupt();
                try {
                    f.join(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Ecrit un lot dans UNE transaction : evenements puis checkpoint. Toute exception annule l'ensemble.
     * Public pour pouvoir tester l'atomicite sans passer par la file.
     */
    public void ecrireLot(List<SimEvent> lot) {
        ecrireLot(lot, List.of());
    }

    /**
     * Idem, avec les ecritures annexes du lot (factures...) : elles sont ecrites AVANT les evenements, dans la meme
     * transaction. Un lot peut ne contenir que des annexes (leurs evenements suivent dans le lot suivant).
     */
    public void ecrireLot(List<SimEvent> lot, List<EcritureAnnexe> annexes) {
        if (lot.isEmpty() && annexes.isEmpty()) {
            return;
        }
        OffsetDateTime reel = OffsetDateTime.now(horloge);
        transaction.executeWithoutResult(status -> {
            ecrireAnnexes(annexes);
            if (lot.isEmpty()) {
                return;
            }
            Map<UUID, Set<UUID>> parRun = new LinkedHashMap<>();
            Map<UUID, UUID> dernierParRun = new HashMap<>();
            for (SimEvent e : lot) {
                parRun.computeIfAbsent(e.runId(), r -> new HashSet<>()).add(e.eventId());
                dernierParRun.put(e.runId(), e.eventId());
            }
            Map<UUID, Long> dejaEcrits = dejaEcritsParRun(lot);

            jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    SimEvent e = lot.get(i);
                    ps.setObject(1, e.eventId());
                    ps.setObject(2, e.runId());
                    ps.setObject(3, factureId(e));
                    ps.setString(4, e.domaine());
                    ps.setString(5, e.type());
                    ps.setTimestamp(6, Timestamp.valueOf(e.timestampSimule()));
                    ps.setObject(7, reel);
                    ps.setString(8, payload(e));
                }

                @Override
                public int getBatchSize() {
                    return lot.size();
                }
            });

            // Verite terrain dans la MEME transaction que les evenements : pas d'anomalie sans son evenement.
            List<Object[]> anomalies = new ArrayList<>();
            for (SimEvent e : lot) {
                for (AnomalieTracee a : e.anomalies()) {
                    anomalies.add(new Object[] {e.runId(), e.eventId(), factureId(e), a.type(), a.champ(),
                            a.valeurOrigine(), a.valeurCorrompue(), a.doublonDe()});
                }
            }
            if (!anomalies.isEmpty()) {
                jdbc.batchUpdate(INSERT_ANOMALIE, anomalies, anomalies.size(), (ps, ligne) -> {
                    for (int i = 0; i < ligne.length; i++) {
                        ps.setObject(i + 1, ligne[i]);
                    }
                });
            }

            for (Map.Entry<UUID, Set<UUID>> run : parRun.entrySet()) {
                long nouveaux = run.getValue().size() - dejaEcrits.getOrDefault(run.getKey(), 0L);
                if (nouveaux > 0) {   // un lot entierement rejoue ne fait pas reculer le checkpoint
                    jdbc.update(CHECKPOINT, run.getKey(), dernierParRun.get(run.getKey()), nouveaux);
                }
            }
        });
    }

    private void ecrireAnnexes(List<EcritureAnnexe> annexes) {
        if (annexes.isEmpty()) {
            return;
        }
        Map<Class<?>, List<EcritureAnnexe>> parType = new LinkedHashMap<>();
        for (EcritureAnnexe a : annexes) {
            parType.computeIfAbsent(a.getClass(), c -> new ArrayList<>()).add(a);
        }
        parType.forEach((type, liste) -> ecrireGroupe(ecrivains.get(type), liste));
    }

    @SuppressWarnings("unchecked")
    private <T extends EcritureAnnexe> void ecrireGroupe(EcrivainAnnexe<T> ecrivain, List<EcritureAnnexe> liste) {
        ecrivain.ecrire(jdbc, liste.stream().map(a -> (T) a).toList());
    }

    private Map<UUID, Long> dejaEcritsParRun(List<SimEvent> lot) {
        UUID[] ids = lot.stream().map(SimEvent::eventId).distinct().toArray(UUID[]::new);
        Map<UUID, Long> resultat = new HashMap<>();
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "select run_id, count(*) as n from evenement where event_id = any(?) group by run_id");
            Array tableau = con.createArrayOf("uuid", ids);
            ps.setArray(1, tableau);
            return ps;
        }, rs -> {
            resultat.put(rs.getObject("run_id", UUID.class), rs.getLong("n"));
        });
        return resultat;
    }

    private static UUID factureId(SimEvent e) {
        Object valeur = e.payload() == null ? null : e.payload().get("factureId");
        return valeur == null ? null : UUID.fromString(valeur.toString());
    }

    private String payload(SimEvent e) {
        try {
            return json.writeValueAsString(e.payload() == null ? Map.of() : e.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload non serialisable pour l'evenement " + e.eventId(), ex);
        }
    }

    private void verifierSain() {
        Throwable t = echec;
        if (t != null) {
            throw new IllegalStateException("L'ecriture des evenements en base a echoue : " + t.getMessage(), t);
        }
    }

    private void boucle() {
        List<Object> lot = new ArrayList<>(tailleLot);
        while (!arret) {
            lot.clear();
            try {
                Object premier = file.poll(100, TimeUnit.MILLISECONDS);
                if (premier == null) {
                    continue;
                }
                lot.add(premier);
                long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMaxMs);
                file.drainTo(lot, tailleLot - 1);
                while (lot.size() < tailleLot) {
                    long reste = limite - System.nanoTime();
                    if (reste <= 0) {
                        break;
                    }
                    Object suivant = file.poll(reste, TimeUnit.NANOSECONDS);
                    if (suivant == null) {
                        break;
                    }
                    lot.add(suivant);
                    file.drainTo(lot, tailleLot - lot.size());
                }
            } catch (InterruptedException e) {
                if (lot.isEmpty()) {
                    return;
                }
            }
            if (!lot.isEmpty() && !ecrireAvecReprises(lot)) {
                return;
            }
        }
    }

    /**
     * Ecrit un lot, avec reprises. Deux regimes :
     * <ul>
     *   <li>vraie erreur (contrainte, SQL) : {@code tentatives} essais puis echec ;</li>
     *   <li>base INDISPONIBLE (liaison coupee) : on patiente jusqu'a {@code indisponibiliteMaxMs}. Le lot reste
     *       en memoire, la file se remplit, le generateur ralentit (contre-pression) : rien n'est perdu.</li>
     * </ul>
     *
     * @return false si le sink est passe en echec definitif
     */
    private boolean ecrireAvecReprises(List<Object> elements) {
        List<SimEvent> lot = new ArrayList<>(elements.size());
        List<EcritureAnnexe> annexes = new ArrayList<>();
        for (Object element : elements) {
            if (element instanceof SimEvent evenement) {
                lot.add(evenement);
            } else {
                annexes.add((EcritureAnnexe) element);
            }
        }
        Throwable derniere = null;
        int essaisOrdinaires = 0;
        int pannes = 0;
        long limitePanne = 0;
        while (!arret) {
            long debut = System.nanoTime();
            try {
                ecrireLot(lot, annexes);
                metriques.lotEcrit(lot.size(), Duration.ofNanos(System.nanoTime() - debut));
                enAttente.addAndGet(-elements.size());
                synchronized (signal) {
                    signal.notifyAll();
                }
                enPanne = false;
                if (pannes > 0) {
                    LOG.info("Base de nouveau disponible : lot de {} evenements ecrit apres {} essais en panne",
                            lot.size(), pannes);
                }
                return true;
            } catch (RuntimeException e) {
                derniere = e;
                metriques.lotEnEchec();
                boolean panne = IndisponibiliteBase.estCausee(e);
                if (panne) {
                    enPanne = true;
                    if (pannes++ == 0) {
                        limitePanne = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(indisponibiliteMaxMs);
                        LOG.warn("Base indisponible ({}), le lot de {} evenements est conserve et retente",
                                e.getClass().getSimpleName(), lot.size());
                    }
                    if (System.nanoTime() > limitePanne) {
                        break;
                    }
                } else {
                    LOG.warn("Ecriture du lot ({} evenements) en echec, essai {}/{}", lot.size(),
                            essaisOrdinaires + 1, tentatives, e);
                    if (++essaisOrdinaires >= tentatives) {
                        break;
                    }
                }
                try {
                    Thread.sleep(panne ? Math.min(1_000L, 100L * pannes) : Math.min(1_000L, 50L << essaisOrdinaires));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (derniere == null) {
            return false;   // arret demande avant toute tentative
        }
        enPanne = false;
        echec = derniere;
        LOG.error("Ecriture abandonnee : {} elements du lot perdus, le run doit s'arreter", elements.size());
        synchronized (signal) {
            signal.notifyAll();
        }
        return false;
    }
}
