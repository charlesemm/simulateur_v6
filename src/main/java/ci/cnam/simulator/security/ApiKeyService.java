package ci.cnam.simulator.security;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.persistence.entity.ApiKey;
import ci.cnam.simulator.persistence.repository.ApiKeyRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Cles d'API stockees HACHEES (SHA-256). La cle de {@code API_KEY} est enregistree au demarrage sous forme d'empreinte ;
 * la cle elle-meme n'est jamais ecrite en base.
 *
 * <p>La verification se fait contre un ENSEMBLE D'EMPREINTES EN MEMOIRE, rafraichi en arriere-plan : pendant un
 * crash test qui coupe la base, l'API doit continuer a accepter la cle (sinon plus moyen d'appeler "tout retablir").
 * L'empreinte de la cle d'environnement est toujours acceptee.
 */
@Service
public class ApiKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String NOM_CLE_ENVIRONNEMENT = "cle-environnement";

    private final ApiKeyRepository cles;
    private final String empreinteEnvironnement;
    private volatile Set<String> empreintesActives;
    private final ScheduledExecutorService rafraichisseur = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "api-key-refresh");
        t.setDaemon(true);
        return t;
    });

    public ApiKeyService(ApiKeyRepository cles, SimulateurProperties proprietes) {
        this.cles = cles;
        String cle = proprietes.securite().cleApi();
        this.empreinteEnvironnement = cle == null || cle.isEmpty() ? null : empreinte(cle);
        this.empreintesActives = empreinteEnvironnement == null ? Set.of() : Set.of(empreinteEnvironnement);
    }

    /** Enregistre l'empreinte de la cle d'environnement (une seule fois) puis lit les cles de la base. */
    @EventListener(ApplicationReadyEvent.class)
    void initialiser() {
        try {
            if (empreinteEnvironnement != null && cles.findByHash(empreinteEnvironnement).isEmpty()) {
                cles.save(new ApiKey(NOM_CLE_ENVIRONNEMENT, empreinteEnvironnement));
            }
            rafraichir();
        } catch (RuntimeException e) {
            LOG.warn("Cles d'API non synchronisees avec la base : {}", e.getMessage());
        }
        rafraichisseur.scheduleWithFixedDelay(this::rafraichirSansEchec, 30, 30, TimeUnit.SECONDS);
    }

    void rafraichir() {
        Set<String> actives = cles.findAll().stream().filter(ApiKey::isActif).map(ApiKey::getHash)
                .collect(Collectors.toSet());
        // Une cle d'environnement desactivee en base reste desactivee ; sinon elle est toujours valable.
        boolean environnementDesactive = empreinteEnvironnement != null && cles.findByHash(empreinteEnvironnement)
                .map(k -> !k.isActif()).orElse(false);
        if (empreinteEnvironnement != null && !environnementDesactive) {
            actives.add(empreinteEnvironnement);
        }
        empreintesActives = Set.copyOf(actives);
    }

    private void rafraichirSansEchec() {
        try {
            rafraichir();
        } catch (RuntimeException e) {
            LOG.debug("Rafraichissement des cles d'API impossible, ensemble precedent conserve : {}", e.getMessage());
        }
    }

    /** Vrai si la cle presentee correspond a une cle active. Comparaison a temps constant des empreintes. */
    public boolean valide(String cleFournie) {
        if (cleFournie == null || cleFournie.isEmpty()) {
            return false;
        }
        byte[] fournie = empreinte(cleFournie).getBytes(StandardCharsets.US_ASCII);
        boolean trouvee = false;
        for (String active : empreintesActives) {
            trouvee |= MessageDigest.isEqual(fournie, active.getBytes(StandardCharsets.US_ASCII));
        }
        return trouvee;
    }

    public static String empreinte(String cle) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(cle.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    @PreDestroy
    void arreter() {
        rafraichisseur.shutdownNow();
    }
}
