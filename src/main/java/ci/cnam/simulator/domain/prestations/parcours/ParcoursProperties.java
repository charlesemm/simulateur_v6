package ci.cnam.simulator.domain.prestations.parcours;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Probabilites des trois voies apres la consultation (simulator.parcours.*). Valeurs de depart 60 / 10 / 30,
 * reglables par les directions metier ; leur somme doit valoir 1.
 */
@ConfigurationProperties(prefix = "simulator.parcours")
public record ParcoursProperties(
        @DefaultValue("0.60") double probaExamens,
        @DefaultValue("0.10") double probaHospitalisation,
        @DefaultValue("0.30") double probaDirect) {

    public ParcoursProperties {
        if (probaExamens < 0 || probaHospitalisation < 0 || probaDirect < 0
                || Math.abs(probaExamens + probaHospitalisation + probaDirect - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Les probabilites des voies doivent etre positives et sommer a 1");
        }
    }
}
