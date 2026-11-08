package ci.cnam.simulator.core.event;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lecture des evenements d'un run. Borne la pagination : une requete ne peut jamais ramener toute la table
 * (un run en produit des millions).
 */
@Service
public class EventQueryService {

    public static final int TAILLE_PAGE_PAR_DEFAUT = 50;
    public static final int TAILLE_PAGE_MAX = 500;

    private final EventQueryStore store;

    public EventQueryService(EventQueryStore store) {
        this.store = store;
    }

    /**
     * Les filtres nuls ou vides ne filtrent pas ; {@code taille} est ramene a {@value #TAILLE_PAGE_MAX} au plus.
     *
     * @param eventId un evenement precis, nul pour ne pas filtrer : c'est ce filtre que l'ecran
     *                Qualite utilise pour ouvrir la ligne exacte d'une anomalie ratee
     */
    public PageEvenements rechercher(UUID runId, UUID eventId, String domaine, String type, String statut,
                                     Integer page, Integer taille) {
        int numero = Math.max(0, page == null ? 0 : page);
        int demandee = taille == null ? TAILLE_PAGE_PAR_DEFAUT : taille;
        int bornee = Math.min(TAILLE_PAGE_MAX, Math.max(1, demandee));
        return store.rechercher(new RechercheEvenements(runId, eventId, vide(domaine), vide(type), vide(statut),
                numero, bornee));
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
