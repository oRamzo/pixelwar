import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ConnexionController implements Initializable {

    //champs et labels fxml
    @FXML private TextField fieldPseudo;
    @FXML private TextField fieldAdresse;
    @FXML private TextField fieldPort;

    @FXML private Label erreurPseudo;
    @FXML private Label erreurAdresse;
    @FXML private Label erreurPort;
    @FXML private Label erreurGenerale;

    @FXML private Button boutonConnexion;

    //initialisation

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //active bouton seulement si tous champs remplis
        Runnable miseAJourBouton = () -> {
            boolean vide = fieldPseudo.getText().trim().isEmpty()
                        || fieldAdresse.getText().trim().isEmpty()
                        || fieldPort.getText().trim().isEmpty();
            boutonConnexion.setDisable(vide);
        };
        fieldPseudo.textProperty().addListener((obs, o, n)  -> { cacherErreur(erreurPseudo);  miseAJourBouton.run(); });
        fieldAdresse.textProperty().addListener((obs, o, n) -> { cacherErreur(erreurAdresse); miseAJourBouton.run(); });
        fieldPort.textProperty().addListener((obs, o, n)    -> { cacherErreur(erreurPort);    miseAJourBouton.run(); });

        //au chargement bouton desactive
        boutonConnexion.setDisable(true);
    }

    //action bouton connexion

    @FXML
    private void handleConnexion() {
        //vide erreurs avant validation
        cacherErreur(erreurPseudo);
        cacherErreur(erreurAdresse);
        cacherErreur(erreurPort);
        cacherErreur(erreurGenerale);

        //validation champs
        boolean valide = true;

        String pseudo = fieldPseudo.getText().trim();
        if (pseudo.isEmpty()) {
            afficherErreur(erreurPseudo, "Le pseudo ne peut pas être vide.");
            valide = false;
        } else if (pseudo.matches(".*\\s+.*")) { //regex trouvé en ligne pour verif presence d'espaces dans pseudo
            afficherErreur(erreurPseudo, "Le pseudo ne doit pas contenir d'espaces.");
            valide = false;
        } else if (pseudo.length() > 20) {
            afficherErreur(erreurPseudo, "Le pseudo ne doit pas dépasser 20 caractères.");
            valide = false;
        }

        String adresse = fieldAdresse.getText().trim();
        if (adresse.isEmpty()) {
            afficherErreur(erreurAdresse, "L'adresse ne peut pas être vide.");
            valide = false;
        }

        int port = -1;
        String portTexte = fieldPort.getText().trim();
        try {
            port = Integer.parseInt(portTexte);
            if (port < 1 || port > 100000) {
                afficherErreur(erreurPort, "Le port doit être entre 1 et 100000.");
                valide = false;
            }
        } catch (NumberFormatException e) {
            afficherErreur(erreurPort, "Le port doit être un nombre entier.");
            valide = false;
        }

        if (!valide) return;

        //tentative connexion
        boutonConnexion.setDisable(true);
        boutonConnexion.setText("Connexion en cours...");

        final String adresseFinal = adresse;
        final int portFinal = port;
        final String pseudoFinal = pseudo;

        //lance connexion dans thread separe pour pas bloquer ui
        new Thread(() -> {
            try {
                NetworkClient networkClient = new NetworkClient(adresseFinal, portFinal, pseudoFinal);

                Platform.runLater(() -> ouvrirGrille(pseudoFinal, networkClient));

            } catch (IOException e) {
                final String message = (e.getMessage() != null && !e.getMessage().trim().isEmpty())
                    ? e.getMessage()
                    : "Impossible de se connecter au serveur. Vérifiez l'adresse et le port.";

                javafx.application.Platform.runLater(() -> {
                    afficherErreur(erreurGenerale, message);
                    boutonConnexion.setDisable(false);
                    boutonConnexion.setText("Se connecter");
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    afficherErreur(erreurGenerale,
                        "Impossible de se connecter au serveur. Vérifiez l'adresse et le port.");
                    boutonConnexion.setDisable(false);
                    boutonConnexion.setText("Se connecter");
                });
            }
        }).start();
    }

    //navigation vers grille

    private void ouvrirGrille(String pseudo, NetworkClient networkClient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("grille.fxml"));
            Parent root = loader.load();
            GrilleController grilleController = loader.getController();
            grilleController.setPseudo(pseudo);
            grilleController.setNetworkClient(networkClient);
            Stage stage = (Stage) boutonConnexion.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
        } catch (Exception e) {
            afficherErreur(erreurGenerale, "Erreur lors du chargement de l'interface.");
            boutonConnexion.setDisable(false);
            boutonConnexion.setText("Se connecter");
        }
    }

    //utilitaires erreurs

    private void afficherErreur(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void cacherErreur(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }
}