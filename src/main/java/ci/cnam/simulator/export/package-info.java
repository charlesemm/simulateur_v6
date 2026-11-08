/**
 * Export complet d'une simulation terminee, en xlsx, csv (archive zip) et json.
 *
 * <p>Quatre tables, toujours dans le meme ordre : {@code assures} (seulement ceux concernes par la
 * simulation), {@code factures}, {@code facture_lignes}, {@code evenements}. Aucune table de lignes
 * exportees n'existe : on interroge les tables metier.
 *
 * <p>Deux regles non negociables :
 * <ul>
 *   <li><b>marquage</b> : chaque ligne porte {@code donnee_fictive = OUI} et la reference de la
 *       simulation, ajoutes par {@link ExportService} en un seul endroit, sans aucun reglage ;</li>
 *   <li><b>le corrige ne part jamais</b> : {@code anomaly_injected} a son propre telechargement,
 *       reserve aux roles ADMIN et OPERATEUR.</li>
 * </ul>
 */
package ci.cnam.simulator.export;
