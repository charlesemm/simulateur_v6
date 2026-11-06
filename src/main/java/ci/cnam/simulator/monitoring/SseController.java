package ci.cnam.simulator.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Flux temps reel du tableau de bord : une photo JSON par seconde, partagee entre tous les clients connectes.
 * Derriere un reverse proxy, desactiver la mise en tampon ({@code proxy_buffering off}) ; l'en-tete
 * {@code X-Accel-Buffering: no} suffit pour nginx. Aucun calcul ici : tout est dans {@link TableauDeBordService}.
 */
@Controller
public class SseController {

    private static final Logger LOG = LoggerFactory.getLogger(SseController.class);
    private static final long DUREE_MAX_MS = TimeUnit.MINUTES.toMillis(10);   // le navigateur se reconnecte seul

    private final TableauDeBordService tableau;
    private final ObjectMapper json;
    private final Set<SseEmitter> clients = new CopyOnWriteArraySet<>();
    private ScheduledExecutorService minuteur;

    public SseController(TableauDeBordService tableau, ObjectMapper json) {
        this.tableau = tableau;
        this.json = json;
    }

    @PostConstruct
    void demarrer() {
        minuteur = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flux-tableau-de-bord");
            t.setDaemon(true);
            return t;
        });
        minuteur.scheduleWithFixedDelay(this::diffuser, 1, 1, TimeUnit.SECONDS);
    }

    @GetMapping(path = "/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter flux(HttpServletResponse reponse) {
        reponse.setHeader("X-Accel-Buffering", "no");
        reponse.setHeader("Cache-Control", "no-cache");
        SseEmitter client = new SseEmitter(DUREE_MAX_MS);
        client.onCompletion(() -> clients.remove(client));
        client.onTimeout(() -> clients.remove(client));
        client.onError(e -> clients.remove(client));
        clients.add(client);
        envoyer(client, tableau.dernier());
        return client;
    }

    int nombreClients() {
        return clients.size();
    }

    private void diffuser() {
        try {
            if (clients.isEmpty()) {
                tableau.oublier();
                return;
            }
            EtatTableauDeBord etat = tableau.echantillonner();
            clients.forEach(c -> envoyer(c, etat));
        } catch (RuntimeException e) {
            LOG.warn("Echantillonnage du tableau de bord en echec : {}", e.getMessage());
        }
    }

    private void envoyer(SseEmitter client, EtatTableauDeBord etat) {
        try {
            client.send(SseEmitter.event().name("etat").data(json.writeValueAsString(etat)));
        } catch (IOException | IllegalStateException e) {
            clients.remove(client);   // client parti : rien a signaler
        }
    }

    @PreDestroy
    void arreter() {
        minuteur.shutdownNow();
        clients.forEach(SseEmitter::complete);
    }
}
