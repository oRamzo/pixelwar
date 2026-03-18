package com.pixelwar.server;

import com.pixelwar.model.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur TCP du jeu Pixel War.
 *
 * Lancement : java -cp target/pixel-war-1.0-SNAPSHOT.jar com.pixelwar.server.Serveur <port>
 * Ou avec Maven : mvn exec:java -Dexec.args="12345"
 *
 * Responsabilités :
 *  - Accepter les connexions des clients
 *  - Gérer l'état de la grille (couleurs + embargo)
 *  - Broadcaster les mises à jour à tous les clients connectés
 */
public class Serveur {

    // Durée d'embargo par pixel : 1 minute
    public static final long EMBARGO_DURATION_MS = 60_000L;

    // Taille de la grille
    public static final int GRID_COLS = 20;
    public static final int GRID_ROWS = 20;

    private final int port;

    /** pseudo → handler du client */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    /** "col,row" → couleur hex actuelle du pixel */
    private final Map<String, String> pixelColors = new ConcurrentHashMap<>();

    /** "col,row" → timestamp (ms) de fin d'embargo */
    private final Map<String, Long> pixelEmbargoEnd = new ConcurrentHashMap<>();

    public Serveur(int port) {
        this.port = port;
    }

    // ─── Démarrage ───────────────────────────────────────────────────────────

    public void start() {
        System.out.println("[Serveur] Démarrage sur le port " + port);
        System.out.println("[Serveur] Grille " + GRID_COLS + "x" + GRID_ROWS
                + " | Embargo pixel = " + EMBARGO_DURATION_MS / 1000 + "s");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("[Serveur] Nouvelle connexion depuis "
                            + socket.getInetAddress().getHostAddress());
                    ClientHandler handler = new ClientHandler(socket, this);
                    Thread t = new Thread(handler, "client-" + socket.getPort());
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    System.err.println("[Serveur] Erreur acceptation : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Serveur] Impossible de démarrer : " + e.getMessage());
            System.exit(1);
        }
    }

    // ─── Gestion des clients ─────────────────────────────────────────────────

    /**
     * Enregistre un client. Retourne false si le pseudo est déjà pris.
     */
    public synchronized boolean registerClient(String pseudo, ClientHandler handler) {
        if (clients.containsKey(pseudo)) {
            return false;
        }
        clients.put(pseudo, handler);
        System.out.println("[Serveur] Client enregistré : " + pseudo
                + " (" + clients.size() + " connecté(s))");
        return true;
    }

    public synchronized void removeClient(String pseudo) {
        if (pseudo != null && clients.remove(pseudo) != null) {
            System.out.println("[Serveur] Client déconnecté : " + pseudo
                    + " (" + clients.size() + " connecté(s))");
        }
    }

    // ─── Logique de la grille ────────────────────────────────────────────────

    /**
     * Tente de colorier un pixel.
     *
     * @return true si réussi (le pixel est libre), false si sous embargo
     */
    public synchronized boolean colorPixel(String pseudo, int x, int y, String color) {
        String key = x + "," + y;
        Long end = pixelEmbargoEnd.get(key);

        if (end != null && System.currentTimeMillis() < end) {
            // Pixel sous embargo
            return false;
        }

        // Appliquer la couleur et démarrer l'embargo
        pixelColors.put(key, color);
        long embargoEnd = System.currentTimeMillis() + EMBARGO_DURATION_MS;
        pixelEmbargoEnd.put(key, embargoEnd);

        // Broadcaster à tous les clients
        Message update = Message.pixelUpdate(pseudo, x, y, color, EMBARGO_DURATION_MS);
        broadcast(update);

        System.out.println("[Serveur] Pixel (" + x + "," + y + ") colorié par "
                + pseudo + " en " + color);
        return true;
    }

    /**
     * Retourne le temps d'embargo restant (en ms) pour un pixel donné.
     * Retourne 0 si pas d'embargo actif.
     */
    public long getRemainingEmbargo(int x, int y) {
        Long end = pixelEmbargoEnd.get(x + "," + y);
        if (end == null) return 0L;
        long remaining = end - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    // ─── Broadcast ──────────────────────────────────────────────────────────

    public synchronized void broadcast(Message message) {
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(message);
        }
    }

    // ─── Accès à l'état courant (pour les nouveaux clients) ─────────────────

    public Map<String, String> getPixelColors() {
        return new ConcurrentHashMap<>(pixelColors);
    }

    public Map<String, Long> getPixelEmbargoEnd() {
        return new ConcurrentHashMap<>(pixelEmbargoEnd);
    }

    // ─── Main ────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage : java com.pixelwar.server.Serveur <port>");
            System.err.println("Exemple : java com.pixelwar.server.Serveur 12345");
            System.exit(1);
        }
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            new Serveur(port).start();
        } catch (NumberFormatException e) {
            System.err.println("Port invalide : " + args[0] + " (doit être entre 1 et 65535)");
            System.exit(1);
        }
    }
}
