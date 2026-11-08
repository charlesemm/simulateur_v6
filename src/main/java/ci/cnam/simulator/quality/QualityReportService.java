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
    private final EchangeService echanges;

    public QualityReportService(QualityStore store, RunManager runs, EchangeService echanges) {
        this.store = store;
        this.runs = runs;
        this.echanges = echanges;
    }

    /**
     * Une detection est exploitable si elle designe a la fois un type et un champ (cahier 4.3 : la
     * ligne, le champ, le type). Un rapport dont AUCUNE detection ne l'est ne permet aucune mesure
     * fiable : c'est une information sur l'outil, a documenter, pas a contourner.
     *
     * <p>Consequence assumee : un rapport qui ne contiendrait que des doublons signales sans champ
     * est accepte et conserve, mais compte comme « sans detail ». Un rapport mixte, lui, est mesure
     * normalement.
     */
    public static boolean exploitable(Detection detection) {
        return detection.type() != null && detection.champ() != null && !detection.champ().isBlank();
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

    /**
     * @param versionOutil nul pour prendre tous les rapports du run ensemble
     *
     *                     <p>Si l'echange n'a pas abouti (silence du script, rapport refuse, rapport
     *                     sans detail), AUCUN taux n'est rendu : la comparaison porte l'echec
     *                     d'echange et rien d'autre.
     */
    public ComparaisonQualite comparer(UUID runId, String versionOutil) {
        runs.info(runId);
        String version = versionOutil == null || versionOutil.isBlank() ? null : versionOutil.strip();
        EtatEchange echange = echanges.etat(runId).orElse(null);
        if (echange != null && !echange.mesurable()) {
            return ComparaisonQualite.sansMesure(runId, version, echange);
        }
        return construire(runId, version, echange, store.injectees(runId), store.detectees(runId, version),
                store.fauxPositifs(runId, version));
    }

    /** Nombre de lignes montrees par page dans les volets « ratées » et « faux positifs ». */
    public static final int LIGNES_PAR_PAGE = 25;

    /** Plafond dur : aucun ecran ne demande plus que cela d'un coup. */
    public static final int LIGNES_PAR_PAGE_MAX = 200;

    /**
     * Les anomalies ratees de ce run, ligne a ligne, pour aller voir la ligne exacte.
     *
     * <p>Le TOTAL n'est pas recompte : c'est celui que {@link LigneQualite} a deja calcule
     * ({@code ratees}). Le calcul du rapport n'est touche d'aucune maniere.
     *
     * @param page   numero de page, a partir de 0
     * @param taille taille de page, bornee a {@value #LIGNES_PAR_PAGE_MAX}
     */
    public PageQualite<AnomalieRatee> ratees(ComparaisonQualite rapport, int page, int taille) {
        if (!rapport.mesure() || rapport.total().ratees() == 0) {
            return PageQualite.vide(borner(taille));
        }
        int bornee = borner(taille);
        int numero = Math.max(0, page);
        return new PageQualite<>(store.ratees(rapport.runId(), rapport.versionOutil(), bornee, numero * bornee),
                numero, bornee, rapport.total().ratees());
    }

    /** Les faux positifs de ce run, ligne a ligne. Meme principe, meme total deja calcule. */
    public PageQualite<FauxPositifSignale> fauxPositifs(ComparaisonQualite rapport, int page, int taille) {
        if (!rapport.mesure() || rapport.total().fauxPositifs() == 0) {
            return PageQualite.vide(borner(taille));
        }
        int bornee = borner(taille);
        int numero = Math.max(0, page);
        return new PageQualite<>(
                store.fauxPositifsSignales(rapport.runId(), rapport.versionOutil(), bornee, numero * bornee),
                numero, bornee, rapport.total().fauxPositifs());
    }

    private static int borner(int taille) {
        return Math.min(LIGNES_PAR_PAGE_MAX, Math.max(1, taille));
    }

    static ComparaisonQualite construire(UUID runId, String version, EtatEchange echange,
                                         Map<AnomalyType, Long> injectees,
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
        return new ComparaisonQualite(runId, version, somme[0] == 0, echange,
                LigneQualite.de("TOTAL", somme[0], somme[1], somme[2]), categories, types);
    }
}
