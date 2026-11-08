package ci.cnam.simulator.export;

import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.core.engine.RunInfo;
import ci.cnam.simulator.core.engine.RunStore;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sert un export et en garde la trace. Le seul endroit ou le marquage de donnee fictive est pose,
 * et il n'y a aucun moyen de le retirer : ni parametre, ni reglage, ni en-tete de requete.
 *
 * <p>L'empreinte est calculee sur les octets qui partent reellement, par un
 * {@link DigestOutputStream} intercale entre l'ecrivain et la reponse.
 */
@Service
public class ExportService {

    /** Colonnes de tete de CHAQUE ligne de CHAQUE table. Non desactivables (cahier 2.2). */
    public static final List<String> COLONNES_MARQUAGE = List.of("donnee_fictive", "simulation");

    /** Valeur de la premiere colonne. Jamais autre chose : rien de ce qui sort d'Echo n'est reel. */
    public static final String MARQUE = "OUI";

    private final JeuSimulation jeu;
    private final JeuStore store;
    private final RunStore runs;
    private final InjecteurFichier pieges;
    private final ProfilsAnomalies profils;
    private final Map<FormatExport, EcrivainExport> ecrivains;

    public ExportService(JeuSimulation jeu, JeuStore store, RunStore runs, InjecteurFichier pieges,
                         ProfilsAnomalies profils, List<EcrivainExport> ecrivains) {
        this.jeu = jeu;
        this.store = store;
        this.runs = runs;
        this.pieges = pieges;
        this.profils = profils;
        this.ecrivains = ecrivains.stream()
                .collect(Collectors.toUnmodifiableMap(EcrivainExport::format, e -> e));
    }

    /**
     * Ecrit le jeu a analyser (assures concernes, factures, lignes, evenements) dans le flux donne,
     * puis inscrit le telechargement dans {@code export_fichier}.
     *
     * @throws IllegalArgumentException      run inconnu
     * @throws ExportIndisponibleException   run pas encore termine
     */
    @Transactional
    public ResultatExport exporter(UUID runId, FormatExport format, OutputStream sortie) throws IOException {
        RunInfo run = termine(runId);
        // Les deux anomalies de fichier sont posees ICI, a l'ecriture, et inscrites au corrige dans la MEME
        // transaction que la trace du telechargement. La base, elle, reste propre.
        List<AnomalyRecord> corrige = new ArrayList<>();
        List<TableExport> tables = jeu.tables(run.id()).stream()
                .map(table -> "evenements".equals(table.nom())
                        ? pieges.pieger(table, run.id(), profils.parDomaine(run), corrige::add)
                        : table)
                .toList();
        ResultatExport resultat = servir(run.id(), format, tables, sortie);
        store.tracerAnomalies(corrige);
        return resultat;
    }

    /**
     * Ecrit la verite terrain. Le corrige n'entre JAMAIS dans l'export du jeu : un script qui le
     * lirait rendrait le score sans valeur. L'autorisation est posee par les controleurs.
     */
    @Transactional
    public ResultatExport exporterCorrige(UUID runId, OutputStream sortie) throws IOException {
        RunInfo run = termine(runId);
        return servir(run.id(), FormatExport.CORRIGE, List.of(jeu.corrige(run.id())), sortie);
    }

    /**
     * Verifie qu'un export est possible AVANT d'ouvrir la reponse : une fois le premier octet parti,
     * un refus ne peut plus sortir en 404 ni en 409.
     */
    public RunInfo verifier(UUID runId) {
        return termine(runId);
    }

    /** Ce qui a deja ete telecharge pour ce run, empreintes comprises. */
    public List<Telechargement> telechargements(UUID runId) {
        return store.telechargements(runId);
    }

    private ResultatExport servir(UUID runId, FormatExport format, List<TableExport> tables, OutputStream sortie)
            throws IOException {
        EcrivainExport ecrivain = ecrivains.get(format);
        if (ecrivain == null) {
            throw new IllegalArgumentException("Format d'export inconnu : " + format);
        }
        long[] lignes = {0};
        List<TableExport> marquees = tables.stream()
                .map(t -> marquer(t, runId, lignes))
                .toList();

        DigestOutputStream compteur = new DigestOutputStream(sortie, sha256());
        ecrivain.ecrire(marquees, compteur);
        compteur.flush();
        String empreinte = HexFormat.of().formatHex(compteur.getMessageDigest().digest());

        UUID id = UUID.randomUUID();
        store.tracer(id, runId, format, lignes[0], empreinte);
        return new ResultatExport(id, runId, format, lignes[0], empreinte);
    }

    /**
     * Ajoute les deux colonnes de marquage en tete de l'en-tete et de chaque ligne, et compte les
     * lignes au passage. Rien ne traverse cette methode sans etre marque.
     */
    private TableExport marquer(TableExport table, UUID runId, long[] lignes) {
        List<String> colonnes = Stream.concat(COLONNES_MARQUAGE.stream(), table.colonnes().stream()).toList();
        String reference = runId.toString();
        TableExport.Parcours parcours = (Consumer<List<String>> ligne) -> table.lignes().pour(valeurs -> {
            List<String> marquee = new ArrayList<>(colonnes.size());
            marquee.add(MARQUE);
            marquee.add(reference);
            marquee.addAll(valeurs);
            lignes[0]++;
            ligne.accept(marquee);
        });
        return new TableExport(table.nom(), colonnes, parcours);
    }

    private RunInfo termine(UUID runId) {
        RunInfo run = runs.trouver(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run inconnu : " + runId));
        if (!run.etat().terminal()) {
            throw new ExportIndisponibleException("La simulation " + runId + " n'est pas terminee (" + run.etat()
                    + ") : son export changerait a chaque appel");
        }
        return run;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
