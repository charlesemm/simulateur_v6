package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.quality.planification.CibleAnalyse;
import ci.cnam.simulator.quality.planification.Echeance;
import ci.cnam.simulator.quality.planification.JoursSemaine;
import ci.cnam.simulator.quality.planification.Planification;
import ci.cnam.simulator.quality.planification.PlanificationStore;
import ci.cnam.simulator.quality.planification.StatutAnalyse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Tables {@code planification_analyse} et {@code analyse_attendue} (JDBC).
 *
 * <p>L'ouverture d'une echeance passe par un {@code on conflict do nothing} sur
 * {@code (planification_id, date_echeance)} : c'est la base qui garantit l'idempotence de la tache
 * planifiee, pas une verification lue-puis-ecrite qui laisserait une fenetre entre les deux.
 */
@Repository
public class PlanificationRepository implements PlanificationStore {

    private static final String SELECT_PLANIFICATION = """
            select id, libelle, heure, jours_semaine, cible, actif, date_creation, date_maj
            from planification_analyse
            """;

    private static final String SELECT_ECHEANCE = """
            select id, planification_id, run_id, date_echeance, date_retrait, date_rapport, statut, detail
            from analyse_attendue
            """;

    private static final RowMapper<Planification> PLANIFICATION = PlanificationRepository::lirePlanification;
    private static final RowMapper<Echeance> ECHEANCE = PlanificationRepository::lireEcheance;

    private final JdbcTemplate jdbc;

    public PlanificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void creer(Planification p) {
        jdbc.update("""
                insert into planification_analyse
                    (id, libelle, heure, jours_semaine, cible, actif, date_creation, date_maj)
                values (?, ?, ?, ?, ?, ?, ?, ?)""",
                p.id(), p.libelle(), p.heure(), p.jours().texte(), p.cible().name(), p.actif(),
                date(p.dateCreation()), date(p.dateMaj()));
    }

    @Override
    public boolean changerActivite(UUID id, boolean actif, Instant maintenant) {
        return jdbc.update("update planification_analyse set actif = ?, date_maj = ? where id = ?",
                actif, date(maintenant), id) == 1;
    }

    @Override
    public boolean supprimer(UUID id) {
        // Les echeances deja ouvertes survivent (planification_id passe a nul par ON DELETE SET NULL) :
        // ce qui a eu lieu reste tracable, meme si la regle n'existe plus.
        return jdbc.update("delete from planification_analyse where id = ?", id) == 1;
    }

    @Override
    public List<Planification> planifications() {
        return jdbc.query(SELECT_PLANIFICATION + " order by heure, libelle", PLANIFICATION);
    }

    @Override
    public Optional<Planification> planification(UUID id) {
        return jdbc.query(SELECT_PLANIFICATION + " where id = ?", PLANIFICATION, id).stream().findFirst();
    }

    @Override
    public boolean ouvrir(Echeance e) {
        return jdbc.update("""
                insert into analyse_attendue
                    (id, planification_id, run_id, date_echeance, date_retrait, date_rapport, statut, detail)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (planification_id, date_echeance) do nothing""",
                e.id(), e.planificationId(), e.runId(), date(e.dateEcheance()), date(e.dateRetrait()),
                date(e.dateRapport()), e.statut().name(), e.detail()) == 1;
    }

    @Override
    public List<Echeance> echeances(int limite) {
        return jdbc.query(SELECT_ECHEANCE + " order by date_echeance desc, id limit ?", ECHEANCE, limite);
    }

    @Override
    public List<Echeance> echeancesAuStatut(StatutAnalyse statut) {
        return jdbc.query(SELECT_ECHEANCE + " where statut = ? order by date_echeance, id",
                ECHEANCE, statut.name());
    }

    @Override
    public Optional<Instant> derniereEcheance(UUID planificationId) {
        return Optional.ofNullable(jdbc.queryForObject(
                        "select max(date_echeance) from analyse_attendue where planification_id = ?",
                        OffsetDateTime.class, planificationId))
                .map(OffsetDateTime::toInstant);
    }

    @Override
    public Optional<Echeance> echeanceOuvertePour(UUID runId) {
        return jdbc.query(SELECT_ECHEANCE + """
                 where run_id = ? and statut in ('ATTENDUE', 'RETIREE')
                 order by date_echeance, id limit 1""", ECHEANCE, runId).stream().findFirst();
    }

    @Override
    public void avancer(UUID id, StatutAnalyse statut, Instant dateRetrait, Instant dateRapport, String detail) {
        // coalesce : une date nulle n'efface pas celle qui est deja inscrite (le retrait reste
        // visible une fois le rapport arrive).
        jdbc.update("""
                update analyse_attendue
                set statut = ?,
                    date_retrait = coalesce(?, date_retrait),
                    date_rapport = coalesce(?, date_rapport),
                    detail = ?
                where id = ?""",
                statut.name(), date(dateRetrait), date(dateRapport), detail, id);
    }

    @Override
    public Optional<UUID> derniereSimulationTerminee() {
        // « Terminee » au sens de la planification : ARRETE. Un run ECHOUE n'est pas un jeu a
        // analyser, meme s'il est exportable.
        return jdbc.query("""
                select id from simulation_run where statut = 'ARRETE'
                order by coalesce(date_fin, date_creation) desc, id desc limit 1""",
                (rs, i) -> rs.getObject("id", UUID.class)).stream().findFirst();
    }

    private static Planification lirePlanification(ResultSet rs, int ligne) throws SQLException {
        return new Planification(
                rs.getObject("id", UUID.class),
                rs.getString("libelle"),
                rs.getObject("heure", LocalTime.class),
                JoursSemaine.lire(rs.getString("jours_semaine")),
                CibleAnalyse.valueOf(rs.getString("cible")),
                rs.getBoolean("actif"),
                instant(rs, "date_creation"),
                instant(rs, "date_maj"));
    }

    private static Echeance lireEcheance(ResultSet rs, int ligne) throws SQLException {
        return new Echeance(
                rs.getObject("id", UUID.class),
                rs.getObject("planification_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                instant(rs, "date_echeance"),
                instant(rs, "date_retrait"),
                instant(rs, "date_rapport"),
                StatutAnalyse.valueOf(rs.getString("statut")),
                rs.getString("detail"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime date = rs.getObject(colonne, OffsetDateTime.class);
        return date == null ? null : date.toInstant();
    }

    private static OffsetDateTime date(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
