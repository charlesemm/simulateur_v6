package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.core.scenario.Scenario;
import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import ci.cnam.simulator.core.scenario.ScenarioStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Table scenario (JDBC). Le nom est unique : enregistrer deux fois remplace le contenu. */
@Repository
public class ScenarioRepository implements ScenarioStore {

    private static final String SELECT = "select nom, contenu_yaml, date_creation, date_maj from scenario";
    private static final RowMapper<ScenarioEnregistre> MAPPER = ScenarioRepository::lire;

    private final JdbcTemplate jdbc;

    public ScenarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(Scenario scenario) {
        jdbc.update("""
                insert into scenario (id, nom, contenu_yaml) values (?, ?, ?)
                on conflict (nom) do update set contenu_yaml = excluded.contenu_yaml, date_maj = now()""",
                UUID.randomUUID(), scenario.nom(), scenario.contenuYaml());
    }

    @Override
    public List<ScenarioEnregistre> lister() {
        return jdbc.query(SELECT + " order by nom", MAPPER);
    }

    @Override
    public Optional<ScenarioEnregistre> trouverParNom(String nom) {
        return jdbc.query(SELECT + " where nom = ?", MAPPER, nom).stream().findFirst();
    }

    private static ScenarioEnregistre lire(ResultSet rs, int ligne) throws SQLException {
        OffsetDateTime creation = rs.getObject("date_creation", OffsetDateTime.class);
        OffsetDateTime maj = rs.getObject("date_maj", OffsetDateTime.class);
        return new ScenarioEnregistre(
                rs.getString("nom"),
                rs.getString("contenu_yaml"),
                ScenarioEnregistre.Source.BASE,
                creation == null ? null : creation.toInstant(),
                maj == null ? null : maj.toInstant());
    }
}
