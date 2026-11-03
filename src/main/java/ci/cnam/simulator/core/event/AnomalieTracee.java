package ci.cnam.simulator.core.event;

import java.util.UUID;

/**
 * Trace d'une anomalie injectee dans un evenement : la verite terrain. Elle voyage avec l'evenement jusqu'a
 * l'ecriture en base, ou elle est enregistree dans la meme transaction (jamais d'anomalie sans son evenement).
 *
 * @param type             nom du type d'anomalie (ex. "FAUTE_ORTHOGRAPHE")
 * @param champ            champ corrompu ; null pour un doublon (l'evenement entier est concerne)
 * @param valeurOrigine    valeur propre avant corruption, en texte ; null pour un doublon
 * @param valeurCorrompue  valeur ecrite a la place, en texte ; null si le champ a ete mis a null
 * @param doublonDe        evenement d'origine, pour un doublon seulement
 */
public record AnomalieTracee(String type, String champ, String valeurOrigine, String valeurCorrompue,
                             UUID doublonDe) {
}
