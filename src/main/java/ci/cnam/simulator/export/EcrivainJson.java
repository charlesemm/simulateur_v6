package ci.cnam.simulator.export;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Un objet racine, un tableau par table, un objet par ligne. Ecrit au fil de l'eau par un
 * {@link JsonGenerator} : aucun arbre d'objets n'est construit.
 *
 * <p>Toutes les valeurs sont des chaines, ou {@code null} quand la base n'a rien : le json dit donc
 * exactement la meme chose que le csv et le xlsx. Le champ {@code payload} des evenements est le
 * texte du json stocke en base, pas un objet imbrique.
 */
@Component
public class EcrivainJson implements EcrivainExport {

    private static final JsonFactory FABRIQUE = JsonFactory.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)   // le flux appartient a la reponse
            .build();

    @Override
    public FormatExport format() {
        return FormatExport.JSON;
    }

    @Override
    public void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException {
        try (JsonGenerator json = FABRIQUE.createGenerator(sortie)) {
            json.writeStartObject();
            for (TableExport table : tables) {
                json.writeArrayFieldStart(table.nom());
                List<String> colonnes = table.colonnes();
                table.lignes().pour(valeurs -> {
                    try {
                        json.writeStartObject();
                        for (int i = 0; i < colonnes.size(); i++) {
                            String valeur = valeurs.get(i);
                            if (valeur == null) {
                                json.writeNullField(colonnes.get(i));
                            } else {
                                json.writeStringField(colonnes.get(i), valeur);
                            }
                        }
                        json.writeEndObject();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                json.writeEndArray();
            }
            json.writeEndObject();
        }
    }
}
