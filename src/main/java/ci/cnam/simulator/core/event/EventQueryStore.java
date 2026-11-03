package ci.cnam.simulator.core.event;

/** Lecture des evenements ecrits (table evenement). Implementee dans persistence/. */
public interface EventQueryStore {

    PageEvenements rechercher(RechercheEvenements recherche);
}
