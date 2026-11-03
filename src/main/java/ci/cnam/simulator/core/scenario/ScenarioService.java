package ci.cnam.simulator.core.scenario;

import ci.cnam.simulator.SimulateurProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Catalogue des scenarios : ceux enregistres en base et ceux livres dans le dossier {@code scenarios/}.
 * Un scenario rendu par ce service est toujours passe par {@link ScenarioLoader} (donc valide).
 */
@Service
public class ScenarioService {

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioService.class);

    private final ScenarioLoader loader;
    private final ScenarioStore store;
    private final Path dossier;

    public ScenarioService(ScenarioLoader loader, ScenarioStore store, SimulateurProperties proprietes) {
        this.loader = loader;
        this.store = store;
        this.dossier = Path.of(proprietes.scenariosDir());
    }

    /** Valide le YAML puis l'enregistre en base (remplacement si le nom existe deja). */
    public Scenario enregistrer(String yaml) {
        Scenario scenario = loader.lire(yaml);
        store.enregistrer(scenario);
        return scenario;
    }

    /** Base d'abord, puis fichiers : un scenario enregistre masque le fichier de meme nom. */
    public List<ScenarioEnregistre> lister() {
        Map<String, ScenarioEnregistre> parNom = new LinkedHashMap<>();
        store.lister().forEach(s -> parNom.put(s.nom(), s));
        fichiers().forEach(s -> parNom.putIfAbsent(s.nom(), s));
        return parNom.values().stream().sorted(Comparator.comparing(ScenarioEnregistre::nom)).toList();
    }

    /** Charge et valide le scenario portant ce nom (nom du YAML, ou nom du fichier sans extension). */
    public Scenario charger(String nom) {
        Optional<ScenarioEnregistre> enregistre = store.trouverParNom(nom);
        if (enregistre.isPresent()) {
            return loader.lire(enregistre.get().contenuYaml());
        }
        return fichiers().stream()
                .filter(s -> s.nom().equals(nom))
                .findFirst()
                .map(s -> loader.lire(s.contenuYaml()))
                .orElseThrow(() -> new ScenarioInconnuException(nom));
    }

    /** Scenarios du dossier, indexes sous leur nom YAML ET sous le nom du fichier. Un fichier illisible est ignore. */
    private List<ScenarioEnregistre> fichiers() {
        if (!Files.isDirectory(dossier)) {
            return List.of();
        }
        List<ScenarioEnregistre> resultat = new ArrayList<>();
        try (Stream<Path> chemins = Files.walk(dossier)) {
            for (Path fichier : chemins.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".yaml") || f.toString().endsWith(".yml"))
                    .sorted()
                    .toList()) {
                lireFichier(fichier, resultat);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier des scenarios illisible : " + dossier, e);
        }
        return resultat;
    }

    private void lireFichier(Path fichier, List<ScenarioEnregistre> resultat) {
        String contenu;
        try {
            contenu = Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Scenario illisible, ignore : {} ({})", fichier, e.getMessage());
            return;
        }
        try {
            Scenario scenario = loader.lire(contenu);
            resultat.add(new ScenarioEnregistre(scenario.nom(), contenu, ScenarioEnregistre.Source.FICHIER,
                    null, null));
            String nomFichier = fichier.getFileName().toString().replaceFirst("[.](yaml|yml)$", "");
            if (!nomFichier.equals(scenario.nom())) {
                resultat.add(new ScenarioEnregistre(nomFichier, contenu, ScenarioEnregistre.Source.FICHIER,
                        null, null));
            }
        } catch (ScenarioInvalideException e) {
            LOG.warn("Scenario invalide, ignore : {} ({})", fichier, e.getMessage());
        }
    }
}
