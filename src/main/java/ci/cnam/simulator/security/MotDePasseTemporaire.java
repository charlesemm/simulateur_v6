package ci.cnam.simulator.security;

import java.security.SecureRandom;

/**
 * Fabrique un mot de passe temporaire remis a la personne concernee.
 *
 * <p>L'alea vient de {@link SecureRandom} et non de {@code SeededRandom} : il ne s'agit pas d'une donnee de
 * simulation, elle ne doit surtout pas etre reproductible.</p>
 *
 * <p>L'alphabet ecarte les caracteres qui se confondent a l'oral ou a la lecture (0/O, 1/l/I) : le mot de passe
 * est souvent dicte au telephone ou recopie d'un courriel.</p>
 */
public final class MotDePasseTemporaire {

    /** 16 caracteres sur 56 symboles : largement au-dessus du minimum de 12 impose aux comptes. */
    static final int LONGUEUR = 16;

    private static final char[] ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom ALEA = new SecureRandom();

    private MotDePasseTemporaire() {
    }

    /** Groupe par 4 avec des tirets (xxxx-xxxx-xxxx-xxxx) : plus facile a dicter et a recopier sans erreur. */
    public static String generer() {
        StringBuilder sb = new StringBuilder(LONGUEUR + 3);
        for (int i = 0; i < LONGUEUR; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET[ALEA.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
