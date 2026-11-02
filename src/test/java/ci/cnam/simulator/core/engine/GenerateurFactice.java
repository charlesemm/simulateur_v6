package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.domain.FlowGenerator;
import java.time.LocalDateTime;
import java.util.Map;

/** Generateur de test : un evenement par parcours, tout tire de l'alea et de l'horloge du contexte. */
class GenerateurFactice implements FlowGenerator {

    private final String domaine;

    GenerateurFactice(String domaine) {
        this.domaine = domaine;
    }

    @Override
    public String domaine() {
        return domaine;
    }

    @Override
    public void generer(ContexteGeneration ctx) {
        ctx.sink().emettre(new SimEvent(ctx.random().nextUuid(), ctx.runId(), domaine, "TOP",
                LocalDateTime.now(ctx.horloge()), Map.of("tirage", ctx.random().nextInt(1000)), null));
    }
}
