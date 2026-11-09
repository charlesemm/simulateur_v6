package ci.cnam.simulator.domain.prestations.facture;

import ci.cnam.simulator.sink.EcrivainAnnexe;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ecrit les factures par lots JDBC (facture, puis lignes, puis historique), dans la transaction du lot d'evenements.
 * Rejouable : les identifiants sont deterministes et chaque insertion est {@code ON CONFLICT DO NOTHING}.
 */
@Component
public class FactureEcrivain implements EcrivainAnnexe<FactureEcrite> {

    private static final String FACTURE = """
            insert into "TB_ECHO_FACTURE" (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id, type_bon,
                                 statut, date_ouverture, date_finalisation, montant_total, part_cnam,
                                 part_assurance_complementaire, part_assure)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing""";

    private static final String LIGNE = """
            insert into "TB_ECHO_FACTURE_LIGNE" (id, run_id, facture_id, numero_ligne, code, designation, date_soin, quantite,
                                       prix_unitaire, montant, part_cnam, part_assurance_complementaire, part_assure)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing""";

    private static final String HISTORIQUE = """
            insert into "TB_ECHO_FACTURE_HISTORIQUE" (id, run_id, facture_id, ancien_statut, nouveau_statut, horodatage_simule,
                                            horodatage_reel)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing""";

    private final Clock horloge;

    public FactureEcrivain(Clock horloge) {
        this.horloge = horloge;
    }

    @Override
    public Class<FactureEcrite> type() {
        return FactureEcrite.class;
    }

    @Override
    public void ecrire(JdbcTemplate jdbc, List<FactureEcrite> lot) {
        List<Facture> factures = new ArrayList<>();
        List<FactureLigne> lignes = new ArrayList<>();
        List<Object[]> historique = new ArrayList<>();
        // Horodatage reel strictement croissant dans une facture (1 microseconde par transition) : le "dernier
        // statut" reste sans ambiguite meme si le pilote regroupe les insertions.
        OffsetDateTime reel = OffsetDateTime.now(horloge);
        for (FactureEcrite f : lot) {
            factures.add(f.facture());
            lignes.addAll(f.lignes());
            int rang = 0;
            for (FactureHistorique h : f.historique()) {
                historique.add(new Object[] {h.id(), h.runId(), h.factureId(),
                        h.ancienStatut() == null ? null : h.ancienStatut().name(), h.nouveauStatut().name(),
                        Timestamp.valueOf(h.horodatageSimule()), reel.plusNanos(1_000L * rang++)});
            }
        }
        jdbc.batchUpdate(FACTURE, factures, factures.size(), (ps, f) -> {
            ps.setObject(1, f.id());
            ps.setObject(2, f.runId());
            ps.setString(3, f.numeroFacture());
            ps.setObject(4, f.assureId());
            ps.setObject(5, f.beneficiaireId());
            ps.setObject(6, f.centreSanteId());
            ps.setString(7, f.typeBon().name());
            ps.setString(8, f.statut().name());
            ps.setTimestamp(9, Timestamp.valueOf(f.dateOuverture()));
            ps.setTimestamp(10, f.dateFinalisation() == null ? null : Timestamp.valueOf(f.dateFinalisation()));
            ps.setBigDecimal(11, f.montantTotal());
            ps.setBigDecimal(12, f.partCnam());
            ps.setBigDecimal(13, f.partAssuranceComplementaire());
            ps.setBigDecimal(14, f.partAssure());
        });
        if (!lignes.isEmpty()) {
            jdbc.batchUpdate(LIGNE, lignes, lignes.size(), (ps, l) -> {
                ps.setObject(1, l.id());
                ps.setObject(2, l.runId());
                ps.setObject(3, l.factureId());
                ps.setInt(4, l.numeroLigne());
                ps.setString(5, l.code());
                ps.setString(6, l.designation());
                ps.setTimestamp(7, Timestamp.valueOf(l.dateSoin()));
                ps.setInt(8, l.quantite());
                ps.setBigDecimal(9, l.prixUnitaire());
                ps.setBigDecimal(10, l.montant());
                ps.setBigDecimal(11, l.partCnam());
                ps.setBigDecimal(12, l.partAssuranceComplementaire());
                ps.setBigDecimal(13, l.partAssure());
            });
        }
        jdbc.batchUpdate(HISTORIQUE, historique, historique.size(), (ps, v) -> {
            for (int i = 0; i < v.length; i++) {
                ps.setObject(i + 1, v[i]);
            }
        });
    }
}
