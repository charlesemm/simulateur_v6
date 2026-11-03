package ci.cnam.simulator.sink;

import ci.cnam.simulator.SimulateurProperties;
import ci.cnam.simulator.monitoring.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Destination des evenements : ecriture en base par lots (InMemoryEventSink reste reserve aux tests). */
@Configuration
public class SinkConfig {

    @Bean(initMethod = "demarrer", destroyMethod = "close")
    DatabaseSink eventSink(JdbcTemplate jdbc, PlatformTransactionManager tx, ObjectMapper json, Clock horloge,
                           MetricsService metriques, SimulateurProperties proprietes) {
        return new DatabaseSink(jdbc, tx, json, horloge, metriques, proprietes.ecriture());
    }
}
