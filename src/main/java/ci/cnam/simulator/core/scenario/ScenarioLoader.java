package ci.cnam.simulator.core.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lit et valide un scenario YAML. Toutes les erreurs sont collectees puis levees ensemble
 * ({@link ScenarioInvalideException}), chacune avec le chemin du champ fautif.
 * Les champs des briques a venir (chaos) sont acceptes mais ignores, avec un avertissement.
 */
@Component
public class ScenarioLoader {

    public static final LocalDateTime DEBUT_SIMULE_PAR_DEFAUT = LocalDateTime.of(2026, 3, 10, 8, 0);

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static final Set<String> CHAMPS_RACINE = Set.of("nom", "seed", "accelerationTemps", "debutSimule",
            "domaines", "phases");
    private static final Set<String> CHAMPS_PHASE = Set.of("de", "a", "charge");
    private static final Pattern DEBIT = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:/\\s*s)?$");
    private static final Pattern POURCENTAGE = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*%?$");
    private static final Pattern DUREE = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h)$");
    private static final Pattern CHARGE = Pattern.compile("^x(\\d+(?:\\.\\d+)?)$");
    private static final Pattern ACCELERATION = Pattern.compile("^x?(\\d+(?:\\.\\d+)?)$");

    public Scenario lireFichier(Path fichier) {
        try {
            return lire(Files.readString(fichier, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScenarioInvalideException(List.of("fichier illisible " + fichier + " : " + e.getMessage()));
        }
    }

    public Scenario lire(String yaml) {
        JsonNode racine;
        try {
            racine = YAML.readTree(yaml);
        } catch (JsonProcessingException e) {
            throw new ScenarioInvalideException(List.of("YAML illisible : " + e.getOriginalMessage()));
        }
        if (racine == null || !racine.isObject()) {
            throw new ScenarioInvalideException(List.of("le scenario doit etre un objet YAML (nom, seed, domaines...)"));
        }
        List<String> erreurs = new ArrayList<>();
        champsInconnus(racine, CHAMPS_RACINE, "", erreurs);

        String nom = lireNom(racine, erreurs);
        Long seed = lireSeed(racine, erreurs);
        double acceleration = lireAcceleration(racine, erreurs);
        LocalDateTime debutSimule = lireDebutSimule(racine, erreurs);
        Map<String, ConfigDomaine> domaines = lireDomaines(racine, erreurs);
        List<Phase> phases = lirePhases(racine, erreurs);

        if (!erreurs.isEmpty()) {
            throw new ScenarioInvalideException(erreurs);
        }
        return new Scenario(nom, seed, acceleration, debutSimule, domaines, phases, yaml);
    }

    private static String lireNom(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("nom");
        if (absent(n)) {
            erreurs.add("nom : champ manquant");
            return null;
        }
        if (!n.isTextual() || n.asText().isBlank()) {
            erreurs.add("nom : doit etre un texte non vide");
            return null;
        }
        return n.asText().strip();
    }

    private static Long lireSeed(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("seed");
        if (absent(n)) {
            erreurs.add("seed : champ manquant");
            return null;
        }
        if (!n.isIntegralNumber() || !n.canConvertToLong()) {
            erreurs.add("seed : doit etre un entier (valeur lue : " + n.asText() + ")");
            return null;
        }
        return n.asLong();
    }

    private static double lireAcceleration(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("accelerationTemps");
        if (absent(n)) {
            return 1.0;
        }
        Double valeur = null;
        if (n.isNumber()) {
            valeur = n.asDouble();
        } else if (n.isTextual()) {
            Matcher m = ACCELERATION.matcher(n.asText().strip().toLowerCase(Locale.ROOT));
            if (m.matches()) {
                valeur = Double.parseDouble(m.group(1));
            }
        }
        if (valeur == null) {
            erreurs.add("accelerationTemps : nombre attendu, par exemple 60 ou x100 (valeur lue : " + n.asText() + ")");
            return 1.0;
        }
        if (valeur <= 0) {
            erreurs.add("accelerationTemps : doit etre strictement positif (valeur lue : " + n.asText() + ")");
            return 1.0;
        }
        return valeur;
    }

    private static LocalDateTime lireDebutSimule(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("debutSimule");
        if (absent(n)) {
            return DEBUT_SIMULE_PAR_DEFAUT;
        }
        try {
            return LocalDateTime.parse(n.asText().strip());
        } catch (DateTimeParseException e) {
            erreurs.add("debutSimule : date-heure ISO attendue, par exemple 2026-03-10T08:00:00 (valeur lue : "
                    + n.asText() + ")");
            return DEBUT_SIMULE_PAR_DEFAUT;
        }
    }

    private static Map<String, ConfigDomaine> lireDomaines(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("domaines");
        Map<String, ConfigDomaine> resultat = new LinkedHashMap<>();
        if (absent(n)) {
            erreurs.add("domaines : champ manquant");
            return resultat;
        }
        if (!n.isObject() || n.isEmpty()) {
            erreurs.add("domaines : au moins un domaine attendu (ex. prestations)");
            return resultat;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entree = it.next();
            ConfigDomaine config = lireDomaine("domaines." + entree.getKey(), entree.getValue(), erreurs);
            if (config != null) {
                resultat.put(entree.getKey(), config);
            }
        }
        return resultat;
    }

    private static ConfigDomaine lireDomaine(String chemin, JsonNode n, List<String> erreurs) {
        if (!n.isObject()) {
            erreurs.add(chemin + " : objet attendu (debit, parcours)");
            return null;
        }
        champsInconnus(n, Set.of("debit", "parcours", "anomalies"), chemin + ".", erreurs);

        int avant = erreurs.size();
        double debit = lireDebit(chemin + ".debit", n.get("debit"), erreurs);
        Map<String, Double> parcours = lireParcours(chemin + ".parcours", n.get("parcours"), erreurs);
        Map<String, Double> anomalies = lireAnomalies(chemin + ".anomalies", n.get("anomalies"), erreurs);
        return erreurs.size() > avant ? null : new ConfigDomaine(debit, parcours, anomalies);
    }

    private static double lireDebit(String chemin, JsonNode n, List<String> erreurs) {
        if (absent(n)) {
            erreurs.add(chemin + " : champ manquant");
            return 0;
        }
        Double valeur = null;
        if (n.isNumber()) {
            valeur = n.asDouble();
        } else if (n.isTextual()) {
            Matcher m = DEBIT.matcher(n.asText().strip());
            if (m.matches()) {
                valeur = Double.parseDouble(m.group(1));
            }
        }
        if (valeur == null) {
            erreurs.add(chemin + " : nombre par seconde attendu, par exemple 100/s (valeur lue : " + n.asText() + ")");
            return 0;
        }
        if (valeur <= 0) {
            erreurs.add(chemin + " : doit etre strictement positif (valeur lue : " + n.asText() + ")");
        }
        return valeur;
    }

    private static Map<String, Double> lireParcours(String chemin, JsonNode n, List<String> erreurs) {
        Map<String, Double> resultat = new LinkedHashMap<>();
        if (absent(n)) {
            return resultat;
        }
        if (!n.isObject()) {
            erreurs.add(chemin + " : objet attendu (ex. examens: 60%)");
            return resultat;
        }
        double somme = 0;
        boolean valide = true;
        for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entree = it.next();
            String cheminCle = chemin + "." + entree.getKey();
            Double taux = lirePourcentage(entree.getValue());
            if (taux == null) {
                erreurs.add(cheminCle + " : pourcentage attendu, par exemple 60% (valeur lue : "
                        + entree.getValue().asText() + ")");
                valide = false;
            } else if (taux < 0 || taux > 100) {
                erreurs.add(cheminCle + " : taux hors de 0 a 100 (valeur lue : " + entree.getValue().asText() + ")");
                valide = false;
            } else {
                resultat.put(entree.getKey(), taux);
                somme += taux;
            }
        }
        if (valide && !resultat.isEmpty() && Math.abs(somme - 100.0) > 0.01) {
            erreurs.add(chemin + " : les taux doivent totaliser 100 % (total lu : " + formater(somme) + " %)");
        }
        return resultat;
    }

    /** Taux d'anomalies par nom : chacun de 0 a 100 %. Les noms sont verifies par le domaine, pas ici. */
    private static Map<String, Double> lireAnomalies(String chemin, JsonNode n, List<String> erreurs) {
        Map<String, Double> resultat = new LinkedHashMap<>();
        if (absent(n)) {
            return resultat;
        }
        if (!n.isObject()) {
            erreurs.add(chemin + " : objet attendu (ex. champVide: 3%)");
            return resultat;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entree = it.next();
            String cheminCle = chemin + "." + entree.getKey();
            Double taux = lirePourcentage(entree.getValue());
            if (taux == null) {
                erreurs.add(cheminCle + " : pourcentage attendu, par exemple 3% (valeur lue : "
                        + entree.getValue().asText() + ")");
            } else if (taux < 0 || taux > 100) {
                erreurs.add(cheminCle + " : taux hors de 0 a 100 (valeur lue : " + entree.getValue().asText() + ")");
            } else {
                resultat.put(entree.getKey(), taux);
            }
        }
        return resultat;
    }

    private static Double lirePourcentage(JsonNode n) {
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isTextual()) {
            Matcher m = POURCENTAGE.matcher(n.asText().strip());
            if (m.matches()) {
                return Double.parseDouble(m.group(1));
            }
        }
        return null;
    }

    private static List<Phase> lirePhases(JsonNode racine, List<String> erreurs) {
        JsonNode n = racine.get("phases");
        List<Phase> phases = new ArrayList<>();
        if (absent(n)) {
            return phases;
        }
        if (!n.isArray()) {
            erreurs.add("phases : liste attendue");
            return phases;
        }
        for (int i = 0; i < n.size(); i++) {
            JsonNode p = n.get(i);
            String chemin = "phases[" + i + "]";
            if (!p.isObject()) {
                erreurs.add(chemin + " : objet attendu ({ de, a, charge })");
                continue;
            }
            if (p.has("chaos")) {
                LOG.warn("{} : phase de chaos ignoree (brique chaos non livree)", chemin);
                continue;
            }
            champsInconnus(p, CHAMPS_PHASE, chemin + ".", erreurs);
            Phase phase = lirePhase(chemin, p, erreurs);
            if (phase != null) {
                phases.add(phase);
            }
        }
        phases.sort(Comparator.comparing(Phase::de));
        for (int i = 1; i < phases.size(); i++) {
            if (phases.get(i).de().compareTo(phases.get(i - 1).a()) < 0) {
                erreurs.add("phases : chevauchement entre " + phases.get(i - 1).charge() + " (jusqu'a "
                        + phases.get(i - 1).a() + ") et la phase suivante (des " + phases.get(i).de() + ")");
            }
        }
        return phases;
    }

    private static Phase lirePhase(String chemin, JsonNode p, List<String> erreurs) {
        int avant = erreurs.size();
        Duration de = lireDuree(chemin + ".de", p.get("de"), erreurs);
        Duration a = lireDuree(chemin + ".a", p.get("a"), erreurs);
        String charge = "normale";
        double multiplicateur = 1.0;
        JsonNode c = p.get("charge");
        if (!absent(c)) {
            charge = c.asText().strip().toLowerCase(Locale.ROOT);
            if (!charge.equals("normale")) {
                Matcher m = CHARGE.matcher(charge);
                if (!m.matches() || Double.parseDouble(m.group(1)) <= 0) {
                    erreurs.add(chemin + ".charge : 'normale' ou 'xN' avec N > 0 attendu, par exemple x20 (valeur lue : "
                            + c.asText() + ")");
                } else {
                    multiplicateur = Double.parseDouble(m.group(1));
                }
            }
        }
        if (de != null && a != null && a.compareTo(de) <= 0 && erreurs.size() == avant) {
            erreurs.add(chemin + " : 'a' (" + p.get("a").asText() + ") doit etre apres 'de' (" + p.get("de").asText() + ")");
        }
        return erreurs.size() > avant ? null : new Phase(de, a, multiplicateur, charge);
    }

    private static Duration lireDuree(String chemin, JsonNode n, List<String> erreurs) {
        if (absent(n)) {
            erreurs.add(chemin + " : champ manquant");
            return null;
        }
        Matcher m = DUREE.matcher(n.asText().strip().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            erreurs.add(chemin + " : duree attendue, par exemple 30s, 5m ou 1h (valeur lue : " + n.asText() + ")");
            return null;
        }
        double quantite = Double.parseDouble(m.group(1));
        if (quantite < 0) {
            erreurs.add(chemin + " : duree negative interdite (valeur lue : " + n.asText() + ")");
            return null;
        }
        double secondes = switch (m.group(2)) {
            case "ms" -> quantite / 1000.0;
            case "s" -> quantite;
            case "m" -> quantite * 60;
            default -> quantite * 3600;
        };
        return Duration.ofNanos(Math.round(secondes * 1_000_000_000L));
    }

    private static void champsInconnus(JsonNode n, Set<String> connus, String prefixe, List<String> erreurs) {
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            String champ = it.next();
            if (!connus.contains(champ)) {
                erreurs.add(prefixe + champ + " : champ inconnu (champs permis : " + new java.util.TreeSet<>(connus) + ")");
            }
        }
    }

    private static boolean absent(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode();
    }

    private static String formater(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
