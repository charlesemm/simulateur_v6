package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.sink.EventSink;
import java.time.Clock;
import java.util.UUID;

/**
 * Ce que le moteur donne a un generateur pour produire UN flux unitaire (un parcours).
 *
 * @param runId   run en cours
 * @param random  alea du domaine pour ce run (seul point d'alea autorise)
 * @param horloge horloge figee a l'instant simule d'arrivee de ce flux
 * @param config  reglages du domaine dans le scenario
 * @param sink    destination des evenements
 */
public record ContexteGeneration(UUID runId, SeededRandom random, Clock horloge, ConfigDomaine config, EventSink sink) {
}
