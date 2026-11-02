package ci.cnam.simulator.domain.reference.catalog;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.catalog.Acte.CategorieActe;
import ci.cnam.simulator.domain.reference.catalog.Examen.TypeExamen;

/** Tirages dans les catalogues (le tarif vient de l'element tire). Implemente par ReferenceService. */
public interface Catalogue {

    Acte acteAleatoire(CategorieActe categorie, SeededRandom random);

    Examen examenAleatoire(TypeExamen type, SeededRandom random);

    Medicament medicamentAleatoire(SeededRandom random);
}
