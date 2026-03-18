package com.pixelwar.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Contrôleur de la vue connexion.fxml.
 * Gère le formulaire de connexion au serveur.
 */
public class ConnexionController {

    @FXML private TextField fieldPseudo;
    @FXML private TextField fieldAdresse;
    @FXML private TextField fieldPort;
    @FXML private Button    boutonConnexion;
    @FXML private Label     labelErreur;

    // ─── Initialisation FXML ─────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Valeurs par défaut pratiques pour le développement
        fieldAdresse.setText("localhost");
        fieldPort.setText("12345");

        // Validation du champ port : chiffres seulement
        fieldPort.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*")) {
                fieldPort.setText(old);
            }
        });

        // Appuyer sur Entrée dans n'importe quel champ lance la connexion
        fieldPseudo.setOnAction(e -> onConnexion());
        fieldAdresse.setOnAction(e -> onConnexion());
        fieldPort.setOnAction(e -> onConnexion());
    }

    // ─── Action du bouton Connexion ───────────────────────────────────────────

    @FXML
    private void onConnexion() {
        String pseudo  = fieldPseudo.getText().trim();
        String adresse = fieldAdresse.getText().trim();
        String portStr = fieldPort.getText().trim();

        // Validation locale
        if (pseudo.isEmpty()) {
            afficherErreur("Veuillez entrer un pseudo.");
            fieldPseudo.requestFocus();
            return;
        }
        if (adresse.isEmpty()) {
            afficherErreur("Veuillez entrer l'adresse du serveur.");
            return;
        }
        if (portStr.isEmpty()) {
            afficherErreur("Veuillez entrer un port.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            afficherErreur("Port invalide (1–65535).");
            return;
        }

        // Désactiver le bouton et lancer la connexion en arrière-plan
        setFormDisabled(true);
        afficherInfo("Connexion en cours...");

        NetworkClient client = new NetworkClient();
        final int finalPort = port;

        Thread connectionThread = new Thread(() -> {
            try {
                client.connect(adresse, finalPort, pseudo);
                // Connexion réussie → ouvrir la grille dans le thread JavaFX
                Platform.runLater(() -> ouvrirGrille(client, pseudo));

            } catch (IOException e) {
                Platform.runLater(() -> {
                    afficherErreur("Impossible de se connecter : " + e.getMessage());
                    setFormDisabled(false);
                });
            }
        }, "connection-thread");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    // ─── Navigation vers la grille ────────────────────────────────────────────

    private void ouvrirGrille(NetworkClient client, String pseudo) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pixelwar/grille.fxml"));
            Parent root = loader.load();

            GrilleController controller = loader.getController();
            controller.init(client, pseudo);

            Stage stage = (Stage) boutonConnexion.getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Pixel War — " + pseudo);
            stage.setResizable(true);

        } catch (IOException e) {
            afficherErreur("Erreur lors du chargement de la grille.");
            setFormDisabled(false);
            e.printStackTrace();
        }
    }

    // ─── Utilitaires UI ──────────────────────────────────────────────────────

    private void setFormDisabled(boolean disabled) {
        fieldPseudo.setDisable(disabled);
        fieldAdresse.setDisable(disabled);
        fieldPort.setDisable(disabled);
        boutonConnexion.setDisable(disabled);
    }

    private void afficherErreur(String msg) {
        labelErreur.setStyle("-fx-text-fill: #ff6b6b;");
        labelErreur.setText(msg);
    }

    private void afficherInfo(String msg) {
        labelErreur.setStyle("-fx-text-fill: #a8d8a8;");
        labelErreur.setText(msg);
    }
}
