package ci.cnam.simulator.domain.reference.model;

/**
 * Numero d'assure : 8 chiffres + cle de controle sur 2 chiffres, cle = 97 - (8 chiffres modulo 97).
 * Hypothese de travail (format reel du numero a confirmer, voir cahier des charges section 15).
 */
public final class NumeroAssure {

    public static final int BASE_MAX_EXCLUE = 100_000_000;

    private NumeroAssure() {
    }

    public static String depuisBase(int base) {
        if (base < 0 || base >= BASE_MAX_EXCLUE) {
            throw new IllegalArgumentException("base hors limites : " + base);
        }
        return "%08d%02d".formatted(base, 97 - (base % 97));
    }

    public static boolean estValide(String numero) {
        if (numero == null || !numero.matches("[0-9]{10}")) {
            return false;
        }
        int base = Integer.parseInt(numero.substring(0, 8));
        int cle = Integer.parseInt(numero.substring(8));
        return cle == 97 - (base % 97);
    }
}
