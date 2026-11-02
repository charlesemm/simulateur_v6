package ci.cnam.simulator.domain.reference.seed;

import ci.cnam.simulator.domain.reference.catalog.Acte.CategorieActe;
import ci.cnam.simulator.domain.reference.catalog.Examen.TypeExamen;
import java.util.List;

/** Libelles et tarifs fictifs (FCFA) des catalogues. Les tarifs reels sont fixes par les directions metier. */
final class DonneesCatalogue {

    record LigneActe(String code, String libelle, CategorieActe categorie, long tarif) {
    }

    record LigneExamen(String code, String libelle, TypeExamen type, long tarif) {
    }

    static final List<LigneActe> ACTES = List.of(
            new LigneActe("ACT-001", "Consultation generaliste", CategorieActe.CONSULTATION, 5000),
            new LigneActe("ACT-002", "Consultation specialiste", CategorieActe.CONSULTATION, 10000),
            new LigneActe("ACT-003", "Consultation de suivi", CategorieActe.CONSULTATION, 3000),
            new LigneActe("ACT-004", "Consultation pediatrique", CategorieActe.CONSULTATION, 5000),
            new LigneActe("ACT-005", "Consultation prenatale", CategorieActe.CONSULTATION, 6000),
            new LigneActe("ACT-101", "Journee d'hospitalisation en medecine", CategorieActe.HOSPITALISATION, 25000),
            new LigneActe("ACT-102", "Journee d'hospitalisation en chirurgie", CategorieActe.HOSPITALISATION, 40000),
            new LigneActe("ACT-103", "Accouchement normal", CategorieActe.HOSPITALISATION, 60000),
            new LigneActe("ACT-104", "Intervention chirurgicale mineure", CategorieActe.HOSPITALISATION, 85000),
            new LigneActe("ACT-201", "Detartrage", CategorieActe.DENTAIRE, 8000),
            new LigneActe("ACT-202", "Extraction dentaire", CategorieActe.DENTAIRE, 12000),
            new LigneActe("ACT-203", "Soin de carie", CategorieActe.DENTAIRE, 10000),
            new LigneActe("ACT-204", "Couronne dentaire", CategorieActe.DENTAIRE, 75000));

    static final List<LigneExamen> EXAMENS = List.of(
            new LigneExamen("EXA-B01", "Numeration formule sanguine", TypeExamen.BIOLOGIE, 4500),
            new LigneExamen("EXA-B02", "Glycemie a jeun", TypeExamen.BIOLOGIE, 2000),
            new LigneExamen("EXA-B03", "Goutte epaisse (paludisme)", TypeExamen.BIOLOGIE, 2500),
            new LigneExamen("EXA-B04", "Creatininemie", TypeExamen.BIOLOGIE, 3000),
            new LigneExamen("EXA-B05", "Bilan lipidique", TypeExamen.BIOLOGIE, 9000),
            new LigneExamen("EXA-B06", "Examen cytobacteriologique des urines", TypeExamen.BIOLOGIE, 6000),
            new LigneExamen("EXA-B07", "Serologie VIH", TypeExamen.BIOLOGIE, 3500),
            new LigneExamen("EXA-B08", "Test de grossesse", TypeExamen.BIOLOGIE, 1500),
            new LigneExamen("EXA-I01", "Radiographie du thorax", TypeExamen.IMAGERIE, 12000),
            new LigneExamen("EXA-I02", "Echographie abdominale", TypeExamen.IMAGERIE, 15000),
            new LigneExamen("EXA-I03", "Echographie obstetricale", TypeExamen.IMAGERIE, 15000),
            new LigneExamen("EXA-I04", "Scanner cerebral", TypeExamen.IMAGERIE, 80000),
            new LigneExamen("EXA-I05", "Radiographie osseuse", TypeExamen.IMAGERIE, 10000),
            new LigneExamen("EXA-I06", "Electrocardiogramme", TypeExamen.IMAGERIE, 8000));

    static final List<String> MEDICAMENTS = List.of(
            "Paracetamol 500 mg comprime", "Paracetamol 1 g comprime", "Ibuprofene 400 mg comprime",
            "Amoxicilline 500 mg gelule", "Amoxicilline-acide clavulanique 1 g comprime",
            "Artemether-lumefantrine comprime", "Artesunate injectable", "Metronidazole 500 mg comprime",
            "Ciprofloxacine 500 mg comprime", "Cotrimoxazole 480 mg comprime", "Azithromycine 250 mg comprime",
            "Omeprazole 20 mg gelule", "Sels de rehydratation orale sachet", "Zinc 20 mg comprime dispersible",
            "Salbutamol aerosol-doseur", "Prednisolone 20 mg comprime", "Loratadine 10 mg comprime",
            "Amlodipine 5 mg comprime", "Captopril 25 mg comprime", "Hydrochlorothiazide 25 mg comprime",
            "Metformine 850 mg comprime", "Glibenclamide 5 mg comprime", "Atorvastatine 20 mg comprime",
            "Acide folique 5 mg comprime", "Sulfate ferreux 200 mg comprime", "Vitamine C 500 mg comprime",
            "Albendazole 400 mg comprime", "Mebendazole 100 mg comprime", "Fluconazole 150 mg gelule",
            "Diclofenac 50 mg comprime", "Tramadol 50 mg gelule", "Serum glucose 5 % perfusion",
            "Serum sale isotonique perfusion", "Ceftriaxone 1 g injectable", "Gentamicine 80 mg injectable",
            "Diazepam 5 mg comprime", "Chlorhexidine solution antiseptique", "Collyre antibiotique",
            "Pommade a l'oxyde de zinc", "Sirop antitussif");

    static final List<String> SPECIALITES = List.of(
            "Medecine generale", "Pediatrie", "Gynecologie-obstetrique", "Chirurgie generale", "Cardiologie",
            "Chirurgie dentaire", "Ophtalmologie", "Radiologie", "Biologie medicale", "Medecine interne");

    static final List<String> VILLES = List.of(
            "Abidjan", "Bouake", "Daloa", "Yamoussoukro", "San-Pedro", "Korhogo", "Man", "Abengourou", "Gagnoa",
            "Divo", "Odienne", "Bondoukou", "Ferkessedougou", "Sassandra", "Dimbokro");

    private DonneesCatalogue() {
    }
}
