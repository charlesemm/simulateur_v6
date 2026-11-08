package ci.cnam.simulator.persistence.repository;

import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.export.FormatExport;
import ci.cnam.simulator.export.JeuStore;
import ci.cnam.simulator.export.TableExport;
import ci.cnam.simulator.export.Telechargement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecture des tables metier pour l'export, PAR PAQUETS et sur une cle stable (pagination par cle,
 * jamais {@code offset}, jamais un {@code findAll()} de 100 000 lignes) : la memoire ne retient
 * qu'un paquet, et l'ordre ne depend d'aucun horodatage reel.
 *
 * <p>Toutes les valeurs sortent en texte, mises en forme par PostgreSQL : les dates en ISO, les
 * horodatages reels ramenes en UTC, les montants tels qu'ils sont stockes. L'empreinte d'un export
 * ne depend donc ni du fuseau de la session, ni de la locale de la machine.
 *
 * <p>La cle de tri est toujours la PREMIERE colonne selectionnee : son texte sert de borne au
 * paquet suivant.
 */
@Repository
public class ExportRepository implements JeuStore {

    /** Lignes lues par aller-retour. Au-dela on gagne peu et on retient plus de memoire. */
    static final int TAILLE_LOT = 1_000;

    /** Horodatage simule : pas de fuseau en base, rendu tel quel. */
    private static final String SIMULE = "'YYYY-MM-DD\"T\"HH24:MI:SS.US'";

    /** Horodatage reel : ramene en UTC et suffixe Z, pour que deux lectures donnent le meme texte. */
    private static final String REEL = "'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'";

    private final JdbcTemplate jdbc;

    public ExportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TableExport assures(UUID runId) {
        return table("assures",
                List.of("id", "numero_assure", "nom", "prenoms", "date_naissance", "genre", "statut_cotisation",
                        "date_debut_droits", "date_fin_droits"),
                "select a.id::text, a.numero_assure, a.nom, a.prenoms,"
                        + " to_char(a.date_naissance, 'YYYY-MM-DD'), a.genre, a.statut_cotisation,"
                        + " to_char(a.date_debut_droits, 'YYYY-MM-DD'), to_char(a.date_fin_droits, 'YYYY-MM-DD')"
                        + " from assure a"
                        + " where exists (select 1 from facture f where f.assure_id = a.id and f.run_id = ?)",
                "a.id", "uuid", runId);
    }

    @Override
    public TableExport factures(UUID runId) {
        return table("factures",
                List.of("id", "numero_facture", "assure_id", "beneficiaire_id", "centre_sante_id", "type_bon",
                        "statut", "date_ouverture", "date_finalisation", "montant_total", "part_cnam",
                        "part_assurance_complementaire", "part_assure"),
                "select f.id::text, f.numero_facture, f.assure_id::text, f.beneficiaire_id::text,"
                        + " f.centre_sante_id::text, f.type_bon, f.statut,"
                        + " to_char(f.date_ouverture, " + SIMULE + "), to_char(f.date_finalisation, " + SIMULE + "),"
                        + " f.montant_total::text, f.part_cnam::text,"
                        + " f.part_assurance_complementaire::text, f.part_assure::text"
                        + " from facture f where f.run_id = ?",
                "f.id", "uuid", runId);
    }

    @Override
    public TableExport factureLignes(UUID runId) {
        return table("facture_lignes",
                List.of("id", "facture_id", "numero_ligne", "code", "designation", "date_soin", "quantite",
                        "prix_unitaire", "montant", "part_cnam", "part_assurance_complementaire", "part_assure"),
                "select l.id::text, l.facture_id::text, l.numero_ligne::text, l.code, l.designation,"
                        + " to_char(l.date_soin, " + SIMULE + "), l.quantite::text, l.prix_unitaire::text,"
                        + " l.montant::text, l.part_cnam::text, l.part_assurance_complementaire::text,"
                        + " l.part_assure::text"
                        + " from facture_ligne l where l.run_id = ?",
                "l.id", "uuid", runId);
    }

    @Override
    public TableExport evenements(UUID runId) {
        return table("evenements",
                List.of("event_id", "facture_id", "domaine", "type", "timestamp_simule", "timestamp_reel", "statut",
                        "payload"),
                "select e.event_id::text, e.facture_id::text, e.domaine, e.type,"
                        + " to_char(e.timestamp_simule, " + SIMULE + "),"
                        + " to_char(e.timestamp_reel at time zone 'UTC', " + REEL + "),"
                        + " e.statut, e.payload::text"
                        + " from evenement e where e.run_id = ?",
                "e.event_id", "uuid", runId);
    }

    @Override
    public TableExport corrige(UUID runId) {
        return table("corrige",
                List.of("id", "event_id", "facture_id", "type", "champ", "valeur_origine", "valeur_corrompue",
                        "doublon_de"),
                "select a.id::text, a.event_id::text, a.facture_id::text, a.type, a.champ, a.valeur_origine,"
                        + " a.valeur_corrompue, a.doublon_de::text"
                        + " from anomaly_injected a where a.run_id = ?",
                "a.id", "bigint", runId);
    }

    @Override
    public void tracer(UUID id, UUID runId, FormatExport format, long nombreLignes, String empreinte) {
        jdbc.update("insert into export_fichier (id, run_id, format, nombre_lignes, empreinte) values (?, ?, ?, ?, ?)",
                id, runId, format.name(), nombreLignes, empreinte);
    }

    @Override
    public void tracerAnomalies(List<AnomalyRecord> anomalies) {
        if (anomalies.isEmpty()) {
            return;
        }
        // ON CONFLICT DO NOTHING : retelecharger le meme export rejoue les memes pieges (meme alea, derive de
        // event_id), donc les memes lignes de corrige. On les ecrit une fois, pas une par telechargement.
        jdbc.batchUpdate("""
                insert into anomaly_injected
                    (run_id, event_id, facture_id, type, champ, valeur_origine, valeur_corrompue)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing""",
                anomalies, anomalies.size(), (ps, a) -> {
                    ps.setObject(1, a.runId());
                    ps.setObject(2, a.eventId());
                    ps.setObject(3, a.factureId());
                    ps.setString(4, a.type().name());
                    ps.setString(5, a.champ());
                    ps.setString(6, a.valeurOrigine());
                    ps.setString(7, a.valeurCorrompue());
                });
    }

    @Override
    public List<Telechargement> telechargements(UUID runId) {
        return jdbc.query("""
                select id, format, date_creation, nombre_lignes, empreinte
                from export_fichier where run_id = ? order by date_creation desc, id""",
                (rs, i) -> new Telechargement(
                        rs.getObject("id", UUID.class),
                        FormatExport.valueOf(rs.getString("format")),
                        rs.getObject("date_creation", OffsetDateTime.class).toInstant(),
                        rs.getLong("nombre_lignes"),
                        rs.getString("empreinte").strip()),
                runId);
    }

    private TableExport table(String nom, List<String> colonnes, String select, String cle, String typeCle,
                              UUID runId) {
        String fin = " order by " + cle + " limit " + TAILLE_LOT;
        String premier = select + fin;
        String suivant = select + " and " + cle + " > ?::" + typeCle + fin;
        return new TableExport(nom, colonnes,
                ligne -> parcourir(premier, suivant, runId, colonnes.size(), ligne));
    }

    private void parcourir(String premier, String suivant, UUID runId, int largeur, Consumer<List<String>> ligne) {
        String borne = null;
        while (true) {
            List<List<String>> lot = borne == null
                    ? jdbc.query(premier, (rs, i) -> lire(rs, largeur), runId)
                    : jdbc.query(suivant, (rs, i) -> lire(rs, largeur), runId, borne);
            lot.forEach(ligne);
            if (lot.size() < TAILLE_LOT) {
                return;
            }
            borne = lot.get(lot.size() - 1).get(0);
        }
    }

    private static List<String> lire(ResultSet rs, int largeur) throws SQLException {
        String[] valeurs = new String[largeur];
        for (int i = 0; i < largeur; i++) {
            valeurs[i] = rs.getString(i + 1);
        }
        return Arrays.asList(valeurs);
    }
}
