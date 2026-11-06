package ci.cnam.simulator.web;

import static org.assertj.core.api.Assertions.assertThat;

import ci.cnam.simulator.core.event.EvenementLu;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvenementsCsvTest {

    @Test
    @DisplayName("Une cellule avec virgule, guillemet ou retour a la ligne est entouree de guillemets (RFC 4180)")
    void guillemets() {
        assertThat(EvenementsCsv.cellule("simple")).isEqualTo("simple");
        assertThat(EvenementsCsv.cellule("a,b")).isEqualTo("\"a,b\"");
        assertThat(EvenementsCsv.cellule("dit \"oui\"")).isEqualTo("\"dit \"\"oui\"\"\"");
        assertThat(EvenementsCsv.cellule("l1\nl2")).isEqualTo("\"l1\nl2\"");
        assertThat(EvenementsCsv.cellule(null)).isEmpty();
    }

    @Test
    @DisplayName("Une cellule qui commence par = + - @ est neutralisee : jamais lue comme une formule")
    void neutraliseLesFormules() {
        assertThat(EvenementsCsv.cellule("=CMD()")).isEqualTo("'=CMD()");
        assertThat(EvenementsCsv.cellule("+1")).isEqualTo("'+1");
        assertThat(EvenementsCsv.cellule("-2")).isEqualTo("'-2");
        assertThat(EvenementsCsv.cellule("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(EvenementsCsv.cellule("\tx")).isEqualTo("'\tx");
        assertThat(EvenementsCsv.cellule("normal=ok")).isEqualTo("normal=ok");
    }

    @Test
    @DisplayName("Une ligne d'evenement : 9 colonnes, payload en JSON entre guillemets")
    void ligneEvenement() throws IOException {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID run = UUID.fromString("22222222-2222-2222-2222-222222222222");
        EvenementLu e = new EvenementLu(id, run, null, "prestations", "FACTURE_OUVERTE",
                LocalDateTime.parse("2026-03-10T08:00:00"), Instant.parse("2026-11-05T10:00:00Z"), "ECRIT",
                Map.of("nom", "=EVIL"));
        StringWriter sortie = new StringWriter();

        EvenementsCsv.entete(sortie);
        EvenementsCsv.ecrire(sortie, e, o -> "{\"nom\":\"=EVIL\"}");

        String[] lignes = sortie.toString().split("\r\n");
        assertThat(lignes).hasSize(2);
        assertThat(lignes[0]).isEqualTo(
                "event_id,run_id,facture_id,domaine,type,timestamp_simule,timestamp_reel,statut,payload");
        assertThat(lignes[1]).startsWith(id + "," + run + ",,prestations,FACTURE_OUVERTE,2026-03-10T08:00,")
                .endsWith(",ECRIT,\"{\"\"nom\"\":\"\"=EVIL\"\"}\"");
    }
}
