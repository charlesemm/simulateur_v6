package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.export.ExportIndisponibleException;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.export.FormatExport;
import ci.cnam.simulator.security.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Telechargements depuis l'interface (session, pas de cle d'API) : le jeu a analyser dans les trois
 * formats, et le corrige a part.
 *
 * <p>Le corrige est reserve aux roles ADMIN et OPERATEUR. La cle d'API ne porte que {@code ROLE_API} :
 * un script ne peut donc pas l'obtenir, ni par l'API ni par ici.
 *
 * <p>Aucune logique : tout est dans {@link ExportService}.
 */
@Controller
public class TelechargementController {

    private final ExportService export;
    private final AuditService audit;
    private final RunManager runs;

    public TelechargementController(ExportService export, AuditService audit, RunManager runs) {
        this.export = export;
        this.audit = audit;
        this.runs = runs;
    }

    /**
     * Ecran de telechargement : les trois formats, le corrige pour qui y a droit, et les empreintes
     * des fichiers deja servis.
     */
    @GetMapping("/export")
    public String page(@RequestParam(required = false) UUID run, Model modele) {
        List<RunInfo> tous = runs.lister();
        RunInfo courant = tous.stream()
                .filter(r -> run != null ? r.id().equals(run) : r.etat().terminal())
                .findFirst().orElse(null);
        modele.addAttribute("courant", courant);
        modele.addAttribute("telechargements",
                courant == null ? List.of() : export.telechargements(courant.id()));
        return "export";
    }

    @GetMapping("/export/jeu.{extension}")
    public ResponseEntity<StreamingResponseBody> jeu(@RequestParam UUID run, @PathVariable String extension) {
        FormatExport format = FormatExport.duJeu(extension)
                .orElseThrow(() -> new IllegalArgumentException("Format d'export inconnu : " + extension));
        export.verifier(run);
        return reponse(run, format, sortie -> export.exporter(run, format, sortie));
    }

    /** La verite terrain, jamais melangee au jeu : un script qui la lirait rendrait son score sans valeur. */
    @GetMapping("/export/corrige.csv")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public ResponseEntity<StreamingResponseBody> corrige(@RequestParam UUID run, Authentication auth) {
        export.verifier(run);
        audit.enregistrer(auth.getName(), "EXPORT_CORRIGE", run.toString(),
                "Telechargement de la verite terrain");
        return reponse(run, FormatExport.CORRIGE, sortie -> export.exporterCorrige(run, sortie));
    }

    private ResponseEntity<StreamingResponseBody> reponse(UUID runId, FormatExport format,
                                                          StreamingResponseBody corps) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(format.nomFichier(runId)).build().toString())
                .body(corps);
    }

    /** Simulation en cours : refus net, avant le premier octet. */
    @ExceptionHandler(ExportIndisponibleException.class)
    public ResponseEntity<String> nonTerminee(ExportIndisponibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_PLAIN)
                .body("RUN_NON_TERMINE : " + e.getMessage());
    }

    /** Run inconnu ou extension inconnue. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> introuvable(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.valueOf(e.getMessage()));
    }
}
