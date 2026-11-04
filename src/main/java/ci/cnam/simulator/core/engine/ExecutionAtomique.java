package ci.cnam.simulator.core.engine;

/**
 * Execute un parcours en TOUT OU RIEN et dit si une erreur vient d'une indisponibilite passagere de la destination.
 * Le moteur ne sait pas ce qu'est une base de donnees : il sait seulement qu'un parcours interrompu par une
 * indisponibilite est annule en entier puis rejoue a l'identique.
 */
public interface ExecutionAtomique {

    /** Sans transaction ni reprise (tests du moteur seul). */
    ExecutionAtomique DIRECTE = new ExecutionAtomique() {
        @Override
        public void executer(Runnable travail) {
            travail.run();
        }

        @Override
        public boolean estIndisponibilite(Throwable erreur) {
            return false;
        }

        @Override
        public long patienceMs() {
            return 0;
        }
    };

    /** Execute {@code travail} ; si une exception sort, rien de ce qu'il a ecrit ne subsiste. */
    void executer(Runnable travail);

    /** Vrai si l'erreur est passagere (liaison coupee, base injoignable) : le parcours peut etre rejoue. */
    boolean estIndisponibilite(Throwable erreur);

    /** Duree maximale pendant laquelle on rejoue un parcours avant de declarer l'echec du run. */
    long patienceMs();
}
