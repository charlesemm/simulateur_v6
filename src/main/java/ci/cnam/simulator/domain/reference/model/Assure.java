package ci.cnam.simulator.domain.reference.model;

import java.time.LocalDate;
import java.util.UUID;

public record Assure(
        UUID id,
        String numeroAssure,
        String nom,
        String prenoms,
        LocalDate dateNaissance,
        Genre genre,
        StatutCotisation statutCotisation,
        LocalDate dateDebutDroits,
        LocalDate dateFinDroits) {
}
