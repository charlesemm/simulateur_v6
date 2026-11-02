package ci.cnam.simulator.domain.reference.seed;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.ReferenceProperties;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.AyantDroit;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Remplit la base du referentiel fictif au demarrage, une seule fois : rien n'est fait si la table assure
 * contient deja des lignes. Insertion par lots, dans une seule transaction (tout ou rien).
 */
@Component
public class ReferenceSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSeeder.class);
    private static final int TAILLE_LOT = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ReferenceProperties proprietes;

    public ReferenceSeeder(JdbcTemplate jdbc, TransactionTemplate transaction, ReferenceProperties proprietes) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.proprietes = proprietes;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existants = jdbc.queryForObject("select count(*) from assure", Integer.class);
        if (existants != null && existants > 0) {
            log.info("Referentiel deja present ({} assures) : aucun remplissage", existants);
            return;
        }
        transaction.executeWithoutResult(status -> remplir(proprietes.nbAssures(), proprietes.seed()));
    }

    void remplir(int nbAssures, long seed) {
        long debut = System.currentTimeMillis();
        // Deux flux d'alea : les assures ne dependent pas du reste, quel que soit le volume demande.
        ReferenceGenerator personnes = new ReferenceGenerator(new SeededRandom(seed));
        ReferenceGenerator structures = new ReferenceGenerator(new SeededRandom(seed + 1));

        var actes = structures.actes();
        var examens = structures.examens();
        var medicaments = structures.medicaments();
        List<CentreSante> centres = structures.centres(Math.max(10, nbAssures / 500));
        List<Medecin> medecins = structures.medecins(centres);
        List<Pharmacie> pharmacies = structures.pharmacies(Math.max(5, nbAssures / 2000));
        List<Assure> assures = personnes.assures(nbAssures);
        List<AyantDroit> ayantsDroit = personnes.ayantsDroit(assures);

        lot("insert into acte (id, code, libelle, categorie, tarif) values (?, ?, ?, ?, ?)", actes,
                (ps, a) -> { ps.setObject(1, a.id()); ps.setString(2, a.code()); ps.setString(3, a.libelle());
                    ps.setString(4, a.categorie().name()); ps.setBigDecimal(5, a.tarif()); });
        lot("insert into examen (id, code, libelle, type, tarif) values (?, ?, ?, ?, ?)", examens,
                (ps, e) -> { ps.setObject(1, e.id()); ps.setString(2, e.code()); ps.setString(3, e.libelle());
                    ps.setString(4, e.type().name()); ps.setBigDecimal(5, e.tarif()); });
        lot("insert into medicament (id, code, libelle, tarif) values (?, ?, ?, ?)", medicaments,
                (ps, m) -> { ps.setObject(1, m.id()); ps.setString(2, m.code()); ps.setString(3, m.libelle());
                    ps.setBigDecimal(4, m.tarif()); });
        lot("insert into centre_sante (id, code, nom, type, ville) values (?, ?, ?, ?, ?)", centres,
                (ps, c) -> { ps.setObject(1, c.id()); ps.setString(2, c.code()); ps.setString(3, c.nom());
                    ps.setString(4, c.type().name()); ps.setString(5, c.ville()); });
        lot("insert into medecin (id, code, nom, prenoms, specialite, centre_sante_id) values (?, ?, ?, ?, ?, ?)", medecins,
                (ps, m) -> { ps.setObject(1, m.id()); ps.setString(2, m.code()); ps.setString(3, m.nom());
                    ps.setString(4, m.prenoms()); ps.setString(5, m.specialite()); ps.setObject(6, m.centreSanteId()); });
        lot("insert into pharmacie (id, code, nom, ville) values (?, ?, ?, ?)", pharmacies,
                (ps, p) -> { ps.setObject(1, p.id()); ps.setString(2, p.code()); ps.setString(3, p.nom());
                    ps.setString(4, p.ville()); });
        lot("""
                insert into assure (id, numero_assure, nom, prenoms, date_naissance, genre, statut_cotisation,
                                    date_debut_droits, date_fin_droits) values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", assures,
                (ps, a) -> { ps.setObject(1, a.id()); ps.setString(2, a.numeroAssure()); ps.setString(3, a.nom());
                    ps.setString(4, a.prenoms()); ps.setObject(5, a.dateNaissance()); ps.setString(6, a.genre().name());
                    ps.setString(7, a.statutCotisation().name()); ps.setObject(8, a.dateDebutDroits());
                    ps.setObject(9, a.dateFinDroits()); });
        lot("""
                insert into ayant_droit (id, assure_id, nom, prenoms, date_naissance, genre, lien_parente)
                values (?, ?, ?, ?, ?, ?, ?)""", ayantsDroit,
                (ps, a) -> { ps.setObject(1, a.id()); ps.setObject(2, a.assureId()); ps.setString(3, a.nom());
                    ps.setString(4, a.prenoms()); ps.setObject(5, a.dateNaissance()); ps.setString(6, a.genre().name());
                    ps.setString(7, a.lienParente().name()); });

        log.info("Referentiel fictif cree en {} ms : {} assures, {} ayants droit, {} centres, {} medecins, {} pharmacies (graine {})",
                System.currentTimeMillis() - debut, assures.size(), ayantsDroit.size(), centres.size(), medecins.size(),
                pharmacies.size(), seed);
    }

    private <T> void lot(String sql, List<T> lignes, org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<T> setter) {
        jdbc.batchUpdate(sql, lignes, TAILLE_LOT, setter);
    }
}
