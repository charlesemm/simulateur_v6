package ci.cnam.simulator.web;

import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Explorateur d'evenements : filtres, pagination, export CSV, rejeu d'un evenement echoue. */
@Controller
public class EvenementsController {

    /** Un export ne depasse jamais ce nombre de lignes (un run en produit des millions). */
    static final int LIMITE_EXPORT = 100_000;

    private final EventQueryService evenements;
    private final RunManager runs;
    private final RejeuEvenementService rejeu;
    private final ObjectMapper json;

    public EvenementsController(EventQueryService evenements, RunManager runs, RejeuEvenementService rejeu,
                                ObjectMapper json) {
        this.evenements = evenements;
        this.runs = runs;
        this.rejeu = rejeu;
        this.json = json;
    }

    /** Page complete, ou seulement le tableau quand la requete vient de htmx (en-tete HX-Request). */
    @GetMapping("/evenements")
    public String page(@RequestParam(required = false) UUID run, @RequestParam(defaultValue = "") String domaine,
                       @RequestParam(defaultValue = "") String type, @RequestParam(defaultValue = "") String statut,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille,
                       @RequestHeader(value = "HX-Request", required = false) String htmx, Model modele,
                       HttpServletResponse reponse) {
        List<RunInfo> tous = runs.lister();
        UUID choisi = run != null ? run : tous.stream().findFirst().map(RunInfo::id).orElse(null);
        PageEvenements resultat = choisi == null
                ? new PageEvenements(List.of(), 0, EventQueryService.TAILLE_PAGE_PAR_DEFAUT, 0)
                : evenements.rechercher(choisi, domaine, type, statut, page, taille);
        modele.addAttribute("runs", tous);
        modele.addAttribute("run", choisi);
        modele.addAttribute("domaine", domaine);
        modele.addAttribute("type", type);
        modele.addAttribute("statut", statut);
        modele.addAttribute("resultat", resultat);
        modele.addAttribute("ecrit", RejeuEvenementService.ECRIT);
        reponse.setHeader(HttpHeaders.VARY, "HX-Request");
        return htmx != null ? "evenements :: tableau" : "evenements";
    }

    @GetMapping("/evenements/export.csv")
    public ResponseEntity<StreamingResponseBody> exporter(@RequestParam UUID run,
                                                          @RequestParam(defaultValue = "") String domaine,
                                                          @RequestParam(defaultValue = "") String type,
                                                          @RequestParam(defaultValue = "") String statut) {
        StreamingResponseBody corps = sortie -> {
            Writer ecrivain = new BufferedWriter(new OutputStreamWriter(sortie, StandardCharsets.UTF_8));
            ecrivain.write('﻿');   // BOM : les tableurs lisent correctement les accents
            EvenementsCsv.entete(ecrivain);
            int ecrites = 0;
            for (int p = 0; ecrites < LIMITE_EXPORT; p++) {
                PageEvenements tranche = evenements.rechercher(run, domaine, type, statut, p,
                        EventQueryService.TAILLE_PAGE_MAX);
                if (tranche.contenu().isEmpty()) {
                    break;
                }
                for (var e : tranche.contenu()) {
                    if (ecrites++ >= LIMITE_EXPORT) {
                        break;
                    }
                    EvenementsCsv.ecrire(ecrivain, e, this::enJson);
                }
            }
            ecrivain.flush();
        };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("evenements-" + run.toString().substring(0, 8) + ".csv").build().toString())
                .body(corps);
    }

    @PostMapping("/evenements/{id}/rejouer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATEUR')")
    public String rejouer(@PathVariable UUID id, Authentication auth, Model modele, HttpServletResponse reponse) {
        ActionWeb.executer(modele, reponse, () -> {
            rejeu.rejouer(id, auth.getName());
            return "Rejoué";
        });
        return "evenements :: rejeu";
    }

    private String enJson(Object valeur) {
        try {
            return json.writeValueAsString(valeur);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
