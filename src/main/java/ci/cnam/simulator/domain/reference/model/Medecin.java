package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

/**
 * Professionnel de sante. {@code specialiteCode} est un code de
 * {@code TB_REF_SPECIALITES_MEDICALES} : la specialite n'est plus du texte libre, son libelle vit
 * dans le catalogue.
 */
public record Medecin(UUID id, String code, String nom, String prenoms, String specialiteCode, UUID centreSanteId) {
}
