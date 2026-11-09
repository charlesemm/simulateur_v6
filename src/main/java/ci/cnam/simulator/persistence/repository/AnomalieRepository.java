package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.anomaly.AnomalyQueryStore;
import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.anomaly.AnomalyType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecture paginee de la table anomaly_injected, dans l'ordre d'ecriture (tri stable). */
@Repository
public class AnomalieRepository implements AnomalyQueryStore {

    private final JdbcTemplate jdbc;

    public AnomalieRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AnomalyRecord> rechercher(UUID runId, AnomalyType type, String champ, int page, int taille) {
        List<Object> parametres = new ArrayList<>();
        String ou = filtres(runId, type, champ, parametres);
        parametres.add(taille);
        parametres.add((long) page * taille);
        return jdbc.query("""
                select run_id, event_id, facture_id, type, champ, valeur_origine, valeur_corrompue, doublon_de
                from "TB_ECHO_ANOMALY_INJECTED\"""" + ou + " order by id limit ? offset ?",
                (rs, n) -> new AnomalyRecord(
                        rs.getObject("run_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("facture_id", UUID.class),
                        AnomalyType.valueOf(rs.getString("type")),
                        rs.getString("champ"),
                        rs.getString("valeur_origine"),
                        rs.getString("valeur_corrompue"),
                        rs.getObject("doublon_de", UUID.class)),
                parametres.toArray());
    }

    @Override
    public long compter(UUID runId, AnomalyType type, String champ) {
        List<Object> parametres = new ArrayList<>();
        String ou = filtres(runId, type, champ, parametres);
        Long total = jdbc.queryForObject("select count(*) from \"TB_ECHO_ANOMALY_INJECTED\"" + ou, Long.class,
                parametres.toArray());
        return total == null ? 0 : total;
    }

    private static String filtres(UUID runId, AnomalyType type, String champ, List<Object> parametres) {
        StringBuilder ou = new StringBuilder(" where run_id = ?");
        parametres.add(runId);
        if (type != null) {
            ou.append(" and type = ?");
            parametres.add(type.name());
        }
        if (champ != null) {
            ou.append(" and champ = ?");
            parametres.add(champ);
        }
        return ou.toString();
    }
}
