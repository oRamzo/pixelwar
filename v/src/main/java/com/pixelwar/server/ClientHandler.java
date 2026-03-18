package com.pixelwar.server;

import com.pixelwar.model.Message;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

/**
 * Gère la communication avec UN client connecté.
 * Tourne dans son propre thread (daemon).
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Serveur serveur;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String pseudo;

    public ClientHandler(Socket socket, Serveur serveur) {
        this.socket = socket;
        this.serveur = serveur;
    }

    // ─── Boucle principale ────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // IMPORTANT : créer le OOS avant l'OIS pour éviter le deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // 1) Attendre le message CONNECT
            Message firstMsg = (Message) in.readObject();
            if (firstMsg.getType() != Message.Type.CONNECT) {
                socket.close();
                return;
            }

            pseudo = firstMsg.getPseudo().trim();

            // Vérifier que le pseudo est valide et disponible
            if (pseudo.isEmpty()) {
                sendMessage(Message.connectError("Pseudo invalide (vide)."));
                socket.close();
                return;
            }
            if (!serveur.registerClient(pseudo, this)) {
                sendMessage(Message.connectError("Le pseudo \"" + pseudo + "\" est déjà utilisé."));
                socket.close();
                return;
            }

            // 2) Confirmer la connexion
            sendMessage(Message.connectOk());

            // 3) Envoyer l'état actuel de la grille au nouveau client
            sendCurrentGridState();

            // 4) Boucle de lecture des messages du client
            while (!socket.isClosed()) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }

        } catch (EOFException | SocketException e) {
            // Déconnexion normale ou crash du client
            System.out.println("[Serveur] Client perdu (EOF/Socket) : " + pseudo);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Serveur] Erreur client " + pseudo + " : " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ─── Traitement des messages ──────────────────────────────────────────────

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case PIXEL_COLOR:
                handlePixelColor(msg);
                break;

            case DISCONNECT:
                cleanup();
                break;

            default:
                System.out.println("[Serveur] Message inattendu de " + pseudo
                        + " : " + msg.getType());
        }
    }

    private void handlePixelColor(Message msg) {
        int x = msg.getX();
        int y = msg.getY();
        String color = msg.getColor();

        // Vérifier les bornes
        if (x < 0 || x >= Serveur.GRID_COLS || y < 0 || y >= Serveur.GRID_ROWS) {
            System.err.println("[Serveur] Pixel hors grille de " + pseudo
                    + " : (" + x + "," + y + ")");
            return;
        }

        boolean success = serveur.colorPixel(pseudo, x, y, color);
        if (!success) {
            // Pixel sous embargo : notifier uniquement ce client
            long remaining = serveur.getRemainingEmbargo(x, y);
            sendMessage(Message.pixelEmbargo(x, y, remaining));
            System.out.println("[Serveur] Pixel (" + x + "," + y
                    + ") refusé pour " + pseudo + " : embargo " + remaining / 1000 + "s restant");
        }
    }

    // ─── État initial de la grille ────────────────────────────────────────────

    /**
     * Envoie à ce client tous les pixels déjà coloriés (avec leur embargo si actif).
     */
    private void sendCurrentGridState() {
        Map<String, String> colors = serveur.getPixelColors();
        Map<String, Long> embargoEnds = serveur.getPixelEmbargoEnd();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, String> entry : colors.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            String color = entry.getValue();

            long end = embargoEnds.getOrDefault(entry.getKey(), now);
            long remaining = Math.max(0L, end - now);

            sendMessage(Message.pixelUpdate("?", x, y, color, remaining));
        }
    }

    // ─── Envoi de messages ────────────────────────────────────────────────────

    /**
     * Envoi thread-safe d'un message au client.
     */
    public synchronized void sendMessage(Message message) {
        try {
            if (out != null && !socket.isClosed()) {
                out.writeObject(message);
                out.flush();
                out.reset(); // Évite le cache des objets dans OOS
            }
        } catch (IOException e) {
            System.err.println("[Serveur] Erreur d'envoi à " + pseudo + " : " + e.getMessage());
        }
    }

    // ─── Nettoyage ────────────────────────────────────────────────────────────

    private void cleanup() {
        serveur.removeClient(pseudo);
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignoré
        }
    }
}
