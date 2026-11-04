package ci.cnam.simulator.chaos;

import ci.cnam.simulator.SimulateurProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Client de l'API de Toxiproxy (URL dans TOXIPROXY_URL). C'est l'UNIQUE moyen d'agir sur la liaison avec la base :
 * le simulateur n'a aucun acces au moteur de conteneurs.
 */
@Component
public class ToxiproxyClient {

    static final String TOXIC_LATENCE = "chaos-latence";
    static final String TOXIC_RESET = "chaos-reset-peer";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String racine;
    private final String proxy;

    @Autowired
    public ToxiproxyClient(SimulateurProperties proprietes) {
        this(proprietes.toxiproxy().url(), proprietes.toxiproxy().proxy());
    }

    public ToxiproxyClient(String url, String proxy) {
        this.racine = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.proxy = proxy;
    }

    /** Ferme les connexions existantes et refuse les nouvelles : la base devient injoignable. */
    public void desactiverProxy() {
        envoyer("POST", "/proxies/" + proxy, Map.of("enabled", false), false);
    }

    public void activerProxy() {
        envoyer("POST", "/proxies/" + proxy, Map.of("enabled", true), false);
    }

    /** Reinitialise (RST) les connexions : les sessions en cours echouent brutalement, en plein lot. */
    public void ajouterResetPeer() {
        ajouterToxic(TOXIC_RESET, "reset_peer", Map.of("timeout", 0));
    }

    /** Ajoute {@code latenceMs} (+/- {@code jitterMs}) a chaque reponse de la base. */
    public void ajouterLatence(int latenceMs, int jitterMs) {
        ajouterToxic(TOXIC_LATENCE, "latency", Map.of("latency", latenceMs, "jitter", jitterMs));
    }

    public void supprimerToxic(String nom) {
        envoyer("DELETE", "/proxies/" + proxy + "/toxics/" + nom, null, true);
    }

    /** Supprime tous les toxics du proxy (y compris ceux poses a la main) et reactive le proxy. */
    public void retablirTout() {
        for (String nom : toxics()) {
            supprimerToxic(nom);
        }
        activerProxy();
    }

    /** Noms des toxics poses sur le proxy. */
    public List<String> toxics() {
        List<String> noms = new ArrayList<>();
        lire("/proxies/" + proxy + "/toxics").forEach(t -> noms.add(t.path("name").asText()));
        return noms;
    }

    public boolean proxyActif() {
        return lire("/proxies/" + proxy).path("enabled").asBoolean(false);
    }

    private void ajouterToxic(String nom, String type, Map<String, Object> attributs) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("name", nom);
        corps.put("type", type);
        corps.put("stream", "downstream");   // reponses de la base vers l'application
        corps.put("toxicity", 1.0);
        corps.put("attributes", attributs);
        supprimerToxic(nom);   // idempotent : remplace un toxic du meme nom
        envoyer("POST", "/proxies/" + proxy + "/toxics", corps, false);
    }

    private JsonNode lire(String chemin) {
        String corps = envoyer("GET", chemin, null, false);
        try {
            return json.readTree(corps);
        } catch (JsonProcessingException e) {
            throw new ChaosIndisponibleException("Reponse Toxiproxy illisible pour " + chemin, e);
        }
    }

    private String envoyer(String methode, String chemin, Object corps, boolean ignorer404) {
        try {
            HttpRequest.BodyPublisher contenu = corps == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corps));
            HttpRequest requete = HttpRequest.newBuilder(URI.create(racine + chemin))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .method(methode, contenu)
                    .build();
            HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
            int code = reponse.statusCode();
            if (code >= 200 && code < 300 || ignorer404 && code == 404) {
                return reponse.body();
            }
            throw new ChaosIndisponibleException("Toxiproxy a refuse " + methode + " " + chemin + " : HTTP " + code
                    + " " + reponse.body());
        } catch (IOException e) {
            throw new ChaosIndisponibleException("Toxiproxy injoignable (" + racine + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChaosIndisponibleException("Appel a Toxiproxy interrompu", e);
        }
    }
}
