package ci.cnam.simulator.quality;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.engine.RunManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recoit les detections de l'outil qualite et les compare a la verite terrain du run. */
@Service
public class QualityReportService {

    static final int ERREURS_MAX = 20;

    private final QualityStore store;
    private final RunManager runs;

    public QualityReportService(QualityStore store, RunManager runs) {
        this.store = store;
        this.runs = runs;
    }

    /** @param nouvelles detections reellement enregistrees (les autres repetaient une detection du meme envoi) */
    public record Recu(UUID rapportId, int recues, int nouvelles) {
    }

    /**
     * @throws IllegalArgumentException   si le run est inconnu
     * @throws DetectionInvalideException si un champ manque ou si un evenement n'est pas du run
     */
    @Transactional
    public Recu recevoir(UUID runId, String versionOutil, List<Detection> detections) {
        runs.info(runId);
        Set<UUID> connus = new HashSet<>(store.evenementsDuRun(runId,
                detections.stream().map(Detection::eventId).distinct().toList()));
        List<String> erreurs = new ArrayList<>();
        long invalides = 0;
        for (int i = 0; i < detections.size(); i++) {
            Detection d = detections.get(i);
            String erreur = null;
            if (!connus.contains(d.eventId())) {
                erreur = "evenement inconnu pour ce run : " + d.eventId();
            } else if (d.type() != AnomalyType.DOUBLON_EXACT && (d.champ() == null || d.champ().isBlank())) {
                erreur = "champ obligatoire pour le type " + d.type();
            }
            if (erreur != null) {
                invalides++;
                if (erreurs.size() < ERREURS_MAX) {
                    erreurs.add("detections[" + i + "] : " + erreur);
                }
            }
        }
        if (invalides > 0) {
            throw new DetectionInvalideException("Envoi refuse, rien n'a ete enregistre : " + invalides
                    + " detection(s) invalide(s)", erreurs);
        }
        UUID rapportId = UUID.randomUUID();
        int nouvelles = store.enregistrer(rapportId, runId, versionOutil, detections);
        return new Recu(rapportId, detections.size(), nouvelles);
    }

    /** @param versionOutil nul pour prendre tous les rapports du run ensemble */
    public ComparaisonQualite comparer(UUID runId, String versionOutil) {
        runs.info(runId);
        String version = versionOutil == null || versionOutil.isBlank() ? null : versionOutil.strip();
        return construire(runId, version, store.injectees(runId), store.detectees(runId, version),
                store.fauxPositifs(runId, version));
    }

    static ComparaisonQualite construire(UUID runId, String version, Map<AnomalyType, Long> injectees,
                                         Map<AnomalyType, Long> detectees, Map<AnomalyType, Long> fauxPositifs) {
        List<LigneQualite> types = new ArrayList<>();
        Map<CategorieAnomalie, long[]> cumuls = new EnumMap<>(CategorieAnomalie.class);
        long[] somme = new long[3];
        for (AnomalyType t : AnomalyType.values()) {
            long[] valeurs = {injectees.getOrDefault(t, 0L), detectees.getOrDefault(t, 0L),
                    fauxPositifs.getOrDefault(t, 0L)};
            types.add(LigneQualite.de(t.name(), valeurs[0], valeurs[1], valeurs[2]));
            long[] c = cumuls.computeIfAbsent(CategorieAnomalie.de(t), k -> new long[3]);
            for (int i = 0; i < 3; i++) {
                c[i] += valeurs[i];
                somme[i] += valeurs[i];
            }
        }
        List<LigneQualite> categories = Arrays.stream(CategorieAnomalie.values()).map(c -> {
            long[] v = cumuls.getOrDefault(c, new long[3]);
            return LigneQualite.de(c.name(), v[0], v[1], v[2]);
        }).toList();
        return new ComparaisonQualite(runId, version, somme[0] == 0,
                LigneQualite.de("TOTAL", somme[0], somme[1], somme[2]), categories, types);
    }
}
