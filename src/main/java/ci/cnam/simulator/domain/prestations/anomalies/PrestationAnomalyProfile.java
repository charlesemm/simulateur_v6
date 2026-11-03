package ci.cnam.simulator.domain.prestations.anomalies;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.anomaly.ChampsEligibles;
import ci.cnam.simulator.domain.prestations.parcours.ParcoursContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Champs d'un evenement de prestations qui peuvent etre corrompus, par type d'anomalie.
 *
 * <p>Priorite aux champs saisis a la main par l'agent (poids 4 : noms, prenoms, quantites, genre), les plus
 * exposes dans la realite ; puis ceux qu'il saisit parfois (poids 2 a 3) ; les champs renseignes par le systeme
 * ont le poids 1. Jamais eligibles : les cles (factureId, assureId, centreSanteId, medecinId, pharmacieId).
 * Le doublon vise l'evenement entier : aucun champ.
 */
@Component
public class PrestationAnomalyProfile implements ChampsEligibles {

    private static final int AGENT = 4;
    private static final int SOUVENT = 3;
    private static final int PARFOIS = 2;
    private static final int SYSTEME = 1;

    private final Map<AnomalyType, List<ChampEligible>> champs = new EnumMap<>(AnomalyType.class);

    public PrestationAnomalyProfile() {
        champs.put(AnomalyType.CHAMP_VIDE, List.of(c("nom", AGENT), c("prenoms", AGENT), c("dateNaissance", SOUVENT),
                c("genre", PARFOIS), c("numeroAssure", PARFOIS), c("motif", SYSTEME), c("typeBon", SYSTEME)));
        champs.put(AnomalyType.FAUTE_ORTHOGRAPHE, List.of(c("nom", AGENT), c("prenoms", AGENT)));
        champs.put(AnomalyType.ESPACES_PARASITES, List.of(c("nom", AGENT), c("prenoms", AGENT),
                c("numeroAssure", PARFOIS), c("numeroFacture", SYSTEME)));
        champs.put(AnomalyType.CASSE_INCOHERENTE, List.of(c("nom", AGENT), c("prenoms", AGENT)));
        champs.put(AnomalyType.CARACTERES_SPECIAUX, List.of(c("nom", AGENT), c("prenoms", AGENT)));
        champs.put(AnomalyType.FORMAT_DATE_INVALIDE, List.of(c("dateNaissance", AGENT)));
        champs.put(AnomalyType.FORMAT_NUMERIQUE_INVALIDE, List.of(c("quantite", AGENT), c("joursSejour", PARFOIS),
                c("nombreExamens", SYSTEME)));
        champs.put(AnomalyType.FORMAT_IDENTIFIANT_INVALIDE, List.of(c("numeroAssure", AGENT),
                c("numeroFacture", SYSTEME)));
        champs.put(AnomalyType.VALEUR_NEGATIVE, List.of(c("quantite", AGENT), c("joursSejour", PARFOIS),
                c("nombreExamens", SYSTEME)));
        champs.put(AnomalyType.VALEUR_HORS_LISTE, List.of(c("genre", AGENT), c("typeBon", SYSTEME),
                c("motif", SYSTEME)));
    }

    @Override
    public String domaine() {
        return ParcoursContext.DOMAINE;
    }

    @Override
    public List<ChampEligible> champs(AnomalyType type) {
        return champs.getOrDefault(type, List.of());
    }

    private static ChampEligible c(String nom, int poids) {
        return new ChampEligible(nom, poids);
    }
}
