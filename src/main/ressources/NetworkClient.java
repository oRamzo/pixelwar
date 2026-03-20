import javafx.application.Platform;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

//client tcp du jeu
//ecoute serveur en arriere plan et met a jour ui
public class NetworkClient {

    private static final int TIMEOUT_HANDSHAKE_MS = 3000;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private GrilleController grilleController;
    private Thread threadEcoute; //thread qui ecoute le serveur en arriere plan
    private boolean connecte = false;
    private String monPseudo;

    //connexion

    //ouvre connexion tcp et envoie pseudo
    //@throws ioexception si serveur injoignable
    public NetworkClient(String adresse, int port, String pseudo) throws IOException {
        try {
            socket = new Socket(adresse, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            connecte = true;
            monPseudo = pseudo;

            //identification serveur + attente validation
            socket.setSoTimeout(TIMEOUT_HANDSHAKE_MS);
            envoyerObjet("CONNECT " + pseudo);

            Object reponseObjet = in.readObject();
            if (!(reponseObjet instanceof String)) {
                throw new IOException("Réponse inattendue du serveur.");
            }

            String reponse = (String) reponseObjet;
            if (reponse.startsWith("ERROR ")) {
                String erreur = reponse.substring("ERROR ".length()).trim();
                throw new IOException(erreur.isEmpty() ? "Connexion refusée par le serveur." : erreur);
            }
            if (!reponse.startsWith("CONNECTED")) {
                throw new IOException("Réponse inattendue du serveur.");
            }

            socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
            connecte = false;
            fermerSilencieusement();
            throw new IOException("Le serveur ne répond pas à la connexion.", e);
        } catch (ClassNotFoundException e) {
            connecte = false;
            fermerSilencieusement();
            throw new IOException("Objet réseau inconnu reçu du serveur.", e);
        } catch (IOException e) {
            connecte = false;
            fermerSilencieusement();
            throw e;
        }
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
        envoyerObjet(Pixel.pixelDemande(row, col, couleurVersHex(couleur)));
    }

    //demande deconnexion propre
    public void deconnecter() {
        envoyerObjet("DISCONNECT");
        fermer();
    }

    //envoi objet vers serveur
    private synchronized void envoyerObjet(Object objet) {
        if (out != null && connecte) {
            try {
                out.writeObject(objet);
                out.flush();
                out.reset();
            } catch (IOException e) {
                connecte = false;
            }
        }
    }

    //ecoute serveur

    //lance thread qui lit messages tant que connecté
    private void demarrerEcoute() {
        threadEcoute = new Thread(() -> {
            try {
                Object objet;
                while (connecte && (objet = in.readObject()) != null) {
                    traiterMessage(objet);
                }
            } catch (IOException | ClassNotFoundException e) {
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

    //redirige message serveur vers ui
    private void traiterMessage(Object message) {
        if (message instanceof Pixel) {
            traiterPixel((Pixel) message);
            return;
        }

        if (message instanceof String) {
            traiterTexte((String) message);
            return;
        }

        System.err.println("Objet inconnu reçu : " + message);
    }

    private void traiterPixel(Pixel pixel) {
        switch (pixel.getType()) {
            case UPDATE:
                try {
                    int row = pixel.getRow();
                    int col = pixel.getCol();
                    Color couleur = Color.web(pixel.getCouleurHex());
                    boolean estMoi = pixel.getPseudo() != null && pixel.getPseudo().equals(monPseudo);
                    Platform.runLater(() -> {
                        if (grilleController != null)
                            grilleController.recevoirPixelDistant(row, col, couleur, estMoi);
                    });
                } catch (Exception e) {
                    System.err.println("Objet UPDATE malformé.");
                }
                break;

            case STATE:
                try {
                    int row  = pixel.getRow();
                    int col = pixel.getCol();
                    Color couleur = Color.web(pixel.getCouleurHex());
                    int secondesRestantes = pixel.getSecondesRestantes();
                    Platform.runLater(() -> {
                        if (grilleController != null)
                            grilleController.appliquerEtatInitialPixel(row, col, couleur, secondesRestantes);
                    });
                } catch (Exception e) {
                    System.err.println("Objet STATE malformé.");
                }
                break;

            case BUSY:
                try {
                    int row = pixel.getRow();
                    int col = pixel.getCol();
                    int secondes = pixel.getSecondesRestantes();
                    Platform.runLater(() -> {
                        if (grilleController != null)
                            grilleController.recevoirBusy(row, col, secondes);
                    });
                } catch (Exception e) {
                    System.err.println("Objet BUSY malformé.");
                }
                break;

            default:
                System.err.println("Type de pixel inattendu reçu : " + pixel.getType());
                break;
        }
    }

    private void traiterTexte(String message) {
        if (message.startsWith("ERROR ")) {
            String erreur = message.substring("ERROR ".length());
            Platform.runLater(() -> {
                if (grilleController != null)
                    grilleController.afficherErreurReseau(erreur);
            });
            return;
        }

        if (message.startsWith("CONNECTED")) {
            return;
        }

        System.err.println("Message texte inconnu reçu : " + message);
    }

    //fermeture

    //ferme connexion et arrete thread ecoute
    public void fermer() {
        connecte = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //fermeture sans afficher d'erreur (utilisee en cas de probleme de connexion initiale)
    private void fermerSilencieusement() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    //getter statut connexion
    public boolean isConnecte() {
        return connecte;
    }

    //couleur vers hex

    //convertit couleur javafx en #rrggbb
    private String couleurVersHex(Color c) {
        return String.format("#%02X%02X%02X", (int)(c.getRed()   * 255),
                                                (int)(c.getGreen() * 255),
                                                (int)(c.getBlue()  * 255));
    }
}
