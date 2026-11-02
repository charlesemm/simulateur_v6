package ci.cnam.simulator.domain.reference;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Volume et graine du referentiel synthetique (simulator.reference.*). */
@ConfigurationProperties(prefix = "simulator.reference")
public record ReferenceProperties(
        @DefaultValue("50000") int nbAssures,
        @DefaultValue("42") long seed) {
}
