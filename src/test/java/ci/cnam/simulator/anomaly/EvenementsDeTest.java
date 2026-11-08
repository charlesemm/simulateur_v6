package ci.cnam.simulator.anomaly;

import ci.cnam.simulator.anomaly.mutators.CaracteresSpeciauxMutator;
import ci.cnam.simulator.anomaly.mutators.CasseIncoherenteMutator;
import ci.cnam.simulator.anomaly.mutators.ChampVideMutator;
import ci.cnam.simulator.anomaly.mutators.DoublonApproximatifMutator;
import ci.cnam.simulator.anomaly.mutators.DoublonExactMutator;
import ci.cnam.simulator.anomaly.mutators.EspacesParasitesMutator;
import ci.cnam.simulator.anomaly.mutators.FauteOrthographeMutator;
import ci.cnam.simulator.anomaly.mutators.FormatDateInvalideMutator;
import ci.cnam.simulator.anomaly.mutators.FormatIdentifiantInvalideMutator;
import ci.cnam.simulator.anomaly.mutators.FormatNumeriqueInvalideMutator;
import ci.cnam.simulator.anomaly.mutators.IncoherenceCroiseeMutator;
import ci.cnam.simulator.anomaly.mutators.IncoherenceTemporelleMutator;
import ci.cnam.simulator.anomaly.mutators.ReferenceBriseeMutator;
import ci.cnam.simulator.anomaly.mutators.ValeurHorsListeMutator;
import ci.cnam.simulator.anomaly.mutators.ValeurNegativeMutator;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.prestations.anomalies.PrestationAnomalyProfile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Evenements propres, de la forme de ceux du domaine prestations, et injecteur reel (vrais mutators). */
public final class EvenementsDeTest {

    private static final List<String> NOMS = List.of("Kouassi", "Traore", "Yao", "N'Guessan", "Konan", "Diallo");
    private static final List<String> PRENOMS = List.of("Awa", "Moussa", "Marie-Claire", "Jean Baptiste", "Fatou");
    private static final List<String> TYPES = List.of("PATIENT_ARRIVE", "FACTURE_OUVERTE", "CONSULTATION_REALISEE",
            "MEDICAMENTS_DELIVRES", "FACTURE_FINALISEE");

    private EvenementsDeTest() {
    }

    public static AnomalyInjector injecteur() {
        return new AnomalyInjector(List.of(new ChampVideMutator(), new FauteOrthographeMutator(),
                new EspacesParasitesMutator(), new CasseIncoherenteMutator(), new CaracteresSpeciauxMutator(),
                new FormatDateInvalideMutator(), new FormatNumeriqueInvalideMutator(),
                new FormatIdentifiantInvalideMutator(), new ValeurNegativeMutator(), new ValeurHorsListeMutator(),
                new ReferenceBriseeMutator()),
                List.of(new DoublonExactMutator(), new DoublonApproximatifMutator()),
                List.of(new IncoherenceTemporelleMutator(), new IncoherenceCroiseeMutator()),
                List.of(new PrestationAnomalyProfile()));
    }

    /** {@code n} evenements propres, reproductibles : meme graine, memes evenements. */
    public static List<SimEvent> propres(UUID run, long graine, int n) {
        SeededRandom random = new SeededRandom(graine);
        List<SimEvent> evenements = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("factureId", random.nextUuid());
            payload.put("assureId", random.nextUuid());
            payload.put("centreSanteId", random.nextUuid());
            payload.put("numeroAssure", "%010d".formatted(random.entre(0, 999_999_999)));
            payload.put("nom", random.choisir(NOMS));
            payload.put("prenoms", random.choisir(PRENOMS));
            // Contexte temporel, comme le parcours le pose : c'est contre lui que se jugent les incoherences.
            // Valeurs fixes, aucun tirage : les evenements des tests precedents ne bougent pas.
            payload.put("dateSoin", "2026-03-10");
            payload.put("dateDebutDroits", "2024-01-01");
            payload.put("dateFinDroits", "2026-12-31");
            payload.put("droitsOuverts", true);
            payload.put("medecinId", new UUID(9, i));
            String type = TYPES.get(i % TYPES.size());
            if (type.equals("PATIENT_ARRIVE")) {
                payload.put("dateNaissance", "19%02d-%02d-%02d".formatted(random.entre(50, 99), random.entre(1, 12),
                        random.entre(1, 28)));
                payload.put("genre", random.chance(0.5) ? "M" : "F");
            } else if (type.equals("FACTURE_OUVERTE")) {
                payload.put("numeroFacture", "FS-2026-%06d".formatted(i));
                payload.put("typeBon", "AMBULATOIRE");
            } else if (type.equals("MEDICAMENTS_DELIVRES")) {
                payload.put("pharmacieId", random.nextUuid());
                payload.put("quantite", random.entre(1, 6));
            }
            evenements.add(new SimEvent(random.nextUuid(), run, "prestations", type,
                    LocalDateTime.of(2026, 3, 10, 8, 0).plusMinutes(i), payload, List.of()));
        }
        return evenements;
    }
}
