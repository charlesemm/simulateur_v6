package ci.cnam.simulator.domain.prestations.droits;

import ci.cnam.simulator.domain.prestations.droits.ResultatDroits.MotifRefus;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Un soin est pris en charge si les droits sont ouverts a la date de soin ET les cotisations sont a jour. */
@Service
public class ControleDroitsService {

    public ResultatDroits controler(Assure assure, LocalDate dateSoin) {
        boolean ouverts = !dateSoin.isBefore(assure.dateDebutDroits())
                && (assure.dateFinDroits() == null || !dateSoin.isAfter(assure.dateFinDroits()));
        if (!ouverts) {
            return ResultatDroits.refuse(MotifRefus.DROITS_FERMES);
        }
        if (assure.statutCotisation() != StatutCotisation.A_JOUR) {
            return ResultatDroits.refuse(MotifRefus.COTISATIONS_EN_RETARD);
        }
        return ResultatDroits.accepte();
    }
}
