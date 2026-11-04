package ci.cnam.simulator.sink;

/**
 * Ce qu'un parcours doit ecrire en base EN PLUS de ses evenements (ex. une facture, ses lignes, son historique).
 * Elle voyage dans la meme file que les evenements et est validee dans la MEME transaction que le lot qui la contient :
 * soit tout est ecrit, soit rien. L'{@link EcrivainAnnexe} du type correspondant sait l'ecrire, par lots.
 */
public interface EcritureAnnexe {
}
