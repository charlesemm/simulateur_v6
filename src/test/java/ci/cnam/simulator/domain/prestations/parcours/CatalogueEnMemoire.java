package ci.cnam.simulator.domain.prestations.parcours;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.catalog.Acte;
import ci.cnam.simulator.domain.reference.catalog.Acte.CategorieActe;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import ci.cnam.simulator.domain.reference.catalog.Examen;
import ci.cnam.simulator.domain.reference.catalog.Examen.TypeExamen;
import ci.cnam.simulator.domain.reference.catalog.Medicament;
import ci.cnam.simulator.domain.reference.seed.ReferenceGenerator;
import java.util.List;

/** Catalogues generes en memoire (meme generateur que le referentiel) pour les tests de parcours sans base. */
class CatalogueEnMemoire implements Catalogue {

    private final List<Acte> actes;
    private final List<Examen> examens;
    private final List<Medicament> medicaments;

    CatalogueEnMemoire() {
        ReferenceGenerator generateur = new ReferenceGenerator(new SeededRandom(1L));
        this.actes = generateur.actes();
        this.examens = generateur.examens();
        this.medicaments = generateur.medicaments();
    }

    @Override
    public Acte acteAleatoire(CategorieActe categorie, SeededRandom random) {
        return random.choisir(actes.stream().filter(a -> a.categorie() == categorie).toList());
    }

    @Override
    public Examen examenAleatoire(TypeExamen type, SeededRandom random) {
        return random.choisir(examens.stream().filter(e -> e.type() == type).toList());
    }

    @Override
    public Medicament medicamentAleatoire(SeededRandom random) {
        return random.choisir(medicaments);
    }
}
