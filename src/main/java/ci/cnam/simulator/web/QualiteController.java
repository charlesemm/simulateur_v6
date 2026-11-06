package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.LigneQualite;
import ci.cnam.simulator.quality.QualityReportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Rapport qualite : injectees contre detectees pour un run, ou pour deux runs cote a cote. Lecture seule. */
@Controller
public class QualiteController {

    private final QualityReportService qualite;
    private final RunManager runs;

    public QualiteController(QualityReportService qualite, RunManager runs) {
        this.qualite = qualite;
        this.runs = runs;
    }

    @GetMapping("/qualite")
    public String page(@RequestParam(required = false) UUID run, @RequestParam(required = false) UUID comparer,
                       @RequestParam(defaultValue = "") String version, Model modele) {
        List<RunInfo> tous = runs.lister();
        UUID choisi = run != null ? run : tous.stream().findFirst().map(RunInfo::id).orElse(null);
        modele.addAttribute("runs", tous);
        modele.addAttribute("run", choisi);
        modele.addAttribute("comparer", comparer);
        modele.addAttribute("version", version);
        if (choisi != null) {
            modele.addAttribute("rapport", qualite.comparer(choisi, version));
            if (comparer != null) {
                modele.addAttribute("rapportB", qualite.comparer(comparer, version));
            }
        }
        return "quality-report";
    }

    @GetMapping("/qualite/export.csv")
    public ResponseEntity<byte[]> exporter(@RequestParam UUID run, @RequestParam(defaultValue = "") String version) {
        ComparaisonQualite c = qualite.comparer(run, version);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("categorie,type,injectees,detectees,ratees,faux_positifs,taux_detection,precision\n");
        c.types().forEach(l -> ligne(csv, CategorieLibelle.de(l.libelle()), l));
        c.categories().forEach(l -> ligne(csv, "CATEGORIE", l));
        ligne(csv, "TOTAL", c.total());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("qualite-" + run.toString().substring(0, 8) + ".csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
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
