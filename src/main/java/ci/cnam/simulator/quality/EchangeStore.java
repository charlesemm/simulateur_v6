package ci.cnam.simulator.quality;

import java.util.List;
import java.util.UUID;

/** Acces a la table {@code echange}. Implemente dans persistence/. */
public interface EchangeStore {

    /** Inscrit un echange. Rien n'est jamais modifie ni supprime : le journal est une trace. */
    void tracer(Echange echange);

    /** Le journal d'un run, du plus ANCIEN au plus recent (l'ordre de l'echange compte). */
    List<Echange> journal(UUID runId);
}
