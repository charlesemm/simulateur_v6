package ci.cnam.simulator.domain.reference;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Genre;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import ci.cnam.simulator.domain.reference.model.TypeCentreSante;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Acces au referentiel pour les generateurs : tirages au hasard pilotes par le SeededRandom de l'appelant.
 * Le referentiel est lu une fois en memoire (quelques dizaines de milliers de lignes) au premier tirage.
 */
@Service
public class ReferenceService {

    private record Instantane(List<Assure> assures, List<CentreSante> centres, List<Medecin> medecins,
                              Map<UUID, List<Medecin>> medecinsParCentre, List<Pharmacie> pharmacies) {
    }

    private final JdbcTemplate jdbc;
    private volatile Instantane instantane;

    public ReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Assure assureAleatoire(SeededRandom random) {
        return random.choisir(donnees().assures());
    }

    public CentreSante centreAleatoire(SeededRandom random) {
        return random.choisir(donnees().centres());
    }

    public Medecin medecinAleatoire(SeededRandom random) {
        return random.choisir(donnees().medecins());
    }

    /** Medecin exercant dans l'etablissement donne (chaque etablissement en compte au moins un). */
    public Medecin medecinAleatoire(CentreSante centre, SeededRandom random) {
        List<Medecin> medecins = donnees().medecinsParCentre().get(centre.id());
        if (medecins == null || medecins.isEmpty()) {
            throw new IllegalStateException("Aucun medecin dans l'etablissement " + centre.code());
        }
        return random.choisir(medecins);
    }

    public Pharmacie pharmacieAleatoire(SeededRandom random) {
        return random.choisir(donnees().pharmacies());
    }

    /** Oublie la copie en memoire (apres un nouveau remplissage de la base). */
    public void recharger() {
        instantane = null;
    }

    private Instantane donnees() {
        Instantane courant = instantane;
        if (courant == null) {
            synchronized (this) {
                courant = instantane;
                if (courant == null) {
                    courant = charger();
                    instantane = courant;
                }
            }
        }
        return courant;
    }

    private Instantane charger() {
        List<Assure> assures = jdbc.query("""
                select id, numero_assure, nom, prenoms, date_naissance, genre, statut_cotisation,
                       date_debut_droits, date_fin_droits
                from assure order by numero_assure""",
                (rs, i) -> new Assure(rs.getObject("id", UUID.class), rs.getString("numero_assure"),
                        rs.getString("nom"), rs.getString("prenoms"),
                        rs.getObject("date_naissance", java.time.LocalDate.class),
                        Genre.valueOf(rs.getString("genre")), StatutCotisation.valueOf(rs.getString("statut_cotisation")),
                        rs.getObject("date_debut_droits", java.time.LocalDate.class),
                        rs.getObject("date_fin_droits", java.time.LocalDate.class)));
        List<CentreSante> centres = jdbc.query("select id, code, nom, type, ville from centre_sante order by code",
                (rs, i) -> new CentreSante(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        TypeCentreSante.valueOf(rs.getString("type")), rs.getString("ville")));
        List<Medecin> medecins = jdbc.query(
                "select id, code, nom, prenoms, specialite, centre_sante_id from medecin order by code",
                (rs, i) -> new Medecin(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("prenoms"), rs.getString("specialite"), rs.getObject("centre_sante_id", UUID.class)));
        List<Pharmacie> pharmacies = jdbc.query("select id, code, nom, ville from pharmacie order by code",
                (rs, i) -> new Pharmacie(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("ville")));
        if (assures.isEmpty() || centres.isEmpty() || medecins.isEmpty() || pharmacies.isEmpty()) {
            throw new IllegalStateException("Referentiel vide : le remplissage au demarrage n'a pas eu lieu");
        }
        Map<UUID, List<Medecin>> parCentre = new HashMap<>();
        medecins.forEach(m -> parCentre.computeIfAbsent(m.centreSanteId(), k -> new java.util.ArrayList<>()).add(m));
        return new Instantane(assures, centres, medecins, parCentre, pharmacies);
    }
}
