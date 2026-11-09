package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.core.event.EvenementLu;
import ci.cnam.simulator.core.event.EventQueryStore;
import ci.cnam.simulator.core.event.PageEvenements;
import ci.cnam.simulator.core.event.RechercheEvenements;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Lecture paginee de la table evenement. Tri stable (temps reel puis identifiant) pour qu'une page
 * ne saute ni ne repete un evenement.
 */
@Repository
public class EvenementRepository implements EventQueryStore {

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EvenementRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public PageEvenements rechercher(RechercheEvenements r) {
        StringBuilder ou = new StringBuilder(" where run_id = ?");
        List<Object> parametres = new ArrayList<>();
        parametres.add(r.runId());
        if (r.eventId() != null) {
            ou.append(" and event_id = ?");
            parametres.add(r.eventId());
        }
        ajouter(ou, parametres, "domaine", r.domaine());
        ajouter(ou, parametres, "type", r.type());
        ajouter(ou, parametres, "statut", r.statut());

        Long total = jdbc.queryForObject("select count(*) from \"TB_ECHO_EVENEMENT\"" + ou, Long.class,
                parametres.toArray());

        List<Object> page = new ArrayList<>(parametres);
        page.add(r.taille());
        page.add((long) r.page() * r.taille());
        List<EvenementLu> contenu = jdbc.query("""
                select event_id, run_id, facture_id, domaine, type, timestamp_simule, timestamp_reel, statut, payload
                from "TB_ECHO_EVENEMENT\"""" + ou + " order by timestamp_reel, event_id limit ? offset ?",
                (RowMapper<EvenementLu>) this::lire, page.toArray());

        return new PageEvenements(contenu, r.page(), r.taille(), total == null ? 0 : total);
    }

    private static void ajouter(StringBuilder ou, List<Object> parametres, String colonne, String valeur) {
        if (valeur != null) {
            ou.append(" and ").append(colonne).append(" = ?");
            parametres.add(valeur);
        }
    }

    private EvenementLu lire(ResultSet rs, int ligne) throws SQLException {
        OffsetDateTime reel = rs.getObject("timestamp_reel", OffsetDateTime.class);
        return new EvenementLu(
                rs.getObject("event_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("facture_id", UUID.class),
                rs.getString("domaine"),
                rs.getString("type"),
                rs.getObject("timestamp_simule", LocalDateTime.class),
                reel == null ? null : reel.toInstant(),
                rs.getString("statut"),
                payload(rs.getString("payload")));
    }

    private Map<String, Object> payload(String texte) {
        if (texte == null || texte.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(texte, PAYLOAD);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload d'evenement illisible", e);
        }
    }
}
