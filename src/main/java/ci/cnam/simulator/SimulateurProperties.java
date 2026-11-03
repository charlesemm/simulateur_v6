package ci.cnam.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        Ecriture ecriture) {

    public record Securite(String cleApi, String motDePasseAdminInitial, int echecsAvantVerrouillage) {
    }

    public record Toxiproxy(String url, String proxy) {
    }

    /**
     * @param tailleLot      evenements par lot JDBC
     * @param fileAttenteMax capacite de la file bornee (au-dela : contre-pression sur le generateur)
     * @param delaiMaxMs     attente maximale avant d'ecrire un lot incomplet
     * @param tentatives     essais d'ecriture d'un lot avant de declarer l'echec
     * @param flushTimeoutMs attente maximale de flush()
     */
    public record Ecriture(
            @DefaultValue("500") int tailleLot,
            @DefaultValue("10000") int fileAttenteMax,
            @DefaultValue("200") long delaiMaxMs,
            @DefaultValue("3") int tentatives,
            @DefaultValue("30000") long flushTimeoutMs) {
    }
}
