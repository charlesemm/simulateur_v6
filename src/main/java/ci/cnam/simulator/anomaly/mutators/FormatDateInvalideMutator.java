package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Date invalide, de quatre facons : jour impossible (0, 2026-03-35), mois superieur a 12 (1, 2026-13-10),
 * texte libre (2, "hier") ou date valide dans un mauvais format (3, 10/03/2026). Accepte une date ou un texte ISO.
 */
@Component
public class FormatDateInvalideMutator implements Mutator {

    static final List<String> TEXTES = List.of("hier", "date inconnue", "N/A", "a definir", "bientot");
    static final List<String> FORMATS = List.of("dd/MM/yyyy", "yyyyMMdd", "dd.MM.yy", "MM-dd-yyyy");

    @Override
    public AnomalyType type() {
        return AnomalyType.FORMAT_DATE_INVALIDE;
    }

    @Override
    public Optional<Mutation> muter(Object valeur, SeededRandom random) {
        Optional<LocalDate> date = lire(valeur);
        if (date.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = date.get();
        String corrompue = switch (random.entre(0, 3)) {
            case 0 -> "%04d-%02d-%02d".formatted(d.getYear(), d.getMonthValue(),
                    random.entre(d.lengthOfMonth() + 1, 39));
            case 1 -> "%04d-%02d-%02d".formatted(d.getYear(), random.entre(13, 99), d.getDayOfMonth());
            case 2 -> TEXTES.get(random.entre(0, TEXTES.size() - 1));
            default -> d.format(DateTimeFormatter.ofPattern(FORMATS.get(random.entre(0, FORMATS.size() - 1))));
        };
        return Optional.of(new Mutation(corrompue));
    }

    private static Optional<LocalDate> lire(Object valeur) {
        if (valeur instanceof LocalDate d) {
            return Optional.of(d);
        }
        if (valeur instanceof LocalDateTime dt) {
            return Optional.of(dt.toLocalDate());
        }
        if (valeur instanceof String s) {
            try {
                return Optional.of(LocalDate.parse(s.strip()));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
