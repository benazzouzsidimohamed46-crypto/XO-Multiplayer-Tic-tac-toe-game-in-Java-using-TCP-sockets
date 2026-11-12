public class Plateau {
    private String[][] grille = new String[3][3];
    // Stores the 3 winning cells as int[]{row,col} or null if no winner
    private int[][] combGagnante = null;

    public Plateau() {
        reset();
    }

    public boolean jouerCoup(int ligne, int col, String symbole) {
        if (grille[ligne][col].equals("")) {
            grille[ligne][col] = symbole;
            return true;
        }
        return false;
    }

    public boolean estPlein() {
        for (String[] ligne : grille)
            for (String cell : ligne)
                if (cell.equals(""))
                    return false;
        return true;
    }

    /** Returns the winning symbol or null. Also stores combGagnante. */
    public String getGagnant() {
        // Rows
        for (int i = 0; i < 3; i++) {
            if (!grille[i][0].equals("") && grille[i][0].equals(grille[i][1]) && grille[i][1].equals(grille[i][2])) {
                combGagnante = new int[][] { { i, 0 }, { i, 1 }, { i, 2 } };
                return grille[i][0];
            }
        }
        // Columns
        for (int i = 0; i < 3; i++) {
            if (!grille[0][i].equals("") && grille[0][i].equals(grille[1][i]) && grille[1][i].equals(grille[2][i])) {
                combGagnante = new int[][] { { 0, i }, { 1, i }, { 2, i } };
                return grille[0][i];
            }
        }
        // Diagonals
        if (!grille[0][0].equals("") && grille[0][0].equals(grille[1][1]) && grille[1][1].equals(grille[2][2])) {
            combGagnante = new int[][] { { 0, 0 }, { 1, 1 }, { 2, 2 } };
            return grille[0][0];
        }
        if (!grille[0][2].equals("") && grille[0][2].equals(grille[1][1]) && grille[1][1].equals(grille[2][0])) {
            combGagnante = new int[][] { { 0, 2 }, { 1, 1 }, { 2, 0 } };
            return grille[0][2];
        }
        combGagnante = null;
        return null;
    }

    /**
     * Returns the 3 winning cells [[r,c],[r,c],[r,c]] or null. Call after
     * getGagnant().
     */
    public int[][] getCombGagnante() {
        return combGagnante;
    }

    public void reset() {
        combGagnante = null;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                grille[i][j] = "";
    }

    public String[][] getGrille() {
        return grille;
    }
}