package ci.cnam.simulator.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.sink.DatabaseSink;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Crash tests de bout en bout : un VRAI simulateur (moteur, sink par lots, API de chaos) ecrit dans un VRAI
 * PostgreSQL, a travers un VRAI Toxiproxy. Rien n'est simule : la liaison est coupee, ralentie, surchargee.
 *
 * <p>Les mesures (debits, temps de reprise) sont affichees en console avec le prefixe {@code CHAOS-MESURE} : elles
 * alimentent docs/04-chaos.md.
 */
@SpringBootTest(properties = {
        "simulateur.ecriture.taille-lot=20",
        "simulateur.ecriture.file-attente-max=200",
        "simulateur.ecriture.delai-max-ms=50",
        "simulateur.ecriture.indisponibilite-max-ms=120000",
        "simulateur.chaos.duree-max-secondes=120",
        "simulateur.toxiproxy.proxy=postgres"
})
@ActiveProfiles("test")
class ChaosIntegrationTest {

    private static final int PORT_PROXY = 8666;
    private static final Network RESEAU = Network.newNetwork();
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/library/postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withNetwork(RESEAU).withNetworkAliases("postgres").withStartupAttempts(3);
    static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0").asCompatibleSubstituteFor("shopify/toxiproxy"))
            .withNetwork(RESEAU);

    static {
        POSTGRES.start();
        TOXIPROXY.start();
        creerLeProxy();
        // Demarres une seule fois pour toute la classe ; arretes avec la JVM (meme si Ryuk est desactive).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TOXIPROXY.stop();
            POSTGRES.stop();
        }));
    }

    /** Reproduit docker/toxiproxy.json : l'application ne voit la base QUE par ce proxy. */
    private static void creerLeProxy() {
        try {
            HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest
                    .newBuilder(URI.create(urlToxiproxy() + "/proxies"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"postgres\",\"listen\":\"0.0.0.0:" + PORT_PROXY
                                    + "\",\"upstream\":\"postgres:5432\",\"enabled\":true}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 201) {
                throw new IllegalStateException("Creation du proxy refusee : " + r.statusCode() + " " + r.body());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String urlToxiproxy() {
        return "http://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getControlPort();
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", () -> "jdbc:postgresql://" + TOXIPROXY.getHost() + ":"
                + TOXIPROXY.getMappedPort(PORT_PROXY) + "/" + POSTGRES.getDatabaseName()
                + "?reWriteBatchedInserts=true");
        registre.add("spring.datasource.username", POSTGRES::getUsername);
        registre.add("spring.datasource.password", POSTGRES::getPassword);
        registre.add("simulateur.toxiproxy.url", ChaosIntegrationTest::urlToxiproxy);
    }

    @Autowired RunManager runs;
    @Autowired ChaosInjector chaos;
    @Autowired ToxiproxyClient toxiproxy;
    @Autowired ScenarioLoader loader;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseSink sink;
    @Autowired MeterRegistry metriques;

    private UUID runEnCours;

    @AfterEach
    void toutRemettreEnEtat() {
        // Quoi qu'il arrive dans un test : la liaison est rendue et le run arrete, pour ne pas polluer le suivant.
        toxiproxy.retablirTout();
        if (runEnCours != null) {
            try {
                chaos.retablirTout(runEnCours, "test");
                if (runs.info(runEnCours).etat().actif()) {
                    runs.arreter(runEnCours);
                }
            } finally {
                runEnCours = null;
            }
        }
    }

    private UUID lancer(int debit) {
        String nom = "chaos-" + UUID.randomUUID();
        runEnCours = runs.lancer(loader.lire("""
                nom: "%s"
                seed: %d
                accelerationTemps: 100
                domaines:
                  prestations: { debit: %d, parcours: { examens: 60%%, hospitalisation: 10%%, prescriptionDirecte: 30%% } }
                """.formatted(nom, System.nanoTime() % 1_000_000_000L, debit))).id();   // graine propre au run : meme graine = memes event_id
        return runEnCours;
    }

    private double ecrits() {
        return metriques.get("simulateur.evenements.ecrits").counter().count();
    }

    /** Evenements ecrits par seconde sur la fenetre, lus en memoire : fonctionne meme quand la base est coupee. */
    private double debitSur(Duration fenetre) throws InterruptedException {
        double avant = ecrits();
        long debut = System.nanoTime();
        Thread.sleep(fenetre.toMillis());
        return (ecrits() - avant) / ((System.nanoTime() - debut) / 1e9);
    }

    private void attendreDesEvenements(UUID run, long minimum) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .until(() -> jdbc.queryForObject("select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = ?", Long.class, run)
                        >= minimum);
    }

    /** Les trois garanties : rien de perdu, rien en double, aucune facture a moitie ecrite. */
    private void verifierAucunePerteAucunDoublonAucuneFactureIncomplete(UUID run) {
        RunInfo info = runs.info(run);
        long ecrits = jdbc.queryForObject("select count(*) from \"TB_ECHO_EVENEMENT\" where run_id = ?", Long.class, run);
        long distincts = jdbc.queryForObject("select count(distinct event_id) from \"TB_ECHO_EVENEMENT\" where run_id = ?",
                Long.class, run);
        assertThat(ecrits).as("evenements en base = evenements emis par le moteur").isEqualTo(info.evenementsEmis());
        assertThat(distincts).as("aucun doublon").isEqualTo(ecrits);
        assertThat(jdbc.queryForObject("select compteur from \"TB_ECHO_CHECKPOINT_EVENEMENT\" where run_id = ?", Long.class, run))
                .as("le checkpoint colle aux lignes reellement ecrites").isEqualTo(ecrits);

        Long nonTerminees = jdbc.queryForObject("""
                select count(*) from "TB_ECHO_FACTURE" where run_id = ?
                   and statut not in ('FINALISEE', 'REFUSEE', 'ABANDONNEE')""", Long.class, run);
        assertThat(nonTerminees).as("aucune facture restee a mi-parcours").isZero();
        Long sansEvenement = jdbc.queryForObject("""
                select count(*) from "TB_ECHO_FACTURE" f where f.run_id = ?
                   and not exists (select 1 from "TB_ECHO_EVENEMENT" e where e.facture_id = f.id)""", Long.class, run);
        assertThat(sansEvenement).as("aucune facture sans ses evenements").isZero();
        Long evenementOrphelin = jdbc.queryForObject("""
                select count(*) from "TB_ECHO_EVENEMENT" e where e.run_id = ? and e.facture_id is not null
                   and not exists (select 1 from "TB_ECHO_FACTURE" f where f.id = e.facture_id)""", Long.class, run);
        assertThat(evenementOrphelin).as("aucun evenement d'une facture inexistante").isZero();
        Long historiqueIncoherent = jdbc.queryForObject("""
                select count(*) from "TB_ECHO_FACTURE" f where f.run_id = ? and f.statut <>
                   (select h.nouveau_statut from "TB_ECHO_FACTURE_HISTORIQUE" h where h.facture_id = f.id
                     order by h.horodatage_reel desc, h.id desc limit 1)""", Long.class, run);
        assertThat(historiqueIncoherent).as("statut de la facture = dernier statut de son historique").isZero();
        // Chaque facture a ses lignes completes : le total est la somme de ses lignes.
        Long totalFaux = jdbc.queryForObject("""
                select count(*) from "TB_ECHO_FACTURE" f where f.run_id = ? and f.montant_total <>
                   coalesce((select sum(l.montant) from "TB_ECHO_FACTURE_LIGNE" l where l.facture_id = f.id), 0)""",
                Long.class, run);
        assertThat(totalFaux).as("montant de la facture = somme de ses lignes").isZero();
    }

    @Test
    void coupureDeTrenteSecondesEnPleinLotSansPerteNiDoublonNiFactureACheval() throws Exception {
        UUID run = lancer(20);
        attendreDesEvenements(run, 200);   // le run ecrit deja : la coupure tombera en plein flux
        double debitAvant = debitSur(Duration.ofSeconds(4));

        ChaosAction coupure = chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(30), Map.of(), "test");
        long debutCoupure = System.nanoTime();
        int filePic = 0;
        while (System.nanoTime() - debutCoupure < Duration.ofSeconds(31).toNanos()) {
            filePic = Math.max(filePic, sink.tailleFile());   // lecture en memoire : la base est coupee
            Thread.sleep(100);
        }

        // L'action prend fin toute seule a l'echeance ; le service n'est pas tombe.
        await().atMost(Duration.ofSeconds(15)).until(() -> !chaos.actif(ChaosType.COUPURE_BASE));
        assertThat(runs.info(run).etat()).as("le run a survecu a la coupure").isEqualTo(EtatRun.EN_COURS);
        Instant finCoupure = Instant.now();
        long avantReprise = (long) ecrits();
        await().atMost(Duration.ofSeconds(60)).until(() -> ecrits() > avantReprise + 200);
        double secondesDeReprise = Duration.between(finCoupure, Instant.now()).toMillis() / 1000.0;
        double debitApres = debitSur(Duration.ofSeconds(4));

        runs.arreter(run);   // ARRETE = tout ce qui a ete emis est en base

        ChaosAction tracee = chaos.actions(run).get(0);
        assertThat(tracee.id()).isEqualTo(coupure.id());
        assertThat(tracee.motifFin()).isEqualTo("EXPIREE");
        assertThat(tracee.fin()).isNotNull();
        assertThat(tracee.auteur()).isEqualTo("test");
        // La coupure a vraiment eu lieu : plus aucune ecriture pendant ~30 s.
        Double trouMax = jdbc.queryForObject("""
                select max(extract(epoch from (t - precedent))) from
                   (select timestamp_reel as t, lag(timestamp_reel) over (order by timestamp_reel) as precedent
                      from "TB_ECHO_EVENEMENT" where run_id = ?) x""", Double.class, run);
        System.out.printf("CHAOS-MESURE coupure-30s : debit avant = %.0f evt/s, trou d'ecriture = %.1f s, pic de la file = %d/200, "
                + "reprise (200 evenements de plus) en %.1f s apres le retablissement, debit apres = %.0f evt/s%n",
                debitAvant, trouMax, filePic, secondesDeReprise, debitApres);
        assertThat(trouMax).as("trou d'ecriture pendant la coupure").isGreaterThan(25.0);
        assertThat(filePic).as("la file ne depasse jamais sa capacite").isLessThanOrEqualTo(200);
        verifierAucunePerteAucunDoublonAucuneFactureIncomplete(run);
    }

    @Test
    void reinitialisationDesConnexionsEnPleinLotNeLaissePasDeTraces() throws Exception {
        UUID run = lancer(20);
        attendreDesEvenements(run, 200);

        chaos.declencher(run, ChaosType.COUPURE_BASE, Duration.ofSeconds(6), Map.of("mode", "RESET_PEER"), "test");
        Thread.sleep(8_000);   // reset_peer ferme chaque connexion a la volee, puis l'action expire

        await().atMost(Duration.ofSeconds(60)).until(() -> !chaos.actif(ChaosType.COUPURE_BASE));
        long avant = (long) ecrits();
        await().atMost(Duration.ofSeconds(60)).until(() -> ecrits() > avant + 100);
        assertThat(runs.info(run).etat()).isEqualTo(EtatRun.EN_COURS);
        runs.arreter(run);

        verifierAucunePerteAucunDoublonAucuneFactureIncomplete(run);
    }

    @Test
    void latenceDeCinqCentsMillisecondesFaitBaisserLeDebitSansErreurFatale() throws Exception {
        UUID run = lancer(20);
        attendreDesEvenements(run, 200);
        double normal = debitSur(Duration.ofSeconds(6));

        chaos.declencher(run, ChaosType.LATENCE, Duration.ofSeconds(100), Map.of("latenceMs", 500), "test");
        Thread.sleep(3_000);   // laisser les lots deja partis se terminer
        double ralenti = debitSur(Duration.ofSeconds(15));
        EtatRun etatPendant = runs.info(run).etat();

        chaos.retablirTout(run, "test");
        Thread.sleep(2_000);
        double retabli = debitSur(Duration.ofSeconds(6));
        runs.arreter(run);

        System.out.printf("CHAOS-MESURE latence-500ms : debit normal = %.0f evt/s, avec 500 ms = %.1f evt/s, apres retablissement = %.0f evt/s%n",
                normal, ralenti, retabli);
        assertThat(etatPendant).as("aucune erreur fatale").isEqualTo(EtatRun.EN_COURS);
        assertThat(ralenti).as("le debit baisse").isLessThan(normal * 0.5);
        assertThat(retabli).as("le debit remonte apres retablissement").isGreaterThan(ralenti * 2);
        verifierAucunePerteAucunDoublonAucuneFactureIncomplete(run);
    }

    @Test
    void surchargeX20LaFileSeRemplitLeGenerateurRalentitEtLeServiceResteVivant() throws Exception {
        int debit = 50;   // x20 = 1000 parcours/s demandes : au-dela de ce que la base absorbe
        UUID run = lancer(debit);
        attendreDesEvenements(run, 100);
        long parcoursAvant = runs.info(run).parcoursGeneres();
        long t0 = System.nanoTime();
        Thread.sleep(5_000);
        double parcoursNormal = (runs.info(run).parcoursGeneres() - parcoursAvant) / ((System.nanoTime() - t0) / 1e9);

        chaos.declencher(run, ChaosType.SURCHARGE, Duration.ofSeconds(100), Map.of("multiplicateur", 20), "test");
        long parcoursDebut = runs.info(run).parcoursGeneres();
        long debutSurcharge = System.nanoTime();
        AtomicLong filePic = new AtomicLong();
        while (System.nanoTime() - debutSurcharge < Duration.ofSeconds(20).toNanos()) {
            filePic.accumulateAndGet(sink.tailleFile(), Math::max);
            Thread.sleep(20);
        }
        double secondes = (System.nanoTime() - debutSurcharge) / 1e9;
        double parcoursSurcharge = (runs.info(run).parcoursGeneres() - parcoursDebut) / secondes;
        double demande = debit * 20.0;
        EtatRun etat = runs.info(run).etat();
        Integer vivant = jdbc.queryForObject("select 1", Integer.class);   // la base repond encore

        chaos.retablirTout(run, "test");
        runs.arreter(run);

        System.out.printf("CHAOS-MESURE surcharge-x20 : debit demande = %.0f parcours/s, obtenu = %.1f parcours/s "
                + "(normal : %.1f), pic de la file = %d/200%n", demande, parcoursSurcharge, parcoursNormal, filePic.get());
        assertThat(etat).as("le service reste vivant").isEqualTo(EtatRun.EN_COURS);
        assertThat(vivant).isEqualTo(1);
        assertThat(parcoursSurcharge).as("le generateur ralentit : il ne tient pas le debit demande")
                .isLessThan(demande * 0.9);
        // Et il ne s'effondre pas pour autant. On ne peut pas exiger qu'il fasse MIEUX qu'en regime normal :
        // des que la file atteint sa capacite, la contre-pression de l'ecriture ramene le generateur a son
        // debit nominal, qui est deja ce qu'il produit sans surcharge. L'invariant tenable est donc qu'il
        // reste a ce niveau, la marge absorbant le bruit de mesure de deux fenetres de durees differentes.
        assertThat(parcoursSurcharge).as("mais il ne s'effondre pas : il tient son debit nominal")
                .isGreaterThan(parcoursNormal * 0.8);
        assertThat(filePic.get()).as("la file ne depasse jamais sa capacite").isLessThanOrEqualTo(200);
        verifierAucunePerteAucunDoublonAucuneFactureIncomplete(run);
    }

    @Test
    void messagesTronquesSontCompteursEtLaTraceDAuditLeDit() throws Exception {
        UUID run = lancer(20);
        attendreDesEvenements(run, 50);

        chaos.declencher(run, ChaosType.MESSAGE_TRONQUE, Duration.ofSeconds(100), Map.of("proportion", 0.5), "test");
        Thread.sleep(5_000);
        chaos.retablirTout(run, "test");
        runs.arreter(run);

        ChaosAction a = chaos.actions(run).get(0);
        assertThat(a.evenementsTouches()).isPositive();
        verifierAucunePerteAucunDoublonAucuneFactureIncomplete(run);
    }
}
