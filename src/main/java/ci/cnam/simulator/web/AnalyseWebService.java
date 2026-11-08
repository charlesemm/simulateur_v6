package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.EtatEchange;
import ci.cnam.simulator.quality.QualityReportService;
import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.Echeance;
import ci.cnam.simulator.quality.planification.PlanificationService;
import ci.cnam.simulator.quality.planification.StatutAnalyse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/**
 * Tout ce que l'onglet Analyse a besoin de savoir, assemble en un seul endroit : le calendrier, le
 * suivi des echeances, le perimetre compare, les telechargements deja servis et les lignes a aller voir.
 *
 * <p>Le controleur ne fait que passer les parametres : aucune regle ici non plus, seulement de la
 * mise en forme. Les chiffres viennent tels quels de {@link QualityReportService} — le calcul du
 * rappel, de la precision, des ratees et des faux positifs n'est pas touche.
 */
@Service
public class AnalyseWebService {

    /** Fragment de l'ecran que les actions d'ecriture du calendrier renvoient. */
    public static final String ZONE_PLANIFICATION = "quality-report :: calendrier";

    private final QualityReportService qualite;
    private final PlanificationService planification;
    private final ExportService export;
    private final RunManager runs;

    public AnalyseWebService(QualityReportService qualite, PlanificationService planification,
                             ExportService export, RunManager runs) {
        this.qualite = qualite;
        this.planification = planification;
        this.export = export;
        this.runs = runs;
    }

    /**
     * Une echeance telle qu'elle se lit a l'ecran : son statut en clair, et, quand l'echange n'a pas
     * abouti, l'echec d'echange nomme comme tel. JAMAIS un taux : un silence du script n'est pas une
     * detection de 0 % (cahier 4.5).
     */
    public record EcheanceVue(Echeance echeance, String etatEnClair, boolean echecEchange) {
    }

    /**
     * Le perimetre de la comparaison, affiche en haut de l'ecran et non en note de bas de page.
     *
     * @param scenariosDifferents vrai quand les deux simulations ne viennent pas du meme scenario :
     *                            leurs chiffres ne se comparent alors pas, et l'ecran le dit
     */
    public record Perimetre(RunInfo simulation, RunInfo comparee, boolean scenariosDifferents) {
    }

    /**
     * Remplit le modele de l'ecran complet.
     *
     * @param run              simulation analysee ; a defaut la plus recente
     * @param comparer         simulation mise en seconde colonne, nulle pour aucune
     * @param version          version de l'outil, vide pour prendre tous ses rapports ensemble
     * @param pageRatees       page du volet « anomalies ratees »
     * @param pageFauxPositifs page du volet « faux positifs »
     */
    public void preparerEcran(UUID run, UUID comparer, String version, int pageRatees, int pageFauxPositifs,
                              Model modele) {
        List<RunInfo> tous = runs.lister();
        UUID choisi = run != null ? run : tous.stream().findFirst().map(RunInfo::id).orElse(null);
        modele.addAttribute("runs", tous);
        modele.addAttribute("run", choisi);
        modele.addAttribute("comparer", comparer);
        modele.addAttribute("version", version);
        preparerPlanification(modele);
        if (choisi == null) {
            return;
        }
        ComparaisonQualite rapport = qualite.comparer(choisi, version);
        modele.addAttribute("rapport", rapport);
        modele.addAttribute("echecEnClair", enClair(rapport.echange()));
        if (comparer != null) {
            // Une simulation dont l'echange a echoue n'a AUCUN chiffre : elle ne peut pas servir
            // de colonne de comparaison, on dit pourquoi au lieu d'afficher des cases vides.
            ComparaisonQualite autre = qualite.comparer(comparer, version);
            modele.addAttribute(autre.mesure() ? "rapportB" : "comparerSansMesure", autre);
        }
        modele.addAttribute("perimetre", perimetre(tous, choisi, comparer));
        modele.addAttribute("telechargements", export.telechargements(choisi));
        modele.addAttribute("ratees", qualite.ratees(rapport, pageRatees, QualityReportService.LIGNES_PAR_PAGE));
        modele.addAttribute("fauxPositifs",
                qualite.fauxPositifs(rapport, pageFauxPositifs, QualityReportService.LIGNES_PAR_PAGE));
    }

    /** Le bloc Planification seul : c'est ce que les trois actions d'ecriture renvoient a htmx. */
    public void preparerPlanification(Model modele) {
        modele.addAttribute("planifications", planification.planifications());
        modele.addAttribute("cibles", CibleAnalyse.values());
        modele.addAttribute("fuseau", planification.fuseau());
        modele.addAttribute("delaiRapportMinutes", planification.delaiRapport().toMinutes());
        modele.addAttribute("echeances", planification.historique().stream().map(AnalyseWebService::vue).toList());
    }

    private Perimetre perimetre(List<RunInfo> tous, UUID run, UUID comparer) {
        RunInfo simulation = tous.stream().filter(r -> r.id().equals(run)).findFirst().orElse(null);
        RunInfo comparee = comparer == null ? null
                : tous.stream().filter(r -> r.id().equals(comparer)).findFirst().orElse(null);
        boolean differents = simulation != null && comparee != null
                && !simulation.scenario().equals(comparee.scenario());
        return new Perimetre(simulation, comparee, differents);
    }

    private static EcheanceVue vue(Echeance echeance) {
        return new EcheanceVue(echeance, enClair(echeance.statut()), echeance.statut().echec());
    }

    /**
     * Le statut d'une echeance, dit en mots d'exploitant. Les echecs d'echange portent les mots du
     * cahier 4.5 : « en attente du script », « rapport refuse », « rapport sans detail ».
     */
    static String enClair(StatutAnalyse statut) {
        return switch (statut) {
            case ATTENDUE, RETIREE, SILENCE -> "en attente du script";
            case RAPPORT_REFUSE -> "rapport refusé";
            case RAPPORT_RECU -> "rapport reçu, mesure possible";
            case SANS_CIBLE -> "aucune simulation à analyser";
        };
    }

    /** Idem pour l'echange lu dans le journal. Nul quand un rapport exploitable est arrive. */
    static String enClair(EtatEchange echange) {
        if (echange == null || echange.mesurable()) {
            return null;
        }
        return switch (echange.statut()) {
            case RAPPORT_MALFORME -> "rapport refusé";
            case RAPPORT_SANS_DETAIL -> "rapport sans détail";
            default -> "en attente du script";
        };
    }
}
