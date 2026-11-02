package ci.cnam.simulator.domain.reference.model;

import java.time.LocalDate;
import java.util.UUID;

public record AyantDroit(
        UUID id,
        UUID assureId,
        String nom,
        String prenoms,
        LocalDate dateNaissance,
        Genre genre,
        LienParente lienParente) {
}
