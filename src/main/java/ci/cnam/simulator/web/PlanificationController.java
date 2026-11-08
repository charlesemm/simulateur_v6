package ci.cnam.simulator.web;

import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.Planification;
import ci.cnam.simulator.quality.planification.PlanificationService;
import ci.cnam.simulator.security.AuditService;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Ecrire le calendrier des analyses : creer une regle, l'activer ou la desactiver, la supprimer.
 *
 * <p>Ces trois actions sont reservees a ADMIN et OPERATEUR, et le controle est fait COTE SERVEUR
 * ({@code @PreAuthorize}) : masquer les boutons a un OBSERVATEUR ne suffirait pas. La creation et la
 * suppression passent au journal d'audit — c'est un calendrier qui engage l'outil teste.
 *
 * <p>La LECTURE du calendrier et l'ecran qui le presente arrivent au prompt 16 : ici, chaque action
 * repond par une redirection vers {@code /qualite}, qui est l'ecran de pilotage prevu au plan.
 *
 * <p>Aucune logique : {@link PlanificationService} fait le travail.
 */
@Controller
@RequestMapping("/qualite/planifications")
public class PlanificationController {

    private final PlanificationService planification;
    private final AuditService audit;

    public PlanificationController(PlanificationService planification, AuditService audit) {
        this.planification = planification;
        this.audit = audit;
    }

    /**
     * @param heure heure du SERVEUR, dans le fuseau affiche dans l'aide du champ
     * @param jours {@code TOUS} ou une liste comme {@code LUNDI,MARDI}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String creer(@RequestParam String libelle,
                        @RequestParam LocalTime heure,
                        @RequestParam(defaultValue = "TOUS") String jours,
                        @RequestParam(defaultValue = "DERNIERE_SIMULATION_TERMINEE") CibleAnalyse cible,
                        Authentication auth) {
        Planification creee = planification.creer(libelle, heure, jours, cible);
        audit.enregistrer(auth.getName(), "PLANIFICATION_CREEE", creee.id().toString(),
                creee.libelle() + " : " + creee.libelleCalendrier() + " (" + planification.fuseau() + "), cible "
                        + creee.cible());
        return "redirect:/qualite";
    }

    @PostMapping("/{id}/activer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String activer(@PathVariable UUID id,
                          @RequestParam boolean actif, Authentication auth) {
        if (!planification.activer(id, actif)) {
            throw new IllegalArgumentException("Planification inconnue : " + id);
        }
        audit.enregistrer(auth.getName(), actif ? "PLANIFICATION_ACTIVEE" : "PLANIFICATION_DESACTIVEE",
                id.toString(), actif ? "Les echeances s'ouvrent de nouveau"
                        : "Plus aucune echeance ne s'ouvre, l'historique est conserve");
        return "redirect:/qualite";
    }

    @PostMapping("/{id}/supprimer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String supprimer(@PathVariable UUID id, Authentication auth) {
        if (!planification.supprimer(id)) {
            throw new IllegalArgumentException("Planification inconnue : " + id);
        }
        audit.enregistrer(auth.getName(), "PLANIFICATION_SUPPRIMEE", id.toString(),
                "Regle supprimee ; les echeances deja ouvertes restent dans l'historique");
        return "redirect:/qualite";
    }

    /** Libelle vide, heure absente, jour inconnu, planification inconnue : refus net et lisible. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> refus(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN)
                .body(String.valueOf(e.getMessage()));
    }
}
