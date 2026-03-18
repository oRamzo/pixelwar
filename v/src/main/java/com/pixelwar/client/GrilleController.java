package com.pixelwar.client;

import com.pixelwar.model.Message;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur de la vue grille.fxml.
 *
 * Gère :
 *  - L'affichage et l'interaction avec la grille de pixels
 *  - Le cooldown utilisateur (30s, toute la grille désactivée)
 *  - L'embargo par pixel (60s, pixel grisé)
 *  - Le sélecteur de couleurs et les couleurs récentes
 *  - Les messages réseau via Platform.runLater()
 */
public class GrilleController {

    // ─── Constantes ──────────────────────────────────────────────────────────

    private static final int GRID_COLS    = 20;
    private static final int GRID_ROWS    = 20;
    private static final int CELL_SIZE    = 28;
    private static final int USER_COOLDOWN_SEC = 30;
    private static final int MAX_RECENT_COLORS = 6;

    // ─── Composants FXML ─────────────────────────────────────────────────────

    @FXML private Label     labelPseudo;
    @FXML private Label     labelStatut;
    @FXML private Circle    cercleStatut;

    @FXML private Rectangle rectCouleurSelectionnee;
    @FXML private Label     labelCouleurSelectionnee;
    @FXML private HBox      hboxCouleursRecentes;

    @FXML private HBox      hboxBanniere;
    @FXML private Label     labelBanniere;

    @FXML private GridPane  grille;
    @FXML private Button    boutonDeconnexion;

    // ─── État applicatif ─────────────────────────────────────────────────────

    private NetworkClient client;
    private String pseudo;

    /** Tableau 2D des cellules (rectangles) de la grille */
    private Rectangle[][] cells;

    /** Couleur sélectionnée par l'utilisateur */
    private Color selectedColor = Color.web("#3A86FF");

    /** Timestamp de fin d'embargo par pixel : "col,row" → ms */
    private final Map<String, Long> embargoEndTimes = new HashMap<>();

    /** Liste des couleurs récentes (du plus récent au plus ancien) */
    private final List<Color> recentColors = new ArrayList<>();

    /** Timeline du cooldown utilisateur */
    private Timeline cooldownTimeline;
    private int cooldownRemaining = 0;

    /** Timeline qui vérifie et libère les pixels dont l'embargo est expiré */
    private Timeline embargoCheckTimeline;

    // ─── Initialisation ───────────────────────────────────────────────────────

    /**
     * Appelé par ConnexionController après le chargement du FXML.
     */
    public void init(NetworkClient client, String pseudo) {
        this.client = client;
        this.pseudo = pseudo;

        // Afficher le pseudo et le statut connecté
        labelPseudo.setText(pseudo);
        setStatutConnecte(true);

        // Construire la grille dynamiquement
        construireGrille();

        // Initialiser le sélecteur de couleur
        mettreAJourCouleurSelectionnee(selectedColor);

        // Masquer la bannière
        cacherBanniere();

        // Brancher le listener réseau
        client.setListener(new NetworkClient.MessageListener() {
            @Override
            public void onMessage(Message message) {
                // CRITIQUE : mise à jour UI depuis le thread réseau → Platform.runLater
                Platform.runLater(() -> gererMessage(message));
            }

            @Override
            public void onConnectionLost() {
                Platform.runLater(() -> onServeurDeconnecte());
            }
        });

        // Démarrer le timer de vérification des embargos (toutes les secondes)
        demarrerTimerEmbargo();
    }

    // ─── Construction de la grille ───────────────────────────────────────────

    private void construireGrille() {
        cells = new Rectangle[GRID_COLS][GRID_ROWS];
        grille.getChildren().clear();
        grille.setHgap(1);
        grille.setVgap(1);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                Rectangle rect = new Rectangle(CELL_SIZE, CELL_SIZE);
                rect.setFill(Color.WHITE);
                rect.setStroke(Color.web("#2a2a2a"));
                rect.setStrokeWidth(0.5);
                rect.setCursor(javafx.scene.Cursor.HAND);

                // Effet hover
                final int x = col;
                final int y = row;
                rect.setOnMouseEntered(e -> {
                    if (!isPixelEmbargoed(x, y)) {
                        rect.setStyle("-fx-effect: dropshadow(gaussian, #3A86FF, 6, 0.5, 0, 0);");
                    }
                });
                rect.setOnMouseExited(e -> rect.setStyle(""));
                rect.setOnMouseClicked(e -> onCellCliked(x, y));

                cells[col][row] = rect;
                grille.add(rect, col, row);
            }
        }
    }

    // ─── Gestion des clics sur la grille ─────────────────────────────────────

    private void onCellCliked(int x, int y) {
        // Cooldown utilisateur : la grille entière est désactivée (setDisable)
        // Mais par sécurité, on vérifie aussi ici
        if (grille.isDisabled()) return;

        if (isPixelEmbargoed(x, y)) {
            // Feedback visuel immédiat (le serveur enverra aussi PIXEL_EMBARGO)
            afficherFeedbackEmbargo(x, y);
            return;
        }

        // Envoyer la demande au serveur
        String colorHex = colorToHex(selectedColor);
        client.sendMessage(Message.pixelColor(x, y, colorHex));
    }

    // ─── Réception des messages réseau ───────────────────────────────────────

    private void gererMessage(Message msg) {
        switch (msg.getType()) {
            case PIXEL_UPDATE:
                appliquerPixelUpdate(msg);
                break;

            case PIXEL_EMBARGO:
                afficherFeedbackEmbargo(msg.getX(), msg.getY());
                // Mémoriser le temps restant reçu du serveur
                long end = System.currentTimeMillis() + msg.getRemainingMs();
                embargoEndTimes.put(msg.getX() + "," + msg.getY(), end);
                mettreAJourVisuelEmbargo(msg.getX(), msg.getY(), true);
                break;

            case SERVER_SHUTDOWN:
                onServeurDeconnecte();
                break;

            default:
                break;
        }
    }

    private void appliquerPixelUpdate(Message msg) {
        int x = msg.getX();
        int y = msg.getY();
        Color color = Color.web(msg.getColor());

        // Colorer la cellule
        cells[x][y].setFill(color);

        // Enregistrer l'embargo
        long embargoEnd = System.currentTimeMillis() + msg.getRemainingMs();
        embargoEndTimes.put(x + "," + y, embargoEnd);
        mettreAJourVisuelEmbargo(x, y, true);

        // Si c'est MOI qui ai colorié → démarrer mon cooldown utilisateur
        if (pseudo.equals(msg.getPseudo())) {
            demarrerCooldownUtilisateur();
        }
    }

    // ─── Cooldown utilisateur (30s) ───────────────────────────────────────────

    private void demarrerCooldownUtilisateur() {
        if (cooldownTimeline != null) cooldownTimeline.stop();

        cooldownRemaining = USER_COOLDOWN_SEC;
        grille.setDisable(true);
        afficherBanniere("⏳ Attente : " + cooldownRemaining + "s avant de recolorer", false);

        cooldownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            cooldownRemaining--;
            if (cooldownRemaining <= 0) {
                // Cooldown terminé
                grille.setDisable(false);
                cacherBanniere();
                cooldownTimeline.stop();
            } else {
                afficherBanniere("⏳ Attente : " + cooldownRemaining + "s avant de recolorer", false);
            }
        }));
        cooldownTimeline.setCycleCount(USER_COOLDOWN_SEC);
        cooldownTimeline.play();
    }

    // ─── Timer embargo des pixels ─────────────────────────────────────────────

    private void demarrerTimerEmbargo() {
        embargoCheckTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long now = System.currentTimeMillis();
            for (int col = 0; col < GRID_COLS; col++) {
                for (int row = 0; row < GRID_ROWS; row++) {
                    String key = col + "," + row;
                    Long end = embargoEndTimes.get(key);
                    if (end != null && now >= end) {
                        embargoEndTimes.remove(key);
                        mettreAJourVisuelEmbargo(col, row, false);
                    }
                }
            }
        }));
        embargoCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        embargoCheckTimeline.play();
    }

    // ─── Visuels embargo ─────────────────────────────────────────────────────

    private boolean isPixelEmbargoed(int x, int y) {
        Long end = embargoEndTimes.get(x + "," + y);
        return end != null && System.currentTimeMillis() < end;
    }

    private void mettreAJourVisuelEmbargo(int x, int y, boolean embargoed) {
        Rectangle rect = cells[x][y];
        if (embargoed) {
            rect.setStroke(Color.web("#FF6B6B"));
            rect.setStrokeWidth(1.5);
            rect.setOpacity(0.85);
        } else {
            rect.setStroke(Color.web("#2a2a2a"));
            rect.setStrokeWidth(0.5);
            rect.setOpacity(1.0);
        }
    }

    private void afficherFeedbackEmbargo(int x, int y) {
        Rectangle cell = cells[x][y];
        Color originalFill = (Color) cell.getFill();

        // Flash rouge
        cell.setFill(Color.web("#FF6B6B"));
        Timeline flash = new Timeline(
            new KeyFrame(Duration.millis(250), e -> cell.setFill(originalFill))
        );
        flash.play();

        // Temps restant
        Long end = embargoEndTimes.get(x + "," + y);
        int secs = end != null ? (int) Math.max(0, (end - System.currentTimeMillis()) / 1000) : 60;
        afficherBanniere("🔒 Pixel bloqué — disponible dans " + secs + "s", true);

        // Masquer la bannière après 3s (si le cooldown utilisateur n'est pas actif)
        if (cooldownRemaining <= 0) {
            Timeline hide = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                if (cooldownRemaining <= 0) cacherBanniere();
            }));
            hide.play();
        }
    }

    // ─── Sélection de couleur ────────────────────────────────────────────────

    @FXML
    private void onOuvrirColorPicker() {
        ColorPicker picker = new ColorPicker(selectedColor);
        picker.setStyle("-fx-color-label-visible: false;");

        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle("Choisir une couleur");
        dialog.setHeaderText("Sélectionnez votre couleur");
        dialog.getDialogPane().setContent(picker);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.initOwner(grille.getScene().getWindow());
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? picker.getValue() : null);

        Optional<Color> result = dialog.showAndWait();
        result.ifPresent(color -> {
            selectedColor = color;
            mettreAJourCouleurSelectionnee(color);
            ajouterCouleurRecente(color);
        });
    }

    private void mettreAJourCouleurSelectionnee(Color color) {
        rectCouleurSelectionnee.setFill(color);
        labelCouleurSelectionnee.setText(colorToHex(color));
    }

    private void ajouterCouleurRecente(Color color) {
        // Supprimer si déjà présente (éviter les doublons)
        recentColors.removeIf(c ->
            Math.abs(c.getRed() - color.getRed()) < 0.01
            && Math.abs(c.getGreen() - color.getGreen()) < 0.01
            && Math.abs(c.getBlue() - color.getBlue()) < 0.01
        );
        recentColors.add(0, color);
        if (recentColors.size() > MAX_RECENT_COLORS) {
            recentColors.remove(recentColors.size() - 1);
        }
        rafraichirCouleursRecentes();
    }

    private void rafraichirCouleursRecentes() {
        hboxCouleursRecentes.getChildren().clear();
        for (Color color : recentColors) {
            Rectangle rect = new Rectangle(22, 22);
            rect.setFill(color);
            rect.setStroke(Color.web("#555555"));
            rect.setStrokeWidth(1);
            rect.setCursor(javafx.scene.Cursor.HAND);
            rect.setArcWidth(4);
            rect.setArcHeight(4);

            Tooltip tip = new Tooltip(colorToHex(color));
            Tooltip.install(rect, tip);

            rect.setOnMouseClicked(e -> {
                selectedColor = color;
                mettreAJourCouleurSelectionnee(color);
            });
            rect.setOnMouseEntered(e ->
                rect.setStyle("-fx-effect: dropshadow(gaussian, white, 4, 0.4, 0, 0);"));
            rect.setOnMouseExited(e ->
                rect.setStyle(""));

            hboxCouleursRecentes.getChildren().add(rect);
        }
    }

    // ─── Bannière de statut ───────────────────────────────────────────────────

    private void afficherBanniere(String texte, boolean estErreur) {
        labelBanniere.setText(texte);
        if (estErreur) {
            hboxBanniere.setStyle(
                "-fx-background-color: #c0392b; -fx-padding: 10 20; -fx-alignment: center;");
            labelBanniere.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");
        } else {
            hboxBanniere.setStyle(
                "-fx-background-color: #f39c12; -fx-padding: 10 20; -fx-alignment: center;");
            labelBanniere.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");
        }
        hboxBanniere.setVisible(true);
        hboxBanniere.setManaged(true);
    }

    private void cacherBanniere() {
        hboxBanniere.setVisible(false);
        hboxBanniere.setManaged(false);
    }

    // ─── Statut de connexion ─────────────────────────────────────────────────

    private void setStatutConnecte(boolean connecte) {
        if (connecte) {
            labelStatut.setText("Connecté");
            cercleStatut.setFill(Color.web("#2ecc71"));
        } else {
            labelStatut.setText("Déconnecté");
            cercleStatut.setFill(Color.web("#e74c3c"));
        }
    }

    // ─── Déconnexion ─────────────────────────────────────────────────────────

    @FXML
    private void onDeconnexion() {
        client.disconnect();
        arreterTimers();
        retournerALaConnexion();
    }

    private void onServeurDeconnecte() {
        setStatutConnecte(false);
        grille.setDisable(true);
        boutonDeconnexion.setDisable(true);
        arreterTimers();
        afficherBanniere("⚠ Connexion perdue avec le serveur", true);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Connexion perdue");
        alert.setHeaderText("Le serveur est inaccessible");
        alert.setContentText("La connexion au serveur a été interrompue.\nVous allez être redirigé vers l'écran de connexion.");
        alert.initOwner(grille.getScene().getWindow());
        alert.showAndWait();

        retournerALaConnexion();
    }

    private void arreterTimers() {
        if (cooldownTimeline != null)    cooldownTimeline.stop();
        if (embargoCheckTimeline != null) embargoCheckTimeline.stop();
    }

    private void retournerALaConnexion() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pixelwar/connexion.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) boutonDeconnexion.getScene().getWindow();
            stage.setScene(new Scene(root, 480, 380));
            stage.setTitle("Pixel War — Connexion");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─── Utilitaire couleur ──────────────────────────────────────────────────

    private String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed()   * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue()  * 255));
    }
}
