package ci.cnam.simulator.chaos;

import ci.cnam.simulator.sink.DatabaseSink;
import ci.cnam.simulator.sink.EventSink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** La destination injectee dans le moteur passe par la troncature (sans effet tant que le chaos n'est pas actif). */
@Configuration
public class ChaosConfig {

    @Bean
    @Primary
    EventSink eventSinkAvecChaos(DatabaseSink base, TroncatureMessages troncature) {
        return new ChaosEventSink(base, troncature);
    }
}
