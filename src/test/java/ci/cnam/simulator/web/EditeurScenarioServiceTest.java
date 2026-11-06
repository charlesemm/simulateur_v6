package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.AuditService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EditeurScenarioServiceTest {

    private final ScenarioLoader loader = new ScenarioLoader();
    private final ScenarioService scenarios = mock(ScenarioService.class);
    private final AuditService audit = mock(AuditService.class);
    private final EditeurScenarioService editeur = new EditeurScenarioService(loader, scenarios, audit);

    private static FormulaireScenario formulaire(String nom) {
        return new FormulaireScenario(nom, 7L, 60.0, "2026-03-10T08:00:00", 5.0, 60.0, 10.0, 30.0, 3.0,
                List.of("0m", "10m", ""), List.of("10m", "20m", ""), List.of("normale", "x20", ""));
    }

    @Test
    @DisplayName("Le YAML genere est accepte tel quel par le chargeur, avec les valeurs saisies")
    void yamlGenereEstValide() {
        String yaml = editeur.genererYaml(formulaire("Mon scenario"));

        Scenario s = loader.lire(yaml);
        assertThat(s.nom()).isEqualTo("Mon scenario");
        assertThat(s.seed()).isEqualTo(7L);
        assertThat(s.accelerationTemps()).isEqualTo(60.0);
        assertThat(s.domaines().get("prestations").debit()).isEqualTo(5.0);
        assertThat(s.domaines().get("prestations").parcours()).containsEntry("examens", 60.0);
        assertThat(s.domaines().get("prestations").anomalies()).containsEntry("global", 3.0);
        assertThat(s.phases()).hasSize(2);
        assertThat(s.phases().get(1).multiplicateur()).isEqualTo(20.0);
        assertThat(editeur.valider(yaml).valide()).isTrue();
    }

    @Test
    @DisplayName("Un nom avec guillemets ou retour a la ligne ne peut pas injecter d'autres champs YAML")
    void nomNeutralise() {
        String yaml = editeur.genererYaml(formulaire("x\"\nseed: 999\nnom: \"y"));

        Scenario s = loader.lire(yaml);
        assertThat(s.seed()).isEqualTo(7L);
        assertThat(s.nom()).contains("seed: 999");
    }

    @Test
    @DisplayName("Une duree ou une charge mal formee est refusee avant de produire du YAML")
    void champsMalFormes() {
        FormulaireScenario mauvais = new FormulaireScenario("n", 1L, 60.0, null, 5.0, 60.0, 10.0, 30.0, 0.0,
                List.of("0m; rm"), List.of("10m"), List.of("normale"));
        assertThatThrownBy(() -> editeur.genererYaml(mauvais)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phase 1");
        FormulaireScenario date = new FormulaireScenario("n", 1L, 60.0, "demain", 5.0, 60.0, 10.0, 30.0, 0.0,
                List.of(), List.of(), List.of());
        assertThatThrownBy(() -> editeur.genererYaml(date)).hasMessageContaining("Debut simule");
        assertThatThrownBy(() -> editeur.genererYaml(formulaire(" "))).hasMessageContaining("nom");
    }

    @Test
    @DisplayName("La validation rend toutes les erreurs du chargeur, avec le chemin du champ")
    void validationRendLesErreurs() {
        var validation = editeur.valider("nom: x\nseed: abc\ndomaines:\n  prestations:\n    debit: beaucoup\n");

        assertThat(validation.valide()).isFalse();
        assertThat(validation.erreurs()).isNotEmpty();
        assertThat(String.join(" ", validation.erreurs())).contains("seed");
        assertThat(editeur.valider("").erreurs()).containsExactly("Le YAML est vide");
        assertThat(editeur.valider("x".repeat(EditeurScenarioService.TAILLE_YAML_MAX + 1)).erreurs())
                .anyMatch(e -> e.contains("volumineux"));
    }

    @Test
    @DisplayName("Enregistrer delegue au service de scenarios et inscrit l'action a l'audit")
    void enregistrer() {
        Scenario s = loader.lire(editeur.genererYaml(formulaire("Audit")));
        when(scenarios.enregistrer("yaml")).thenReturn(s);

        editeur.enregistrer("yaml", "alice");

        verify(audit).enregistrer("alice", "SCENARIO_ENREGISTRE", "Audit", null);
    }
}
