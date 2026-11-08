package ci.cnam.simulator.api;

import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.export.FormatExport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Retrait du jeu a analyser par un programme, sous cle d'API. Trois formats, le meme contenu :
 * assures concernes, factures, lignes de facture, evenements, chaque ligne marquee comme fictive et
 * portant son identifiant metier.
 *
 * <p>Le corrige ({@code anomaly_injected}) N'EST PAS servi ici : il se telecharge depuis l'interface,
 * sous un compte ADMIN ou OPERATEUR. Un script qui lirait les reponses rendrait son score sans valeur.
 *
 * <p>Aucune logique : le controleur ouvre la reponse et laisse le service ecrire dedans.
 */
@RestController
@RequestMapping("/api/runs/{runId}")
@Tag(name = "Export", description = "Telecharger tout ce qu'une simulation terminee a produit")
public class ExportController {

    private final ExportService export;

    public ExportController(ExportService export) {
        this.export = export;
    }

    @GetMapping("/export.csv")
    @Operation(summary = "Archive zip : un .csv par table (BOM UTF-8). 409 si la simulation n'est pas terminee")
    public ResponseEntity<StreamingResponseBody> csv(@PathVariable UUID runId) {
        return reponse(runId, FormatExport.CSV);
    }

    @GetMapping("/export.json")
    @Operation(summary = "Un objet racine, un tableau par table. 409 si la simulation n'est pas terminee")
    public ResponseEntity<StreamingResponseBody> json(@PathVariable UUID runId) {
        return reponse(runId, FormatExport.JSON);
    }

    @GetMapping("/export.xlsx")
    @Operation(summary = "Classeur xlsx, un onglet par table. 409 si la simulation n'est pas terminee")
    public ResponseEntity<StreamingResponseBody> xlsx(@PathVariable UUID runId) {
        return reponse(runId, FormatExport.XLSX);
    }

    private ResponseEntity<StreamingResponseBody> reponse(UUID runId, FormatExport format) {
        export.verifier(runId);   // run inconnu ou non termine : refus AVANT le premier octet
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(format.nomFichier(runId)).build().toString())
                .body(sortie -> export.exporter(runId, format, sortie));
    }
}
