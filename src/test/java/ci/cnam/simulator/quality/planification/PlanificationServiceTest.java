package ci.cnam.simulator.quality.planification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.SimulateurProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le calendrier, horloge en main : aucune attente reelle, aucun sommeil. Tout ce qui depend de
 * l'heure passe par une {@link Clock} pilotee, et la base est remplacee par un magasin en memoire
 * qui reproduit la SEULE regle qui compte ici : l'unicite (planification, echeance).
 */
class PlanificationServiceTest {

    private static final int DELAI = 60;
    /** Un dimanche, pour que le decoupage des jours de la semaine se voie. */
    private static final Instant T0 = Instant.parse("2026-11-08T00:30:00Z");

    /** Magasin en memoire : l'unicite (planification_id, date_echeance) est reproduite telle quelle. */
    private static class Magasin implements PlanificationStore {

        private final List<Planification> regles = new ArrayList<>();
        private final List<Echeance> echeances = new ArrayList<>();
        private UUID derniereTerminee;

        @Override
        public void creer(Planification p) {
            regles.add(p);
        }

        @Override
        public boolean changerActivite(UUID id, boolean actif, Instant maintenant) {
            for (int i = 0; i < regles.size(); i++) {
                Planification p = regles.get(i);
                if (p.id().equals(id)) {
                    regles.set(i, new Planification(p.id(), p.libelle(), p.heure(), p.jours(), p.cible(),
                            actif, p.dateCreation(), maintenant));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean supprimer(UUID id) {
            return regles.removeIf(p -> p.id().equals(id));
        }

        @Override
        public List<Planification> planifications() {
            return List.copyOf(regles);
        }

        @Override
        public Optional<Planification> planification(UUID id) {
            return regles.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public boolean ouvrir(Echeance e) {
            boolean deja = echeances.stream().anyMatch(x -> e.planificationId() != null
                    && e.planificationId().equals(x.planificationId())
                    && x.dateEcheance().equals(e.dateEcheance()));
            if (deja) {
                return false;
            }
            echeances.add(e);
            return true;
        }

        @Override
        public List<Echeance> echeances(int limite) {
            return echeances.stream()
                    .sorted((a, b) -> b.dateEcheance().compareTo(a.dateEcheance())).limit(limite).toList();
        }

        @Override
        public List<Echeance> echeancesAuStatut(StatutAnalyse statut) {
            return echeances.stream().filter(e -> e.statut() == statut)
                    .sorted((a, b) -> a.dateEcheance().compareTo(b.dateEcheance())).toList();
        }

        @Override
        public Optional<Instant> derniereEcheance(UUID planificationId) {
            return echeances.stream().filter(e -> planificationId.equals(e.planificationId()))
                    .map(Echeance::dateEcheance).max(Instant::compareTo);
        }

        @Override
        public Optional<Echeance> echeanceOuvertePour(UUID runId) {
            return echeances.stream()
                    .filter(e -> runId.equals(e.runId()) && e.statut().ouverte())
                    .min((a, b) -> a.dateEcheance().compareTo(b.dateEcheance()));
        }

        @Override
        public void avancer(UUID id, StatutAnalyse statut, Instant retrait, Instant rapport, String detail) {
            for (int i = 0; i < echeances.size(); i++) {
                Echeance e = echeances.get(i);
                if (e.id().equals(id)) {
                    echeances.set(i, new Echeance(e.id(), e.planificationId(), e.runId(), e.dateEcheance(),
                            retrait == null ? e.dateRetrait() : retrait,
                            rapport == null ? e.dateRapport() : rapport, statut, detail));
                    return;
                }
            }
        }

        @Override
        public Optional<UUID> derniereSimulationTerminee() {
            return Optional.ofNullable(derniereTerminee);
        }
    }

    private final Magasin magasin = new Magasin();

    /** L'horloge est une variable : les tests la font avancer a la main. */
    private Instant maintenant = T0;

    /** Horloge pilotee : elle rend ce que le test a decide, sans jamais attendre. */
    private final Clock horloge = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    };

    private final PlanificationService service = new PlanificationService(magasin, horloge,
            new SimulateurProperties(42, "scenarios", null, null, null, null,
                    new SimulateurProperties.Qualite(DELAI, "UTC")));

    private Planification regleA3h() {
        return service.creer("Analyse de nuit", LocalTime.of(3, 0), JoursSemaine.TOUS, null);
    }

    @Test
    @DisplayName("03h00 TOUS les jours : exactement une echeance par jour, et aucune en avance")
    void uneParJour() {
        magasin.derniereTerminee = UUID.randomUUID();
        regleA3h();

        // 02h59 : rien. L'echeance ne s'ouvre pas en avance.
        maintenant = Instant.parse("2026-11-08T02:59:00Z");
        assertThat(service.ouvrirEcheancesDues()).isZero();
        assertThat(service.enAttente()).isEmpty();

        // 03h00 : une, et une seule.
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);

        // Le reste de la journee : plus rien, l'heure est passee.
        for (String heure : List.of("03:01", "12:00", "23:59")) {
            maintenant = Instant.parse("2026-11-08T" + heure + ":00Z");
            assertThat(service.ouvrirEcheancesDues()).isZero();
        }

        // Le lendemain a la meme heure : une de plus. Trois jours, trois echeances.
        maintenant = Instant.parse("2026-11-09T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
        maintenant = Instant.parse("2026-11-10T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);

        assertThat(service.historique()).hasSize(3)
                .allSatisfy(e -> assertThat(e.statut()).isEqualTo(StatutAnalyse.ATTENDUE));
        assertThat(service.historique()).extracting(Echeance::dateEcheance)
                .containsExactly(Instant.parse("2026-11-10T03:00:00Z"),
                        Instant.parse("2026-11-09T03:00:00Z"),
                        Instant.parse("2026-11-08T03:00:00Z"));
    }

    @Test
    @DisplayName("Deux passages dans la meme minute n'ouvrent qu'une echeance")
    void idempotent() {
        magasin.derniereTerminee = UUID.randomUUID();
        regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");

        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
        assertThat(service.ouvrirEcheancesDues()).isZero();

        maintenant = Instant.parse("2026-11-08T03:00:59Z");
        assertThat(service.ouvrirEcheancesDues()).isZero();
        assertThat(service.historique()).hasSize(1);
    }

    @Test
    @DisplayName("Aucune simulation terminee a l'heure dite : l'echeance s'ouvre quand meme, SANS_CIBLE")
    void sansCible() {
        regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");

        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);

        Echeance echeance = service.historique().get(0);
        assertThat(echeance.statut()).isEqualTo(StatutAnalyse.SANS_CIBLE);
        assertThat(echeance.runId()).isNull();
        assertThat(echeance.detail()).contains("Aucune simulation terminee");
        // Rien a retirer : le script ne la voit pas dans ce qu'il doit analyser.
        assertThat(service.enAttente()).isEmpty();
        // Et elle n'est pas rouverte au passage suivant.
        maintenant = Instant.parse("2026-11-08T03:01:00Z");
        magasin.derniereTerminee = UUID.randomUUID();
        assertThat(service.ouvrirEcheancesDues()).isZero();
    }

    @Test
    @DisplayName("Echeance retiree sans rapport, delai depasse : SILENCE, et jamais un taux de 0 %")
    void silence() {
        UUID run = UUID.randomUUID();
        magasin.derniereTerminee = run;
        regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        service.ouvrirEcheancesDues();

        // Le script retire le jeu a 03h05.
        maintenant = Instant.parse("2026-11-08T03:05:00Z");
        service.retraitConstate(run, "Jeu retire au format CSV");
        assertThat(service.historique().get(0).statut()).isEqualTo(StatutAnalyse.RETIREE);
        assertThat(service.historique().get(0).dateRetrait()).isEqualTo(maintenant);

        // Avant le delai : toujours attendu, pas de silence.
        maintenant = Instant.parse("2026-11-08T04:00:00Z");
        assertThat(service.marquerSilences()).isZero();
        assertThat(service.historique().get(0).statut()).isEqualTo(StatutAnalyse.RETIREE);

        // Delai depasse : SILENCE, dit comme un echec d'echange.
        maintenant = Instant.parse("2026-11-08T04:06:00Z");
        assertThat(service.marquerSilences()).isEqualTo(1);
        Echeance echeance = service.historique().get(0);
        assertThat(echeance.statut()).isEqualTo(StatutAnalyse.SILENCE);
        assertThat(echeance.statut().echec()).isTrue();
        assertThat(echeance.detail()).contains("aucune mesure n'est possible");
        // Le retrait reste visible : une date nulle n'efface pas celle qui est inscrite.
        assertThat(echeance.dateRetrait()).isEqualTo(Instant.parse("2026-11-08T03:05:00Z"));
        // Et un deuxieme passage ne la recompte pas.
        assertThat(service.marquerSilences()).isZero();
    }

    @Test
    @DisplayName("Le cycle complet : ouverte, retiree, rapport recu")
    void cycleComplet() {
        UUID run = UUID.randomUUID();
        magasin.derniereTerminee = run;
        regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        service.ouvrirEcheancesDues();

        assertThat(service.enAttente()).singleElement().satisfies(a -> {
            assertThat(a.runId()).isEqualTo(run);
            assertThat(a.statut()).isEqualTo("ATTENDUE");
            assertThat(a.export()).isEqualTo("/api/runs/" + run + "/export");
            assertThat(a.echeance()).isEqualTo(Instant.parse("2026-11-08T04:00:00Z"));
        });

        maintenant = Instant.parse("2026-11-08T03:05:00Z");
        service.retraitConstate(run, "Jeu retire au format CSV");
        // Retiree : le script l'a prise, elle n'est plus a prendre.
        assertThat(service.enAttente()).isEmpty();

        maintenant = Instant.parse("2026-11-08T03:10:00Z");
        service.rapportConstate(run, "42 detection(s)");

        Echeance echeance = service.historique().get(0);
        assertThat(echeance.statut()).isEqualTo(StatutAnalyse.RAPPORT_RECU);
        assertThat(echeance.statut().echec()).isFalse();
        assertThat(echeance.dateRapport()).isEqualTo(maintenant);
        assertThat(echeance.dateRetrait()).isEqualTo(Instant.parse("2026-11-08T03:05:00Z"));
        // Plus aucun silence a constater : l'echeance est honoree.
        maintenant = Instant.parse("2026-11-09T00:00:00Z");
        assertThat(service.marquerSilences()).isZero();
    }

    @Test
    @DisplayName("Rapport refuse : RAPPORT_REFUSE, un echec d'echange et non un taux")
    void rapportRefuse() {
        UUID run = UUID.randomUUID();
        magasin.derniereTerminee = run;
        regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        service.ouvrirEcheancesDues();
        service.retraitConstate(run, "Jeu retire");

        service.refusConstate(run, "detections[0] : type inconnu 'FOO'");

        assertThat(service.historique().get(0).statut()).isEqualTo(StatutAnalyse.RAPPORT_REFUSE);
        assertThat(service.historique().get(0).statut().echec()).isTrue();
    }

    @Test
    @DisplayName("Planification desactivee : plus aucune echeance ne s'ouvre, l'historique reste")
    void desactivee() {
        magasin.derniereTerminee = UUID.randomUUID();
        Planification regle = regleA3h();
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        service.ouvrirEcheancesDues();

        assertThat(service.activer(regle.id(), false)).isTrue();

        maintenant = Instant.parse("2026-11-09T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isZero();
        assertThat(service.historique()).hasSize(1);

        // Reactivee : elle reprend, sans rouvrir le jour saute (le rattrapage est borne a 24 h).
        assertThat(service.activer(regle.id(), true)).isTrue();
        maintenant = Instant.parse("2026-11-10T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
        assertThat(service.historique()).extracting(Echeance::dateEcheance)
                .containsExactly(Instant.parse("2026-11-10T03:00:00Z"),
                        Instant.parse("2026-11-08T03:00:00Z"));
    }

    @Test
    @DisplayName("Jours choisis : rien ne s'ouvre un jour qui n'est pas retenu")
    void joursChoisis() {
        magasin.derniereTerminee = UUID.randomUUID();
        service.creer("Analyse du lundi", LocalTime.of(3, 0), "lundi, mercredi", null);

        // 2026-11-08 est un dimanche : rien.
        maintenant = Instant.parse("2026-11-08T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isZero();
        // Le lundi 9 : une.
        maintenant = Instant.parse("2026-11-09T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
        // Le mardi 10 : rien.
        maintenant = Instant.parse("2026-11-10T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isZero();
        // Le mercredi 11 : une.
        maintenant = Instant.parse("2026-11-11T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
    }

    @Test
    @DisplayName("Une application arretee longtemps ne rouvre pas tout le passe au redemarrage")
    void rattrapageBorne() {
        magasin.derniereTerminee = UUID.randomUUID();
        regleA3h();

        // Cinq jours plus tard : une seule echeance, celle de la journee ecoulee.
        maintenant = Instant.parse("2026-11-13T03:00:00Z");
        assertThat(service.ouvrirEcheancesDues()).isEqualTo(1);
        assertThat(service.historique()).singleElement()
                .extracting(Echeance::dateEcheance).isEqualTo(Instant.parse("2026-11-13T03:00:00Z"));
    }

    @Test
    @DisplayName("Saisie refusee : libelle vide, et jour inconnu avec les valeurs permises")
    void saisieRefusee() {
        assertThatThrownBy(() -> service.creer("  ", LocalTime.of(3, 0), JoursSemaine.TOUS, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Libelle obligatoire");
        assertThatThrownBy(() -> service.creer("Analyse", LocalTime.of(3, 0), "LUNDI,FUNDI", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FUNDI").hasMessageContaining("DIMANCHE");
        assertThatThrownBy(() -> service.creer("Analyse", null, JoursSemaine.TOUS, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Heure obligatoire");
    }

    @Test
    @DisplayName("Un rapport pousse hors planification ne fait echouer personne")
    void horsPlanification() {
        service.retraitConstate(UUID.randomUUID(), "Jeu retire sans echeance");
        service.rapportConstate(UUID.randomUUID(), "12 detection(s)");
        assertThat(service.historique()).isEmpty();
    }
}
