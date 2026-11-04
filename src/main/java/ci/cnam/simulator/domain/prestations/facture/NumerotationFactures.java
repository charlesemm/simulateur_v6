package ci.cnam.simulator.domain.prestations.facture;

import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Numeros de facture pris dans la sequence {@code seq_numero_facture} PAR BLOCS : un aller-retour vers la base pour
 * {@value #BLOC} numeros, au lieu d'un par facture. Les numeros d'un bloc non consomme sont perdus a l'arret
 * (des trous, jamais de doublon : la sequence ne revient pas en arriere).
 */
@Component
public class NumerotationFactures {

    static final int BLOC = 1000;

    private final JdbcTemplate jdbc;
    private final Deque<Long> reserve = new ArrayDeque<>();

    public NumerotationFactures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized long suivant() {
        if (reserve.isEmpty()) {
            reserve.addAll(jdbc.queryForList(
                    "select nextval('seq_numero_facture') from generate_series(1, ?)", Long.class, BLOC));
        }
        return reserve.poll();
    }
}
