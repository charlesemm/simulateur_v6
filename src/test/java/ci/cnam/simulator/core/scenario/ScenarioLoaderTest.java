package ci.cnam.simulator.core.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    private final ScenarioLoader loader = new ScenarioLoader();

    private static final String VALIDE = """
            nom: "Test"
            seed: 42
            accelerationTemps: 60
            domaines:
              prestations:
                debit: 100/s
                parcours:
                  examens: 60%
                  hospitalisation: 10%
                  prescriptionDirecte: 30%
            phases:
              - { de: 0m, a: 5m, charge: normale }
              - { de: 5m, a: 8m, charge: x20 }
            """;

    private void refuse(String yaml, String... fragments) {
        assertThatThrownBy(() -> loader.lire(yaml))
                .isInstanceOf(ScenarioInvalideException.class)
                .satisfies(e -> {
                    for (String f : fragments) {
                        assertThat(e.getMessage()).contains(f);
                    }
                });
    }

    @Test
    void litUnScenarioValide() {
        Scenario s = loader.lire(VALIDE);

        assertThat(s.nom()).isEqualTo("Test");
        assertThat(s.seed()).isEqualTo(42);
        assertThat(s.accelerationTemps()).isEqualTo(60);
        assertThat(s.domaines().get("prestations").debit()).isEqualTo(100);
        assertThat(s.domaines().get("prestations").parcours())
                .containsEntry("examens", 60.0).containsEntry("hospitalisation", 10.0)
                .containsEntry("prescriptionDirecte", 30.0);
        assertThat(s.phases()).hasSize(2);
        assertThat(s.phases().get(1).multiplicateur()).isEqualTo(20);
        assertThat(s.phases().get(1).de()).isEqualTo(Duration.ofMinutes(5));
        assertThat(s.debutSimule()).isEqualTo(ScenarioLoader.DEBUT_SIMULE_PAR_DEFAUT);
        assertThat(s.contenuYaml()).isEqualTo(VALIDE);
    }

    @Test
    void leScenarioDeReferenceDuDepotEstValide() {
        Scenario s = loader.lireFichier(Path.of("scenarios/prestations/parcours-normal.yaml"));

        assertThat(s.domaines()).containsKey("prestations");
        assertThat(s.domaines().get("prestations").debit()).isEqualTo(5);
    }

    @Test
    void accelerationParDefautEtFormeXN() {
        assertThat(loader.lire("nom: a\nseed: 1\ndomaines: {p: {debit: 3}}").accelerationTemps()).isEqualTo(1);
        assertThat(loader.lire("nom: a\nseed: 1\naccelerationTemps: x100\ndomaines: {p: {debit: 3}}")
                .accelerationTemps()).isEqualTo(100);
    }

    @Test
    void refuseUnChampManquantEnNommantLeChemin() {
        refuse("seed: 1\ndomaines: {prestations: {debit: 5}}", "nom : champ manquant");
        refuse("nom: a\ndomaines: {prestations: {debit: 5}}", "seed : champ manquant");
        refuse("nom: a\nseed: 1", "domaines : champ manquant");
        refuse("nom: a\nseed: 1\ndomaines:\n  prestations:\n    parcours: {examens: 100%}",
                "domaines.prestations.debit : champ manquant");
    }

    @Test
    void refuseUnTauxHorsDeZeroACent() {
        refuse("""
                nom: a
                seed: 1
                domaines:
                  prestations:
                    debit: 5/s
                    parcours: {examens: 150%, hospitalisation: -50%, prescriptionDirecte: 0%}
                """,
                "domaines.prestations.parcours.examens : taux hors de 0 a 100",
                "domaines.prestations.parcours.hospitalisation : taux hors de 0 a 100");
    }

    @Test
    void refuseDesTauxQuineTotalisentPasCent() {
        refuse("nom: a\nseed: 1\ndomaines:\n  prestations:\n    debit: 5\n"
                + "    parcours: {examens: 60%, hospitalisation: 10%, prescriptionDirecte: 20%}",
                "les taux doivent totaliser 100 %", "total lu : 90 %");
    }

    @Test
    void refuseUneDureeNegativeOuInversee() {
        refuse(VALIDE.replace("{ de: 0m, a: 5m, charge: normale }", "{ de: -5m, a: 5m, charge: normale }"),
                "phases[0].de : duree negative interdite");
        refuse(VALIDE.replace("{ de: 5m, a: 8m, charge: x20 }", "{ de: 8m, a: 5m, charge: x20 }"),
                "phases[1] : 'a' (5m) doit etre apres 'de' (8m)");
    }

    @Test
    void refuseDesPhasesQuiSeChevauchent() {
        refuse(VALIDE.replace("de: 5m, a: 8m", "de: 4m, a: 8m"), "chevauchement");
    }

    @Test
    void refuseDesValeursIllisiblesEtUnDebitNul() {
        refuse(VALIDE.replace("100/s", "beaucoup"), "domaines.prestations.debit : nombre par seconde attendu");
        refuse(VALIDE.replace("100/s", "0/s"), "domaines.prestations.debit : doit etre strictement positif");
        refuse(VALIDE.replace("x20", "enorme"), "phases[1].charge");
        refuse(VALIDE.replace("seed: 42", "seed: abc"), "seed : doit etre un entier");
        refuse(VALIDE.replace("accelerationTemps: 60", "accelerationTemps: 0"), "accelerationTemps : doit etre strictement positif");
    }

    @Test
    void refuseUnChampInconnuEtDesErreursMultiplesEnUneFois() {
        refuse("nom: a\nseed: 1\ntypo: 3\ndomaines: {prestations: {debit: 5}}", "typo : champ inconnu");
        refuse("domaines: {prestations: {parcours: {examens: 200%}}}",
                "nom : champ manquant", "seed : champ manquant", "debit : champ manquant", "taux hors de 0 a 100");
    }

    @Test
    void refuseUnYamlIllisibleOuVide() {
        refuse("nom: [pas ferme", "YAML illisible");
        refuse("", "objet YAML");
        refuse("- une\n- liste", "objet YAML");
    }

    @Test
    void ignoreAvecAvertissementAnomaliesEtPhaseDeChaos() {
        Scenario s = loader.lire("""
                nom: a
                seed: 1
                domaines:
                  prestations:
                    debit: 5/s
                    anomalies: { champVide: 3% }
                phases:
                  - { de: 0m, a: 5m, charge: normale }
                  - { de: 8m, chaos: coupure-base, duree: 30s }
                """);

        assertThat(s.phases()).hasSize(1);
    }
}
