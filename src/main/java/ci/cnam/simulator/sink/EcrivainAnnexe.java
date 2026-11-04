package ci.cnam.simulator.sink;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ecrit par lots les {@link EcritureAnnexe} d'un type donne. Il est appele DANS la transaction du lot d'evenements :
 * il ne doit ni ouvrir de transaction ni valider lui-meme, et doit etre rejouable sans doublon
 * (ex. {@code ON CONFLICT DO NOTHING} sur des identifiants deterministes).
 */
public interface EcrivainAnnexe<T extends EcritureAnnexe> {

    Class<T> type();

    void ecrire(JdbcTemplate jdbc, List<T> lot);
}
