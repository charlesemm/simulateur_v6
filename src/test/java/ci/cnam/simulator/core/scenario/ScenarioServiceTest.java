package ci.cnam.simulator.core.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ci.cnam.simulator.SimulateurProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lecture du dossier des scenarios. En production ce dossier est monte depuis l'hote : ses droits echappent a
 * l'application, qui doit donc rester debout quoi qu'elle y trouve.
 */
class ScenarioServiceTest {

    private static final String YAML = """
            nom: "Scenario lisible"
            seed: 42
            accelerationTemps: 60
            domaines:
              prestations:
                debit: 5/s
                parcours:
                  examens: 60%
                  hospitalisation: 10%
                  prescriptionDirecte: 30%
            phases:
              - { de: 0m, a: 10m, charge: normale }
            """;

    /** Store vide : seuls les fichiers nous interessent ici. */
    private static final ScenarioStore STORE_VIDE = new ScenarioStore() {
        @Override
        public void enregistrer(Scenario scenario) {
            // sans objet pour ce test
        }

        @Override
        public List<ScenarioEnregistre> lister() {
            return List.of();
        }

        @Override
        public Optional<ScenarioEnregistre> trouverParNom(String nom) {
            return Optional.empty();
        }
    };

    private ScenarioService service(Path dossier) {
        SimulateurProperties proprietes = new SimulateurProperties(0L, dossier.toString(), null, null, null, null);
        return new ScenarioService(new ScenarioLoader(), STORE_VIDE, proprietes);
    }

    @Test
    @DisplayName("les scenarios des sous-dossiers sont listes")
    void listeLesFichiers(@TempDir Path dossier) throws IOException {
        Files.createDirectory(dossier.resolve("prestations"));
        Files.writeString(dossier.resolve("prestations/parcours-normal.yaml"), YAML);

        assertThat(service(dossier).lister())
                .extracting(ScenarioEnregistre::nom)
                .contains("Scenario lisible", "parcours-normal");
    }

    @Test
    @DisplayName("un dossier vide ne rend aucun scenario")
    void dossierVide(@TempDir Path dossier) {
        assertThat(service(dossier).lister()).isEmpty();
    }

    /**
     * Panne constatee en production le 07/11/2026 : le dossier monte depuis l'hote appartenait a l'administrateur
     * du serveur sans droit de lecture pour autrui, alors que le conteneur tourne sous l'uid 10001.
     * {@code Files.walk} levait une {@code AccessDeniedException} et TOUT l'ecran de lancement repondait 500.
     * Un sous-dossier interdit doit maintenant etre ignore, sans empecher les autres d'etre lus.
     */
    @Test
    @DisplayName("un sous-dossier interdit est ignore, les autres scenarios restent listes")
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "droits POSIX ; la panne visee est propre au serveur Linux")
    void sousDossierInterdit(@TempDir Path dossier) throws IOException {
        Files.createDirectory(dossier.resolve("lisible"));
        Files.writeString(dossier.resolve("lisible/bon.yaml"), YAML);
        Path interdit = Files.createDirectory(dossier.resolve("interdit"));
        Files.writeString(interdit.resolve("cache.yaml"), YAML);
        Files.setPosixFilePermissions(interdit, PosixFilePermissions.fromString("---------"));

        ScenarioService service = service(dossier);
        try {
            assertThatCode(service::lister).doesNotThrowAnyException();
            assertThat(service.lister())
                    .extracting(ScenarioEnregistre::nom)
                    .contains("bon")
                    .doesNotContain("cache");
        } finally {
            // sans quoi @TempDir ne peut pas faire le menage
            Files.setPosixFilePermissions(interdit, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
