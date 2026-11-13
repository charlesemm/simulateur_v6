package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.chaos.ChaosAction;
import ci.cnam.simulator.chaos.ChaosInjector;
import ci.cnam.simulator.chaos.ChaosType;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EvenementLu;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.monitoring.EtatTableauDeBord;
import ci.cnam.simulator.monitoring.SseController;
import ci.cnam.simulator.monitoring.TableauDeBordService;
import ci.cnam.simulator.persistence.entity.AppUser;
import ci.cnam.simulator.security.ApiKeyFilter;
import ci.cnam.simulator.security.AuditService;
import ci.cnam.simulator.security.CompteCree;
import ci.cnam.simulator.security.ComptesService;
import ci.cnam.simulator.security.ComptesService.ComptesException;
import ci.cnam.simulator.security.Role;
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tous les ecrans, la couche web seule (les services sont simules) : contenu, routes, droits par role,
 * CSRF, refus metier (422), liens sous le chemin /simulateur.
 */
@WebMvcTest(controllers = {TableauDeBordController.class, LancementController.class, ChaosPanelController.class,
        EvenementsController.class, SimulationsController.class, EditeurScenarioController.class,
        AdminUtilisateursController.class, LoginController.class, SseController.class},
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, InterfaceWebTest.Support.class})
@EnableAutoConfiguration
class InterfaceWebTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID EVT = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    @TestConfiguration
    static class Support {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter("cle");
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
        }

        /** Le vrai assembleur de la fiche : la liste des simulations passe par lui. */
        @Bean
        SimulationWebService simulationWebService(RunManager runs, ChaosInjector chaos,
                                                  ci.cnam.simulator.quality.QualityReportService qualite,
                                                  ci.cnam.simulator.export.ExportService export, Clock horloge) {
            return new SimulationWebService(runs, chaos, qualite, export, horloge);
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
    @MockitoBean TableauDeBordService tableau;
    @MockitoBean ci.cnam.simulator.quality.QualityReportService qualite;
    @MockitoBean ci.cnam.simulator.export.ExportService export;

    private static RunInfo run(EtatRun etat) {
        return new RunInfo(RUN, "parcours-normal", 42L, etat, 12, 120, Instant.parse("2026-11-05T09:00:00Z"),
                null, null);
    }

    private static EvenementLu evenement(UUID id, String type, String statut) {
        return new EvenementLu(id, RUN, null, "prestations", type, LocalDateTime.parse("2026-03-10T08:00:00"),
                Instant.parse("2026-11-05T09:00:01Z"), statut, Map.of("nom", "Kone"));
    }

    @BeforeEach
    void preparer() {
        when(runs.actif()).thenReturn(Optional.empty());
        when(runs.lister()).thenReturn(List.of(run(EtatRun.ARRETE)));
        List<ScenarioEnregistre> catalogue = List.of(
                new ScenarioEnregistre("parcours-normal", "nom: parcours-normal", ScenarioEnregistre.Source.FICHIER,
                        null, null));
        when(scenarios.lister()).thenReturn(catalogue);
        when(chaos.dureeMax()).thenReturn(Duration.ofSeconds(300));
        when(evenements.rechercher(any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageEvenements(
                List.of(evenement(EVT, "FACTURE_OUVERTE", "ECRIT"),
                        evenement(UUID.randomUUID(), "=FORMULE", "ECHEC")), 0, 50, 2));
        when(editeur.lister()).thenReturn(catalogue);
        when(editeur.valider(any())).thenReturn(new EditeurScenarioService.Validation(List.of(), "Scenario valide : x"));
        when(tableau.dernier()).thenReturn(new EtatTableauDeBord(Instant.parse("2026-11-05T10:00:00Z"), null,
                List.of(), 0, 0, 0, 0, 0, 0, "UP", new EtatTableauDeBord.ProxyVue("ACTIF", List.of())));
        when(comptes.lister()).thenReturn(List.of(new AppUser("marie", "hash", Role.OBSERVATEUR)));
    }

    // ---- Acces et droits -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/lancement", "/chaos", "/evenements", "/historique", "/scenarios",
            "/admin/utilisateurs", "/flux", "/"})
    @DisplayName("Sans connexion : toute page renvoie vers /login")
    void sansConnexion(String chemin) throws Exception {
        mvc.perform(get(chemin)).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATEUR", "OBSERVATEUR"})
    @DisplayName("Les trois roles lisent tous les ecrans de pilotage")
    void lectureOuverteAuxTroisRoles(String role) throws Exception {
        for (String chemin : List.of("/dashboard", "/lancement", "/chaos", "/evenements?run=" + RUN, "/simulations",
                "/historique", "/scenarios")) {
            mvc.perform(get(chemin).with(user("u").roles(role))).andExpect(status().isOk());
        }
        // Sans simulation choisie, le flux de donnees renvoie a la liste : c'est elle qui fait choisir.
        mvc.perform(get("/evenements").with(user("u").roles(role))).andExpect(redirectedUrl("/simulations"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATEUR", "OBSERVATEUR"})
    @DisplayName("Administration : refusee aux roles autres que ADMIN, en lecture comme en ecriture")
    void administrationReserveeAdmin(String role) throws Exception {
        mvc.perform(get("/admin/utilisateurs").with(user("u").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/utilisateurs").with(csrf()).with(user("u").roles(role))
                        .param("nom", "x").param("motDePasse", "un-mot-de-passe-long").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        verify(comptes, never()).creer(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lancement", "/lancement/aaaaaaaa-1111-2222-3333-444444444444/pause",
            "/lancement/aaaaaaaa-1111-2222-3333-444444444444/reprendre",
            "/lancement/aaaaaaaa-1111-2222-3333-444444444444/arreter",
            "/lancement/aaaaaaaa-1111-2222-3333-444444444444/rejouer",
            "/chaos/declencher", "/chaos/retablir", "/scenarios/enregistrer",
            "/evenements/bbbbbbbb-1111-2222-3333-444444444444/rejouer"})
    @DisplayName("OBSERVATEUR : aucune action d'ecriture n'est possible (controle serveur, pas seulement boutons masques)")
    void observateurSansEcriture(String chemin) throws Exception {
        mvc.perform(post(chemin).with(csrf()).with(user("obs").roles("OBSERVATEUR")).param("scenario", "x")
                        .param("yaml", "nom: x").param("type", "COUPURE_BASE").param("dureeSecondes", "10"))
                .andExpect(status().isForbidden());
        verify(pilotage, never()).lancer(any(), any(), any(), any());
        verify(pilotage, never()).declencher(any(), any(), any());
        verify(pilotage, never()).retablir(any(), any());
        verify(rejeu, never()).rejouer(any(), any());
        verify(editeur, never()).enregistrer(any(), any());
    }

    @Test
    @DisplayName("CSRF : un POST sans jeton est refuse, meme pour un ADMIN")
    void csrfObligatoire() throws Exception {
        mvc.perform(post("/chaos/retablir").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());
        verify(pilotage, never()).retablir(any(), any());
    }

    // ---- Tableau de bord, liens, ressources locales -----------------------------------------------------

    @Test
    @DisplayName("Tableau de bord : page statique branchee sur /flux, tous les liens sous /simulateur, aucune ressource externe")
    void tableauDeBord() throws Exception {
        MvcResult resultat = mvc.perform(get("/simulateur/dashboard").contextPath("/simulateur")
                        .with(user("u").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-flux=\"/simulateur/flux\"")))
                .andExpect(content().string(containsString("href=\"/simulateur/lancement\"")))
                .andExpect(content().string(containsString("src=\"/simulateur/js/vendor/htmx.min.js\"")))
                .andExpect(content().string(containsString("src=\"/simulateur/js/vendor/chart.umd.js\"")))
                .andReturn();
        String html = resultat.getResponse().getContentAsString();
        assertThat(html).doesNotContainPattern("(src|href)=\"(https?:)?//");
        assertThat(html).doesNotContain("href=\"/dashboard").doesNotContain("href=\"/lancement");
    }

    @Test
    @DisplayName("La racine renvoie vers le tableau de bord")
    void racine() throws Exception {
        mvc.perform(get("/").with(user("u").roles("OBSERVATEUR"))).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("Flux SSE : ouvert aux comptes connectes, type text/event-stream, mise en tampon desactivee pour le proxy")
    void fluxSse() throws Exception {
        mvc.perform(get("/flux").with(user("u").roles("OBSERVATEUR")))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    // ---- Lancement --------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lancement : l'OPERATEUR voit le formulaire, l'OBSERVATEUR un message de lecture seule sans formulaire")
    void lancementSelonRole() throws Exception {
        mvc.perform(get("/lancement").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("Démarrer")))
                .andExpect(content().string(containsString("parcours-normal")));
        mvc.perform(get("/lancement").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(content().string(containsString("Lecture seule")))
                .andExpect(content().string(not(containsString("Démarrer"))));
    }

    @Test
    @DisplayName("Lancer : graine et vitesse transmises au service, message de confirmation")
    void lancer() throws Exception {
        when(pilotage.lancer("parcours-normal", 99L, 10.0, "op")).thenReturn(run(EtatRun.EN_COURS));

        mvc.perform(post("/lancement").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("scenario", "parcours-normal").param("graine", "99").param("vitesse", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lancé (graine 42)")));
        verify(pilotage).lancer("parcours-normal", 99L, 10.0, "op");
    }

    @Test
    @DisplayName("Un refus metier (run deja actif) s'affiche en 422 avec son message, sans page d'erreur")
    void lancerRefuse() throws Exception {
        when(pilotage.lancer(any(), any(), any(), any())).thenThrow(new IllegalStateException("Un run est deja actif"));

        mvc.perform(post("/lancement").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("scenario", "parcours-normal"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Un run est deja actif")));
    }

    @Test
    @DisplayName("Pause, reprise, arret et rejeu appellent le service avec l'identifiant du run et le nom du compte")
    void pilotageDuRun() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(run(EtatRun.EN_COURS)));
        when(pilotage.rejouer(RUN, "op")).thenReturn(run(EtatRun.EN_COURS));

        for (String action : List.of("pause", "reprendre", "arreter", "rejouer")) {
            mvc.perform(post("/lancement/{id}/" + action, RUN).with(csrf()).with(user("op").roles("OPERATEUR")))
                    .andExpect(status().isOk());
        }
        verify(pilotage).pause(RUN, "op");
        verify(pilotage).reprendre(RUN, "op");
        verify(pilotage).arreter(RUN, "op");
        verify(pilotage).rejouer(RUN, "op");
    }

    @Test
    @DisplayName("Run en cours : les boutons Pause et Arreter (avec confirmation) sont proposes a l'OPERATEUR seulement")
    void boutonsDuRunActif() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(run(EtatRun.EN_COURS)));

        mvc.perform(get("/lancement").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("/lancement/" + RUN + "/pause")))
                .andExpect(content().string(containsString("hx-confirm=\"Arrêter la simulation en cours")));
        mvc.perform(get("/lancement").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(content().string(not(containsString("/lancement/" + RUN + "/pause"))));
    }

    // ---- Chaos ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Panneau chaos : cinq confirmations (4 actions + tout retablir) pour l'ADMIN, aucun bouton pour l'OBSERVATEUR")
    void panneauChaosSelonRole() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(run(EtatRun.EN_COURS)));

        String admin = mvc.perform(get("/chaos").with(user("a").roles("ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin.split("hx-confirm", -1).length - 1).isGreaterThanOrEqualTo(5);
        assertThat(admin).contains("Couper la base", "Ralentir", "Surcharger", "Tronquer des messages", "Tout rétablir");

        mvc.perform(get("/chaos").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(content().string(containsString("Lecture seule")))
                .andExpect(content().string(not(containsString("hx-confirm"))))
                .andExpect(content().string(not(containsString("Couper la base"))))
                .andExpect(content().string(not(containsString("Tout rétablir"))));
    }

    @Test
    @DisplayName("Declencher : le formulaire est traduit en DemandeChaos et envoye au run actif, au nom du compte")
    void declencherChaos() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(run(EtatRun.EN_COURS)));
        when(pilotage.declencher(eq(RUN), any(), eq("op"))).thenReturn(new ChaosAction(UUID.randomUUID(), RUN,
                ChaosType.LATENCE, Map.of("latenceMs", 500), Instant.now(), Instant.now(), null, null, "op", 0));

        mvc.perform(post("/chaos/declencher").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("type", "LATENCE").param("dureeSecondes", "30").param("latenceMs", "500")
                        .param("jitterMs", "50"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LATENCE")));

        verify(pilotage).declencher(eq(RUN), argThat(d -> d.type() == ChaosType.LATENCE
                && d.dureeSecondes() == 30 && d.latenceMs() == 500 && d.jitterMs() == 50), eq("op"));
    }

    @Test
    @DisplayName("Declencher sans run actif : refus 422 explicite, rien n'est envoye")
    void declencherSansRun() throws Exception {
        mvc.perform(post("/chaos/declencher").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("type", "COUPURE_BASE").param("dureeSecondes", "30"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Aucun run actif")));
        verify(pilotage, never()).declencher(any(), any(), any());
    }

    @Test
    @DisplayName("Tout retablir : agit sur le run le plus recent meme s'il vient de s'arreter")
    void toutRetablir() throws Exception {
        when(pilotage.retablir(RUN, "op")).thenReturn(List.of());

        mvc.perform(post("/chaos/retablir").with(csrf()).with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tout est rétabli")));
        verify(pilotage).retablir(RUN, "op");
    }

    @Test
    @DisplayName("La liste des actions de chaos se rafraichit seule (fragment, sans la barre de navigation)")
    void listeDesActions() throws Exception {
        when(runs.actif()).thenReturn(Optional.of(run(EtatRun.EN_COURS)));
        when(chaos.actions(RUN)).thenReturn(List.of(new ChaosAction(UUID.randomUUID(), RUN, ChaosType.SURCHARGE,
                Map.of("multiplicateur", 20), Instant.parse("2026-11-05T10:00:00Z"),
                Instant.parse("2026-11-05T10:00:30Z"), null, null, "op", 0)));

        mvc.perform(get("/chaos/actions").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Surcharge")))
                .andExpect(content().string(containsString("en cours")))
                .andExpect(content().string(not(containsString("Navigation principale"))));
    }

    // ---- Evenements -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Explorateur : le bouton Rejouer n'apparait que sur un evenement non ecrit, et pas pour l'OBSERVATEUR")
    void explorateur() throws Exception {
        mvc.perform(get("/evenements").param("run", RUN.toString()).with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FACTURE_OUVERTE")))
                .andExpect(content().string(containsString("2</strong> événement")))
                .andExpect(content().string(containsString("/evenements/export.csv?run=" + RUN)))
                .andExpect(content().string(containsString("hx-confirm=\"Rejouer cet événement échoué")));
        mvc.perform(get("/evenements").param("run", RUN.toString()).with(user("obs").roles("OBSERVATEUR")))
                .andExpect(content().string(not(containsString("Rejouer"))));
    }

    @Test
    @DisplayName("Les filtres sont transmis au service ; une requete htmx ne rend que le tableau")
    void filtresEtFragment() throws Exception {
        mvc.perform(get("/evenements").param("run", RUN.toString()).param("type", "FACTURE_OUVERTE")
                        .param("statut", "ECHEC").param("page", "1").param("taille", "20")
                        .header("HX-Request", "true").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Vary", containsString("HX-Request")))
                .andExpect(content().string(containsString("id=\"tableau\"")))
                .andExpect(content().string(not(containsString("Navigation principale"))));
        verify(evenements).rechercher(RUN, null, "", "FACTURE_OUVERTE", "ECHEC", 1, 20);
    }

    @Test
    @DisplayName("Export CSV : en-tete, BOM, evenements, formules neutralisees ; ouvert a l'OBSERVATEUR (lecture)")
    void exportCsv() throws Exception {
        when(evenements.rechercher(eq(RUN), any(), any(), any(), any(), eq(1), any()))
                .thenReturn(new PageEvenements(List.of(), 1, 500, 2));

        MvcResult debut = mvc.perform(get("/evenements/export.csv").param("run", RUN.toString())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult fin = mvc.perform(asyncDispatch(debut)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("evenements-aaaaaaaa.csv")))
                .andReturn();

        String csv = fin.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(fin.getResponse().getContentType()).startsWith("text/csv");
        assertThat(csv).startsWith("﻿event_id,run_id,facture_id,domaine,type,");
        assertThat(csv).contains(EVT.toString()).contains("FACTURE_OUVERTE").contains(",'=FORMULE,");
        assertThat(csv.split("\r\n")).hasSize(3);   // en-tete + 2 evenements
    }

    @Test
    @DisplayName("Rejouer un evenement : appel du service au nom du compte ; refus metier en 422")
    void rejouerEvenement() throws Exception {
        mvc.perform(post("/evenements/{id}/rejouer", EVT).with(csrf()).with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Rejoué")));
        verify(rejeu).rejouer(EVT, "op");

        org.mockito.Mockito.doThrow(new IllegalArgumentException("Cet evenement est deja ecrit"))
                .when(rejeu).rejouer(any(), any());
        mvc.perform(post("/evenements/{id}/rejouer", EVT).with(csrf()).with(user("op").roles("OPERATEUR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("deja ecrit")));
    }

    // ---- Simulations (ancien historique, dont l'adresse reste servie) -------------------------------------

    @Test
    @DisplayName("Liste des simulations sous l'ancienne adresse : lien vers la fiche, rejeu propose a l'OPERATEUR seulement")
    void historique() throws Exception {
        mvc.perform(get("/historique").with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aaaaaaaa")))
                .andExpect(content().string(containsString("parcours-normal")))
                .andExpect(content().string(containsString("/simulations/" + RUN)))
                .andExpect(content().string(containsString("/lancement/" + RUN + "/rejouer")));
        mvc.perform(get("/historique").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(content().string(not(containsString("/rejouer"))));
    }

    // ---- Editeur de scenarios ---------------------------------------------------------------------------

    @Test
    @DisplayName("Editeur : generation du YAML depuis le formulaire, affiche dans la zone de texte")
    void genererYaml() throws Exception {
        when(editeur.genererYaml(any())).thenReturn("nom: \"Mon scenario\"\nseed: 7\n");

        mvc.perform(post("/scenarios/generer").with(csrf()).with(user("obs").roles("OBSERVATEUR"))
                        .param("nom", "Mon scenario").param("seed", "7").param("debit", "5")
                        .param("phaseDe", "0m", "").param("phaseA", "10m", "").param("phaseCharge", "normale", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("seed: 7")))
                .andExpect(content().string(containsString("Scenario valide")));
        verify(editeur).genererYaml(argThat(f -> f.seed() == 7L && f.debit() == 5.0
                && f.phaseDe().equals(List.of("0m", ""))));
    }

    @Test
    @DisplayName("Validation : toutes les erreurs du chargeur sont affichees, avec le chemin du champ")
    void validerAvecErreurs() throws Exception {
        when(editeur.valider(any())).thenReturn(new EditeurScenarioService.Validation(
                List.of("seed : nombre attendu", "domaines.prestations.debit : invalide"), null));

        mvc.perform(post("/scenarios/valider").with(csrf()).with(user("obs").roles("OBSERVATEUR"))
                        .param("yaml", "nom: x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("seed : nombre attendu")))
                .andExpect(content().string(containsString("domaines.prestations.debit : invalide")));
    }

    @Test
    @DisplayName("Enregistrer : OPERATEUR autorise, refus du chargeur affiche en 422")
    void enregistrer() throws Exception {
        Scenario s = new Scenario("mon", 1L, 1.0, LocalDateTime.parse("2026-03-10T08:00:00"), Map.of(), List.of(), "x");
        when(editeur.enregistrer("nom: mon", "op")).thenReturn(s);
        mvc.perform(post("/scenarios/enregistrer").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("yaml", "nom: mon"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("enregistré")));

        when(editeur.enregistrer(eq("nom: mauvais"), any())).thenThrow(
                new ci.cnam.simulator.core.scenario.ScenarioInvalideException(List.of("seed : manquant")));
        mvc.perform(post("/scenarios/enregistrer").with(csrf()).with(user("op").roles("OPERATEUR"))
                        .param("yaml", "nom: mauvais"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("seed : manquant")));
    }

    @Test
    @DisplayName("Import : le contenu du fichier remplit l'editeur (rien n'est enregistre) ; fichier vide refuse")
    void importer() throws Exception {
        mvc.perform(multipart("/scenarios/importer")
                        .file(new MockMultipartFile("fichier", "s.yaml", "text/yaml", "nom: importe\nseed: 3\n".getBytes()))
                        .with(csrf()).with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nom: importe")));
        verify(editeur, never()).enregistrer(any(), any());

        mvc.perform(multipart("/scenarios/importer")
                        .file(new MockMultipartFile("fichier", "vide.yaml", "text/yaml", new byte[0]))
                        .with(csrf()).with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Export : fichier YAML en piece jointe, nom de fichier assaini")
    void exporter() throws Exception {
        when(editeur.trouver("Prestations - ../x")).thenReturn(
                new ScenarioEnregistre("Prestations - ../x", "nom: x\nseed: 1\n", ScenarioEnregistre.Source.BASE, null, null));

        mvc.perform(get("/scenarios/export").param("nom", "Prestations - ../x").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("Prestations_-_.._x.yaml")))
                .andExpect(content().string("nom: x\nseed: 1\n"));
    }

    // ---- Administration ---------------------------------------------------------------------------------

    @Test
    @DisplayName("Administration : l'ADMIN liste les comptes et en cree un ; les refus metier s'affichent en 422")
    void administration() throws Exception {
        mvc.perform(get("/admin/utilisateurs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("marie")))
                .andExpect(content().string(containsString("Mot de passe à changer")));

        when(comptes.creer("admin", "paul", "paul@cnam.ci", "un-mot-de-passe-long", Role.OPERATEUR))
                .thenReturn(new CompteCree(new AppUser("paul", "h", Role.OPERATEUR), "tire-au-hasard-1234"));
        mvc.perform(post("/admin/utilisateurs").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("nom", "paul").param("email", "paul@cnam.ci")
                        .param("motDePasse", "un-mot-de-passe-long").param("role", "OPERATEUR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("paul")))
                // Le mot de passe temporaire est rendu une fois, dans cette reponse et nulle part ailleurs.
                .andExpect(content().string(containsString("tire-au-hasard-1234")));

        when(comptes.creer(any(), any(), any(), any(), any()))
                .thenThrow(new ComptesException("Ce nom d utilisateur existe deja"));
        mvc.perform(post("/admin/utilisateurs").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("nom", "paul").param("motDePasse", "un-mot-de-passe-long").param("role", "OPERATEUR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("existe deja")));
    }

    @Test
    @DisplayName("Administration : role, activation, deverrouillage et mot de passe passent par le service, au nom de l'ADMIN")
    void actionsSurUnCompte() throws Exception {
        UUID id = UUID.randomUUID();
        var admin = user("admin").roles("ADMIN");

        mvc.perform(post("/admin/utilisateurs/{id}/role", id).with(csrf()).with(admin).param("role", "OPERATEUR"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/utilisateurs/{id}/actif", id).with(csrf()).with(admin)).andExpect(status().isOk());
        mvc.perform(post("/admin/utilisateurs/{id}/deverrouiller", id).with(csrf()).with(admin))
                .andExpect(status().isOk());
        when(comptes.reinitialiserMotDePasse("admin", id, "temporaire-12345"))
                .thenReturn(new CompteCree(new AppUser("marie", "h", Role.OBSERVATEUR), "temporaire-12345"));
        mvc.perform(post("/admin/utilisateurs/{id}/mot-de-passe", id).with(csrf()).with(admin)
                .param("motDePasse", "temporaire-12345")).andExpect(status().isOk());
        mvc.perform(post("/admin/utilisateurs/{id}/email", id).with(csrf()).with(admin)
                .param("email", "marie@cnam.ci")).andExpect(status().isOk());

        verify(comptes).changerRole("admin", id, Role.OPERATEUR);
        verify(comptes).basculerActif("admin", id);
        verify(comptes).deverrouiller("admin", id);
        verify(comptes).reinitialiserMotDePasse("admin", id, "temporaire-12345");
        verify(comptes).changerEmail("admin", id, "marie@cnam.ci");
    }

    // ---- Connexion --------------------------------------------------------------------------------------

    @Test
    @DisplayName("Page de connexion : accessible sans compte, formulaire avec jeton CSRF, ressources locales")
    void pageDeConnexion() throws Exception {
        MvcResult r = mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\""))).andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContainPattern("(src|href)=\"(https?:)?//");
        // Styles, scripts et polices doivent repondre SANS etre connecte, sinon la page de connexion
        // retombe silencieusement sur la police du systeme.
        for (String ressource : List.of("/css/app.css", "/css/tokens.css", "/css/base.css",
                "/css/composants.css", "/css/mise-en-page.css",
                "/fonts/plex-sans-var.woff2", "/fonts/plex-mono-400.woff2",
                "/js/theme.js", "/js/app.js")) {
            mvc.perform(get(ressource)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Message d'erreur de connexion generique, sans indiquer si le compte existe")
    void messageGenerique() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Identifiant ou mot de passe incorrect.")));
    }
}
