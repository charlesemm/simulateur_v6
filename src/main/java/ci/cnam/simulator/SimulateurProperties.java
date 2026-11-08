package ci.cnam.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reglages propres au simulateur (section "simulateur" de application.yml).
 * Aucun secret n'est ecrit dans le code : les valeurs viennent des variables d'environnement.
 */
@ConfigurationProperties(prefix = "simulateur")
public record SimulateurProperties(
        long graineParDefaut,
        String scenariosDir,
        Securite securite,
        Toxiproxy toxiproxy,
        Ecriture ecriture,
        @DefaultValue Chaos chaos,
        @DefaultValue Qualite qualite) {

    /**
     * @param echecsAvantVerrouillage echecs de connexion consecutifs avant verrouillage du compte
     * @param verrouillageMinutes     duree du verrouillage temporaire
     */
    public record Securite(String cleApi, String motDePasseAdminInitial,
                           @DefaultValue("5") int echecsAvantVerrouillage,
                           @DefaultValue("15") int verrouillageMinutes) {
    }

    public record Toxiproxy(String url, String proxy) {
    }

    /**
     * @param delaiRapportMinutes delai au-dela duquel un jeu retire sans rapport devient un SILENCE
     *                            (echec d'echange), et JAMAIS un taux de detection de 0 %
     */
    public record Qualite(@DefaultValue("60") int delaiRapportMinutes) {
    }

    /**
     * @param tailleLot      evenements par lot JDBC
     * @param fileAttenteMax capacite de la file bornee (au-dela : contre-pression sur le generateur)
     * @param delaiMaxMs     attente maximale avant d'ecrire un lot incomplet
     * @param tentatives     essais d'ecriture d'un lot avant de declarer l'echec
     * @param flushTimeoutMs attente maximale de flush()
     * @param indisponibiliteMaxMs duree maximale pendant laquelle une base injoignable est attendue (lot et
     *                       parcours sont rejoues) avant de declarer l'echec du run ; ne concerne QUE les pannes de
     *                       liaison, une vraie erreur SQL echoue apres {@code tentatives} essais
     */
    public record Ecriture(
            @DefaultValue("500") int tailleLot,
            @DefaultValue("10000") int fileAttenteMax,
            @DefaultValue("200") long delaiMaxMs,
            @DefaultValue("3") int tentatives,
            @DefaultValue("30000") long flushTimeoutMs,
            @DefaultValue("120000") long indisponibiliteMaxMs) {

        /** Constructeur utilise par la liaison de configuration (le record a deux constructeurs). */
        @ConstructorBinding
        public Ecriture {
        }

        public Ecriture(int tailleLot, int fileAttenteMax, long delaiMaxMs, int tentatives, long flushTimeoutMs) {
            this(tailleLot, fileAttenteMax, delaiMaxMs, tentatives, flushTimeoutMs, 120_000);
        }
    }

    /**
     * @param dureeMaxSecondes duree maximale imposee a toute action de chaos (au-dela : refusee)
     * @param latenceMaxMs     latence maximale qu'une action peut ajouter
     */
    public record Chaos(
            @DefaultValue("300") int dureeMaxSecondes,
            @DefaultValue("10000") int latenceMaxMs) {
    }
}
