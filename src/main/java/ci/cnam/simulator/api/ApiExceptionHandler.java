package ci.cnam.simulator.api;

import ci.cnam.simulator.api.dto.ErreurResponse;
import ci.cnam.simulator.core.scenario.ScenarioInconnuException;
import ci.cnam.simulator.core.scenario.ScenarioInvalideException;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Toutes les erreurs de l'API sortent au meme format : {@link ErreurResponse} (code, message, details).
 * Les codes sont stables : un programme peut s'y fier.
 */
@RestControllerAdvice(basePackages = "ci.cnam.simulator.api")
public class ApiExceptionHandler {

    /** YAML de scenario refuse a la lecture : 400, avec la liste des erreurs champ par champ. */
    @ExceptionHandler(ScenarioInvalideException.class)
    public ResponseEntity<ErreurResponse> scenarioInvalide(ScenarioInvalideException e) {
        return ResponseEntity.badRequest()
                .body(new ErreurResponse("SCENARIO_INVALIDE", e.getMessage(), e.erreurs()));
    }

    /** Scenario demande introuvable, en base comme dans le dossier des fichiers. */
    @ExceptionHandler(ScenarioInconnuException.class)
    public ResponseEntity<ErreurResponse> scenarioInconnu(ScenarioInconnuException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErreurResponse.de("SCENARIO_INCONNU", e.getMessage()));
    }

    /** Corps de requete invalide (champ manquant, taille de page hors bornes). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurResponse> corpsInvalide(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErreurResponse("REQUETE_INVALIDE", "Requete invalide", details));
    }

    /** Parametre de requete hors bornes (page, taille). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErreurResponse> parametreInvalide(ConstraintViolationException e) {
        List<String> details = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .sorted(Comparator.naturalOrder())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErreurResponse("REQUETE_INVALIDE", "Parametre invalide", details));
    }

    /** JSON illisible. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurResponse> jsonIllisible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ErreurResponse.de("REQUETE_INVALIDE", "Corps de requete illisible"));
    }

    /** Identifiant mal forme dans le chemin (UUID attendu). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurResponse> typeInvalide(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErreurResponse.de("REQUETE_INVALIDE", "Valeur invalide pour " + e.getName()));
    }

    /** Run inconnu, valeur de filtre hors liste : 404 pour un run, 400 sinon. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErreurResponse> introuvable(IllegalArgumentException e) {
        String message = String.valueOf(e.getMessage());
        if (message.startsWith("Run inconnu")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErreurResponse.de("RUN_INCONNU", message));
        }
        return ResponseEntity.badRequest().body(ErreurResponse.de("REQUETE_INVALIDE", message));
    }

    /** Transition d'etat refusee par le gestionnaire de runs (pause d'un run arrete, run deja actif...). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErreurResponse> etatImpossible(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErreurResponse.de("ETAT_IMPOSSIBLE", e.getMessage()));
    }
}
