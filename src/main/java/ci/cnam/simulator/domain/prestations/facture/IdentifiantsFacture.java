package ci.cnam.simulator.domain.prestations.facture;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Identifiants derives (jamais UUID.randomUUID) : meme run et meme numero, meme identifiant. */
final class IdentifiantsFacture {

    private IdentifiantsFacture() {
    }

    static UUID de(String nature, UUID parent, String cle) {
        return UUID.nameUUIDFromBytes((nature + "/" + parent + "/" + cle).getBytes(StandardCharsets.UTF_8));
    }
}
