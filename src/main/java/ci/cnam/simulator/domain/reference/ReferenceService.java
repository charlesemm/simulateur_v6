package ci.cnam.simulator.domain.reference;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.catalog.Acte;
import ci.cnam.simulator.domain.reference.catalog.Acte.CategorieActe;
import ci.cnam.simulator.domain.reference.catalog.Catalogue;
import ci.cnam.simulator.domain.reference.catalog.Examen;
import ci.cnam.simulator.domain.reference.catalog.Examen.TypeExamen;
import ci.cnam.simulator.domain.reference.catalog.Medicament;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Genre;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import ci.cnam.simulator.domain.reference.model.Regime;
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
public class ReferenceService implements Catalogue {

    private record Instantane(List<Assure> assures, List<CentreSante> centres, List<Medecin> medecins,
                              Map<UUID, List<Medecin>> medecinsParCentre, List<Pharmacie> pharmacies,
                              Map<CategorieActe, List<Acte>> actes, Map<TypeExamen, List<Examen>> examens,
                              List<Medicament> medicaments) {
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

    @Override
    public Acte acteAleatoire(CategorieActe categorie, SeededRandom random) {
        return random.choisir(donnees().actes().get(categorie));
    }

    @Override
    public Examen examenAleatoire(TypeExamen type, SeededRandom random) {
        return random.choisir(donnees().examens().get(type));
    }

    @Override
    public Medicament medicamentAleatoire(SeededRandom random) {
        return random.choisir(donnees().medicaments());
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

    /**
     * Un code lu en base qui ne correspond a aucune valeur connue est une incoherence de schema, pas
     * une donnee a ignorer : les cles etrangeres de V19 garantissent qu'il appartient bien a la table
     * de valeurs, mais rien ne garantit que le simulateur sache le representer.
     */
    private static TypeCentreSante typeCentre(String code) {
        return TypeCentreSante.depuisCode(code).orElseThrow(() -> new IllegalStateException(
                "Type d'etablissement inconnu du simulateur : " + code));
    }

    private static Regime regime(String code) {
        return Regime.depuisCode(code).orElseThrow(() -> new IllegalStateException(
                "Regime inconnu du simulateur : " + code));
    }

    private Instantane charger() {
        List<Assure> assures = jdbc.query("""
                select id, numero_assure, nom, prenoms, date_naissance, genre, regime_code, statut_cotisation,
                       date_debut_droits, date_fin_droits
                from "TB_ECHO_ASSURE" order by numero_assure""",
                (rs, i) -> new Assure(rs.getObject("id", UUID.class), rs.getString("numero_assure"),
                        rs.getString("nom"), rs.getString("prenoms"),
                        rs.getObject("date_naissance", java.time.LocalDate.class),
                        Genre.valueOf(rs.getString("genre")), regime(rs.getString("regime_code")),
                        StatutCotisation.valueOf(rs.getString("statut_cotisation")),
                        rs.getObject("date_debut_droits", java.time.LocalDate.class),
                        rs.getObject("date_fin_droits", java.time.LocalDate.class)));
        List<CentreSante> centres = jdbc.query(
                "select id, code, nom, type_etablissement_code, departement_code from \"TB_ECHO_CENTRE_SANTE\" order by code",
                (rs, i) -> new CentreSante(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        typeCentre(rs.getString("type_etablissement_code")), rs.getString("departement_code")));
        List<Medecin> medecins = jdbc.query(
                "select id, code, nom, prenoms, specialite_medicale_code, centre_sante_id from \"TB_ECHO_MEDECIN\" order by code",
                (rs, i) -> new Medecin(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("prenoms"), rs.getString("specialite_medicale_code"),
                        rs.getObject("centre_sante_id", UUID.class)));
        List<Pharmacie> pharmacies = jdbc.query(
                "select id, code, nom, departement_code from \"TB_ECHO_PHARMACIE\" order by code",
                (rs, i) -> new Pharmacie(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("departement_code")));
        if (assures.isEmpty() || centres.isEmpty() || medecins.isEmpty() || pharmacies.isEmpty()) {
            throw new IllegalStateException("Referentiel vide : le remplissage au demarrage n'a pas eu lieu");
        }
        Map<UUID, List<Medecin>> parCentre = new HashMap<>();
        medecins.forEach(m -> parCentre.computeIfAbsent(m.centreSanteId(), k -> new java.util.ArrayList<>()).add(m));
        Map<CategorieActe, List<Acte>> actes = new java.util.EnumMap<>(CategorieActe.class);
        jdbc.query("select id, code, libelle, categorie, tarif from \"TB_ECHO_ACTE\" order by code",
                (rs, i) -> new Acte(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                        CategorieActe.valueOf(rs.getString("categorie")), rs.getBigDecimal("tarif")))
                .forEach(a -> actes.computeIfAbsent(a.categorie(), k -> new java.util.ArrayList<>()).add(a));
        Map<TypeExamen, List<Examen>> examens = new java.util.EnumMap<>(TypeExamen.class);
        jdbc.query("select id, code, libelle, type, tarif from \"TB_ECHO_EXAMEN\" order by code",
                (rs, i) -> new Examen(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("libelle"),
                        TypeExamen.valueOf(rs.getString("type")), rs.getBigDecimal("tarif")))
                .forEach(e -> examens.computeIfAbsent(e.type(), k -> new java.util.ArrayList<>()).add(e));
        List<Medicament> medicaments = jdbc.query("select id, code, libelle, tarif from \"TB_ECHO_MEDICAMENT\" order by code",
                (rs, i) -> new Medicament(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("libelle"), rs.getBigDecimal("tarif")));
        if (actes.isEmpty() || examens.isEmpty() || medicaments.isEmpty()) {
            throw new IllegalStateException("Catalogues vides : le remplissage au demarrage n'a pas eu lieu");
        }
        return new Instantane(assures, centres, medecins, parCentre, pharmacies, actes, examens, medicaments);
    }
}
