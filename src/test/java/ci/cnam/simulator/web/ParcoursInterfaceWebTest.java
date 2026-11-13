package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.ChaosType;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EvenementLu;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.export.FormatExport;
import ci.cnam.simulator.export.Telechargement;
import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.persistence.entity.AuditLog;
import ci.cnam.simulator.quality.AnomalieRatee;
import ci.cnam.simulator.quality.CategorieAnomalie;
import ci.cnam.simulator.quality.ComparaisonQualite;
import ci.cnam.simulator.quality.EchangeService;
import ci.cnam.simulator.quality.EtatEchange;
import ci.cnam.simulator.quality.FauxPositifSignale;
import ci.cnam.simulator.quality.LigneQualite;
import ci.cnam.simulator.quality.PageQualite;
import ci.cnam.simulator.quality.QualityReportService;
import ci.cnam.simulator.quality.StatutEchange;
import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.Echeance;
import ci.cnam.simulator.quality.planification.JoursSemaine;
import ci.cnam.simulator.quality.planification.Planification;
import ci.cnam.simulator.quality.planification.PlanificationService;
import ci.cnam.simulator.quality.planification.StatutAnalyse;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.AuditService;
import ci.cnam.simulator.security.ComptesService;
import ci.cnam.simulator.security.Role;
import ci.cnam.simulator.security.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La refonte « la simulation au centre » : TOUS les controleurs de web/ charges ensemble, avec le vrai
 * rendu des gabarits et les vrais assembleurs de modele. C'est ce test qui attrape un gabarit manquant,
 * un attribut de modele absent ou un lien reste sur l'ancien parcours.
 */
@WebMvcTest(controllers = {TableauDeBordController.class, LancementController.class, ChaosPanelController.class,
        EvenementsController.class, SimulationsController.class, EditeurScenarioController.class,
        AdminUtilisateursController.class, LoginController.class, QualiteController.class,
        PlanificationController.class, TelechargementController.class},
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ParcoursInterfaceWebTest.Support.class})
@EnableAutoConfiguration
class ParcoursInterfaceWebTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID ANCIEN = UUID.fromString("dddddddd-1111-2222-3333-444444444444");
    private static final UUID EVT = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    private static final UUID PLAN = UUID.fromString("cccccccc-1111-2222-3333-444444444444");

    @TestConfiguration
    static class Support {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter("cle");
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
        }

        /** Les vrais assembleurs : ce sont eux qui remplissent le modele que les gabarits lisent. */
        @Bean
        SimulationWebService simulationWebService(RunManager runs, ChaosInjector chaos, QualityReportService qualite,
                                                  ExportService export, Clock horloge) {
            return new SimulationWebService(runs, chaos, qualite, export, horloge);
        }

        @Bean
        AnalyseWebService analyseWebService(QualityReportService qualite, PlanificationService planification,
                                            RunManager runs) {
            return new AnalyseWebService(qualite, planification, runs);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean PilotageService pilotage;
    @MockitoBean RunManager runs;
    @MockitoBean ScenarioService scenarios;
    @MockitoBean ChaosInjector chaos;
    @MockitoBean EditeurScenarioService editeur;
    @MockitoBean EventQueryService evenements;
    @MockitoBean RejeuEvenementService rejeu;
    @MockitoBean ComptesService comptes;
    @MockitoBean AuditService audit;
    @MockitoBean QualityReportService qualite;
    @MockitoBean PlanificationService planification;
    @MockitoBean ExportService export;
    @MockitoBean EchangeService echanges;

    private static RunInfo terminee() {
        return new RunInfo(RUN, "parcours-normal", 42L, EtatRun.ARRETE, 12, 120,
                Instant.parse("2026-11-08T01:00:00Z"), Instant.parse("2026-11-08T01:12:05Z"), null);
    }

    private static RunInfo ancienne() {
        return new RunInfo(ANCIEN, "pic-de-charge", 7L, EtatRun.ECHOUE, 3, 30,
                Instant.parse("2026-11-07T01:00:00Z"), Instant.parse("2026-11-07T01:00:40Z"), "base injoignable");
    }

    private static ComparaisonQualite mesuree() {
        return new ComparaisonQualite(RUN, null, false,
                new EtatEchange(StatutEchange.RAPPORT_RECU, Instant.parse("2026-11-08T03:10:00Z"), "rapport recu"),
                LigneQualite.de("TOTAL", 100, 60, 5),
                List.of(LigneQualite.de(CategorieAnomalie.STRUCTURELLE.name(), 80, 50, 4),
                        LigneQualite.de(CategorieAnomalie.SEMANTIQUE.name(), 20, 10, 1)),
                List.of(LigneQualite.de(AnomalyType.CHAMP_VIDE.name(), 100, 60, 5)));
    }

    @BeforeEach
    void preparer() {
        when(runs.actif()).thenReturn(Optional.empty());
        when(runs.lister()).thenReturn(List.of(terminee(), ancienne()));
        List<ScenarioEnregistre> catalogue = List.of(
                new ScenarioEnregistre("parcours-normal", "nom: parcours-normal", ScenarioEnregistre.Source.FICHIER,
                        null, null),
                new ScenarioEnregistre("pic-de-charge", "nom: pic-de-charge", ScenarioEnregistre.Source.BASE,
                        null, null));
        when(scenarios.lister()).thenReturn(catalogue);
        when(editeur.lister()).thenReturn(catalogue);
        when(editeur.trouver("parcours-normal")).thenReturn(catalogue.get(0));
        when(editeur.valider(any())).thenReturn(new EditeurScenarioService.Validation(List.of(), "Scenario valide"));
        when(chaos.dureeMax()).thenReturn(Duration.ofSeconds(300));
        when(chaos.actions(RUN)).thenReturn(List.of(new ChaosAction(UUID.randomUUID(), RUN, ChaosType.LATENCE,
                Map.of("latenceMs", 500), Instant.parse("2026-11-08T01:05:00Z"),
                Instant.parse("2026-11-08T01:05:30Z"), Instant.parse("2026-11-08T01:05:30Z"), "EXPIREE", "op", 14)));
        when(evenements.rechercher(any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageEvenements(
                List.of(new EvenementLu(EVT, RUN, null, "prestations", "FACTURE_OUVERTE",
                        LocalDateTime.parse("2026-03-10T08:00:00"), Instant.parse("2026-11-08T01:00:01Z"), "ECRIT",
                        Map.of("nom", "Kone"))), 0, 50, 1));
        when(comptes.lister()).thenReturn(List.of(new AppUser("marie", "hash", Role.OBSERVATEUR)));
        when(audit.derniers()).thenReturn(List.of(new AuditLog(Instant.parse("2026-11-08T01:00:00Z"), "op",
                "RUN_LANCE", RUN.toString(), "graine 42")));
        when(qualite.comparer(any(), any())).thenReturn(mesuree());
        when(qualite.ratees(any(), anyInt(), anyInt())).thenReturn(new PageQualite<>(
                List.of(new AnomalieRatee(EVT, null, "CHAMP_VIDE", "nom", "Kone", null)), 0, 25, 40));
        when(qualite.fauxPositifs(any(), anyInt(), anyInt())).thenReturn(new PageQualite<>(
                List.of(new FauxPositifSignale(EVT, "VALEUR_NEGATIVE", "montant")), 0, 25, 5));
        when(planification.planifications()).thenReturn(List.of(new Planification(PLAN, "Analyse de nuit",
                LocalTime.of(3, 0), JoursSemaine.tous(), CibleAnalyse.DERNIERE_SIMULATION_TERMINEE, true,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))));
        when(planification.fuseau()).thenReturn(ZoneId.of("Africa/Abidjan"));
        when(planification.delaiRapport()).thenReturn(Duration.ofMinutes(120));
        when(planification.historique()).thenReturn(List.of(new Echeance(UUID.randomUUID(), PLAN, RUN,
                Instant.parse("2026-11-08T03:00:00Z"), Instant.parse("2026-11-08T03:01:00Z"), null,
                StatutAnalyse.SILENCE, "aucun rapport dans le delai")));
        when(export.telechargements(RUN)).thenReturn(List.of(new Telechargement(UUID.randomUUID(),
                FormatExport.XLSX, Instant.parse("2026-11-08T03:01:00Z"), 1200, "abc123")));
        when(export.telechargements(ANCIEN)).thenReturn(List.of());
        when(echanges.journal(any())).thenReturn(List.of());
        when(echanges.etat(any())).thenReturn(Optional.empty());
        when(echanges.delaiRapport()).thenReturn(Duration.ofMinutes(60));
    }

    private String page(String chemin, String role) throws Exception {
        return mvc.perform(get(chemin).with(user("u").roles(role))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---- Chaque route GET rend son gabarit --------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/lancement", "/lancement?scenario=pic-de-charge", "/lancement/zone",
            "/chaos", "/chaos/zone", "/chaos/pilote", "/chaos/actions",
            "/simulations", "/historique", "/simulations/aaaaaaaa-1111-2222-3333-444444444444",
            "/simulations/dddddddd-1111-2222-3333-444444444444",
            "/evenements?run=aaaaaaaa-1111-2222-3333-444444444444",
            "/evenements?run=aaaaaaaa-1111-2222-3333-444444444444&evenement=bbbbbbbb-1111-2222-3333-444444444444",
            "/qualite", "/qualite?run=aaaaaaaa-1111-2222-3333-444444444444",
            "/qualite?run=aaaaaaaa-1111-2222-3333-444444444444&comparer=dddddddd-1111-2222-3333-444444444444",
            "/qualite/export.csv?run=aaaaaaaa-1111-2222-3333-444444444444",
            "/qualite/planifications",
            "/export", "/export?run=aaaaaaaa-1111-2222-3333-444444444444",
            "/scenarios", "/scenarios/charger?nom=parcours-normal", "/scenarios/export?nom=parcours-normal",
            "/admin/utilisateurs", "/admin/journal", "/mot-de-passe", "/mot-de-passe?modifie", "/login"})
    @DisplayName("Chaque route GET de l'interface repond 200 a un ADMIN, gabarit reellement rendu")
    void chaqueRouteRendSonGabarit(String chemin) throws Exception {
        String html = page(chemin, "ADMIN");
        // Un attribut de modele absent ou un fragment introuvable ferait echouer le rendu : on verifie en plus
        // qu'aucune expression n'est restee telle quelle dans la page.
        assertThat(html).doesNotContain("${").doesNotContainPattern("\\sth:[a-z]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATEUR", "OBSERVATEUR"})
    @DisplayName("Les ecrans de lecture se rendent aussi pour les deux autres roles")
    void lecturePourLesAutresRoles(String role) throws Exception {
        for (String chemin : List.of("/dashboard", "/lancement", "/chaos", "/simulations", "/simulations/" + RUN,
                "/evenements?run=" + RUN, "/qualite", "/qualite/planifications", "/export?run=" + RUN,
                "/scenarios", "/mot-de-passe")) {
            page(chemin, role);
        }
    }

    @Test
    @DisplayName("Les redirections attendues : la racine, et le flux de donnees sans simulation choisie")
    void redirections() throws Exception {
        mvc.perform(get("/").with(user("u").roles("OBSERVATEUR"))).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/evenements").with(user("u").roles("OBSERVATEUR")))
                .andExpect(redirectedUrl("/simulations"));
    }

    // ---- La liste et la fiche ---------------------------------------------------------------------------

    private static String lignes(String html) {
        return html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
    }

    @Test
    @DisplayName("/historique (ancienne adresse) et /simulations rendent la meme liste, chaque ligne menant a sa fiche")
    void memeListeSousLesDeuxAdresses() throws Exception {
        String nouvelle = page("/simulations", "OBSERVATEUR");
        String ancienne = page("/historique", "OBSERVATEUR");

        assertThat(lignes(ancienne)).isEqualTo(lignes(nouvelle));
        assertThat(lignes(nouvelle)).contains("href=\"/simulations/" + RUN + "\"")
                .contains("href=\"/simulations/" + ANCIEN + "\"")
                .contains("parcours-normal").contains("pic-de-charge").contains("base injoignable");
    }

    @Test
    @DisplayName("Une simulation inconnue : 404, pas une fiche vide")
    void ficheInconnue() throws Exception {
        mvc.perform(get("/simulations/{id}", UUID.randomUUID()).with(user("u").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/simulations/pas-un-identifiant").with(user("u").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La fiche : duree, pannes en clair, comparaison, telechargements, et ses trois autres onglets")
    void fiche() throws Exception {
        String html = page("/simulations/" + RUN, "OPERATEUR");

        assertThat(html).contains("12 min 05 s")                       // duree, mise en forme par Libelles
                .contains("Liaison ralentie").contains("Durée écoulée")    // panne et motif traduits
                .doesNotContain(">LATENCE<").doesNotContain(">EXPIREE<")
                .containsPattern("60[.,]0 %")                              // rappel : 60 retrouvees sur 100
                .contains("fichier déjà servi")
                .contains("href=\"/evenements?run=" + RUN + "\"")
                .contains("href=\"/qualite?run=" + RUN + "\"")
                .contains("href=\"/export?run=" + RUN + "\"")
                .contains("/lancement/" + RUN + "/rejouer")
                .contains("href=\"/simulations\"");
    }

    @Test
    @DisplayName("Fiche d'une simulation dont l'echange a echoue : l'echec est nomme, aucun pourcentage")
    void ficheSansMesure() throws Exception {
        when(qualite.comparer(eq(RUN), any())).thenReturn(ComparaisonQualite.sansMesure(RUN, null,
                new EtatEchange(StatutEchange.SILENCE, Instant.parse("2026-11-08T05:00:00Z"),
                        "Le jeu a ete retire et aucun rapport n'est arrive")));

        String html = page("/simulations/" + RUN, "OBSERVATEUR");

        assertThat(html).contains("aucun rapport n&#39;est arrive").contains("Ce n'est pas un taux de rappel de 0 %")
                .doesNotContain("Taux de rappel</dt>");
    }

    @Test
    @DisplayName("Depuis la fiche, chaque onglet s'ouvre sur CETTE simulation et sait y revenir")
    void ongletsSurLaMemeSimulation() throws Exception {
        for (String chemin : List.of("/evenements?run=" + ANCIEN, "/qualite?run=" + ANCIEN,
                "/export?run=" + ANCIEN)) {
            String html = page(chemin, "OBSERVATEUR");
            assertThat(html).as(chemin)
                    .contains("href=\"/simulations/" + ANCIEN + "\"")
                    .contains("href=\"/evenements?run=" + ANCIEN + "\"")
                    .contains("href=\"/qualite?run=" + ANCIEN + "\"")
                    .contains("href=\"/export?run=" + ANCIEN + "\"")
                    .contains("aria-current=\"page\"");
        }
    }

    // ---- Droits : masquer un bouton ne suffit pas --------------------------------------------------------

    @Test
    @DisplayName("Un OBSERVATEUR voit la fiche, sans bouton d'action, et recoit 403 sur lancer, arreter, rejouer")
    void observateurVoitSansAgir() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(new RunInfo(RUN, "parcours-normal", 42L, EtatRun.EN_COURS, 12,
                120, Instant.parse("2026-11-08T09:00:00Z"), null, null)));
        var observateur = user("obs").roles("OBSERVATEUR");

        mvc.perform(get("/simulations/{id}", RUN).with(observateur))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("parcours-normal")))
                .andExpect(content().string(not(containsString("/rejouer"))))
                .andExpect(content().string(not(containsString("/arreter"))));

        mvc.perform(post("/lancement").with(csrf()).with(observateur).param("scenario", "parcours-normal"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/lancement/{id}/arreter", RUN).with(csrf()).with(observateur))
                .andExpect(status().isForbidden());
        mvc.perform(post("/lancement/{id}/rejouer", RUN).with(csrf()).with(observateur))
                .andExpect(status().isForbidden());

        verify(pilotage, never()).lancer(any(), any(), any(), any());
        verify(pilotage, never()).arreter(any(), any());
        verify(pilotage, never()).rejouer(any(), any());
    }

    @Test
    @DisplayName("Le journal d'activite est reserve a l'ADMIN, et ses actions sont dites en clair")
    void journalReserveAdmin() throws Exception {
        assertThat(page("/admin/journal", "ADMIN")).contains("Simulation lancée").doesNotContain(">RUN_LANCE<");
        mvc.perform(get("/admin/journal").with(user("op").roles("OPERATEUR"))).andExpect(status().isForbidden());
    }

    // ---- Ce que chaque gabarit fait de son modele --------------------------------------------------------

    @Test
    @DisplayName("En-tete : le role du compte est affiche en clair ; le rail mene a /simulations, plus a /historique")
    void enTeteEtRail() throws Exception {
        assertThat(page("/dashboard", "OBSERVATEUR")).contains("<small>Observateur</small>");
        String admin = page("/dashboard", "ADMIN");
        assertThat(admin).contains("<small>Administrateur</small>").contains("href=\"/simulations\"")
                .contains("href=\"/admin/journal\"").contains("data-menu-mobile")
                .doesNotContain("historique");
        assertThat(page("/dashboard", "OPERATEUR")).doesNotContain("/admin/");
    }

    @Test
    @DisplayName("Tableau de bord : les dernieres simulations menent a leur fiche, et le total a la liste")
    void tableauDeBord() throws Exception {
        assertThat(page("/dashboard", "OBSERVATEUR")).contains("Dernières simulations")
                .contains("href=\"/simulations/" + RUN + "\"").contains("Les 2 simulations");
    }

    @Test
    @DisplayName("Lancement : le scenario passe dans l'adresse est preselectionne, la derniere simulation rappelee")
    void lancement() throws Exception {
        String html = page("/lancement?scenario=pic-de-charge", "OPERATEUR");

        assertThat(html).containsPattern("<option value=\"pic-de-charge\"\\s+selected=\"selected\"")
                .doesNotContainPattern("<option value=\"parcours-normal\"\\s+selected")
                .contains("Dernière simulation").contains("href=\"/simulations/" + RUN + "\"");
        assertThat(page("/scenarios", "OPERATEUR")).contains("href=\"/lancement?scenario=pic-de-charge\"");
    }

    @Test
    @DisplayName("Mon compte : dans l'application, avec le role en clair et la confirmation apres changement")
    void monCompte() throws Exception {
        String html = page("/mot-de-passe", "OPERATEUR");
        assertThat(html).contains("Mon compte").contains("Navigation principale").contains("Opérateur")
                .contains("Lance les simulations").doesNotContain("a été modifié");

        assertThat(page("/mot-de-passe?modifie", "OPERATEUR")).contains("Votre mot de passe a été modifié");
    }

    @Test
    @DisplayName("Les codes techniques passent par Libelles : etapes du flux, types d'anomalie, categories, roles")
    void codesEnClair() throws Exception {
        assertThat(page("/evenements?run=" + RUN, "OBSERVATEUR"))
                .contains(">Feuille de soins ouverte</td>").contains(">Consultation réalisée</option>");
        assertThat(page("/qualite?run=" + RUN, "OBSERVATEUR"))
                .contains(">Champ vidé</td>").contains(">Structurelle</td>").contains("Catégorie : Sémantique");
        assertThat(page("/admin/utilisateurs", "ADMIN")).contains(">Observateur</option>")
                .contains("Consulte tout, ne modifie rien.");
    }

    @Test
    @DisplayName("Analyses programmees : leur propre ecran ; un silence y est un echec d'echange, pas un taux")
    void analysesProgrammees() throws Exception {
        assertThat(page("/qualite/planifications", "OPERATEUR")).contains("Analyse de nuit")
                .contains("Programmer une analyse").contains("échec d'échange").contains("en attente du script");
        assertThat(page("/qualite/planifications", "OBSERVATEUR")).contains("Analyse de nuit")
                .contains("Lecture seule").doesNotContain("Programmer une analyse").doesNotContain("Supprimer");
        assertThat(page("/qualite", "OBSERVATEUR")).contains("href=\"/qualite/planifications\"");
    }

    // ---- Les gabarits eux-memes --------------------------------------------------------------------------

    private static final Pattern LIEN = Pattern.compile("(?:href|action|hx-get|hx-post|src)\\s*=\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("Aucun lien de gabarit ne pointe vers /historique, et aucun n'est ecrit en dur depuis la racine")
    void liensDesGabarits() throws Exception {
        Resource[] gabarits = new PathMatchingResourcePatternResolver().getResources("classpath:/templates/**/*.html");
        assertThat(gabarits).hasSizeGreaterThan(15);
        for (Resource gabarit : gabarits) {
            String source = gabarit.getContentAsString(StandardCharsets.UTF_8);
            var liens = LIEN.matcher(source);
            while (liens.find()) {
                String cible = liens.group(1);
                assertThat(cible).as("lien de %s", gabarit.getFilename()).doesNotContain("historique");
                // th:href="@{/...}" est la seule forme admise : l'application vit sous /simulateur.
                if (cible.startsWith("/")) {
                    throw new AssertionError(gabarit.getFilename() + " : lien en dur vers la racine : " + cible);
                }
            }
        }
    }
}
