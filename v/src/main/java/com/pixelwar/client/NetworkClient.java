package com.pixelwar.client;

import com.pixelwar.model.Message;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * Gère la connexion TCP avec le serveur depuis le côté client.
 *
 * Tourne dans un thread séparé pour la réception des messages.
 * Utilise un listener pour notifier le contrôleur JavaFX.
 *
 * IMPORTANT : les callbacks du listener sont appelés depuis un thread réseau.
 * Il faut utiliser Platform.runLater() dans le contrôleur pour toute màj de l'UI.
 */
public class NetworkClient {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean running = false;

    private MessageListener listener;

    // ─── Listener ─────────────────────────────────────────────────────────────

    /**
     * Interface de callbacks pour les événements réseau.
     */
    public interface MessageListener {
        /** Appelé quand un message est reçu du serveur. */
        void onMessage(Message message);

        /** Appelé quand la connexion est perdue (EOF, SocketException, etc.). */
        void onConnectionLost();
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    // ─── Connexion ────────────────────────────────────────────────────────────

    /**
     * Se connecte au serveur et envoie le message CONNECT.
     * Bloque jusqu'à la réception de la réponse (OK ou ERROR).
     *
     * @throws IOException si la connexion échoue ou si le serveur refuse (pseudo pris, etc.)
     */
    public void connect(String host, int port, String pseudo) throws IOException {
        socket = new Socket(host, port);

        // Créer OOS avant OIS (même ordre que dans ClientHandler)
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        // Envoyer la demande de connexion
        sendMessage(Message.connect(pseudo));

        // Lire la réponse de manière synchrone (on est encore dans le thread UI via un thread dédié)
        try {
            Message response = (Message) in.readObject();
            if (response.getType() == Message.Type.CONNECT_OK) {
                startListening();
            } else if (response.getType() == Message.Type.CONNECT_ERROR) {
                socket.close();
                throw new IOException(response.getErrorMessage());
            } else {
                socket.close();
                throw new IOException("Réponse inattendue du serveur : " + response.getType());
            }
        } catch (ClassNotFoundException e) {
            socket.close();
            throw new IOException("Erreur de protocole : classe inconnue", e);
        }
    }

    // ─── Écoute en arrière-plan ───────────────────────────────────────────────

    private void startListening() {
        running = true;
        Thread listenerThread = new Thread(() -> {
            try {
                while (running && !socket.isClosed()) {
                    Message msg = (Message) in.readObject();
                    if (listener != null) {
                        listener.onMessage(msg);
                    }
                }
            } catch (EOFException | SocketException e) {
                // Connexion fermée proprement ou plantage réseau
                if (running && listener != null) {
                    listener.onConnectionLost();
                }
            } catch (IOException | ClassNotFoundException e) {
                if (running && listener != null) {
                    System.err.println("[NetworkClient] Erreur lecture : " + e.getMessage());
                    listener.onConnectionLost();
                }
            }
        }, "network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // ─── Envoi de messages ────────────────────────────────────────────────────

    /**
     * Envoie un message au serveur de manière thread-safe.
     */
    public synchronized void sendMessage(Message message) {
        try {
            if (out != null && socket != null && !socket.isClosed()) {
                out.writeObject(message);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.err.println("[NetworkClient] Erreur d'envoi : " + e.getMessage());
        }
    }

    // ─── Déconnexion ─────────────────────────────────────────────────────────

    /**
     * Déconnecte proprement du serveur.
     */
    public void disconnect() {
        running = false;
        try {
            if (isConnected()) {
                sendMessage(Message.disconnect());
                socket.close();
            }
        } catch (IOException e) {
            // Ignoré
        }
    }

    // ─── État ────────────────────────────────────────────────────────────────

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
