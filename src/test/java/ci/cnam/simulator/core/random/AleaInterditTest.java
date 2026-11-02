package ci.cnam.simulator.core.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Garde-fou : Math.random() et new Random() sont interdits dans le code de production. */
class AleaInterditTest {

    @Test
    void aucunAleaHorsSeededRandom() throws IOException {
        List<String> fautifs;
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/java"))) {
            fautifs = fichiers
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("SeededRandom.java"))
                    .filter(f -> !f.getFileName().toString().equals("package-info.java"))
                    .filter(f -> {
                        try {
                            String code = Files.readString(f);
                            return code.contains("Math.random()") || code.contains("new Random(")
                                    || code.contains("ThreadLocalRandom");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(fautifs).isEmpty();
    }
}
