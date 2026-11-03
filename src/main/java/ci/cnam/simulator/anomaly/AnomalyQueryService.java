package ci.cnam.simulator.anomaly;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Lecture paginee de la verite terrain d'un run. La taille de page est bornee comme pour les evenements. */
@Service
public class AnomalyQueryService {

    public static final int TAILLE_PAGE_PAR_DEFAUT = 50;
    public static final int TAILLE_PAGE_MAX = 500;

    /** @param total nombre d'anomalies correspondant aux filtres, toutes pages confondues */
    public record Page(List<AnomalyRecord> contenu, int page, int taille, long total) {

        public Page {
            contenu = List.copyOf(contenu);
        }

        public int totalPages() {
            return taille == 0 ? 0 : (int) ((total + taille - 1) / taille);
        }
    }

    private final AnomalyQueryStore store;

    public AnomalyQueryService(AnomalyQueryStore store) {
        this.store = store;
    }

    /**
     * @param type  nom du type (CHAMP_VIDE ou champVide), nul ou vide pour ne pas filtrer
     * @param champ nom du champ corrompu, nul ou vide pour ne pas filtrer
     * @throws IllegalArgumentException si le type est inconnu
     */
    public Page rechercher(UUID runId, String type, String champ, Integer page, Integer taille) {
        AnomalyType filtreType = null;
        if (type != null && !type.isBlank()) {
            filtreType = AnomalyType.depuis(type.strip()).orElseThrow(() -> new IllegalArgumentException(
                    "type : valeur inconnue '" + type + "' (valeurs permises : " + Arrays.toString(AnomalyType.values())
                            + ")"));
        }
        String filtreChamp = champ == null || champ.isBlank() ? null : champ.strip();
        int numero = Math.max(0, page == null ? 0 : page);
        int bornee = Math.min(TAILLE_PAGE_MAX, Math.max(1, taille == null ? TAILLE_PAGE_PAR_DEFAUT : taille));
        return new Page(store.rechercher(runId, filtreType, filtreChamp, numero, bornee), numero, bornee,
                store.compter(runId, filtreType, filtreChamp));
    }
}
