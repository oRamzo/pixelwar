import java.io.Serializable;

public class Pixel implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        PIXEL,
        UPDATE,
        STATE,
        BUSY
    }

    private final Type type;
    private final int row;
    private final int col;
    private final String couleurHex;
    private final String pseudo;
    private final int secondesRestantes;

    public Pixel(Type type, int row, int col, String couleurHex) {
        this(type, row, col, couleurHex, null, 0);
    }

    public Pixel(Type type, int row, int col, String couleurHex, String pseudo, int secondesRestantes) {
        this.type = type;
        this.row = row;
        this.col = col;
        this.couleurHex = couleurHex;
        this.pseudo = pseudo;
        this.secondesRestantes = secondesRestantes;
    }

    public static Pixel pixelDemande(int row, int col, String couleurHex) {
        return new Pixel(Type.PIXEL, row, col, couleurHex, null, 0);
    }

    public static Pixel pixelUpdate(int row, int col, String couleurHex, String pseudo) {
        return new Pixel(Type.UPDATE, row, col, couleurHex, pseudo, 0);
    }

    public static Pixel pixelState(int row, int col, String couleurHex, String pseudo, int secondesRestantes) {
        return new Pixel(Type.STATE, row, col, couleurHex, pseudo, secondesRestantes);
    }

    public static Pixel pixelBusy(int row, int col, String pseudo, int secondesRestantes) {
        return new Pixel(Type.BUSY, row, col, null, pseudo, secondesRestantes);
    }

    public Type getType() {
        return type;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public String getCouleurHex() {
        return couleurHex;
    }

    public String getPseudo() {
        return pseudo;
    }

    public int getSecondesRestantes() {
        return secondesRestantes;
    }
}
