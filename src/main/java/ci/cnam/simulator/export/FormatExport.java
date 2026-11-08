package ci.cnam.simulator.export;

import java.util.Locale;
import java.util.Optional;

/** Formats offerts au telechargement. {@code CORRIGE} n'est pas un format de jeu : c'est la verite terrain. */
public enum FormatExport {

    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("zip", "application/zip"),
    JSON("json", "application/json"),
    CORRIGE("csv", "text/csv");

    private final String extension;
    private final String typeMime;

    FormatExport(String extension, String typeMime) {
        this.extension = extension;
        this.typeMime = typeMime;
    }

    /** Extension du fichier servi : le format csv sort en archive zip (un .csv par table). */
    public String extension() {
        return extension;
    }

    public String typeMime() {
        return typeMime;
    }

    /**
     * Nom du fichier propose au telechargement : la simulation est reconnaissable, et le corrige
     * se distingue du jeu au premier coup d oeil.
     */
    public String nomFichier(java.util.UUID runId) {
        String court = runId.toString().substring(0, 8);
        return (this == CORRIGE ? "corrige-" : "simulation-") + court + "." + extension;
    }

    /** Format demande dans l URL ({@code export.csv}), hors corrige. */
    public static Optional<FormatExport> duJeu(String nom) {
        return switch (nom == null ? "" : nom.toLowerCase(Locale.ROOT)) {
            case "xlsx" -> Optional.of(XLSX);
            case "csv" -> Optional.of(CSV);
            case "json" -> Optional.of(JSON);
            default -> Optional.empty();
        };
    }
}
