package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.quality.Echange;
import ci.cnam.simulator.quality.EchangeStore;
import ci.cnam.simulator.quality.SensEchange;
import ci.cnam.simulator.quality.StatutEchange;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Table {@code echange} : on y ajoute, on n'y modifie ni n'y supprime rien. Le journal est une
 * trace, conservee pour un eventuel litige sur la performance de l'outil teste (cahier 4.5).
 */
@Repository
public class EchangeRepository implements EchangeStore {

    private final JdbcTemplate jdbc;

    public EchangeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void tracer(Echange echange) {
        jdbc.update("""
                insert into "TB_ECHO_ECHANGE" (id, run_id, sens, date_echange, version_outil, statut, detail)
                values (?, ?, ?, ?, ?, ?, ?)""",
                echange.id(), echange.runId(), echange.sens().name(),
                OffsetDateTime.ofInstant(echange.date(), java.time.ZoneOffset.UTC),
                echange.versionOutil(), echange.statut().name(), echange.detail());
    }

    @Override
    public List<Echange> journal(UUID runId) {
        return jdbc.query("""
                select id, run_id, sens, date_echange, version_outil, statut, detail
                from "TB_ECHO_ECHANGE" where run_id = ? order by date_echange, id""",
                (rs, i) -> new Echange(
                        rs.getObject("id", UUID.class),
                        rs.getObject("run_id", UUID.class),
                        SensEchange.valueOf(rs.getString("sens")),
                        rs.getObject("date_echange", OffsetDateTime.class).toInstant(),
                        rs.getString("version_outil"),
                        StatutEchange.valueOf(rs.getString("statut")),
                        rs.getString("detail")),
                runId);
    }
}
