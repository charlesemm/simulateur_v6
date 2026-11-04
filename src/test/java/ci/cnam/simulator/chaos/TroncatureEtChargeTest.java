package ci.cnam.simulator.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.persistence.IndisponibiliteBase;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientConnectionException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class TroncatureEtChargeTest {

    private static SimEvent evenement(int i) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("factureId", new UUID(1, i).toString());
        payload.put("nom", "KOUASSI");
        payload.put("prenoms", "Awa");
        payload.put("numeroAssure", "1234567890");
        payload.put("dateNaissance", "1990-01-01");
        return new SimEvent(UUID.nameUUIDFromBytes(("e" + i).getBytes()), new UUID(0, 1), "prestations", "TEST",
                LocalDateTime.of(2026, 1, 1, 8, 0), payload, null);
    }

    @Test
    void sansChaosLEvenementPasseInchange() {
        TroncatureMessages troncature = new TroncatureMessages();
        SimEvent e = evenement(1);

        assertThat(troncature.appliquer(e)).isSameAs(e);
    }

    @Test
    void laTroncatureToucheLaProportionDemandeeEtGardeLeLienAvecLaFacture() {
        TroncatureMessages troncature = new TroncatureMessages();
        troncature.activer(0.25);
        int touches = 0;
        for (int i = 0; i < 4000; i++) {
            SimEvent e = evenement(i);
            SimEvent sortie = troncature.appliquer(e);
            if (sortie != e) {
                touches++;
                assertThat(sortie.payload()).containsKey("factureId");
                assertThat(sortie.payload().size()).isLessThan(e.payload().size());
                assertThat(sortie.eventId()).isEqualTo(e.eventId());   // le modele d'evenement ne change pas
            }
        }

        assertThat(touches).isBetween(850, 1150);   // ~25 %
        assertThat(troncature.tronques()).isEqualTo(touches);
    }

    @Test
    void lesMemesEvenementsSontTouchesAChaqueFois() {
        TroncatureMessages a = new TroncatureMessages();
        TroncatureMessages b = new TroncatureMessages();
        a.activer(0.3);
        b.activer(0.3);

        for (int i = 0; i < 500; i++) {
            assertThat(a.appliquer(evenement(i)).payload()).isEqualTo(b.appliquer(evenement(i)).payload());
        }
    }

    @Test
    void desactiverRendLeCompteEtArreteLaTroncature() {
        TroncatureMessages troncature = new TroncatureMessages();
        troncature.activer(1.0);
        troncature.appliquer(evenement(1));

        assertThat(troncature.desactiver()).isEqualTo(1);
        assertThat(troncature.actif()).isFalse();
        SimEvent e = evenement(2);
        assertThat(troncature.appliquer(e)).isSameAs(e);
    }

    @Test
    void leMultiplicateurN_accepteQueLesPaliersPrevus() {
        LoadMultiplier charge = new LoadMultiplier();
        assertThat(charge.facteur()).isEqualTo(1.0);

        for (int palier : new int[] {5, 20, 100}) {
            charge.fixer(palier);
            assertThat(charge.facteur()).isEqualTo(palier);
            assertThat(charge.actif()).isTrue();
        }
        assertThatThrownBy(() -> charge.fixer(7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> charge.fixer(1000)).isInstanceOf(IllegalArgumentException.class);
        charge.retablir();
        assertThat(charge.facteur()).isEqualTo(1.0);
        assertThat(charge.actif()).isFalse();
    }

    @Test
    void uneLiaisonCoupeeEstUneIndisponibiliteUneContrainteViolee_non() {
        assertThat(IndisponibiliteBase.estCausee(new CannotGetJdbcConnectionException("pool vide",
                new SQLTransientConnectionException("timeout")))).isTrue();
        assertThat(IndisponibiliteBase.estCausee(new RuntimeException("enveloppe",
                new SQLException("This connection has been closed.", "08003")))).isTrue();
        assertThat(IndisponibiliteBase.estCausee(new RuntimeException(new SocketException("Connection reset")))).isTrue();
        assertThat(IndisponibiliteBase.estCausee(new SQLException("admin shutdown", "57P01"))).isTrue();

        assertThat(IndisponibiliteBase.estCausee(new DataIntegrityViolationException("fk",
                new SQLIntegrityConstraintViolationException("fk", "23503")))).isFalse();
        assertThat(IndisponibiliteBase.estCausee(new SQLException("syntaxe", "42601"))).isFalse();
        assertThat(IndisponibiliteBase.estCausee(new IllegalStateException("autre"))).isFalse();
    }
}
