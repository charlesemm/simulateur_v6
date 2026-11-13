package ci.cnam.simulator.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunManager;
import ci.cnam.simulator.core.event.EvenementLu;
import ci.cnam.simulator.core.event.EventQueryService;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.export.ExportService;
import ci.cnam.simulator.export.FormatExport;
import ci.cnam.simulator.export.Telechargement;
import ci.cnam.simulator.quality.AnomalieRatee;
import ci.cnam.simulator.quality.CategorieAnomalie;
import ci.cnam.simulator.quality.ComparaisonQualite;
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
import ci.cnam.simulator.security.SecurityConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * L'onglet Analyse : ses trois blocs, son vocabulaire, ses droits, et le lien qui mene a la ligne
 * exacte qu'un script a manquee.
 *
 * <p>Le point le plus important est l'exigence 4.5 : un echange qui n'aboutit pas s'affiche comme un
 * echec d'echange, JAMAIS comme un taux de 0 %.
 */
@WebMvcTest(controllers = {QualiteController.class, PlanificationController.class, EvenementsController.class},
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, QualiteWebTest.Support.class})
@EnableAutoConfiguration
class QualiteWebTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID RATEE = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    private static final UUID PLAN = UUID.fromString("cccccccc-1111-2222-3333-444444444444");

    @TestConfiguration
    static class Support {
        @Bean
        ApiKeyFilter apiKeyFilter() {
            return new ApiKeyFilter("cle");
        }

        /** Le vrai assembleur de modele : c'est lui qu'on veut voir a l'oeuvre. */
        @Bean
        AnalyseWebService analyseWebService(QualityReportService qualite, PlanificationService planification,
                                            RunManager runs) {
            return new AnalyseWebService(qualite, planification, runs);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean QualityReportService qualite;
    @MockitoBean PlanificationService planification;
    @MockitoBean ExportService export;
    @MockitoBean RunManager runs;
    @MockitoBean EventQueryService evenements;
    @MockitoBean RejeuEvenementService rejeu;
    @MockitoBean AuditService audit;

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
        when(runs.lister()).thenReturn(List.of(new RunInfo(RUN, "parcours-normal", 42L, EtatRun.ARRETE,
                12, 120, Instant.parse("2026-11-08T01:00:00Z"), Instant.parse("2026-11-08T02:00:00Z"), null)));
        when(qualite.comparer(eq(RUN), any())).thenReturn(mesuree());
        when(qualite.ratees(any(), anyInt(), anyInt())).thenReturn(new PageQualite<>(
                List.of(new AnomalieRatee(RATEE, null, "CHAMP_VIDE", "nom", "Kone", null)), 0, 25, 40));
        when(qualite.fauxPositifs(any(), anyInt(), anyInt())).thenReturn(new PageQualite<>(
                List.of(new FauxPositifSignale(RATEE, "VALEUR_NEGATIVE", "montant")), 0, 25, 5));
        when(planification.planifications()).thenReturn(List.of(new Planification(PLAN, "Analyse de nuit",
                LocalTime.of(3, 0), JoursSemaine.tous(), CibleAnalyse.DERNIERE_SIMULATION_TERMINEE, true,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))));
        when(planification.fuseau()).thenReturn(ZoneId.of("Africa/Abidjan"));
        when(planification.delaiRapport()).thenReturn(Duration.ofMinutes(120));
        when(planification.historique()).thenReturn(List.of(new Echeance(UUID.randomUUID(), PLAN, RUN,
                Instant.parse("2026-11-08T03:00:00Z"), Instant.parse("2026-11-08T03:01:00Z"), null,
                StatutAnalyse.RAPPORT_RECU, "rapport recu")));
        when(export.telechargements(RUN)).thenReturn(List.of(new Telechargement(UUID.randomUUID(),
                FormatExport.XLSX, Instant.parse("2026-11-08T03:01:00Z"), 1200, "abc123")));
        when(evenements.rechercher(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageEvenements(List.of(new EvenementLu(RATEE, RUN, null, "prestations",
                        "CONSULTATION_REALISEE", LocalDateTime.parse("2026-03-10T08:00:00"),
                        Instant.parse("2026-11-08T01:30:00Z"), "ECRIT", Map.of("nom", "Kone"))), 0, 50, 1));
    }

    // ---- Les trois blocs -------------------------------------------------------------------------------

    @Test
    @DisplayName("Trois blocs sur deux ecrans : Planification et Analyses d'un cote, le Resultat de l'autre")
    void troisBlocs() throws Exception {
        mvc.perform(get("/qualite/planifications").with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Planification")))
                .andExpect(content().string(containsString("Analyses")))
                .andExpect(content().string(containsString("Analyse de nuit")));
        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/qualite/planifications")))
                .andExpect(content().string(containsString("Détail par type")))
                .andExpect(content().string(containsString("Périmètre comparé")))
                .andExpect(content().string(containsString("parcours-normal")))
                .andExpect(content().string(containsString("42")));
    }

    @Test
    @DisplayName("Vocabulaire du metier : « Taux de rappel » et « Précision », avec leur definition sous le chiffre")
    void vocabulaire() throws Exception {
        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Taux de rappel")))
                .andExpect(content().string(containsString("Précision")))
                .andExpect(content().string(containsString("anomalies retrouvées sur anomalies semées")))
                .andExpect(content().string(containsString("anomalies justes sur anomalies signalées")))
                .andExpect(content().string(not(containsString("Taux de détection"))));
    }

    @Test
    @DisplayName("Liens vers les trois formats et le corrige ; les empreintes sont sur l'ecran d'export")
    void telechargements() throws Exception {
        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("jeu.xlsx")))
                .andExpect(content().string(containsString("jeu.csv")))
                .andExpect(content().string(containsString("jeu.json")))
                .andExpect(content().string(containsString("corrige.csv")))
                .andExpect(content().string(containsString("/export?run=" + RUN)))
                .andExpect(content().string(not(containsString("abc123"))));
    }

    // ---- Exigence 4.5 : un echec d'echange n'est jamais un taux ----------------------------------------

    @Test
    @DisplayName("Echange en SILENCE : « en attente du script » et AUCUN pourcentage")
    void silenceSansPourcentage() throws Exception {
        when(qualite.comparer(eq(RUN), any())).thenReturn(ComparaisonQualite.sansMesure(RUN, null,
                new EtatEchange(StatutEchange.SILENCE, Instant.parse("2026-11-08T05:00:00Z"),
                        "Le jeu a ete retire et aucun rapport n'est arrive")));

        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("en attente du script")))
                .andExpect(content().string(containsString("Échec d'échange")))
                .andExpect(content().string(not(containsString("kpi-valeur"))))
                .andExpect(content().string(not(containsString("Taux de rappel"))))
                .andExpect(content().string(not(containsString("Détail par type"))));
    }

    @Test
    @DisplayName("Echeance en SILENCE : l'historique dit « en attente du script », pas un taux")
    void echeanceEnSilence() throws Exception {
        when(planification.historique()).thenReturn(List.of(new Echeance(UUID.randomUUID(), PLAN, RUN,
                Instant.parse("2026-11-08T03:00:00Z"), Instant.parse("2026-11-08T03:01:00Z"), null,
                StatutAnalyse.SILENCE, "aucun rapport dans le delai")));
        when(qualite.comparer(eq(RUN), any())).thenReturn(ComparaisonQualite.sansMesure(RUN, null,
                new EtatEchange(StatutEchange.SILENCE, Instant.parse("2026-11-08T05:00:00Z"), "silence")));

        mvc.perform(get("/qualite/planifications").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("échec d'échange")))
                .andExpect(content().string(containsString("en attente du script")))
                .andExpect(content().string(not(containsString("kpi-valeur"))));
        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("en attente du script")))
                .andExpect(content().string(not(containsString("kpi-valeur"))));
    }

    // ---- Les lignes sont cliquables --------------------------------------------------------------------

    @Test
    @DisplayName("Une anomalie ratee et un faux positif portent un lien vers le flux filtre sur leur event_id")
    void lignesCliquables() throws Exception {
        mvc.perform(get("/qualite").with(user("op").roles("OPERATEUR")))
                .andExpect(content().string(containsString("Anomalies ratées")))
                .andExpect(content().string(containsString("Faux positifs")))
                .andExpect(content().string(containsString("evenement=" + RATEE)));
    }

    @Test
    @DisplayName("Le lien mene au flux de donnees filtre sur cet event_id, qui affiche exactement une ligne")
    void lienMeneALaLigneExacte() throws Exception {
        mvc.perform(get("/evenements").param("run", RUN.toString()).param("evenement", RATEE.toString())
                        .with(user("op").roles("OPERATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Une seule ligne")))
                .andExpect(content().string(containsString("Contenu de l'événement")))
                .andExpect(content().string(containsString("Kone")));

        verify(evenements).rechercher(RUN, RATEE, "", "", "", 0, 50);
    }

    // ---- Droits ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATEUR", "OBSERVATEUR"})
    @DisplayName("Les trois roles lisent l'ecran")
    void lectureOuverteAuxTroisRoles(String role) throws Exception {
        mvc.perform(get("/qualite").with(user("u").roles(role))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un OBSERVATEUR voit l'ecran mais aucun bouton d'ecriture")
    void observateurSansBouton() throws Exception {
        mvc.perform(get("/qualite").with(user("obs").roles("OBSERVATEUR"))).andExpect(status().isOk());
        mvc.perform(get("/qualite/planifications").with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Analyse de nuit")))
                .andExpect(content().string(not(containsString("Programmer une analyse"))))
                .andExpect(content().string(not(containsString("Supprimer"))));
    }

    @Test
    @DisplayName("Un OBSERVATEUR : ses POST sont refuses cote serveur, et rien n'est ecrit")
    void observateurSansEcriture() throws Exception {
        mvc.perform(post("/qualite/planifications").with(csrf()).with(user("obs").roles("OBSERVATEUR"))
                        .param("libelle", "Analyse").param("heure", "03:00"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/qualite/planifications/{id}/supprimer", PLAN).with(csrf())
                        .with(user("obs").roles("OBSERVATEUR")))
                .andExpect(status().isForbidden());

        verify(planification, never()).creer(anyString(), any(), anyString(), any());
        verify(planification, never()).supprimer(any());
        verifyNoInteractions(audit);
    }
}
