package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.quality.AnomalieRatee;
import ci.cnam.simulator.quality.Detection;
import ci.cnam.simulator.quality.FauxPositifSignale;
import ci.cnam.simulator.quality.QualityStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tables quality_report et quality_detection, comparees a anomaly_injected. */
@Repository
public class QualiteRepository implements QualityStore {

    private static final int TAILLE_LOT = 1000;

    /** Detections distinctes du run (tous rapports confondus, ou ceux d'une version de l'outil). */
    private static final String DETECTIONS = """
            select distinct d.event_id, d.type, d.champ
            from "TB_ECHO_QUALITY_DETECTION" d join "TB_ECHO_QUALITY_REPORT" r on r.id = d.report_id
            where d.run_id = ? and (?::text is null or r.version_outil = ?)""";

    private final JdbcTemplate jdbc;

    public QualiteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> evenementsDuRun(UUID runId, Collection<UUID> eventIds) {
        Set<UUID> trouves = new HashSet<>();
        List<UUID> ids = new ArrayList<>(eventIds);
        for (int i = 0; i < ids.size(); i += 5000) {
            List<UUID> tranche = ids.subList(i, Math.min(ids.size(), i + 5000));
            trouves.addAll(jdbc.query(
                    "select event_id from \"TB_ECHO_EVENEMENT\" where run_id = ? and event_id = any (?)",
                    (rs, n) -> rs.getObject(1, UUID.class), runId, tranche.toArray(UUID[]::new)));
        }
        return trouves;
    }

    @Override
    public int enregistrer(UUID rapportId, UUID runId, String versionOutil, List<Detection> detections) {
        jdbc.update("insert into \"TB_ECHO_QUALITY_REPORT\" (id, run_id, version_outil) values (?, ?, ?)", rapportId, runId,
                versionOutil);
        jdbc.batchUpdate("""
                insert into "TB_ECHO_QUALITY_DETECTION" (report_id, run_id, event_id, type, champ, regle, score)
                values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing""", detections, TAILLE_LOT, (ps, d) -> {
            ps.setObject(1, rapportId);
            ps.setObject(2, runId);
            ps.setObject(3, d.eventId());
            ps.setString(4, d.type().name());
            ps.setString(5, d.type() == AnomalyType.DOUBLON_EXACT ? null : d.champ().strip());
            ps.setString(6, d.regle());
            ps.setObject(7, d.score());
        });
        // Le pilote ne dit pas toujours quelles lignes ont ete ignorees : on recompte.
        Integer reel = jdbc.queryForObject("select count(*) from \"TB_ECHO_QUALITY_DETECTION\" where report_id = ?",
                Integer.class, rapportId);
        return reel == null ? 0 : reel;
    }

    @Override
    public Map<AnomalyType, Long> injectees(UUID runId) {
        return compter("select type, count(*) from \"TB_ECHO_ANOMALY_INJECTED\" where run_id = ? group by type", runId);
    }

    @Override
    public Map<AnomalyType, Long> detectees(UUID runId, String versionOutil) {
        return compter("""
                select a.type, count(*) from "TB_ECHO_ANOMALY_INJECTED" a
                where a.run_id = ? and exists (select 1 from (""" + DETECTIONS + """
                ) d where d.event_id = a.event_id and d.type = a.type and d.champ is not distinct from a.champ)
                group by a.type""", runId, runId, versionOutil, versionOutil);
    }

    @Override
    public Map<AnomalyType, Long> fauxPositifs(UUID runId, String versionOutil) {
        return compter("""
                select d.type, count(*) from (""" + DETECTIONS + """
                ) d where not exists (
                    select 1 from "TB_ECHO_ANOMALY_INJECTED" a where a.run_id = ? and a.event_id = d.event_id
                    and a.type = d.type and a.champ is not distinct from d.champ)
                group by d.type""", runId, versionOutil, versionOutil, runId);
    }

    /**
     * Les anomalies ratees, ligne a ligne. La condition est l'exacte negation de celle de
     * {@link #detectees} : rien n'est recompte autrement, seule la projection change.
     *
     * <p>Tri sur {@code a.id} (ordre d'ecriture) : stable, donc une page ne saute ni ne repete.
     */
    @Override
    public List<AnomalieRatee> ratees(UUID runId, String versionOutil, int limite, int decalage) {
        return jdbc.query("""
                select a.event_id, a.facture_id, a.type, a.champ, a.valeur_origine, a.valeur_corrompue
                from "TB_ECHO_ANOMALY_INJECTED" a
                where a.run_id = ? and not exists (select 1 from (""" + DETECTIONS + """
                ) d where d.event_id = a.event_id and d.type = a.type and d.champ is not distinct from a.champ)
                order by a.id limit ? offset ?""",
                (rs, n) -> new AnomalieRatee(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)),
                runId, runId, versionOutil, versionOutil, limite, decalage);
    }

    @Override
    public List<FauxPositifSignale> fauxPositifsSignales(UUID runId, String versionOutil, int limite,
                                                         int decalage) {
        return jdbc.query("select d.event_id, d.type, d.champ from (" + DETECTIONS + """
                ) d where not exists (
                    select 1 from "TB_ECHO_ANOMALY_INJECTED" a where a.run_id = ? and a.event_id = d.event_id
                    and a.type = d.type and a.champ is not distinct from d.champ)
                order by d.event_id, d.type, d.champ limit ? offset ?""",
                (rs, n) -> new FauxPositifSignale(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                runId, versionOutil, versionOutil, runId, limite, decalage);
    }

    private Map<AnomalyType, Long> compter(String sql, Object... parametres) {
        Map<AnomalyType, Long> resultat = new EnumMap<>(AnomalyType.class);
        jdbc.query(sql, rs -> {
            resultat.put(AnomalyType.valueOf(rs.getString(1)), rs.getLong(2));
        }, parametres);
        return resultat;
    }
}
