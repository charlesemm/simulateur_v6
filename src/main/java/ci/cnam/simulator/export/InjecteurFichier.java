package ci.cnam.simulator.export;

import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.anomaly.config.AnomalyProfile;
import ci.cnam.simulator.anomaly.mutators.MutationFichier;
import ci.cnam.simulator.anomaly.mutators.MutatorFichier;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Pose les anomalies DE FICHIER sur la table des evenements au moment ou elle est ecrite dans l'export, et
 * inscrit chacune au corrige.
 *
 * <p>LA BASE N'EST PAS TOUCHEE, et c'est le point entier de ces deux anomalies : un encodage casse ou une
 * date ecrite autrement ne peuvent pas exister dans une colonne typee. La valeur en base reste la valeur
 * saine, c'est elle que le corrige donne en valeur d'origine, et seuls les octets telecharges sont pieges.
 *
 * <p>Reproductibilite : l'alea de chaque ligne est derive de son {@code event_id}, comme a la generation.
 * Deux telechargements du meme run donnent donc exactement les memes octets, donc la meme empreinte -- ce qui
 * est verifie ailleurs, et ce qu'un piege tire au hasard aurait casse.
 */
@Service
public class InjecteurFichier {

    /** Sel distinct de celui de l'injecteur de generation : les deux ne doivent pas tirer la meme suite. */
    private static final long SEL = 0x1F0A7C15L;

    private final Map<AnomalyType, MutatorFichier> mutators = new EnumMap<>(AnomalyType.class);

    public InjecteurFichier(List<MutatorFichier> mutators) {
        mutators.forEach(m -> this.mutators.put(m.type(), m));
    }

    /**
     * Enveloppe la table des evenements : les lignes continuent de defiler en flux, certaines piegees.
     *
     * @param profils taux par domaine, tels que le scenario du run les fixe
     * @param corrige recoit une ligne de verite terrain par anomalie posee
     * @return la meme table, lignes reecrites au passage ; la table d'origine si aucun taux n'est actif
     */
    public TableExport pieger(TableExport evenements, UUID runId, Map<String, AnomalyProfile> profils,
                               Consumer<AnomalyRecord> corrige) {
        if (mutators.isEmpty() || profils.values().stream().noneMatch(InjecteurFichier::actif)) {
            return evenements;
        }
        List<String> colonnes = evenements.colonnes();
        int iEvent = colonnes.indexOf("event_id");
        int iFacture = colonnes.indexOf("facture_id");
        int iDomaine = colonnes.indexOf("domaine");
        if (iEvent < 0 || iDomaine < 0) {
            return evenements;
        }
        TableExport.Parcours lignes = ligne -> evenements.lignes().pour(valeurs ->
                ligne.accept(piegerLigne(valeurs, colonnes, iEvent, iFacture, iDomaine, runId, profils, corrige)));
        return new TableExport(evenements.nom(), colonnes, lignes);
    }

    private List<String> piegerLigne(List<String> valeurs, List<String> colonnes, int iEvent, int iFacture,
                                      int iDomaine, UUID runId, Map<String, AnomalyProfile> profils,
                                      Consumer<AnomalyRecord> corrige) {
        AnomalyProfile profil = profils.get(valeurs.get(iDomaine));
        if (profil == null || !actif(profil)) {
            return valeurs;
        }
        UUID eventId = uuid(valeurs.get(iEvent));
        if (eventId == null) {
            return valeurs;
        }
        SeededRandom random = new SeededRandom(eventId.getMostSignificantBits() * 31
                + eventId.getLeastSignificantBits() + SEL);
        double total = total(profil);
        if (!random.chance(total)) {
            return valeurs;
        }
        MutatorFichier mutator = tirer(profil, total, random);
        int cible = colonnes.indexOf(mutator.colonne());
        if (cible < 0) {
            return valeurs;
        }
        MutationFichier mutation = mutator.muter(valeurs.get(cible), random).orElse(null);
        if (mutation == null) {
            return valeurs;
        }
        List<String> piegee = new ArrayList<>(valeurs);
        piegee.set(cible, mutation.cellule());
        corrige.accept(new AnomalyRecord(runId, eventId, iFacture < 0 ? null : uuid(valeurs.get(iFacture)),
                mutator.type(), mutator.champ(), mutation.valeurOrigine(), mutation.valeurCorrompue(), null));
        return piegee;
    }

    private MutatorFichier tirer(AnomalyProfile profil, double total, SeededRandom random) {
        double tirage = random.nextDouble() * total;
        MutatorFichier dernier = null;
        for (Map.Entry<AnomalyType, MutatorFichier> candidat : mutators.entrySet()) {
            double taux = profil.taux(candidat.getKey());
            if (taux <= 0) {
                continue;
            }
            dernier = candidat.getValue();
            tirage -= taux;
            if (tirage < 0) {
                return dernier;
            }
        }
        return dernier;
    }

    private double total(AnomalyProfile profil) {
        return mutators.keySet().stream().mapToDouble(profil::taux).sum();
    }

    private static boolean actif(AnomalyProfile profil) {
        return profil.taux().keySet().stream().anyMatch(AnomalyType::fichier);
    }

    private static UUID uuid(String texte) {
        try {
            return texte == null ? null : UUID.fromString(texte);
        } catch (IllegalArgumentException pasUnUuid) {
            return null;
        }
    }
}
