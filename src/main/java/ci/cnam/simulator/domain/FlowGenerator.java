package ci.cnam.simulator.domain;

import ci.cnam.simulator.core.engine.ContexteGeneration;
import ci.cnam.simulator.core.scenario.ConfigDomaine;

/**
 * Interface commune a tous les domaines (prestations, puis affiliation, recouvrement).
 * Ajouter un domaine = ecrire un FlowGenerator de plus, sans toucher a core/.
 */
public interface FlowGenerator {

    /** Nom du domaine, tel qu'ecrit dans le scenario (domaines.&lt;nom&gt;). */
    String domaine();

    /** Verifie les reglages du scenario propres au domaine ; leve IllegalArgumentException avec un message clair. */
    default void valider(ConfigDomaine config) {
    }

    /** Produit UN flux unitaire (un parcours) et envoie ses evenements a {@code ctx.sink()}. */
    void generer(ContexteGeneration ctx);
}
