package ci.cnam.simulator.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Le client parle bien le protocole de Toxiproxy (verifie contre un faux serveur HTTP). */
class ToxiproxyClientTest {

    private HttpServer serveur;
    private ToxiproxyClient client;
    private final List<String> requetes = new CopyOnWriteArrayList<>();
    private volatile String toxicsExistants = "[]";
    private volatile int codePourDelete = 204;

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", echange -> {
            String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String chemin = echange.getRequestURI().getPath();
            requetes.add(echange.getRequestMethod() + " " + chemin + (corps.isEmpty() ? "" : " " + corps));
            int code = 200;
            String reponse = "{}";
            if (echange.getRequestMethod().equals("GET") && chemin.endsWith("/toxics")) {
                reponse = toxicsExistants;
            } else if (echange.getRequestMethod().equals("DELETE")) {
                code = codePourDelete;
                reponse = "";
            }
            byte[] octets = reponse.getBytes(StandardCharsets.UTF_8);
            echange.sendResponseHeaders(code, code == 204 ? -1 : octets.length);
            if (code != 204) {
                echange.getResponseBody().write(octets);
            }
            echange.close();
        });
        serveur.start();
        client = new ToxiproxyClient("http://127.0.0.1:" + serveur.getAddress().getPort() + "/", "postgres");
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void desactiverEtReactiverLeProxy() {
        client.desactiverProxy();
        client.activerProxy();

        assertThat(requetes).containsExactly(
                "POST /proxies/postgres {\"enabled\":false}",
                "POST /proxies/postgres {\"enabled\":true}");
    }

    @Test
    void ajouterLaLatenceSurLesReponsesDeLaBase() {
        client.ajouterLatence(500, 50);

        assertThat(requetes.get(0)).isEqualTo("DELETE /proxies/postgres/toxics/chaos-latence");   // idempotent
        String ajout = requetes.get(1);
        assertThat(ajout).startsWith("POST /proxies/postgres/toxics ");
        assertThat(ajout).contains("\"type\":\"latency\"", "\"stream\":\"downstream\"", "\"latency\":500",
                "\"jitter\":50", "\"name\":\"chaos-latence\"");
    }

    @Test
    void reinitialiserLaConnexionAvecResetPeer() {
        client.ajouterResetPeer();

        assertThat(requetes.get(1)).contains("\"type\":\"reset_peer\"", "\"timeout\":0");
    }

    @Test
    void toutRetablirSupprimeChaqueToxicPuisReactiveLeProxy() {
        toxicsExistants = "[{\"name\":\"chaos-latence\"},{\"name\":\"pose-a-la-main\"}]";

        client.retablirTout();

        assertThat(requetes).containsExactly(
                "GET /proxies/postgres/toxics",
                "DELETE /proxies/postgres/toxics/chaos-latence",
                "DELETE /proxies/postgres/toxics/pose-a-la-main",
                "POST /proxies/postgres {\"enabled\":true}");
    }

    @Test
    void supprimerUnToxicInexistantNEstPasUneErreur() {
        codePourDelete = 404;

        client.supprimerToxic("absent");   // ne leve rien
    }

    @Test
    void unRefusDeToxiproxyRemonteEnErreurExplicite() {
        serveur.removeContext("/");
        serveur.createContext("/", echange -> {
            echange.sendResponseHeaders(500, -1);
            echange.close();
        });

        assertThatThrownBy(() -> client.desactiverProxy())
                .isInstanceOf(ChaosIndisponibleException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void toxiproxyInjoignableRemonteEnErreurExplicite() {
        serveur.stop(0);

        assertThatThrownBy(() -> client.desactiverProxy())
                .isInstanceOf(ChaosIndisponibleException.class)
                .hasMessageContaining("injoignable");
    }
}
