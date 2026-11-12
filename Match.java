import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * EF-S04 : Les échanges de coups et résultats passent désormais par
 *           ObjectOutputStream / ObjectInputStream (objet GameMessage).
 */
public class Match implements Runnable {
    public enum Etat { EN_ATTENTE, EN_COURS, FINIE }

    public interface ObservateurMatch {
        void onMaj(Match m);
    }

    private final int id;
    private Socket joueurX;
    private Socket joueurO;
    private String nomX;
    private String nomO;
    private Plateau plateau;
    private Etat etat;
    private String[][] grilleSnapshot;
    private int[][] combGagnanteSnapshot;
    private List<ObservateurMatch> observateurs = new ArrayList<>();
    private int nbCoups = 0;
    private volatile boolean forcerFin = false;
    private final Queue<GameMessage> coupsX = new LinkedList<>();
    private final Queue<GameMessage> coupsO = new LinkedList<>();
    private boolean lecteursDemarres = false;
    
    // AJOUTÉ : stocker le résultat final de la partie
    private String resultatFinal = "En cours";

    // EF-S04 : flux objet reçus de ServeurXO (déjà ouverts)
    private ObjectOutputStream outX, outO;
    private ObjectInputStream inX, inO;

    /**
     * Constructeur pour le premier joueur (X).
     * Les flux sont déjà ouverts par ServeurXO.
     */
    public Match(int id, Socket joueurX, String nomX,
                 ObjectOutputStream outX, ObjectInputStream inX) {
        this.id = id;
        this.joueurX = joueurX;
        this.nomX = nomX;
        this.outX = outX;
        this.inX = inX;
        this.etat = Etat.EN_ATTENTE;
        this.plateau = new Plateau();
        this.grilleSnapshot = new String[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                grilleSnapshot[i][j] = "";
        this.resultatFinal = "En attente...";
    }

    /**
     * Ajoute le second joueur (O) avec ses flux déjà ouverts.
     */
    public synchronized void setJoueurO(Socket joueurO, String nomO,
                                         ObjectOutputStream outO, ObjectInputStream inO) {
        this.joueurO = joueurO;
        this.nomO = nomO;
        this.outO = outO;
        this.inO = inO;
        this.etat = Etat.EN_COURS;
        this.resultatFinal = "En cours";
    }

    public int getId()    { return id; }
    public String getNomX() { return nomX; }
    public String getNomO() { return nomO != null ? nomO : "En attente..."; }
    public Etat getEtat() { return etat; }
    public String[][] getGrilleSnapshot() { return grilleSnapshot; }
    public int[][] getCombGagnanteSnapshot() { return combGagnanteSnapshot; }
    public int getNbCoups() { return nbCoups; }
    
    // AJOUTÉ : getter pour le résultat final
    public String getResultatFinal() { return resultatFinal; }

    public void addObservateur(ObservateurMatch o)    { observateurs.add(o); }
    public void removeObservateur(ObservateurMatch o) { observateurs.remove(o); }
    private void notifier() { for (ObservateurMatch o : observateurs) o.onMaj(this); }

    public void forcerFin() {
        forcerFin = true;
        resultatFinal = "Arrêté par admin";
        try {
            if (outX != null) sendMsg(outX, new GameMessage(GameMessage.Type.FIN_FORCE,
                    "La partie a ete arretee par l'administrateur."));
            if (outO != null) sendMsg(outO, new GameMessage(GameMessage.Type.FIN_FORCE,
                    "La partie a ete arretee par l'administrateur."));
        } catch (Exception ignored) {}
        etat = Etat.FINIE;
        notifier();
        try { if (joueurX != null) joueurX.close(); } catch (IOException ignored) {}
        try { if (joueurO != null) joueurO.close(); } catch (IOException ignored) {}
    }

    private void sendMsg(ObjectOutputStream oos, GameMessage msg) throws IOException {
        oos.writeObject(msg);
        oos.flush();
    }

    private GameMessage recvMsg(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        return (GameMessage) ois.readObject();
    }

    @Override
    public void run() {
        try {
            sendMsg(outX, new GameMessage(GameMessage.Type.ATTENTE, "En attente du joueur O..."));
            notifier();

            while (joueurO == null && !forcerFin) {
                Thread.sleep(100);
            }
            if (forcerFin) return;

            jouerPartie();

        } catch (IOException | InterruptedException | ClassNotFoundException e) {
            e.printStackTrace();
            etat = Etat.FINIE;
            resultatFinal = "Erreur";
            notifier();
        }
    }

    private void jouerPartie() throws IOException, InterruptedException, ClassNotFoundException {

        sendMsg(outX, new GameMessage(GameMessage.Type.SYMBOLE, "X"));
        sendMsg(outO, new GameMessage(GameMessage.Type.SYMBOLE, "O"));
        sendMsg(outX, new GameMessage(GameMessage.Type.NOM_ADVERSAIRE, nomO));
        sendMsg(outO, new GameMessage(GameMessage.Type.NOM_ADVERSAIRE, nomX));
        sendMsg(outX, new GameMessage(GameMessage.Type.INFO, "Match commence ! Vous jouez contre " + nomO));
        sendMsg(outO, new GameMessage(GameMessage.Type.INFO, "Match commence ! Vous jouez contre " + nomX));

        ObjectOutputStream[] outs  = {outX, outO};
        ObjectInputStream[]  ins   = {inX, inO};
        String[] symbols = {"X", "O"};
        String[] noms    = {nomX, nomO};

        plateau.reset();
        combGagnanteSnapshot = null;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                grilleSnapshot[i][j] = "";

        if (!lecteursDemarres) {
            Thread lecteurX = new Thread(() -> lireCoups(ins[0], coupsX, 0, noms, outs));
            Thread lecteurO = new Thread(() -> lireCoups(ins[1], coupsO, 1, noms, outs));
            lecteurX.setDaemon(true);
            lecteurO.setDaemon(true);
            lecteurX.start();
            lecteurO.start();
            lecteursDemarres = true;
        }

        int tour = 0;
        while (!forcerFin) {
            int joueurActuel = tour % 2;
            Queue<GameMessage> coups = joueurActuel == 0 ? coupsX : coupsO;
            int adversaire = 1 - joueurActuel;

            sendMsg(outs[0], new GameMessage(GameMessage.Type.TOUR, noms[joueurActuel]));
            sendMsg(outs[1], new GameMessage(GameMessage.Type.TOUR, noms[joueurActuel]));
            sendMsg(outs[joueurActuel], new GameMessage(GameMessage.Type.VOTRE_TOUR, null));

            GameMessage coup = null;
            while (coup == null && !forcerFin) {
                GameMessage msg = null;
                synchronized (coups) {
                    while (coups.isEmpty() && !forcerFin) {
                        try { coups.wait(100); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (!coups.isEmpty()) msg = coups.poll();
                }
                if (msg == null) continue;

                if (msg.getType() == GameMessage.Type.ADVERSAIRE_DECONNECTE) {
                    sendMsg(outs[adversaire], new GameMessage(
                            GameMessage.Type.ADVERSAIRE_DECONNECTE,
                            "Votre adversaire s'est deconnecte. Vous gagnez !"));
                    resultatFinal = nomX + " a gagné par abandon";
                    etat = Etat.FINIE;
                    notifier();
                    return;
                }

                if (msg.getType() != GameMessage.Type.COUP) continue;

                boolean valid = plateau.jouerCoup(msg.getRow(), msg.getCol(), symbols[joueurActuel]);
                if (!valid) {
                    sendMsg(outs[joueurActuel], new GameMessage(GameMessage.Type.INVALID, null));
                    continue;
                }
                coup = msg;
            }

            if (forcerFin) break;

            nbCoups++;

            String[][] g = plateau.getGrille();
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    grilleSnapshot[i][j] = g[i][j];

            GameMessage update = new GameMessage(
                    GameMessage.Type.COUP,
                    coup.getRow(), coup.getCol(), symbols[joueurActuel]);
            sendMsg(outs[0], update);
            sendMsg(outs[1], update);
            notifier();

            String gagnantSymbole = plateau.getGagnant();
            if (gagnantSymbole != null) {
                combGagnanteSnapshot = plateau.getCombGagnante();
                String combo = combToString(combGagnanteSnapshot);
                String nomGagnant = gagnantSymbole.equals("X") ? nomX : nomO;
                // AJOUTÉ : stocker le résultat
                resultatFinal = nomGagnant + " a gagné";
                String payloadGagne = nomGagnant + ":" + combo;
                sendMsg(outs[0], new GameMessage(GameMessage.Type.GAGNE, payloadGagne));
                sendMsg(outs[1], new GameMessage(GameMessage.Type.GAGNE, payloadGagne));
                etat = Etat.FINIE;
                notifier();
                proposerRevanche();
                return;
            } else if (plateau.estPlein()) {
                // AJOUTÉ : match nul
                resultatFinal = "Match nul";
                sendMsg(outs[0], new GameMessage(GameMessage.Type.NUL, null));
                sendMsg(outs[1], new GameMessage(GameMessage.Type.NUL, null));
                etat = Etat.FINIE;
                notifier();
                proposerRevanche();
                return;
            }

            tour++;
        }
    }

    private void lireCoups(ObjectInputStream in, Queue<GameMessage> queue,
                           int indexJoueur, String[] noms, ObjectOutputStream[] outs) {
        try {
            while (!forcerFin) {
                GameMessage msg = recvMsg(in);
                if (msg == null) break;

                if (msg.getType() == GameMessage.Type.CHAT) {
                    String diffuse = noms[indexJoueur] + ": " + msg.getPayload();
                    try {
                        sendMsg(outs[0], new GameMessage(GameMessage.Type.CHAT, diffuse));
                        sendMsg(outs[1], new GameMessage(GameMessage.Type.CHAT, diffuse));
                    } catch (IOException ignored) {}
                    continue;
                }

                synchronized (queue) {
                    queue.add(msg);
                    queue.notifyAll();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (!forcerFin) {
                synchronized (queue) {
                    queue.add(new GameMessage(GameMessage.Type.ADVERSAIRE_DECONNECTE, "disconnect"));
                    queue.notifyAll();
                }
            }
        }
    }

    private void proposerRevanche() throws IOException, InterruptedException, ClassNotFoundException {
        sendMsg(outX, new GameMessage(GameMessage.Type.REVANCHE_QUESTION, null));
        sendMsg(outO, new GameMessage(GameMessage.Type.REVANCHE_QUESTION, null));

        long deadline = System.currentTimeMillis() + 30_000;
        GameMessage repX = attendreReponseRevanche(coupsX, deadline);
        GameMessage repO = attendreReponseRevanche(coupsO, deadline);

        boolean xVeut = repX != null && "OUI".equals(repX.getPayload());
        boolean oVeut = repO != null && "OUI".equals(repO.getPayload());

        if (xVeut && oVeut) {
            sendMsg(outX, new GameMessage(GameMessage.Type.REVANCHE_OK, null));
            sendMsg(outO, new GameMessage(GameMessage.Type.REVANCHE_OK, null));
            nbCoups = 0;
            plateau.reset();
            combGagnanteSnapshot = null;
            resultatFinal = "En cours (revanche)";
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    grilleSnapshot[i][j] = "";
            etat = Etat.EN_COURS;
            notifier();
            jouerPartie();
        } else {
            sendMsg(outX, new GameMessage(GameMessage.Type.REVANCHE_NON, null));
            sendMsg(outO, new GameMessage(GameMessage.Type.REVANCHE_NON, null));
            try { joueurX.close(); } catch (IOException ignored) {}
            try { joueurO.close(); } catch (IOException ignored) {}
        }
    }

    private GameMessage attendreReponseRevanche(Queue<GameMessage> queue, long deadline)
            throws InterruptedException {
        synchronized (queue) {
            while (System.currentTimeMillis() < deadline) {
                while (!queue.isEmpty()) {
                    GameMessage msg = queue.poll();
                    if (msg.getType() == GameMessage.Type.REVANCHE_QUESTION)
                        return msg;
                    if (msg.getType() == GameMessage.Type.ADVERSAIRE_DECONNECTE)
                        return msg;
                }
                long attente = deadline - System.currentTimeMillis();
                if (attente > 0) queue.wait(attente);
            }
        }
        return null;
    }

    private String combToString(int[][] comb) {
        if (comb == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int[] cell : comb) sb.append(cell[0]).append("-").append(cell[1]).append(";");
        return sb.toString();
    }
}