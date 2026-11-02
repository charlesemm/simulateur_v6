package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

public record Pharmacie(UUID id, String code, String nom, String ville) {
}
