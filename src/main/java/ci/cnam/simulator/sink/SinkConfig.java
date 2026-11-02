package ci.cnam.simulator.sink;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Destination par defaut des evenements, en attendant l'ecriture en base par lots (brique 7).
 * Attention : la liste en memoire grossit sans limite ; ne pas lancer de longue simulation avec ce sink.
 */
@Configuration
public class SinkConfig {

    @Bean
    @ConditionalOnMissingBean(EventSink.class)
    EventSink eventSink() {
        return new InMemoryEventSink();
    }
}
