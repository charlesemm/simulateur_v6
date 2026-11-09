package ci.cnam.simulator.domain.prestations.tarification;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecture de la table taux. */
@Repository
public class TauxRepository {

    private final JdbcTemplate jdbc;

    public TauxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RegleTaux charger() {
        Map<String, BigDecimal> lignes = new HashMap<>();
        jdbc.query("select code, taux from \"TB_ECHO_TAUX\"", rs -> {
            lignes.put(rs.getString("code"), rs.getBigDecimal("taux"));
        });
        return RegleTaux.depuis(lignes);
    }
}
