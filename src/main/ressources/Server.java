import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//serveur tcp principal du jeu pixel battle
//run : java Server <port>
//
//client -> serveur
//  connect <pseudo>
//  pixel (objet Pixel de type PIXEL)
//  disconnect
//
//serveur -> client
//  connected <pseudo>
//  pixel (objet Pixel de type UPDATE, STATE ou BUSY)
//  error <message>

public class Server {

    private static final int COOLDOWN_PIXEL_MS = 60000;
    private static final int ATTENTE_LIBERATION_PSEUDO_MS = 1200;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Long> pixelTimestamps = new ConcurrentHashMap<>();
    private final Map<String, String> pixelCouleurs = new ConcurrentHashMap<>();
    private final Map<String, String> pixelProprietaires = new ConcurrentHashMap<>();
    private final Object etatLock = new Object();

    public void demarrer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Serveur démarré sur le port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            log("Erreur serveur : " + e.getMessage());
        }
    }

    private void diffuserATous(Pixel pixel) {
        for (ClientHandler handler : clients.values()) {
            handler.envoyer(pixel);
        }
    }

    private String clePixel(int row, int col) {
        return row + "," + col;
    }

    private int secondesRestantes(int row, int col) {
        String cle = clePixel(row, col);
        Long timestamp = pixelTimestamps.get(cle);
        if (timestamp == null) return 0;
        long tempsEcoule = System.currentTimeMillis() - timestamp;
        if (tempsEcoule >= COOLDOWN_PIXEL_MS) return 0;
        return (int) ((COOLDOWN_PIXEL_MS - tempsEcoule) / 1000) + 1;
    }

    private void reserverPixel(int row, int col, String pseudo) {
        String cle = clePixel(row, col);
        pixelTimestamps.put(cle, System.currentTimeMillis());
        pixelProprietaires.put(cle, pseudo);
    }

    private String proprietairePixel(int row, int col) {
        return pixelProprietaires.get(clePixel(row, col));
    }

    private void envoyerEtatInitial(ClientHandler handler) {
        for (Map.Entry<String, String> entry : pixelCouleurs.entrySet()) {
            String[] coords = entry.getKey().split(",");
            if (coords.length != 2) continue;
            try {
                int row = Integer.parseInt(coords[0]);
                int col = Integer.parseInt(coords[1]);
                int restant = secondesRestantes(row, col);
                String pseudo = proprietairePixel(row, col);
                handler.envoyer(Pixel.pixelState(row, col, entry.getValue(), pseudo, restant));
            } catch (NumberFormatException e) {
                log("Coordonnees invalides dans l'etat initial : " + entry.getKey());
            }
        }
    }

    private static void log(String message) {
        String heure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + heure + "] " + message);
    }

    private class ClientHandler implements Runnable {

        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String pseudo = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in  = new ObjectInputStream(socket.getInputStream());

                Object objet;
                while ((objet = in.readObject()) != null) {
                    traiterMessage(objet);
                }

            } catch (IOException e) {
                if (pseudo != null)
                    log("Connexion perdue : " + pseudo);
            } catch (ClassNotFoundException e) {
                envoyer("ERROR Objet réseau inconnu.");
            } finally {
                deconnecter();
            }
        }

        private void traiterMessage(Object message) {
            if (message instanceof String) {
                traiterMessageTexte(((String) message).trim());
                return;
            }

            if (message instanceof Pixel) {
                traiterMessagePixel((Pixel) message);
                return;
            }

            envoyer("ERROR Message inconnu.");
        }

        private void traiterMessageTexte(String message) {
            if (message.isEmpty()) return;
            String[] parts = message.split(" ");

            switch (parts[0]) {
                case "CONNECT":
                    if (parts.length >= 2) {
                        if (pseudo != null) {
                            envoyer("ERROR Déjà connecté.");
                            break;
                        }

                        String pseudoDemande = message.substring("CONNECT".length()).trim();
                        if (pseudoDemande.isEmpty()) {
                            envoyer("ERROR Pseudo manquant.");
                            break;
                        }

                        if (pseudoDemande.matches(".*\s+.*")) {
                            envoyer("ERROR Le pseudo ne doit pas contenir d'espaces.");
                            break;
                        }

                        if (!reserverPseudoAvecAttente(pseudoDemande)) {
                            envoyer("ERROR Pseudo déjà utilisé.");
                            log("Connexion refusée (pseudo déjà utilisé) : "
                                + pseudoDemande + " (" + socket.getInetAddress() + ")");
                            deconnecter();
                            break;
                        }

                        pseudo = pseudoDemande;
                        envoyer("CONNECTED " + pseudo);
                        synchronized (etatLock) {
                            envoyerEtatInitial(this);
                        }
                        log("Connecté : " + pseudo + " (" + socket.getInetAddress() + ")");
                    } else {
                        envoyer("ERROR Pseudo manquant.");
                    }
                    break;

                case "DISCONNECT":
                    log("Déconnexion : " + pseudo);
                    deconnecter();
                    break;

                default:
                    envoyer("ERROR Message inconnu : " + parts[0]);
                    log("Message inconnu de " + pseudo + " : " + message);
                    break;
            }
        }

        private void traiterMessagePixel(Pixel pixel) {
            if (pixel.getType() != Pixel.Type.PIXEL) {
                envoyer("ERROR Type de pixel inattendu.");
                return;
            }

            if (pseudo == null) {
                envoyer("ERROR Non identifié.");
                return;
            }

            int ligne = pixel.getRow();
            int col = pixel.getCol();
            String hex = pixel.getCouleurHex();

            int restant;
            String proprietaire;
            Pixel update = null;
            synchronized (etatLock) {
                restant = secondesRestantes(ligne, col);
                if (restant <= 0) {
                    reserverPixel(ligne, col, pseudo);
                    pixelCouleurs.put(clePixel(ligne, col), hex);
                    update = Pixel.pixelUpdate(ligne, col, hex, pseudo);
                    proprietaire = pseudo;
                } else {
                    proprietaire = proprietairePixel(ligne, col);
                }
            }

            if (restant > 0) {
                envoyer(Pixel.pixelBusy(ligne, col, proprietaire, restant));
                log("BUSY pixel (" + ligne + "," + col + ") pour " + pseudo
                    + " — " + restant + "s restantes");
            } else {
                diffuserATous(update);
                log("PIXEL (" + ligne + "," + col + ") " + hex + " par " + pseudo);
            }
        }

        private boolean reserverPseudoAvecAttente(String pseudoDemande) {
            long deadline = System.currentTimeMillis() + ATTENTE_LIBERATION_PSEUDO_MS;

            while (true) {
                ClientHandler existant = clients.putIfAbsent(pseudoDemande, this);
                if (existant == null || existant == this) {
                    return true;
                }

                if (existant.socket.isClosed()) {
                    clients.remove(pseudoDemande, existant);
                    continue;
                }

                if (System.currentTimeMillis() >= deadline) {
                    return false;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        private synchronized void envoyer(Object objet) {
            try {
                if (out != null) {
                    out.writeObject(objet);
                    out.flush();
                    out.reset();
                }
            } catch (IOException e) {
                log("Erreur envoi à " + pseudo + " : " + e.getMessage());
            }
        }

        private void deconnecter() {
            if (pseudo != null) {
                clients.remove(pseudo, this);
                pseudo = null;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage : java Server <port>");
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            new Server().demarrer(port);
        } catch (NumberFormatException e) {
            System.out.println("Le port doit être un entier.");
        }
    }
}
