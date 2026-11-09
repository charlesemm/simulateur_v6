package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

/** Officine. {@code departementCode} est un code de {@code TB_TV_LOCALISATION_DEPARTEMENTS}. */
public record Pharmacie(UUID id, String code, String nom, String departementCode) {
}
