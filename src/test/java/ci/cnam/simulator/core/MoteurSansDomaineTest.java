package ci.cnam.simulator.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Garde-fou d'architecture : le moteur (core/) ne connait aucun domaine, hormis l'interface commune FlowGenerator. */
class MoteurSansDomaineTest {

    @Test
    void coreNImporteAucunDomaine() throws IOException {
        List<String> fautifs;
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/java/ci/cnam/simulator/core"))) {
            fautifs = fichiers.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> lignes(f).stream()
                            .filter(l -> l.startsWith("import ci.cnam.simulator.domain."))
                            .filter(l -> !l.equals("import ci.cnam.simulator.domain.FlowGenerator;"))
                            .map(l -> f.getFileName() + " : " + l))
                    .toList();
        }
        assertThat(fautifs).isEmpty();
    }

    private static List<String> lignes(Path f) {
        try {
            return Files.readAllLines(f);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
