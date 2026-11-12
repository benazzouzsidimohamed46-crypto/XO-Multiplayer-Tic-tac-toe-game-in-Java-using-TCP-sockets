import java.awt.*;
import javax.swing.*;

public class VueMatchServeur extends JFrame implements Match.ObservateurMatch {
    private static final Color X_COLOR = new Color(190, 55, 55);
    private static final Color O_COLOR = new Color(45, 105, 185);
    private static final Color X_CELL_COLOR = new Color(250, 232, 232);
    private static final Color O_CELL_COLOR = new Color(230, 239, 250);
    private static final Color WIN_CELL_COLOR = new Color(190, 225, 195);

    private Match match;
    private JButton[][] boutons = new JButton[3][3];
    private JLabel statusLabel;
    private JLabel nomXLabel, nomOLabel;

    public VueMatchServeur(Match match) {
        this.match = match;
        match.addObservateur(this);

        setTitle("Vue Match #" + match.getId() + " (lecture seule)");
        setSize(380, 440);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(4, 4));

        // Player names
        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        nomXLabel = new JLabel("X: " + match.getNomX(), SwingConstants.CENTER);
        nomOLabel = new JLabel("O: " + match.getNomO(), SwingConstants.CENTER);
        nomXLabel.setForeground(X_COLOR);
        nomOLabel.setForeground(O_COLOR);
        nomXLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        nomOLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        topPanel.add(nomXLabel);
        topPanel.add(nomOLabel);
        add(topPanel, BorderLayout.NORTH);

        // Grid (read only)
        JPanel grillePanel = new JPanel(new GridLayout(3, 3, 4, 4));
        grillePanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                JButton btn = new JButton("");
                btn.setFont(new Font("Dialog", Font.BOLD, 56));
                btn.setEnabled(false);
                boutons[i][j] = btn;
                grillePanel.add(btn);
            }
        add(grillePanel, BorderLayout.CENTER);

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(statusLabel, BorderLayout.SOUTH);

        rafraichir();
        setVisible(true);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) {
                match.removeObservateur(VueMatchServeur.this);
            }
        });
    }

    @Override
    public void onMaj(Match m) {
        SwingUtilities.invokeLater(this::rafraichir);
    }

    private void rafraichir() {
        nomXLabel.setText("X: " + match.getNomX());
        nomOLabel.setText("O: " + match.getNomO());

        // Reset button backgrounds
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                boutons[i][j].setBackground(null);
                boutons[i][j].setOpaque(false);
                boutons[i][j].setForeground(UIManager.getColor("Button.foreground"));
            }

        String[][] g = match.getGrilleSnapshot();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                boutons[i][j].setText(g[i][j]);
                if (!g[i][j].isEmpty()) {
                    boolean estX = "X".equals(g[i][j]);
                    boutons[i][j].setForeground(estX ? X_COLOR : O_COLOR);
                    boutons[i][j].setBackground(estX ? X_CELL_COLOR : O_CELL_COLOR);
                    boutons[i][j].setOpaque(true);
                }
            }

        // EF-C05: highlight winning combo if available
        int[][] combo = match.getCombGagnanteSnapshot();
        if (combo != null) {
            for (int[] cell : combo) {
                boutons[cell[0]][cell[1]].setBackground(WIN_CELL_COLOR);
                boutons[cell[0]][cell[1]].setOpaque(true);
            }
        }

        switch (match.getEtat()) {
            case EN_ATTENTE:
                statusLabel.setText("En attente du 2eme joueur...");
                break;
            case EN_COURS:
                statusLabel.setText("Match en cours — " + match.getNbCoups() + " coup(s)");
                break;
            case FINIE:
                statusLabel.setText("Match termine — " + match.getNbCoups() + " coup(s)");
                break;
        }
    }
}