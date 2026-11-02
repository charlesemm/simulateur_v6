package ci.cnam.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public record Ecriture(int tailleLot, int fileAttenteMax) {
    }
}
