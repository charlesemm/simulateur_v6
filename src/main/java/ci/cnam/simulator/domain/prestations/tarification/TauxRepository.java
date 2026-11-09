package ci.cnam.simulator.domain.prestations.tarification;

import ci.cnam.simulator.domain.reference.model.Regime;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecture des deux tables de taux : les taux par type de prestation et les taux de regime. */
@Repository
public class TauxRepository {

    private static final BigDecimal CENT = new BigDecimal("100");

    private final JdbcTemplate jdbc;

    public TauxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RegleTaux charger() {
        Map<String, BigDecimal> lignes = new HashMap<>();
        jdbc.query("select code, taux from \"TB_ECHO_TAUX\"", rs -> {
            lignes.put(rs.getString("code"), rs.getBigDecimal("taux"));
        });

        // La table de valeurs exprime le taux en pour cent (70.00), la tarification en fraction (0.7000).
        Map<Regime, TauxRegime> regimes = new EnumMap<>(Regime.class);
        jdbc.query("select regime_code, regime_taux_defaut, regime_contributif from \"TB_TV_REGIMES\"", rs -> {
            BigDecimal taux = rs.getBigDecimal("regime_taux_defaut").divide(CENT);
            boolean contributif = rs.getBoolean("regime_contributif");
            Regime.depuisCode(rs.getString("regime_code"))
                    .ifPresent(regime -> regimes.put(regime, new TauxRegime(taux, contributif)));
        });
        return RegleTaux.depuis(lignes, regimes);
    }
}
