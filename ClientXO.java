import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 * EF-S04 : communication via ObjectOutputStream / ObjectInputStream (GameMessage).
 */
public class ClientXO extends JFrame {

    private static final Color X_COLOR = new Color(190, 55, 55);
    private static final Color O_COLOR = new Color(45, 105, 185);
    private static final Color X_CELL_COLOR = new Color(250, 232, 232);
    private static final Color O_CELL_COLOR = new Color(230, 239, 250);
    private static final Color WIN_CELL_COLOR = new Color(190, 225, 195);

    private JButton[][] boutons = new JButton[3][3];
    private ObjectInputStream  in;
    private ObjectOutputStream out;
    private String symbole;
    private String symboleActif;
    private boolean monTour = false;
    private String monNom;

    private JLabel tourLabel;
    private JLabel nomXLabel, nomOLabel;

    // EF-C06 : chat
    private JTextField chatField;
    private JButton btnChat;

    // ── Dialogue de connexion ────────────────────────────────────
    private static String[] demanderNom() {
        JDialog dialog = new JDialog((Frame) null, "Connexion", true);
        dialog.setSize(360, 230);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel center = new JPanel(new GridLayout(4, 2, 6, 6));
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));

        center.add(new JLabel("Votre nom :"));
        JTextField nomField = new JTextField();
        center.add(nomField);

        // EF-C01 : champ IP
        center.add(new JLabel("Adresse IP du serveur :"));
        JTextField ipField = new JTextField("localhost");
        center.add(ipField);

        center.add(new JLabel("Port du serveur :"));
        JTextField portField = new JTextField("4646");
        center.add(portField);

        center.add(new JLabel(""));
        center.add(new JLabel(""));
        dialog.add(center, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        JButton btnEntrer  = new JButton("Entrer");
        JButton btnInvite  = new JButton("Entrer comme invite");
        btnPanel.add(btnEntrer);
        btnPanel.add(btnInvite);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        String[] result = {null, null, "localhost", "4646"};

        ActionListener doConnect = e -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) ip = "localhost";
            String portStr = portField.getText().trim();
            if (portStr.isEmpty()) portStr = "4646";
            result[2] = ip;
            result[3] = portStr;
        };

        btnEntrer.addActionListener(e -> {
            String n = nomField.getText().trim();
            if (!n.isEmpty()) {
                doConnect.actionPerformed(e);
                result[0] = n;
                result[1] = "normal";
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Veuillez entrer un nom.");
            }
        });

        btnInvite.addActionListener(e -> {
            doConnect.actionPerformed(e);
            result[0] = "INVITE";
            result[1] = "invite";
            dialog.dispose();
        });

        nomField.addActionListener(e -> btnEntrer.doClick());
        dialog.setVisible(true);
        return result;
    }

    // ── Constructeur ─────────────────────────────────────────────
    public ClientXO(String hote, int port, String nomEnvoye) throws IOException, ClassNotFoundException {
        Socket socket = new Socket(hote, port);

        // EF-S04 : flux objet — OOS doit être créé avant OIS des deux côtés
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        // Handshake nom
        GameMessage msg = recvMsg();
        if (msg != null && msg.getType() == GameMessage.Type.DEMANDE_NOM)
            sendMsg(new GameMessage(GameMessage.Type.DEMANDE_NOM, nomEnvoye));

        GameMessage confirme = recvMsg();
        monNom = (confirme != null && confirme.getType() == GameMessage.Type.NOM_CONFIRME)
                 ? confirme.getPayload() : nomEnvoye;

        buildUI();
        setVisible(true);
        new Thread(this::jouer).start();
    }

    // ── Envoi / réception ────────────────────────────────────────
    private void sendMsg(GameMessage msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    private GameMessage recvMsg() throws IOException, ClassNotFoundException {
        return (GameMessage) in.readObject();
    }

    // ── Interface Swing ──────────────────────────────────────────
    private void buildUI() {
        setTitle("XO - " + monNom);
        setSize(400, 560);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(4, 4));

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        nomXLabel = new JLabel("X: ?", SwingConstants.CENTER);
        nomOLabel = new JLabel("O: ?", SwingConstants.CENTER);
        nomXLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        nomOLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        nomXLabel.setForeground(X_COLOR);
        nomOLabel.setForeground(O_COLOR);
        topPanel.add(nomXLabel);
        topPanel.add(nomOLabel);
        add(topPanel, BorderLayout.NORTH);

        JPanel grillePanel = new JPanel(new GridLayout(3, 3, 4, 4));
        grillePanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                JButton btn = new JButton("");
                btn.setFont(new Font("Dialog", Font.BOLD, 56));
                btn.setEnabled(false); // EF-C02
                int x = i, y = j;
                btn.addActionListener(e -> {
                    if (monTour && btn.getText().equals("")) {
                        try {
                            // EF-S04 : envoi d'un objet GameMessage COUP
                            sendMsg(new GameMessage(GameMessage.Type.COUP, x, y, null));
                        } catch (IOException ex) {
                            setTour("Erreur réseau.");
                        }
                        monTour = false;
                        setAllButtonsEnabled(false);
                    }
                });
                boutons[i][j] = btn;
                grillePanel.add(btn);
            }
        add(grillePanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 8, 8));

        tourLabel = new JLabel("Connexion en cours...", SwingConstants.CENTER);
        tourLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        tourLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        bottomPanel.add(tourLabel, BorderLayout.NORTH);

        // EF-C06 : zone chat
        JPanel chatPanel = new JPanel(new BorderLayout(4, 0));
        chatField = new JTextField();
        btnChat = new JButton("Envoyer");
        btnChat.setEnabled(false);
        chatPanel.add(new JLabel("Chat: "), BorderLayout.WEST);
        chatPanel.add(chatField, BorderLayout.CENTER);
        chatPanel.add(btnChat, BorderLayout.EAST);

        ActionListener sendChat = e -> {
            String texte = chatField.getText().trim();
            if (!texte.isEmpty()) {
                try {
                    sendMsg(new GameMessage(GameMessage.Type.CHAT, texte));
                } catch (IOException ex) {
                    setTour("Erreur réseau.");
                }
                chatField.setText("");
            }
        };
        btnChat.addActionListener(sendChat);
        chatField.addActionListener(sendChat);

        bottomPanel.add(chatPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setTour(String msg) {
        SwingUtilities.invokeLater(() -> tourLabel.setText(msg));
    }

    // EF-C02 : activer / désactiver les cases
    private void setAllButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    if (boutons[i][j].getText().equals(""))
                        boutons[i][j].setEnabled(enabled);
        });
    }

    // EF-C05 : mettre en évidence la combinaison gagnante
    private void highlightWin(String comboStr) {
        if (comboStr == null || comboStr.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (String cell : comboStr.split(";")) {
                if (cell.isEmpty()) continue;
                String[] rc = cell.split("-");
                int r = Integer.parseInt(rc[0]);
                int c = Integer.parseInt(rc[1]);
                boutons[r][c].setBackground(WIN_CELL_COLOR);
                boutons[r][c].setOpaque(true);
            }
        });
    }

    private void colorerJoueurs() {
        nomXLabel.setForeground(X_COLOR);
        nomOLabel.setForeground(O_COLOR);
        nomXLabel.setFont(new Font("Dialog",
                "X".equals(symboleActif) ? Font.BOLD : Font.PLAIN, 13));
        nomOLabel.setFont(new Font("Dialog",
                "O".equals(symboleActif) ? Font.BOLD : Font.PLAIN, 13));
    }

    private void colorerCase(JButton bouton, String symboleCase) {
        boolean estX = "X".equals(symboleCase);
        bouton.setForeground(estX ? X_COLOR : O_COLOR);
        bouton.setBackground(estX ? X_CELL_COLOR : O_CELL_COLOR);
        bouton.setOpaque(true);
    }

    // ── Boucle de jeu ────────────────────────────────────────────
    private void jouer() {
        try {
            while (true) {
                GameMessage msg = recvMsg();
                if (msg == null) break;

                switch (msg.getType()) {

                    case ATTENTE:
                        setTour(msg.getPayload());
                        break;

                    case SYMBOLE:
                        symbole = msg.getPayload();
                        SwingUtilities.invokeLater(() -> {
                            setTitle("XO - " + monNom + " (" + symbole + ")");
                            if (symbole.equals("X")) nomXLabel.setText("X: " + monNom + " (vous)");
                            else                     nomOLabel.setText("O: " + monNom + " (vous)");
                            colorerJoueurs();
                        });
                        break;

                    case NOM_ADVERSAIRE:
                        String adv = msg.getPayload();
                        SwingUtilities.invokeLater(() -> {
                            if (symbole.equals("X")) nomOLabel.setText("O: " + adv);
                            else                     nomXLabel.setText("X: " + adv);
                            colorerJoueurs();
                            btnChat.setEnabled(true);
                        });
                        break;

                    case INFO:
                        setTour(msg.getPayload());
                        break;

                    case TOUR:
                        String joueurNom = msg.getPayload();
                        symboleActif = joueurNom.equals(monNom) ? symbole
                                : ("X".equals(symbole) ? "O" : "X");
                        SwingUtilities.invokeLater(this::colorerJoueurs);
                        if (!joueurNom.equals(monNom))
                            setTour("Tour de " + joueurNom + "...");
                        break;

                    case VOTRE_TOUR:
                        monTour = true;
                        symboleActif = symbole;
                        SwingUtilities.invokeLater(this::colorerJoueurs);
                        setAllButtonsEnabled(true);
                        setTour("C'est votre tour !");
                        break;

                    case COUP:
                        // EF-S04 : réception d'un objet COUP
                        int ci = msg.getRow(), cj = msg.getCol();
                        String cs = msg.getSymbol();
                        SwingUtilities.invokeLater(() -> {
                            boutons[ci][cj].setText(cs);
                            colorerCase(boutons[ci][cj], cs);
                            boutons[ci][cj].setEnabled(false);
                        });
                        break;

                    case GAGNE: {
                        String[] parts = msg.getPayload().split(":", 2);
                        String nomGagnant = parts[0];
                        String combo = parts.length > 1 ? parts[1] : "";
                        boolean jaiGagne = nomGagnant.equals(monNom);
                        setTour(jaiGagne ? "Vous avez gagné !" : "Vous avez perdu. " + nomGagnant + " a gagné.");
                        highlightWin(combo);
                        setAllButtonsEnabled(false);
                        break;
                    }

                    case NUL:
                        setTour("Match nul !");
                        setAllButtonsEnabled(false);
                        break;

                    case INVALID:
                        monTour = true;
                        setAllButtonsEnabled(true);
                        setTour("Coup invalide, rejouez.");
                        break;

                    case CHAT:
                        String chatMsg = msg.getPayload();
                        SwingUtilities.invokeLater(() -> {
                            String ancien = tourLabel.getText();
                            tourLabel.setText(chatMsg);
                            new javax.swing.Timer(4000, ev -> tourLabel.setText(ancien)) {
                                { setRepeats(false); start(); }
                            };
                        });
                        break;

                    case REVANCHE_QUESTION:
                        setAllButtonsEnabled(false);
                        SwingUtilities.invokeLater(() -> {
                            int rep = JOptionPane.showConfirmDialog(this,
                                "Voulez-vous une revanche ?", "Revanche", JOptionPane.YES_NO_OPTION);
                            try {
                                sendMsg(new GameMessage(GameMessage.Type.REVANCHE_QUESTION,
                                        rep == JOptionPane.YES_OPTION ? "OUI" : "NON"));
                            } catch (IOException ex) { setTour("Erreur réseau."); }
                        });
                        break;

                    case REVANCHE_OK:
                        SwingUtilities.invokeLater(() -> {
                            for (int i = 0; i < 3; i++)
                                for (int j = 0; j < 3; j++) {
                                    boutons[i][j].setText("");
                                    boutons[i][j].setBackground(null);
                                    boutons[i][j].setOpaque(false);
                                    boutons[i][j].setForeground(UIManager.getColor("Button.foreground"));
                                    boutons[i][j].setEnabled(false);
                                }
                            symboleActif = null;
                            colorerJoueurs();
                            setTour("Revanche ! Match relancé...");
                        });
                        break;

                    case REVANCHE_NON:
                        setTour("Pas de revanche. Au revoir !");
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Pas de revanche. Fermeture.");
                            System.exit(0);
                        });
                        break;

                    case ADVERSAIRE_DECONNECTE:
                        setTour(msg.getPayload());
                        setAllButtonsEnabled(false);
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, msg.getPayload());
                            System.exit(0);
                        });
                        break;

                    case FIN_FORCE:
                        setTour(msg.getPayload());
                        setAllButtonsEnabled(false);
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, msg.getPayload());
                            System.exit(0);
                        });
                        break;

                    default:
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            setTour("Connexion perdue.");
        }
    }

    // ── Main ─────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] result = demanderNom();
            if (result[0] == null) return;
            int port = 4646;
            try {
                port = Integer.parseInt(result[3]);
                if (port < 1024 || port > 65535) port = 4646;
            } catch (NumberFormatException ignored) {}
            String host = result[2];
            int finalPort = port;
            try {
                new ClientXO(host, finalPort, result[0]);
            } catch (IOException | ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "Impossible de se connecter.\n" + e.getMessage());
            }
        });
    }
}