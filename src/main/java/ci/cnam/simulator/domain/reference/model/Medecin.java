package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

public record Medecin(UUID id, String code, String nom, String prenoms, String specialite, UUID centreSanteId) {
}
