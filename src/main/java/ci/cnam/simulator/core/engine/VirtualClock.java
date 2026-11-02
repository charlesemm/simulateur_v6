package ci.cnam.simulator.core.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.LongSupplier;

/**
 * Horloge virtuelle : le temps SIMULE s'ecoule {@code facteur} fois plus vite que le temps REEL ACTIF
 * (x1, x10, x100...). La pause fige le temps simule ; la reprise repart de ce point.
 * C'est un {@link Clock} standard : les etapes l'utilisent comme n'importe quelle horloge.
 * Le temps reel (date d'ecriture) reste une notion distincte, portee par l'horloge systeme.
 */
public final class VirtualClock extends Clock {

    private final Instant origine;
    private final double facteur;
    private final LongSupplier sourceNanos;
    private final ZoneId zone;

    private long cumulNanos;
    private long reprisA;
    private boolean enMarche;

    public VirtualClock(Instant origineSimulee, double facteur) {
        this(origineSimulee, facteur, System::nanoTime);
    }

    /** Source de temps injectable (tests : horloge manuelle). */
    public VirtualClock(Instant origineSimulee, double facteur, LongSupplier sourceNanos) {
        if (facteur <= 0) {
            throw new IllegalArgumentException("Le facteur d'acceleration doit etre positif : " + facteur);
        }
        this.origine = origineSimulee;
        this.facteur = facteur;
        this.sourceNanos = sourceNanos;
        this.zone = ZoneOffset.UTC;
    }

    public double facteur() {
        return facteur;
    }

    /** Demarre ou reprend l'ecoulement du temps. Sans effet s'il est deja en marche. */
    public synchronized void demarrer() {
        if (!enMarche) {
            reprisA = sourceNanos.getAsLong();
            enMarche = true;
        }
    }

    public void reprendre() {
        demarrer();
    }

    /** Fige le temps (pause ou arret). Sans effet s'il est deja fige. */
    public synchronized void pause() {
        if (enMarche) {
            cumulNanos += sourceNanos.getAsLong() - reprisA;
            enMarche = false;
        }
    }

    public synchronized boolean enMarche() {
        return enMarche;
    }

    /** Temps reel ecoule PENDANT que l'horloge tournait (la pause ne compte pas). */
    public synchronized Duration tempsActif() {
        long total = cumulNanos + (enMarche ? sourceNanos.getAsLong() - reprisA : 0);
        return Duration.ofNanos(total);
    }

    /** Instant simule correspondant a un temps reel actif donne. */
    public Instant instantSimule(Duration tempsActif) {
        return origine.plusNanos(Math.round(tempsActif.toNanos() * facteur));
    }

    /** Horloge figee a l'instant simule correspondant a un temps reel actif : sert a dater un parcours. */
    public Clock figeeA(Duration tempsActif) {
        return Clock.fixed(instantSimule(tempsActif), zone);
    }

    @Override
    public Instant instant() {
        return instantSimule(tempsActif());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId nouvelleZone) {
        VirtualClock source = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return nouvelleZone;
            }

            @Override
            public Clock withZone(ZoneId autre) {
                return source.withZone(autre);
            }

            @Override
            public Instant instant() {
                return source.instant();
            }
        };
    }
}
