package ci.cnam.simulator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Page de resultats. La taille est bornee par le serveur. */
@Schema(description = "Page de resultats")
public record PageResponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long total,
        int totalPages) {
}
