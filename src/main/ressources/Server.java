import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//serveur tcp principal du jeu pixel battle
//run : java Server <port>
//
//proto texte (1 ligne = 1 message)
//client -> serveur
//  connect <pseudo>
//  pixel <row> <col> <#rrggbb>
//  disconnect
//
//serveur -> client
//  connected <pseudo>
//  update <row> <col> <#rrggbb> <pseudo>
//  state <row> <col> <#rrggbb> <secondesrestantes>
//  busy <row> <col> <secondesrestantes>
//  error <message>
public class Server {

    //constantes
    private static final int COOLDOWN_PIXEL_MS = 60000; //1 minute en millisecondes
    //temps max d'attente pour qu'un pseudo soit libere après deconnexion (en ms) 
    //(pour eviter que quelqu'un se connecte avec un pseudo deja utilise pendant que le serveur traite la deconnexion du pseudo en question)
    private static final int ATTENTE_LIBERATION_PSEUDO_MS = 1200; 

    //etat partage entre threads

    //clients connectes : pseudo -> handler
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    //dernier timestamp par case : row,col -> timestamp
    private final Map<String, Long> pixelTimestamps = new ConcurrentHashMap<>();

    //couleur actuelle par case : row,col -> #rrggbb
    private final Map<String, String> pixelCouleurs = new ConcurrentHashMap<>();

    //verrou pour etat initial + ecritures pixels
    private final Object etatLock = new Object();

    //demarrage

    public void demarrer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Serveur démarré sur le port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                //thread dedie par client
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            log("Erreur serveur : " + e.getMessage());
        }
    }

    //envoie a tous les clients
    private void diffuserATous(String message) {
        for (ClientHandler handler : clients.values()) {
            handler.envoyer(message);
        }
    }

    //gestion pixels

    private String clePixel(int row, int col) {
        return row + "," + col;
    }

    //donne temps restant pour case
    private int secondesRestantes(int row, int col) {
        String cle = clePixel(row, col);
        Long timestamp = pixelTimestamps.get(cle);
        if (timestamp == null) return 0;
        long temps_ecoule = System.currentTimeMillis() - timestamp;
        if (temps_ecoule >= COOLDOWN_PIXEL_MS) return 0;
        return (int) ((COOLDOWN_PIXEL_MS - temps_ecoule) / 1000) + 1;
    }

    //marque case comme utilisee maintenant
    private void reserverPixel(int row, int col) {
        pixelTimestamps.put(clePixel(row, col), System.currentTimeMillis());
    }

    //envoie etat grille au client
    private void envoyerEtatInitial(ClientHandler handler) {
        for (Map.Entry<String, String> entry : pixelCouleurs.entrySet()) {
            String[] coords = entry.getKey().split(",");
            if (coords.length != 2) continue;
            try {
                int row = Integer.parseInt(coords[0]);
                int col = Integer.parseInt(coords[1]);
                int restant = secondesRestantes(row, col);
                handler.envoyer(String.format("STATE %d %d %s %d", row, col, entry.getValue(), restant));
            } catch (NumberFormatException e) {
                log("Coordonnees invalides dans l'etat initial : " + entry.getKey());
            }
        }
    }

    //logging

    private static void log(String message) {
        String heure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + heure + "] " + message);
    }

    //handler client

    private class ClientHandler implements Runnable {

        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String pseudo = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String ligne;
                while ((ligne = in.readLine()) != null) {
                    traiterMessage(ligne.trim());
                }

            } catch (IOException e) {
                //perte connexion non prevue
                if (pseudo != null)
                    log("Connexion perdue : " + pseudo);
            } finally {
                deconnecter();
            }
        }

        //parse puis traite message client
        private void traiterMessage(String message) {
            if (message.isEmpty()) return;
            String[] parts = message.split(" ");

            switch (parts[0]) {

                case "CONNECT":
                    //connect <pseudo>
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

                        if (pseudoDemande.matches(".*\\s+.*")) { //regex trouvé en ligne pour verif presence d'espaces dans pseudo
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

                case "PIXEL":
                    //pixel <row> <col> <#rrggbb>
                    if (pseudo == null) {
                        envoyer("ERROR Non identifié.");
                        break;
                    }
                    if (parts.length >= 4) {
                        try {
                            int ligne = Integer.parseInt(parts[1]);
                            int col = Integer.parseInt(parts[2]);
                            String hex = parts[3];

                            int restant;
                            String update = null;
                            synchronized (etatLock) {
                                restant = secondesRestantes(ligne, col);
                                if (restant <= 0) {
                                    reserverPixel(ligne, col);
                                    pixelCouleurs.put(clePixel(ligne, col), hex);
                                    update = String.format("UPDATE %d %d %s %s",
                                        ligne, col, hex, pseudo);
                                }
                            }

                            if (restant > 0) {
                                //case encore en cooldown => refus
                                envoyer(String.format("BUSY %d %d %d", ligne, col, restant));
                                log("BUSY pixel (" + ligne + "," + col + ") pour " + pseudo
                                    + " — " + restant + "s restantes");
                            } else {
                                //case libre => accepte puis diffuse a tous
                                diffuserATous(update); //inclut expediteur
                                log("PIXEL (" + ligne + "," + col + ") " + hex
                                    + " par " + pseudo);
                            }
                        } catch (NumberFormatException e) {
                            envoyer("ERROR Message PIXEL malformé.");
                        }
                    } else {
                        envoyer("ERROR Message PIXEL incomplet.");
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
            }
        }

        //envoie message a ce client
        void envoyer(String message) {
            if (out != null) out.println(message);
        }

        //ferme connexion client proprement
        private void deconnecter() {
            if (pseudo != null) {
                clients.remove(pseudo, this);
                pseudo = null;
            }
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //main

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage : java Server <port>");
            System.exit(1);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Port invalide : " + args[0]);
            System.exit(1);
            return;
        }
        new Server().demarrer(port);
    }
}