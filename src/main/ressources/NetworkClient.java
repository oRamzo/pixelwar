import javafx.application.Platform;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

//client tcp du jeu
//ecoute serveur en arriere plan et met a jour ui
public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private GrilleController grilleController;
    private Thread threadEcoute;
    private boolean connecte = false;
    private String monPseudo;

    //connexion

    //ouvre connexion tcp et envoie pseudo
    //@throws ioexception si serveur injoignable
    public NetworkClient(String adresse, int port, String pseudo) throws IOException {
        socket = new Socket(adresse, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connecte = true;
        monPseudo = pseudo;

        //identification serveur
        envoyerMessage("CONNECT " + pseudo);
    }

    //lien avec controller

    //branche controller puis lance ecoute
    public void setGrilleController(GrilleController controller) {
        this.grilleController = controller;
        demarrerEcoute();
    }

    //envoi messages

    //envoie demande coloriage au serveur
    public void envoyerPixel(int row, int col, Color couleur) {
        envoyerMessage(String.format("PIXEL %d %d %s", row, col, couleurVersHex(couleur)));
    }

    //demande deconnexion propre
    public void deconnecter() {
        envoyerMessage("DISCONNECT");
        fermer();
    }

    //envoi texte brut vers serveur
    private void envoyerMessage(String message) {
        if (out != null && connecte) {
            out.println(message);
        }
    }

    //ecoute serveur

    //lance thread qui lit messages tant que connecte
    private void demarrerEcoute() {
        threadEcoute = new Thread(() -> {
            try {
                String ligne;
                while (connecte && (ligne = in.readLine()) != null) {
                    traiterMessage(ligne);
                }
            } catch (IOException e) {
                if (connecte) {
                    //deconnexion non prevue
                    Platform.runLater(() -> {
                        if (grilleController != null) {
                            grilleController.setStatutConnecte(false);
                        }
                    });
                }
            }
        });
        threadEcoute.setDaemon(true); //thread s arrete avec appli
        threadEcoute.start();
    }

    //parse puis redirige message serveur vers ui
    private void traiterMessage(String message) {
        String[] parts = message.split(" ");
        if (parts.length == 0) return;

        switch (parts[0]) {

            case "UPDATE":
                //format update row col #rrggbb pseudo
                if (parts.length >= 4) {
                    try {
                        int row       = Integer.parseInt(parts[1]);
                        int col       = Integer.parseInt(parts[2]);
                        Color couleur = Color.web(parts[3]);
                        boolean estMoi = parts.length >= 5 && parts[4].equals(monPseudo);
                        Platform.runLater(() -> {
                            if (grilleController != null)
                                grilleController.recevoirPixelDistant(row, col, couleur, estMoi);
                        });
                    } catch (Exception e) {
                        System.err.println("Message UPDATE malformé : " + message);
                    }
                }
                break;

            case "STATE":
                //format state row col #rrggbb secondesrestantes
                if (parts.length >= 5) {
                    try {
                        int row              = Integer.parseInt(parts[1]);
                        int col              = Integer.parseInt(parts[2]);
                        Color couleur        = Color.web(parts[3]);
                        int secondesRestantes = Integer.parseInt(parts[4]);
                        Platform.runLater(() -> {
                            if (grilleController != null)
                                grilleController.appliquerEtatInitialPixel(row, col, couleur, secondesRestantes);
                        });
                    } catch (Exception e) {
                        System.err.println("Message STATE malformé : " + message);
                    }
                }
                break;

            case "BUSY":
                //format busy row col secondesrestantes
                if (parts.length >= 4) {
                    try {
                        int row      = Integer.parseInt(parts[1]);
                        int col      = Integer.parseInt(parts[2]);
                        int secondes = Integer.parseInt(parts[3]);
                        Platform.runLater(() -> {
                            if (grilleController != null)
                                grilleController.recevoirBusy(row, col, secondes);
                        });
                    } catch (Exception e) {
                        System.err.println("Message BUSY malformé : " + message);
                    }
                }
                break;

            case "ERROR":
                //format error message libre
                String erreur = message.substring("ERROR ".length());
                Platform.runLater(() -> {
                    if (grilleController != null)
                        grilleController.afficherErreurReseau(erreur);
                });
                break;

            default:
                System.err.println("Message inconnu reçu : " + message);
                break;
        }
    }

    //fermeture

    public void fermer() {
        connecte = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnecte() {
        return connecte;
    }

    //couleur vers hex

    //convertit couleur javafx en #rrggbb
    private String couleurVersHex(Color c) {
        return String.format("#%02X%02X%02X",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }
}