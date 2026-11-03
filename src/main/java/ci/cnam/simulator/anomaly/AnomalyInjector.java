package ci.cnam.simulator.anomaly;

import ci.cnam.simulator.anomaly.ChampsEligibles.ChampEligible;
import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.anomaly.mutators.DoublonExactMutator;
import ci.cnam.simulator.anomaly.mutators.Mutation;
import ci.cnam.simulator.anomaly.mutators.Mutator;
import ci.cnam.simulator.core.event.AnomalieTracee;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Decide quoi corrompre. Le generateur fournit toujours un evenement PROPRE ; l'injecteur le laisse tel quel ou
 * le remplace par sa version corrompue, avec la trace de la corruption (valeur d'origine comprise).
 *
 * <p>Reproductibilite : l'alea de chaque evenement est derive de son identifiant, lui-meme tire de la graine du
 * run. Meme graine, memes anomalies aux memes endroits ; et le flux propre ne change pas quand on modifie
 * les taux, puisque l'injecteur ne consomme jamais l'alea du generateur.
 *
 * <p>Regles : au plus une anomalie par evenement ; l'evenement est corrompu avec une probabilite egale au taux
 * global ; le type est tire selon les taux, parmi les types qui ont un champ present dans cet evenement.
 * Un champ n'est jamais choisi s'il est absent ou null. Les cles (factureId, assureId...) ne sont declarees
 * eligibles par aucun domaine : elles restent intactes.
 */
@Component
public class AnomalyInjector {

    private static final long SEL = 0x5DEECE66DL;

    private final Map<AnomalyType, Mutator> mutators = new EnumMap<>(AnomalyType.class);
    private final DoublonExactMutator doublon;
    private final Map<String, ChampsEligibles> champsParDomaine;

    public AnomalyInjector(List<Mutator> mutators, DoublonExactMutator doublon, List<ChampsEligibles> champs) {
        mutators.forEach(m -> this.mutators.put(m.type(), m));
        this.doublon = doublon;
        this.champsParDomaine = champs.stream().collect(Collectors.toMap(ChampsEligibles::domaine, Function.identity()));
    }

    /**
     * @return l'evenement propre, ou sa version corrompue, ou l'original suivi de son doublon
     */
    public List<SimEvent> injecter(SimEvent propre, AnomalyProfile profil) {
        if (!profil.actif()) {
            return List.of(propre);
        }
        SeededRandom random = new SeededRandom(propre.eventId().getMostSignificantBits() * 31
                + propre.eventId().getLeastSignificantBits() + SEL);
        if (!random.chance(profil.tauxGlobal())) {
            return List.of(propre);
        }

        ChampsEligibles declaration = champsParDomaine.get(propre.domaine());
        Map<AnomalyType, List<ChampEligible>> candidats = new EnumMap<>(AnomalyType.class);
        double total = 0;
        for (AnomalyType type : AnomalyType.values()) {
            double taux = profil.taux(type);
            if (taux <= 0) {
                continue;
            }
            List<ChampEligible> presents = type == AnomalyType.DOUBLON_EXACT ? List.of()
                    : presents(declaration, type, propre);
            if (type == AnomalyType.DOUBLON_EXACT || !presents.isEmpty()) {
                candidats.put(type, presents);
                total += taux;
            }
        }
        if (candidats.isEmpty()) {
            return List.of(propre);
        }

        AnomalyType type = tirerType(candidats, profil, total, random);
        if (type == AnomalyType.DOUBLON_EXACT) {
            return List.of(propre, doublon.muter(propre, random));
        }
        ChampEligible champ = tirerChamp(candidats.get(type), random);
        Object origine = propre.payload().get(champ.nom());
        return mutators.get(type).muter(origine, random)
                .map(mutation -> List.of(corrompre(propre, type, champ.nom(), origine, mutation)))
                .orElse(List.of(propre));
    }

    private List<ChampEligible> presents(ChampsEligibles declaration, AnomalyType type, SimEvent e) {
        if (declaration == null || !mutators.containsKey(type)) {
            return List.of();
        }
        List<ChampEligible> presents = new ArrayList<>();
        for (ChampEligible champ : declaration.champs(type)) {
            if (e.payload().get(champ.nom()) != null) {
                presents.add(champ);
            }
        }
        return presents;
    }

    private static AnomalyType tirerType(Map<AnomalyType, List<ChampEligible>> candidats, AnomalyProfile profil,
                                         double total, SeededRandom random) {
        double tirage = random.nextDouble() * total;
        AnomalyType dernier = null;
        for (AnomalyType type : candidats.keySet()) {
            dernier = type;
            tirage -= profil.taux(type);
            if (tirage < 0) {
                return type;
            }
        }
        return dernier;
    }

    private static ChampEligible tirerChamp(List<ChampEligible> champs, SeededRandom random) {
        int poidsTotal = champs.stream().mapToInt(ChampEligible::poids).sum();
        int tirage = random.entre(0, poidsTotal - 1);
        for (ChampEligible champ : champs) {
            tirage -= champ.poids();
            if (tirage < 0) {
                return champ;
            }
        }
        return champs.get(champs.size() - 1);
    }

    private static SimEvent corrompre(SimEvent propre, AnomalyType type, String champ, Object origine,
                                      Mutation mutation) {
        Map<String, Object> payload = new LinkedHashMap<>(propre.payload());
        payload.put(champ, mutation.valeur());
        AnomalieTracee trace = new AnomalieTracee(type.name(), champ, texte(origine), texte(mutation.valeur()), null);
        return new SimEvent(propre.eventId(), propre.runId(), propre.domaine(), propre.type(),
                propre.timestampSimule(), payload, List.of(trace));
    }

    private static String texte(Object valeur) {
        if (valeur == null) {
            return null;
        }
        return valeur instanceof BigDecimal b ? b.toPlainString() : valeur.toString();
    }
}
