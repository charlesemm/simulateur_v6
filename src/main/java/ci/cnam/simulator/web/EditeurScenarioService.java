package ci.cnam.simulator.web;

import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import ci.cnam.simulator.core.scenario.ScenarioInconnuException;
import ci.cnam.simulator.core.scenario.ScenarioInvalideException;
import ci.cnam.simulator.core.scenario.ScenarioLoader;
import ci.cnam.simulator.core.scenario.ScenarioService;
import ci.cnam.simulator.security.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Editeur de scenarios : formulaire vers YAML, validation, enregistrement, export. Le chargeur reste l'arbitre. */
@Service
public class EditeurScenarioService {

    /** Taille maximale d'un YAML importe ou enregistre depuis l'interface. */
    public static final int TAILLE_YAML_MAX = 100_000;

    private static final Pattern DUREE = Pattern.compile("\\d+(\\.\\d+)?(ms|s|m|h)");
    private static final Pattern CHARGE = Pattern.compile("normale|x\\d+(\\.\\d+)?");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?");

    /** Resultat d'une validation : {@code erreurs} vide = valide. */
    public record Validation(List<String> erreurs, String resume) {
        public boolean valide() {
            return erreurs.isEmpty();
        }
    }

    private final ScenarioLoader loader;
    private final ScenarioService scenarios;
    private final AuditService audit;

    public EditeurScenarioService(ScenarioLoader loader, ScenarioService scenarios, AuditService audit) {
        this.loader = loader;
        this.scenarios = scenarios;
        this.audit = audit;
    }

    /** Produit le YAML ; refuse tout champ qui ne serait pas un nombre, une duree ou une charge bien formes. */
    public String genererYaml(FormulaireScenario f) {
        if (f.nom() == null || f.nom().isBlank()) {
            throw new IllegalArgumentException("Le nom du scenario est obligatoire");
        }
        StringBuilder y = new StringBuilder();
        y.append("nom: ").append(texte(f.nom())).append('\n');
        if (f.seed() != null) {
            y.append("seed: ").append(f.seed()).append('\n');
        }
        if (f.accelerationTemps() != null) {
            y.append("accelerationTemps: ").append(nombre(f.accelerationTemps())).append('\n');
        }
        if (f.debutSimule() != null && !f.debutSimule().isBlank()) {
            if (!DATE.matcher(f.debutSimule().strip()).matches()) {
                throw new IllegalArgumentException("Debut simule : date-heure ISO attendue (2026-03-10T08:00:00)");
            }
            y.append("debutSimule: ").append(f.debutSimule().strip()).append('\n');
        }
        y.append("domaines:\n  prestations:\n");
        if (f.debit() != null) {
            y.append("    debit: ").append(nombre(f.debit())).append("/s\n");
        }
        if (f.examens() != null || f.hospitalisation() != null || f.direct() != null) {
            y.append("    parcours:\n");
            ligne(y, "examens", f.examens());
            ligne(y, "hospitalisation", f.hospitalisation());
            ligne(y, "prescriptionDirecte", f.direct());
        }
        if (f.anomaliesGlobal() != null && f.anomaliesGlobal() > 0) {
            y.append("    anomalies:\n      global: ").append(nombre(f.anomaliesGlobal())).append("%\n");
        }
        StringBuilder phases = new StringBuilder();
        for (int i = 0; i < f.phaseDe().size(); i++) {
            String de = valeur(f.phaseDe(), i);
            String a = valeur(f.phaseA(), i);
            String charge = valeur(f.phaseCharge(), i);
            if (de.isEmpty() && a.isEmpty() && charge.isEmpty()) {
                continue;   // ligne vide du formulaire
            }
            charge = charge.isEmpty() ? "normale" : charge.toLowerCase(java.util.Locale.ROOT);
            if (!DUREE.matcher(de).matches() || !DUREE.matcher(a).matches()) {
                throw new IllegalArgumentException("Phase " + (i + 1) + " : duree attendue, par exemple 0m, 30s, 2h");
            }
            if (!CHARGE.matcher(charge).matches()) {
                throw new IllegalArgumentException("Phase " + (i + 1) + " : charge 'normale' ou 'xN' attendue");
            }
            phases.append("  - { de: ").append(de).append(", a: ").append(a).append(", charge: ").append(charge)
                    .append(" }\n");
        }
        if (!phases.isEmpty()) {
            y.append("phases:\n").append(phases);
        }
        return y.toString();
    }

    public Validation valider(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return new Validation(List.of("Le YAML est vide"), null);
        }
        if (yaml.length() > TAILLE_YAML_MAX) {
            return new Validation(List.of("YAML trop volumineux (" + TAILLE_YAML_MAX + " caracteres au plus)"), null);
        }
        try {
            Scenario s = loader.lire(yaml);
            return new Validation(List.of(), "Scénario valide : " + s.nom() + ", graine " + s.seed() + ", "
                    + s.domaines().size() + " domaine(s), " + s.phases().size() + " phase(s)");
        } catch (ScenarioInvalideException e) {
            return new Validation(e.erreurs(), null);
        }
    }

    /** Valide puis enregistre en base (un scenario de meme nom est remplace). */
    public Scenario enregistrer(String yaml, String acteur) {
        if (yaml != null && yaml.length() > TAILLE_YAML_MAX) {
            throw new IllegalArgumentException("YAML trop volumineux");
        }
        Scenario s = scenarios.enregistrer(yaml);
        audit.enregistrer(acteur, "SCENARIO_ENREGISTRE", s.nom(), null);
        return s;
    }

    public List<ScenarioEnregistre> lister() {
        return scenarios.lister();
    }

    public ScenarioEnregistre trouver(String nom) {
        return scenarios.lister().stream().filter(s -> s.nom().equals(nom)).findFirst()
                .orElseThrow(() -> new ScenarioInconnuException(nom));
    }

    private static void ligne(StringBuilder y, String cle, Double v) {
        if (v != null) {
            y.append("      ").append(cle).append(": ").append(nombre(v)).append("%\n");
        }
    }

    private static String valeur(List<String> liste, int i) {
        return i < liste.size() && liste.get(i) != null ? liste.get(i).strip() : "";
    }

    private static String nombre(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("Nombre invalide");
        }
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** Texte entre guillemets, sans retour a la ligne : un nom ne peut pas "sortir" de sa ligne YAML. */
    private static String texte(String brut) {
        String propre = brut.strip().replaceAll("[\\p{Cntrl}]", " ").replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + propre + "\"";
    }
}
