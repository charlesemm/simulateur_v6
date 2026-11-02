package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

public record CentreSante(UUID id, String code, String nom, TypeCentreSante type, String ville) {
}
