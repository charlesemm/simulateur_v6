package ci.cnam.simulator.chaos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Table chaos_action : trace d'audit (qui, quand, quoi) de chaque action de chaos. */
@Repository
public class ChaosActionStore {

    private static final TypeReference<Map<String, Object>> PARAMETRES = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ChaosActionStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void creer(ChaosAction a) {
        jdbc.update("""
                insert into chaos_action (id, run_id, type, parametres, debut, fin_prevue, auteur)
                values (?, ?, ?, ?::jsonb, ?, ?, ?)""",
                a.id(), a.runId(), a.type().name(), ecrire(a.parametres()), utc(a.debut()), utc(a.finPrevue()),
                a.auteur());
    }

    public void terminer(UUID id, Instant fin, String motif, long touches) {
        jdbc.update("update chaos_action set fin = ?, motif_fin = ?, evenements_touches = ? where id = ?",
                utc(fin), motif, touches, id);
    }

    public void supprimer(UUID id) {
        jdbc.update("delete from chaos_action where id = ?", id);
    }

    public List<ChaosAction> lister(UUID runId) {
        return jdbc.query("select * from chaos_action where run_id = ? order by debut, id", ligne(), runId);
    }

    /** Au demarrage : une action restee ouverte appartient a un processus mort, son effet n'existe plus. */
    public int interrompre(Instant maintenant) {
        return jdbc.update("""
                update chaos_action set fin = greatest(?, debut), motif_fin = 'INTERROMPUE' where fin is null""",
                utc(maintenant));
    }

    private RowMapper<ChaosAction> ligne() {
        return (rs, i) -> {
            Timestamp fin = rs.getTimestamp("fin");
            try {
                return new ChaosAction(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class),
                        ChaosType.valueOf(rs.getString("type")),
                        json.readValue(rs.getString("parametres"), PARAMETRES),
                        rs.getTimestamp("debut").toInstant(), rs.getTimestamp("fin_prevue").toInstant(),
                        fin == null ? null : fin.toInstant(), rs.getString("motif_fin"), rs.getString("auteur"),
                        rs.getLong("evenements_touches"));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Parametres illisibles pour l'action " + rs.getString("id"), e);
            }
        };
    }

    private String ecrire(Map<String, Object> parametres) {
        try {
            return json.writeValueAsString(parametres);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Parametres non serialisables", e);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
