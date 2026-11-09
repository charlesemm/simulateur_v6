package ci.cnam.simulator.domain.reference.model;

import java.util.UUID;

/**
 * Etablissement sanitaire. {@code departementCode} est un code de
 * {@code TB_TV_LOCALISATION_DEPARTEMENTS}, pas un nom de ville : le libelle vit dans la table de
 * valeurs, qui en est la seule autorite.
 */
public record CentreSante(UUID id, String code, String nom, TypeCentreSante type, String departementCode) {
}
