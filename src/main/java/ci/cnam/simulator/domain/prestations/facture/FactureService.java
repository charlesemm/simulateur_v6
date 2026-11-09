package ci.cnam.simulator.domain.prestations.facture;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creation et suivi des factures : ouverture, refus direct, lignes, transitions de statut et historique. */
@Service
public class FactureService implements GestionFacture {

    private static final RowMapper<Facture> FACTURE = (rs, i) -> new Facture(
            rs.getObject("id", UUID.class),
            rs.getObject("run_id", UUID.class),
            rs.getString("numero_facture"),
            rs.getObject("assure_id", UUID.class),
            rs.getObject("beneficiaire_id", UUID.class),
            rs.getObject("centre_sante_id", UUID.class),
            TypeBon.valueOf(rs.getString("type_bon")),
            FactureStatut.valueOf(rs.getString("statut")),
            rs.getObject("date_ouverture", LocalDateTime.class),
            rs.getObject("date_finalisation", LocalDateTime.class),
            rs.getBigDecimal("montant_total"),
            rs.getBigDecimal("part_cnam"),
            rs.getBigDecimal("part_assurance_complementaire"),
            rs.getBigDecimal("part_assure"));

    private static final RowMapper<FactureLigne> LIGNE = (rs, i) -> new FactureLigne(
            rs.getObject("id", UUID.class),
            rs.getObject("run_id", UUID.class),
            rs.getObject("facture_id", UUID.class),
            rs.getInt("numero_ligne"),
            rs.getString("code"),
            rs.getString("designation"),
            rs.getObject("date_soin", LocalDateTime.class),
            rs.getInt("quantite"),
            rs.getBigDecimal("prix_unitaire"),
            rs.getBigDecimal("montant"),
            rs.getBigDecimal("part_cnam"),
            rs.getBigDecimal("part_assurance_complementaire"),
            rs.getBigDecimal("part_assure"));

    private static final RowMapper<FactureHistorique> HISTORIQUE = (rs, i) -> {
        String ancien = rs.getString("ancien_statut");
        return new FactureHistorique(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("facture_id", UUID.class),
                ancien == null ? null : FactureStatut.valueOf(ancien),
                FactureStatut.valueOf(rs.getString("nouveau_statut")),
                rs.getObject("horodatage_simule", LocalDateTime.class),
                rs.getObject("horodatage_reel", OffsetDateTime.class));
    };

    private final JdbcTemplate jdbc;

    public FactureService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ouvre une facture au statut OUVERTE. */
    @Transactional
    public Facture ouvrir(NouvelleFacture demande) {
        return creer(demande, FactureStatut.OUVERTE);
    }

    /** Cree directement une facture REFUSEE (droits ou cotisations en defaut) : etat terminal. */
    @Transactional
    public Facture refuser(NouvelleFacture demande) {
        return creer(demande, FactureStatut.REFUSEE);
    }

    private Facture creer(NouvelleFacture d, FactureStatut statut) {
        Long sequence = jdbc.queryForObject("select nextval('seq_numero_facture')", Long.class);
        String numero = "FAC-%04d-%06d".formatted(d.dateOuverture().getYear(), sequence);
        // Identifiant derive (jamais UUID.randomUUID) : meme run et meme numero, meme identifiant.
        UUID id = identifiant("facture", d.runId(), numero);
        jdbc.update("""
                insert into "TB_ECHO_FACTURE" (id, run_id, numero_facture, assure_id, beneficiaire_id, centre_sante_id,
                                     type_bon, statut, date_ouverture)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, d.runId(), numero, d.assureId(), d.beneficiaireId(), d.centreSanteId(),
                d.typeBon().name(), statut.name(), Timestamp.valueOf(d.dateOuverture()));
        historiser(id, d.runId(), null, statut, d.dateOuverture());
        return trouver(id);
    }

    /** Ajoute une ligne et met a jour les totaux de la facture (simples sommes des lignes). */
    @Transactional
    public FactureLigne ajouterLigne(UUID factureId, NouvelleLigne l) {
        Facture facture = verrouiller(factureId);
        if (facture.statut().estTerminal()) {
            throw new IllegalStateException("Facture " + facture.numeroFacture() + " au statut terminal "
                    + facture.statut() + " : ligne refusee");
        }
        Integer numero = jdbc.queryForObject(
                "select coalesce(max(numero_ligne), 0) + 1 from \"TB_ECHO_FACTURE_LIGNE\" where facture_id = ?",
                Integer.class, factureId);
        UUID id = identifiant("ligne", factureId, String.valueOf(numero));
        jdbc.update("""
                insert into "TB_ECHO_FACTURE_LIGNE" (id, run_id, facture_id, numero_ligne, code, designation, date_soin,
                                           quantite, prix_unitaire, montant, part_cnam,
                                           part_assurance_complementaire, part_assure)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, facture.runId(), factureId, numero, l.code(), l.designation(), Timestamp.valueOf(l.dateSoin()),
                l.quantite(), l.prixUnitaire(), l.montant(), l.partCnam(), l.partAssuranceComplementaire(),
                l.partAssure());
        jdbc.update("""
                update "TB_ECHO_FACTURE" set
                    montant_total = montant_total + ?,
                    part_cnam = part_cnam + ?,
                    part_assurance_complementaire = part_assurance_complementaire + ?,
                    part_assure = part_assure + ?
                where id = ?""",
                l.montant(), l.partCnam(), l.partAssuranceComplementaire(), l.partAssure(), factureId);
        return jdbc.queryForObject("select * from \"TB_ECHO_FACTURE_LIGNE\" where id = ?", LIGNE, id);
    }

    /**
     * Change le statut si la transition est autorisee, sinon leve {@link TransitionInterditeException}
     * (rien n'est ecrit). La transition est enregistree dans l'historique.
     */
    @Transactional
    public Facture changerStatut(UUID factureId, FactureStatut nouveau, LocalDateTime dateSimulee) {
        Facture facture = verrouiller(factureId);
        Facture modifiee = facture.avecStatut(nouveau, dateSimulee);
        jdbc.update("update \"TB_ECHO_FACTURE\" set statut = ?, date_finalisation = ? where id = ?",
                nouveau.name(),
                modifiee.dateFinalisation() == null ? null : Timestamp.valueOf(modifiee.dateFinalisation()),
                factureId);
        historiser(factureId, facture.runId(), facture.statut(), nouveau, dateSimulee);
        return modifiee;
    }

    public Facture trouver(UUID factureId) {
        List<Facture> resultat = jdbc.query("select * from \"TB_ECHO_FACTURE\" where id = ?", FACTURE, factureId);
        if (resultat.isEmpty()) {
            throw new IllegalArgumentException("Facture inconnue : " + factureId);
        }
        return resultat.get(0);
    }

    public List<FactureLigne> lignes(UUID factureId) {
        return jdbc.query("select * from \"TB_ECHO_FACTURE_LIGNE\" where facture_id = ? order by numero_ligne", LIGNE, factureId);
    }

    public List<FactureHistorique> historique(UUID factureId) {
        return jdbc.query("select * from \"TB_ECHO_FACTURE_HISTORIQUE\" where facture_id = ? order by horodatage_reel, id",
                HISTORIQUE, factureId);
    }

    private Facture verrouiller(UUID factureId) {
        List<Facture> resultat = jdbc.query("select * from \"TB_ECHO_FACTURE\" where id = ? for update", FACTURE, factureId);
        if (resultat.isEmpty()) {
            throw new IllegalArgumentException("Facture inconnue : " + factureId);
        }
        return resultat.get(0);
    }

    private void historiser(UUID factureId, UUID runId, FactureStatut ancien, FactureStatut nouveau,
                            LocalDateTime dateSimulee) {
        Integer rang = jdbc.queryForObject("select count(*) + 1 from \"TB_ECHO_FACTURE_HISTORIQUE\" where facture_id = ?",
                Integer.class, factureId);
        jdbc.update("""
                insert into "TB_ECHO_FACTURE_HISTORIQUE" (id, run_id, facture_id, ancien_statut, nouveau_statut,
                                                horodatage_simule)
                values (?, ?, ?, ?, ?, ?)""",
                identifiant("historique", factureId, String.valueOf(rang)), runId, factureId,
                ancien == null ? null : ancien.name(), nouveau.name(), Timestamp.valueOf(dateSimulee));
    }

    private static UUID identifiant(String nature, UUID parent, String cle) {
        return IdentifiantsFacture.de(nature, parent, cle);
    }
}
