package ci.cnam.simulator;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectee partout ou l'on a besoin de l'heure : jamais Instant.now() / LocalDateTime.now() sans horloge. */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
