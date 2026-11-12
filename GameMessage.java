import java.io.Serializable;

/**
 * EF-S04 — Sérialisation d'objets sur TCP
 * Objet échangé entre serveur et client via ObjectOutputStream / ObjectInputStream.
 * Remplace les messages texte brut pour les coups et résultats.
 */
public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // Handshake
        DEMANDE_NOM, NOM_CONFIRME,
        // Lobby
        ATTENTE, SYMBOLE, NOM_ADVERSAIRE, INFO,
        // Tour
        TOUR, VOTRE_TOUR,
        // Coup
        COUP, INVALID,
        // Résultat
        GAGNE, NUL,
        // Revanche
        REVANCHE_QUESTION, REVANCHE_OK, REVANCHE_NON,
        // Chat
        CHAT,
        // Fin
        ADVERSAIRE_DECONNECTE, FIN_FORCE
    }

    private final Type type;
    private final String payload;   // texte libre selon le type
    private final int row;          // pour COUP
    private final int col;          // pour COUP
    private final String symbol;    // pour COUP / SYMBOLE

    // Constructeur général (pas de coordonnées)
    public GameMessage(Type type, String payload) {
        this.type    = type;
        this.payload = payload;
        this.row     = -1;
        this.col     = -1;
        this.symbol  = null;
    }

    // Constructeur pour COUP
    public GameMessage(Type type, int row, int col, String symbol) {
        this.type    = type;
        this.payload = null;
        this.row     = row;
        this.col     = col;
        this.symbol  = symbol;
    }

    public Type    getType()    { return type; }
    public String  getPayload() { return payload; }
    public int     getRow()     { return row; }
    public int     getCol()     { return col; }
    public String  getSymbol()  { return symbol; }

    @Override
    public String toString() {
        if (type == Type.COUP)
            return "GameMessage[COUP " + row + "," + col + " " + symbol + "]";
        return "GameMessage[" + type + " " + payload + "]";
    }
}