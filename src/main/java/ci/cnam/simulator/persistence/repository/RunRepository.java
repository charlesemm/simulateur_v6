package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.core.engine.EtatRun;
import ci.cnam.simulator.core.engine.RunStore;
import ci.cnam.simulator.core.scenario.ConfigDomaine;
import ci.cnam.simulator.core.scenario.Phase;
import ci.cnam.simulator.core.scenario.Scenario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Tables scenario et simulation_run (JDBC). */
@Repository
public class RunRepository implements RunStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RunRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void creer(UUID runId, Scenario s) {
        UUID scenarioId = jdbc.queryForObject("""
                insert into scenario (id, nom, contenu_yaml) values (?, ?, ?)
                on conflict (nom) do update set contenu_yaml = excluded.contenu_yaml, date_maj = now()
                returning id""", UUID.class, UUID.randomUUID(), s.nom(), s.contenuYaml());
        jdbc.update("""
                insert into simulation_run (id, scenario_id, seed, statut, debut_simule, parametres)
                values (?, ?, ?, ?, ?, ?::jsonb)""",
                runId, scenarioId, s.seed(), EtatRun.CREE.name(), Timestamp.valueOf(s.debutSimule()), parametres(s));
    }

    @Override
    public void mettreAJour(UUID runId, EtatRun etat, Instant debut, Instant fin, String erreur) {
        jdbc.update("""
                update simulation_run set statut = ?, date_debut = ?, date_fin = ?, message_erreur = ?
                where id = ?""",
                etat.name(), date(debut), date(fin), erreur, runId);
    }

    public String statut(UUID runId) {
        return jdbc.queryForObject("select statut from simulation_run where id = ?", String.class, runId);
    }

    private static OffsetDateTime date(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private String parametres(Scenario s) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accelerationTemps", s.accelerationTemps());
        Map<String, Object> domaines = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigDomaine> e : s.domaines().entrySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("debit", e.getValue().debit());
            d.put("parcours", e.getValue().parcours());
            domaines.put(e.getKey(), d);
        }
        p.put("domaines", domaines);
        List<Map<String, Object>> phases = new ArrayList<>();
        for (Phase ph : s.phases()) {
            phases.add(Map.of("de", ph.de().toString(), "a", ph.a().toString(), "charge", ph.charge()));
        }
        p.put("phases", phases);
        try {
            return json.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parametres du run non serialisables", e);
        }
    }
}
