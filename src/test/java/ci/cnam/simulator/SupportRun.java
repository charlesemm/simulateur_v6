package ci.cnam.simulator;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Tests d'integration : cree un vrai run en base (facture.run_id a une cle etrangere vers simulation_run). */
public final class SupportRun {

    private SupportRun() {
    }

    public static UUID nouveauRun(JdbcTemplate jdbc) {
        UUID scenario = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        jdbc.update("insert into scenario (id, nom, contenu_yaml) values (?, ?, ?)", scenario,
                "test-" + scenario, "nom: test");
        jdbc.update("""
                insert into "TB_ECHO_SIMULATION_RUN" (id, scenario_id, seed, statut, debut_simule, parametres)
                values (?, ?, 42, 'CREE', now(), '{}'::jsonb)""", run, scenario);
        return run;
    }
}
