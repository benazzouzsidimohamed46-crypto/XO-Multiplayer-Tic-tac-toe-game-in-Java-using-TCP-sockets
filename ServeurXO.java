import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

public class ServeurXO extends JFrame {
    private static final int DEFAULT_PORT = 4646;
    private List<Match> matches = Collections.synchronizedList(new ArrayList<>());
    private int matchIdCounter = 1;
    private int inviteCounter  = 1;
    private Match matchEnAttente = null;

    private DefaultTableModel tableModel;
    private JTable table;
    private int portActuel;
    private JLabel statusLabel;
    private JPanel configPanel;
    private JPanel mainPanel;

    // EF-M04 : journal horodaté
    private JTextArea logArea;
    // EF-M03 : historique des parties terminées
    private DefaultTableModel histoModel;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss");

    public ServeurXO() {
        setTitle("Serveur XO");
        setSize(800, 600);  // légèrement agrandi pour mieux voir le résultat
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        configPanel = createConfigPanel();
        add(configPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("  Non démarré");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));
        add(statusLabel, BorderLayout.SOUTH);

        setVisible(true);
    }

    // ── Panneau de configuration ──────────────────────────────────
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Configuration du serveur");
        title.setFont(new Font("Dialog", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("Numéro de port :"), gbc);
        JTextField portField = new JTextField("" + DEFAULT_PORT, 15);
        gbc.gridx = 1;
        panel.add(portField, gbc);

        JButton btnLancer = new JButton("Lancer le serveur");
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(btnLancer, gbc);

        btnLancer.addActionListener(e -> {
            int port = DEFAULT_PORT;
            try {
                String s = portField.getText().trim();
                if (!s.isEmpty()) {
                    port = Integer.parseInt(s);
                    if (port < 1024 || port > 65535) throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Port invalide, utilisation du port par défaut " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
            if (!isPortDisponible(port)) {
                JOptionPane.showMessageDialog(this, "Port " + port + " non disponible.");
                return;
            }
            portActuel = port;
            lancerServeur(port);
            remove(configPanel);
            mainPanel = createMainPanel();
            add(mainPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
        return panel;
    }

    // ── Panneau principal avec onglets ────────────────────────────
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Matches",    createMatchesTab());
        tabs.addTab("Historique", createHistoTab());   // EF-M03
        tabs.addTab("Journal",    createLogTab());      // EF-M04
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    // ── Onglet : matches actifs (EF-M01) ─────────────────────────
    private JPanel createMatchesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = { "#", "Joueur X", "Joueur O", "Etat", "Coups", "Voir", "Forcer fin" };
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return c == 5 || c == 6; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(50);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(90);

        table.getColumnModel().getColumn(5).setCellRenderer(new SimpleButtonRenderer("Voir"));
        table.getColumnModel().getColumn(5).setCellEditor(
                new SimpleButtonEditor("Voir", row -> ouvrirVueMatch(row)));
        table.getColumnModel().getColumn(6).setCellRenderer(new SimpleButtonRenderer("Forcer fin"));
        table.getColumnModel().getColumn(6).setCellEditor(
                new SimpleButtonEditor("Forcer fin", row -> forcerFinMatch(row)));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ── Onglet : historique (EF-M03) avec résultat ─────────────────
    private JPanel createHistoTab() {
        JPanel panel = new JPanel(new BorderLayout());
        // MODIFIÉ : colonne "Résultat" plus large pour bien afficher le gagnant
        String[] cols = { "#", "Joueur X", "Joueur O", "Résultat", "Coups", "Heure fin" };
        histoModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable histoTable = new JTable(histoModel);
        histoTable.setRowHeight(24);
        histoTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Résultat plus large
        histoTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        histoTable.getColumnModel().getColumn(1).setPreferredWidth(100);

        // EF-M05 : bouton d'export vers fichier texte / JSON
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExportTxt  = new JButton("Exporter en TXT");
        JButton btnExportJson = new JButton("Exporter en JSON");
        btnPanel.add(btnExportTxt);
        btnPanel.add(btnExportJson);

        btnExportTxt.addActionListener(e -> exporterHistorique("txt"));
        btnExportJson.addActionListener(e -> exporterHistorique("json"));

        panel.add(new JScrollPane(histoTable), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── EF-M05 : export de l'historique ─────────────────────────
    private void exporterHistorique(String format) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exporter l'historique");
        String defaultName = "historique_xo_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
                + "." + format;
        chooser.setSelectedFile(new File(defaultName));

        int res = chooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File dest = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(dest))) {
            int nbLignes = histoModel.getRowCount();

            if ("json".equalsIgnoreCase(format)) {
                pw.println("[");
                for (int i = 0; i < nbLignes; i++) {
                    pw.println("  {");
                    pw.println("    \"id\": "        + histoModel.getValueAt(i, 0) + ",");
                    pw.println("    \"joueurX\": \"" + histoModel.getValueAt(i, 1) + "\",");
                    pw.println("    \"joueurO\": \"" + histoModel.getValueAt(i, 2) + "\",");
                    pw.println("    \"resultat\": \"" + histoModel.getValueAt(i, 3) + "\",");
                    pw.println("    \"coups\": "    + histoModel.getValueAt(i, 4) + ",");
                    pw.println("    \"heureFin\": \"" + histoModel.getValueAt(i, 5) + "\"");
                    pw.print("  }");
                    pw.println(i < nbLignes - 1 ? "," : "");
                }
                pw.println("]");
            } else {
                pw.println("=== Historique des parties XO ===");
                pw.println("Exporté le : " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
                pw.println();
                pw.printf("%-4s %-15s %-15s %-20s %-6s %-12s%n",
                        "#", "Joueur X", "Joueur O", "Résultat", "Coups", "Heure fin");
                pw.println("-".repeat(80));
                for (int i = 0; i < nbLignes; i++) {
                    pw.printf("%-4s %-15s %-15s %-20s %-6s %-12s%n",
                            histoModel.getValueAt(i, 0),
                            histoModel.getValueAt(i, 1),
                            histoModel.getValueAt(i, 2),
                            histoModel.getValueAt(i, 3),
                            histoModel.getValueAt(i, 4),
                            histoModel.getValueAt(i, 5));
                }
            }

            log("Historique exporté vers " + dest.getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    "Historique exporté avec succès :\n" + dest.getAbsolutePath());

        } catch (IOException ex) {
            log("ERREUR export : " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Erreur lors de l'export :\n" + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Onglet : journal (EF-M04) ────────────────────────────────
    private JPanel createLogTab() {
        JPanel panel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("Effacer le journal");
        clearBtn.addActionListener(e -> {
            logArea.setText("");
            log("Journal effacé");
        });
        topPanel.add(clearBtn);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    // ── Logging ──────────────────────────────────────────────────
    private void log(String msg) {
        String ts   = SDF.format(new Date());
        String line = "[" + ts + "] " + msg;
        System.out.println(line);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    // ── Démarrage du serveur ─────────────────────────────────────
    private boolean isPortDisponible(int port) {
        try (ServerSocket ss = new ServerSocket(port)) { return true; }
        catch (IOException e) { return false; }
    }

    private void lancerServeur(int port) {
        String ip = "?";
        try { ip = InetAddress.getLocalHost().getHostAddress(); }
        catch (UnknownHostException ignored) {}
        statusLabel.setText("  Serveur démarré — IP: " + ip + "  Port: " + port);
        log("Serveur démarré sur " + ip + ":" + port);
        new Thread(() -> accepterConnexions(port)).start();
    }

    private void accepterConnexions(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                log("Nouvelle connexion depuis " + socket.getInetAddress().getHostAddress());
                new Thread(() -> traiterNouvelleConnexion(socket)).start();
            }
        } catch (IOException e) {
            log("ERREUR serveur: " + e.getMessage());
        }
    }

    // ── Gestion d'une nouvelle connexion ─────────────────────────
    private synchronized void traiterNouvelleConnexion(Socket socket) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(new GameMessage(GameMessage.Type.DEMANDE_NOM, null));
            out.flush();

            GameMessage repNom = (GameMessage) in.readObject();
            String nom = repNom != null ? repNom.getPayload() : null;
            if (nom == null || nom.trim().isEmpty() || nom.equals("INVITE"))
                nom = "Invite" + (inviteCounter++);

            out.writeObject(new GameMessage(GameMessage.Type.NOM_CONFIRME, nom));
            out.flush();
            log("Joueur connecté : " + nom);

            final String nomFinal = nom;
            final ObjectOutputStream fluxOut = out;
            final ObjectInputStream fluxIn = in;

            if (matchEnAttente == null) {
                Match m = new Match(matchIdCounter++, socket, nomFinal, fluxOut, fluxIn);
                matches.add(m);
                matchEnAttente = m;
                m.addObservateur(match -> SwingUtilities.invokeLater(() -> onMatchMaj(match)));
                SwingUtilities.invokeLater(() -> tableModel.addRow(
                        new Object[]{ m.getId(), nomFinal, "En attente...", "EN ATTENTE", 0, "Voir", "Forcer fin" }));
                log("Match #" + m.getId() + " créé — en attente d'un 2ème joueur");
                new Thread(m).start();
            } else {
                Match m = matchEnAttente;
                matchEnAttente = null;
                m.setJoueurO(socket, nomFinal, fluxOut, fluxIn);
                log("Match #" + m.getId() + " — " + m.getNomX() + " vs " + nomFinal + " — début");
                SwingUtilities.invokeLater(this::rafraichirTable);
            }
        } catch (IOException | ClassNotFoundException e) {
            log("ERREUR connexion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MODIFIÉ : récupère le résultat pour l'historique
    private void onMatchMaj(Match m) {
        rafraichirTable();
        if (m.getEtat() == Match.Etat.FINIE) {
            log("Match #" + m.getId() + " terminé après " + m.getNbCoups() + " coups - " + m.getResultatFinal());
            ajouterHistorique(m);
        }
    }

    private void rafraichirTable() {
        tableModel.setRowCount(0);
        synchronized (matches) {
            for (Match m : matches) {
                if (m.getEtat() == Match.Etat.FINIE) continue;
                String etatStr;
                switch (m.getEtat()) {
                    case EN_ATTENTE: etatStr = "EN ATTENTE"; break;
                    case EN_COURS:   etatStr = "EN COURS";   break;
                    default:         etatStr = "FINIE";       break;
                }
                tableModel.addRow(new Object[]{
                        m.getId(), m.getNomX(), m.getNomO(), etatStr, m.getNbCoups(), "Voir", "Forcer fin" });
            }
        }
    }

    // MODIFIÉ : stocke le vrai résultat dans l'historique
    private void ajouterHistorique(Match m) {
        String resultat = m.getResultatFinal();
        if (resultat == null || resultat.isEmpty()) {
            resultat = "Terminé";
        }
        String heure = SDF.format(new Date());
        if (histoModel != null) {
            histoModel.addRow(new Object[]{ 
                m.getId(), 
                m.getNomX(), 
                m.getNomO(), 
                resultat,      // Maintenant : "Alice a gagné", "Bob a gagné", "Match nul", etc.
                m.getNbCoups(), 
                heure 
            });
        }
    }

    // ── Actions ──────────────────────────────────────────────────
    public void ouvrirVueMatch(int row) {
        List<Match> actifs = new ArrayList<>();
        synchronized (matches) {
            for (Match m : matches)
                if (m.getEtat() != Match.Etat.FINIE) actifs.add(m);
        }
        if (row < 0 || row >= actifs.size()) return;
        new VueMatchServeur(actifs.get(row));
    }

    public void forcerFinMatch(int row) {
        List<Match> actifs = new ArrayList<>();
        synchronized (matches) {
            for (Match m : matches)
                if (m.getEtat() != Match.Etat.FINIE) actifs.add(m);
        }
        if (row < 0 || row >= actifs.size()) return;
        Match m = actifs.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Forcer la fin du match #" + m.getId() +
                " (" + m.getNomX() + " vs " + m.getNomO() + ") ?",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            log("ADMIN — Fin forcée du match #" + m.getId());
            m.forcerFin();
            SwingUtilities.invokeLater(this::rafraichirTable);
        }
    }

    // ── Rendu / éditeur de boutons dans la table ──────────────────
    static class SimpleButtonRenderer extends JButton implements TableCellRenderer {
        SimpleButtonRenderer(String label) { setText(label); }
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int r, int c) { return this; }
    }

    static class SimpleButtonEditor extends DefaultCellEditor {
        private final java.util.function.IntConsumer action;
        private int currentRow;

        SimpleButtonEditor(String label, java.util.function.IntConsumer action) {
            super(new JCheckBox());
            this.action = action;
        }

        public Component getTableCellEditorComponent(
                JTable t, Object v, boolean sel, int r, int c) {
            currentRow = r;
            JButton btn = new JButton((String) v);
            btn.addActionListener(e -> { fireEditingStopped(); action.accept(currentRow); });
            return btn;
        }

        public Object getCellEditorValue() { return ""; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServeurXO::new);
    }
}