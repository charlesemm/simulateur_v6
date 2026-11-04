package ci.cnam.simulator.persistence;

import java.io.EOFException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Distingue une base INDISPONIBLE (liaison coupee, connexion reinitialisee, pool sans connexion) d'une vraie erreur
 * (contrainte violee, SQL invalide). La premiere se rejoue apres retablissement ; la seconde ne se rejoue jamais.
 */
public final class IndisponibiliteBase {

    private IndisponibiliteBase() {
    }

    public static boolean estCausee(Throwable erreur) {
        int garde = 0;   // protege des chaines de causes circulaires
        for (Throwable t = erreur; t != null && garde < 20; t = t.getCause(), garde++) {
            if (t instanceof CannotGetJdbcConnectionException
                    || t instanceof CannotCreateTransactionException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof TransientDataAccessException
                    || t instanceof SQLTransientException
                    || t instanceof SQLNonTransientConnectionException
                    || t instanceof SQLRecoverableException
                    || t instanceof SocketException
                    || t instanceof EOFException
                    || t instanceof InterruptedIOException) {
                return true;
            }
            if (t instanceof org.hibernate.TransactionException || messageConnexionFermee(t)) {
                return true;   // rollback ou commit impossible : la connexion est morte
            }
            if (t instanceof SQLException sql && etatConnexion(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageConnexionFermee(Throwable t) {
        String m = t.getMessage();
        return t instanceof SQLException && m != null && (m.contains("Connection is closed") || m.contains("has been closed"));
    }

    /** 08xxx : connexion ; 57P0x : arret ou redemarrage du serveur ; 53300 : trop de connexions. */
    private static boolean etatConnexion(String etat) {
        return etat != null && (etat.startsWith("08") || etat.startsWith("57P0") || etat.equals("53300"));
    }
}
