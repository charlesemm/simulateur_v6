package ci.cnam.simulator.web;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.QualityReportService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * La fiche d'une simulation : tout ce qu'on sait d'elle, rassemble pour l'onglet Synthese. Rien n'est
 * calcule ici — chaque chiffre vient tel quel du service qui en est responsable (moteur, chaos, qualite,
 * export). C'est de l'assemblage pour l'ecran, pas une regle.
 */
@Service
public class SimulationWebService {

    private final RunManager runs;
    private final ChaosInjector chaos;
    private final QualityReportService qualite;
    private final ExportService export;
    private final Clock horloge;

    public SimulationWebService(RunManager runs, ChaosInjector chaos, QualityReportService qualite,
                                ExportService export, Clock horloge) {
        this.runs = runs;
        this.chaos = chaos;
        this.qualite = qualite;
        this.export = export;
        this.horloge = horloge;
    }

    /**
     * @param duree           temps reel ecoule entre le debut et la fin (ou maintenant si elle tourne encore)
     * @param pannes          crash tests provoques pendant cette simulation, du plus recent au plus ancien
     * @param rapport         comparaison avec l'outil de qualite ; sans mesure tant qu'aucun rapport exploitable
     * @param telechargements nombre de fichiers deja servis pour cette simulation
     */
    public record Fiche(RunInfo run, Duration duree, List<ChaosAction> pannes, ComparaisonQualite rapport,
                        int telechargements) {
    }

    public List<RunInfo> toutes() {
        return runs.lister();
    }

    public Optional<Fiche> fiche(UUID id) {
        return runs.lister().stream().filter(r -> r.id().equals(id)).findFirst().map(this::assembler);
    }

    private Fiche assembler(RunInfo run) {
        Duration duree = run.debut() == null ? null
                : Duration.between(run.debut(), run.fin() != null ? run.fin() : horloge.instant());
        List<ChaosAction> pannes = new java.util.ArrayList<>(chaos.actions(run.id()));
        java.util.Collections.reverse(pannes);
        return new Fiche(run, duree, List.copyOf(pannes), qualite.comparer(run.id(), ""),
                export.telechargements(run.id()).size());
    }
}
