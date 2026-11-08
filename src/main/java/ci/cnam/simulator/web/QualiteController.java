package ci.cnam.simulator.web;

import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.LigneQualite;
import ci.cnam.simulator.quality.QualityReportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * L'onglet Analyse : d'ou l'on programme les analyses, suit les echanges, lit le rappel et la
 * precision, et ouvre d'un clic la ligne exacte que le script a manquee.
 *
 * <p>Lecture seule : les ecritures du calendrier sont dans {@link PlanificationController}. Aucune
 * logique ici, {@link AnalyseWebService} assemble le modele.
 */
@Controller
public class QualiteController {

    private final QualityReportService qualite;
    private final AnalyseWebService ecran;

    public QualiteController(QualityReportService qualite, AnalyseWebService ecran) {
        this.qualite = qualite;
        this.ecran = ecran;
    }

    /**
     * @param pageRatees       page du volet « anomalies ratees »
     * @param pageFauxPositifs page du volet « faux positifs »
     */
    @GetMapping("/qualite")
    public String page(@RequestParam(required = false) UUID run, @RequestParam(required = false) UUID comparer,
                       @RequestParam(defaultValue = "") String version,
                       @RequestParam(defaultValue = "0") int pageRatees,
                       @RequestParam(defaultValue = "0") int pageFauxPositifs, Model modele) {
        ecran.preparerEcran(run, comparer, version, pageRatees, pageFauxPositifs, modele);
        return "quality-report";
    }

    @GetMapping("/qualite/export.csv")
    public ResponseEntity<byte[]> exporter(@RequestParam UUID run, @RequestParam(defaultValue = "") String version) {
        ComparaisonQualite c = qualite.comparer(run, version);
        StringBuilder csv = new StringBuilder("﻿");
        if (!c.mesure()) {
            // Echec ou attente d'echange : aucun chiffre, pas meme un 0 %. Le fichier dit pourquoi.
            csv.append("echange,statut,detail\n")
                    .append(EvenementsCsv.cellule("ECHEC_ECHANGE")).append(',')
                    .append(EvenementsCsv.cellule(c.echange().statut().name())).append(',')
                    .append(EvenementsCsv.cellule(String.valueOf(c.echange().detail()))).append('\n');
            return reponse(run, csv.toString());
        }
        csv.append("categorie,type,injectees,detectees,ratees,faux_positifs,taux_detection,precision\n");
        c.types().forEach(l -> ligne(csv, CategorieLibelle.de(l.libelle()), l));
        c.categories().forEach(l -> ligne(csv, "CATEGORIE", l));
        ligne(csv, "TOTAL", c.total());
        return reponse(run, csv.toString());
    }

    private static ResponseEntity<byte[]> reponse(UUID run, String csv) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("qualite-" + run.toString().substring(0, 8) + ".csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static void ligne(StringBuilder csv, String categorie, LigneQualite l) {
        csv.append(EvenementsCsv.cellule(categorie)).append(',').append(EvenementsCsv.cellule(l.libelle())).append(',')
                .append(l.injectees()).append(',').append(l.detectees()).append(',').append(l.ratees()).append(',')
                .append(l.fauxPositifs()).append(',').append(taux(l.tauxDetection())).append(',')
                .append(taux(l.precision())).append('\n');
    }

    private static String taux(Double valeur) {
        return valeur == null ? "" : String.valueOf(valeur);
    }

    /** Categorie d'un type, pour la colonne du CSV. */
    private static final class CategorieLibelle {
        static String de(String type) {
            return ci.cnam.simulator.quality.CategorieAnomalie.de(ci.cnam.simulator.anomaly.AnomalyType.valueOf(type))
                    .name();
        }
    }
}
