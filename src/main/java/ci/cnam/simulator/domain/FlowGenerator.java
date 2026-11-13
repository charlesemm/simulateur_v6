package ci.cnam.simulator.domain;

import ci.cnam.simulator.core.engine.ContexteGeneration;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import java.util.List;

/**
 * Interface commune a tous les domaines (prestations, puis affiliation, recouvrement).
 * Ajouter un domaine = ecrire un FlowGenerator de plus, sans toucher a core/.
 */
public interface FlowGenerator {

    /** Nom du domaine, tel qu'ecrit dans le scenario (domaines.&lt;nom&gt;). */
    String domaine();

    /**
     * Types d'evenements que le domaine peut emettre (colonne {@code type} de TB_ECHO_EVENEMENT).
     * La contrainte {@code ck_evenement_type} (V22) enumere l'union de ces listes, et celle sur le
     * domaine enumere les {@link #domaine()} : un test verifie l'alignement, donc un domaine ou un
     * type ajoute sans migration est signale par le build, pas par un lot refuse en production.
     */
    List<String> typesEvenements();

    /** Verifie les reglages du scenario propres au domaine ; leve IllegalArgumentException avec un message clair. */
    default void valider(ConfigDomaine config) {
    }

    /** Produit UN flux unitaire (un parcours) et envoie ses evenements a {@code ctx.sink()}. */
    void generer(ContexteGeneration ctx);
}
