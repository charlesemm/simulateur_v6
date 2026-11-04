package ci.cnam.simulator.sink;

import ci.cnam.simulator.core.event.SimEvent;

/** Destination des evenements produits (memoire, base par lots, CSV...). */
public interface EventSink {

    /** Peut bloquer (contre-pression) si la destination ne suit pas ; ne perd jamais un evenement en silence. */
    void emettre(SimEvent evenement);

    /**
     * Joint une ecriture annexe (ex. la facture d'un parcours) : elle sera validee avec le lot qui la contient, et
     * jamais apres les evenements du meme parcours. Une destination qui ne sait pas l'ecrire la refuse plutot que de
     * la perdre en silence.
     */
    default void joindre(EcritureAnnexe annexe) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " ne sait pas ecrire " + annexe.getClass().getSimpleName());
    }

    /** Rend la main quand tout ce qui a ete emis est durablement ecrit. Sans effet pour une destination synchrone. */
    default void flush() {
    }
}
