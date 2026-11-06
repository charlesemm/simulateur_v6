package ci.cnam.simulator.api;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.api.dto.DetectionsRequest;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.Detection;
import ci.cnam.simulator.quality.QualityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Detections de l'outil qualite et comparaison avec la verite terrain. Aucune logique ici : tout est dans le service. */
@RestController
@RequestMapping("/api/runs/{runId}/quality")
@Tag(name = "Qualite", description = "Envoyer les detections de l'outil qualite, lire injectees contre detectees")
public class QualityController {

    private final QualityReportService qualite;

    public QualityController(QualityReportService qualite) {
        this.qualite = qualite;
    }

    /** @param nouvelles detections reellement enregistrees (une repetition d'une detection deja envoyee n'en compte pas) */
    public record RapportRecu(UUID rapportId, int recues, int nouvelles) {
    }

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoyer des detections. Plusieurs envois par run sont acceptes ; "
            + "un evenement inconnu du run fait refuser tout l'envoi (422)")
    public RapportRecu envoyer(@PathVariable UUID runId, @Valid @RequestBody DetectionsRequest corps) {
        List<String> erreurs = new java.util.ArrayList<>();
        List<Detection> detections = new java.util.ArrayList<>();
        for (int i = 0; i < corps.detections().size(); i++) {
            DetectionsRequest.Detection d = corps.detections().get(i);
            var type = AnomalyType.depuis(d.type().strip());
            if (type.isEmpty()) {
                erreurs.add("detections[" + i + "] : type inconnu '" + d.type() + "' (valeurs permises : "
                        + Arrays.toString(AnomalyType.values()) + ")");
            } else {
                detections.add(new Detection(d.eventId(), type.get(), d.champ(), d.regle(), d.score()));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(erreurs.get(0)
                    + (erreurs.size() > 1 ? " (+" + (erreurs.size() - 1) + " autre(s))" : ""));
        }
        var recu = qualite.recevoir(runId, corps.versionOutil().strip(), detections);
        return new RapportRecu(recu.rapportId(), recu.recues(), recu.nouvelles());
    }

    @GetMapping("/comparison")
    @Operation(summary = "Par type d'anomalie : injectees, detectees, ratees, faux positifs, taux de detection "
            + "et precision ; separe structurelles et semantiques")
    public ComparaisonQualite comparer(
            @PathVariable UUID runId,
            @Parameter(description = "Ne prendre que les rapports de cette version de l'outil")
            @RequestParam(required = false) String versionOutil) {
        return qualite.comparer(runId, versionOutil);
    }
}
