package ci.cnam.simulator.domain.reference.seed;

import ci.cnam.simulator.core.random.SeededRandom;
import ci.cnam.simulator.domain.reference.catalog.Acte;
import ci.cnam.simulator.domain.reference.catalog.Examen;
import ci.cnam.simulator.domain.reference.catalog.Medicament;
import ci.cnam.simulator.domain.reference.model.Assure;
import ci.cnam.simulator.domain.reference.model.AyantDroit;
import ci.cnam.simulator.domain.reference.model.CentreSante;
import ci.cnam.simulator.domain.reference.model.Genre;
import ci.cnam.simulator.domain.reference.model.LienParente;
import ci.cnam.simulator.domain.reference.model.Medecin;
import ci.cnam.simulator.domain.reference.model.NumeroAssure;
import ci.cnam.simulator.domain.reference.model.Pharmacie;
import ci.cnam.simulator.domain.reference.model.StatutCotisation;
import ci.cnam.simulator.domain.reference.model.TypeCentreSante;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.datafaker.Faker;

/**
 * Fabrique les donnees fictives du referentiel, sans toucher a la base.
 * Tout tirage passe par le SeededRandom recu : meme graine, memes donnees.
 */
public class ReferenceGenerator {

    /** Date fixe (et non la date du jour) pour que les donnees restent reproductibles d'un jour a l'autre. */
    static final LocalDate DATE_REFERENCE = LocalDate.of(2026, 1, 1);

    private static final double PART_EN_RETARD = 0.08;
    private static final double PART_DROITS_EXPIRES = 0.10;

    private final SeededRandom random;
    private final Faker faker;

    public ReferenceGenerator(SeededRandom random) {
        this.random = random;
        this.faker = new Faker(Locale.FRENCH, random);
    }

    public List<Acte> actes() {
        return DonneesCatalogue.ACTES.stream()
                .map(a -> new Acte(random.nextUuid(), a.code(), a.libelle(), a.categorie(), montant(a.tarif())))
                .toList();
    }

    public List<Examen> examens() {
        return DonneesCatalogue.EXAMENS.stream()
                .map(e -> new Examen(random.nextUuid(), e.code(), e.libelle(), e.type(), montant(e.tarif())))
                .toList();
    }

    public List<Medicament> medicaments() {
        List<Medicament> liste = new ArrayList<>();
        int i = 1;
        for (String libelle : DonneesCatalogue.MEDICAMENTS) {
            long tarif = random.entre(12, 600) * 25L; // 300 a 15 000 FCFA, par pas de 25
            liste.add(new Medicament(random.nextUuid(), "MED-%03d".formatted(i++), libelle, montant(tarif)));
        }
        return liste;
    }

    public List<CentreSante> centres(int nb) {
        List<CentreSante> liste = new ArrayList<>(nb);
        for (int i = 1; i <= nb; i++) {
            String ville = random.choisir(DonneesCatalogue.VILLES);
            TypeCentreSante type = typeCentre();
            String nom = switch (type) {
                case HOPITAL -> "Hopital general de " + ville + " " + i;
                case CLINIQUE -> "Clinique " + faker.name().lastName() + " " + i;
                case CENTRE_SANTE -> "Centre de sante de " + ville + " " + i;
                case CMR -> "CMR de " + ville + " " + i;
            };
            liste.add(new CentreSante(random.nextUuid(), "CS-%05d".formatted(i), nom, type, ville));
        }
        return liste;
    }

    public List<Medecin> medecins(List<CentreSante> centres) {
        List<Medecin> liste = new ArrayList<>();
        int i = 1;
        for (CentreSante centre : centres) {
            int nb = random.entre(3, 8);
            for (int k = 0; k < nb; k++) {
                liste.add(new Medecin(random.nextUuid(), "DR-%06d".formatted(i++), nom(), prenoms(),
                        random.choisir(DonneesCatalogue.SPECIALITES), centre.id()));
            }
        }
        return liste;
    }

    public List<Pharmacie> pharmacies(int nb) {
        List<Pharmacie> liste = new ArrayList<>(nb);
        for (int i = 1; i <= nb; i++) {
            liste.add(new Pharmacie(random.nextUuid(), "PH-%05d".formatted(i),
                    "Pharmacie " + faker.name().lastName() + " " + i, random.choisir(DonneesCatalogue.VILLES)));
        }
        return liste;
    }

    public List<Assure> assures(int nb) {
        List<Assure> liste = new ArrayList<>(nb);
        Set<String> numeros = new HashSet<>(nb * 2);
        for (int i = 0; i < nb; i++) {
            String numero;
            do {
                numero = NumeroAssure.depuisBase(random.nextInt(NumeroAssure.BASE_MAX_EXCLUE));
            } while (!numeros.add(numero));

            LocalDate debut = DATE_REFERENCE.minusDays(random.entre(30, 3650));
            // Droits clos avant la date de reference pour une petite part des assures, sinon sans date de fin.
            LocalDate fin = random.chance(PART_DROITS_EXPIRES)
                    ? debut.plusDays(random.entre(1, (int) ChronoUnit.DAYS.between(debut, DATE_REFERENCE)))
                    : null;
            StatutCotisation statut = random.chance(PART_EN_RETARD) ? StatutCotisation.EN_RETARD : StatutCotisation.A_JOUR;
            liste.add(new Assure(random.nextUuid(), numero, nom(), prenoms(),
                    DATE_REFERENCE.minusYears(random.entre(18, 70)).minusDays(random.entre(0, 364)),
                    genre(), statut, debut, fin));
        }
        return liste;
    }

    public List<AyantDroit> ayantsDroit(List<Assure> assures) {
        List<AyantDroit> liste = new ArrayList<>();
        for (Assure assure : assures) {
            if (random.chance(0.4)) {
                liste.add(new AyantDroit(random.nextUuid(), assure.id(), assure.nom(), prenoms(),
                        assure.dateNaissance().plusYears(random.entre(-5, 5)),
                        assure.genre() == Genre.M ? Genre.F : Genre.M, LienParente.CONJOINT));
            }
            int enfants = random.entre(0, 3);
            for (int k = 0; k < enfants; k++) {
                liste.add(new AyantDroit(random.nextUuid(), assure.id(), assure.nom(), prenoms(),
                        DATE_REFERENCE.minusYears(random.entre(0, 17)).minusDays(random.entre(0, 364)), genre(),
                        LienParente.ENFANT));
            }
        }
        return liste;
    }

    private TypeCentreSante typeCentre() {
        double tirage = random.nextDouble();
        if (tirage < 0.10) {
            return TypeCentreSante.HOPITAL;
        }
        if (tirage < 0.30) {
            return TypeCentreSante.CLINIQUE;
        }
        return tirage < 0.90 ? TypeCentreSante.CENTRE_SANTE : TypeCentreSante.CMR;
    }

    private Genre genre() {
        return random.nextBoolean() ? Genre.M : Genre.F;
    }

    private String nom() {
        return faker.name().lastName().toUpperCase(Locale.ROOT);
    }

    private String prenoms() {
        return faker.name().firstName();
    }

    private static BigDecimal montant(long fcfa) {
        return BigDecimal.valueOf(fcfa).setScale(2);
    }
}
