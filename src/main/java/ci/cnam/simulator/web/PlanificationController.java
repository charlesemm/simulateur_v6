package ci.cnam.simulator.web;

import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.Planification;
import ci.cnam.simulator.quality.planification.PlanificationService;
import ci.cnam.simulator.security.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
 * <p>Chaque action rend le bloc Planification de l'onglet Analyse, que htmx remet en place ; un refus
 * metier sort en 422 avec son message, comme partout ailleurs dans l'interface.
 *
 * <p>Aucune logique : {@link PlanificationService} fait le travail.
 */
@Controller
@RequestMapping("/qualite/planifications")
public class PlanificationController {

    private final PlanificationService planification;
    private final AnalyseWebService ecran;
    private final AuditService audit;

    public PlanificationController(PlanificationService planification, AnalyseWebService ecran,
                                   AuditService audit) {
        this.planification = planification;
        this.ecran = ecran;
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
                        Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            Planification creee = planification.creer(libelle, heure, jours, cible);
            audit.enregistrer(auth.getName(), "PLANIFICATION_CREEE", creee.id().toString(),
                    creee.libelle() + " : " + creee.libelleCalendrier() + " (" + planification.fuseau()
                            + "), cible " + creee.cible());
            return "Analyse « " + creee.libelle() + " » programmée " + creee.libelleCalendrier() + ".";
        });
        return zone(modele);
    }

    @PostMapping("/{id}/activer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String activer(@PathVariable UUID id, @RequestParam boolean actif, Authentication auth,
                          Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            exigerConnue(planification.activer(id, actif), id);
            audit.enregistrer(auth.getName(), actif ? "PLANIFICATION_ACTIVEE" : "PLANIFICATION_DESACTIVEE",
                    id.toString(), actif ? "Les echeances s'ouvrent de nouveau"
                            : "Plus aucune echeance ne s'ouvre, l'historique est conserve");
            return actif ? "Analyse réactivée." : "Analyse désactivée : plus aucune échéance ne s'ouvrira.";
        });
        return zone(modele);
    }

    @PostMapping("/{id}/supprimer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String supprimer(@PathVariable UUID id, Authentication auth, Model modele,
                            HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            exigerConnue(planification.supprimer(id), id);
            audit.enregistrer(auth.getName(), "PLANIFICATION_SUPPRIMEE", id.toString(),
                    "Regle supprimee ; les echeances deja ouvertes restent dans l'historique");
            return "Analyse supprimée. Les échéances déjà ouvertes restent dans l'historique.";
        });
        return zone(modele);
    }

    private String zone(Model modele) {
        ecran.preparerPlanification(modele);
        return AnalyseWebService.ZONE_PLANIFICATION;
    }

    /** Libelle vide, heure absente, jour inconnu, planification inconnue : refus en 422 et message lisible. */
    private static void exigerConnue(boolean existait, UUID id) {
        if (!existait) {
            throw new IllegalArgumentException("Planification inconnue : " + id);
        }
    }
}
