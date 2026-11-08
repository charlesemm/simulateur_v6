package ci.cnam.simulator.export;

import java.util.List;
import java.util.function.Consumer;

/**
 * Une table de l'export : son nom (onglet xlsx, fichier csv, cle json), ses colonnes METIER
 * (le marquage est ajoute par {@link ExportService}) et un parcours EN FLUX de ses lignes.
 *
 * <p>Toutes les valeurs sont du texte, deliberement : les trois formats ecrivent alors exactement la
 * meme chose, et deux telechargements donnent la meme empreinte. Une valeur nulle en base reste nulle
 * ici ; chaque ecrivain decide comment la rendre (cellule vide, {@code null} json).
 */
public record TableExport(String nom, List<String> colonnes, Parcours lignes) {

    /** Parcours en flux : la table pousse ses lignes une par une, rien n'est accumule. */
    public interface Parcours {
        void pour(Consumer<List<String>> ligne);
    }
}
