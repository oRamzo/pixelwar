package com.pixelwar.model;

import java.io.Serializable;

/**
 * Objet échangé entre le client et le serveur via ObjectOutputStream/ObjectInputStream.
 * Utilise le pattern "factory method" pour chaque type de message.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        // Client → Serveur
        CONNECT,
        PIXEL_COLOR,
        DISCONNECT,

        // Serveur → Client
        CONNECT_OK,
        CONNECT_ERROR,
        PIXEL_UPDATE,   // Un pixel a été colorié (broadcast à tous)
        PIXEL_EMBARGO,  // Le pixel est sous embargo (réponse à l'auteur)
        SERVER_SHUTDOWN // Le serveur s'arrête
    }

    private final Type type;
    private String pseudo;
    private int x;
    private int y;
    private String color;       // Couleur hex, ex: "#FF0000"
    private String errorMessage;
    private long remainingMs;   // Temps restant d'embargo en millisecondes

    // ─── Constructeurs privés ───────────────────────────────────────────────

    private Message(Type type) {
        this.type = type;
    }

    // ─── Factories Client → Serveur ─────────────────────────────────────────

    public static Message connect(String pseudo) {
        Message m = new Message(Type.CONNECT);
        m.pseudo = pseudo;
        return m;
    }

    public static Message pixelColor(int x, int y, String color) {
        Message m = new Message(Type.PIXEL_COLOR);
        m.x = x;
        m.y = y;
        m.color = color;
        return m;
    }

    public static Message disconnect() {
        return new Message(Type.DISCONNECT);
    }

    // ─── Factories Serveur → Client ──────────────────────────────────────────

    public static Message connectOk() {
        return new Message(Type.CONNECT_OK);
    }

    public static Message connectError(String reason) {
        Message m = new Message(Type.CONNECT_ERROR);
        m.errorMessage = reason;
        return m;
    }

    /**
     * Broadcast : un pixel a été colorié.
     * @param pseudo   Le pseudo du joueur qui a colorié
     * @param x        Colonne
     * @param y        Ligne
     * @param color    Couleur hex
     * @param remainingMs Durée d'embargo restante (en ms)
     */
    public static Message pixelUpdate(String pseudo, int x, int y, String color, long remainingMs) {
        Message m = new Message(Type.PIXEL_UPDATE);
        m.pseudo = pseudo;
        m.x = x;
        m.y = y;
        m.color = color;
        m.remainingMs = remainingMs;
        return m;
    }

    /**
     * Réponse quand un client essaie de colorier un pixel sous embargo.
     */
    public static Message pixelEmbargo(int x, int y, long remainingMs) {
        Message m = new Message(Type.PIXEL_EMBARGO);
        m.x = x;
        m.y = y;
        m.remainingMs = remainingMs;
        return m;
    }

    public static Message serverShutdown() {
        return new Message(Type.SERVER_SHUTDOWN);
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public Type getType()          { return type; }
    public String getPseudo()      { return pseudo; }
    public int getX()              { return x; }
    public int getY()              { return y; }
    public String getColor()       { return color; }
    public String getErrorMessage(){ return errorMessage; }
    public long getRemainingMs()   { return remainingMs; }

    @Override
    public String toString() {
        return "Message{type=" + type + ", pseudo=" + pseudo
                + ", x=" + x + ", y=" + y + ", color=" + color + "}";
    }
}
