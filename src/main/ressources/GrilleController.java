import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class GrilleController {

    //composants fxml
    @FXML private GridPane grille;
    @FXML private Label labelPseudo;
    @FXML private Label labelStatut;
    @FXML private Circle cercleStatut;
    @FXML private Rectangle rectCouleurSelectionnee;
    @FXML private Label labelCouleurSelectionnee;
    @FXML private HBox hboxCouleursRecentes;
    @FXML private HBox hboxCouleurSelectionnee;
    @FXML private HBox hboxBanniere;
    @FXML private Label labelBanniere;
    @FXML private Button boutonDeconnexion;

    //parametres fixes de grille
    private static final int COOLDOWN_JOUEUR_SEC = 30;
    private static final int COOLDOWN_PIXEL_SEC = 60;
    private static final int MAX_RECENTS = 6;
    private static final int lignes = 55;
    private static final int cols = 27;
    private static final int taill_pixel = 30;

    private Rectangle[][] cellules;
    private Tooltip[][] tooltipsCellules;
    private int[][] cooldownPixels;
    private String[][] proprietairesPixels;
    private java.util.Timer[][] timersPixels;
    private int cooldownJoueur = -1;
    private java.util.Timer timerJoueur;
    private NetworkClient networkClient;
    private Color couleurSelectionnee = Color.BLUE;
    private final List<Color> couleursRecentes = new ArrayList<>();

    @FXML
    public void initialize() {
        cellules = new Rectangle[lignes][cols];
        tooltipsCellules = new Tooltip[lignes][cols];
        cooldownPixels = new int[lignes][cols];
        proprietairesPixels = new String[lignes][cols];
        timersPixels = new java.util.Timer[lignes][cols];

        for (int r = 0; r < lignes; r++) {
            for (int c = 0; c < cols; c++) {
                cooldownPixels[r][c] = -1;
                proprietairesPixels[r][c] = null;
            }
        }

        creerGrille();
        setCouleurSelectionnee(couleurSelectionnee);
        ajouterColorPicker();
        rafraichirCouleursRecentes();
    }

    private void creerGrille() {
        grille.getChildren().clear();
        for (int r = 0; r < lignes; r++) {
            for (int c = 0; c < cols; c++) {
                Rectangle cell = new Rectangle(taill_pixel, taill_pixel, Color.WHITE);
                cell.setStroke(Color.LIGHTGRAY);
                cell.setStrokeWidth(0.5);
                final int ligne = r;
                final int col = c;
                cell.setOnMouseClicked(e -> handleClicPixel(ligne, col));
                cell.setOnMouseEntered(e -> mettreAJourTooltipPixel(ligne, col));
                cell.setOnMouseMoved(e -> mettreAJourTooltipPixel(ligne, col));
                cell.setStyle("-fx-cursor: hand;");

                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(120));
                Tooltip.install(cell, tooltip);

                cellules[r][c] = cell;
                tooltipsCellules[r][c] = tooltip;
                grille.add(cell, c, r);
            }
        }
    }

    //selecteur couleur inline
    private void ajouterColorPicker() {
        Button btnOuvrir = new Button("Palette");
        btnOuvrir.setStyle("-fx-font-size: 11px; -fx-cursor: hand;");

        javafx.scene.layout.VBox selecteur = new javafx.scene.layout.VBox(6);
        selecteur.setStyle("-fx-background-color: white; -fx-border-color: #cccccc; "
                         + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");
        selecteur.setVisible(false);
        selecteur.setManaged(false);

        Rectangle apercu = new Rectangle(180, 28, couleurSelectionnee);
        apercu.setArcWidth(4);
        apercu.setArcHeight(4);
        apercu.setStroke(Color.LIGHTGRAY);

        Slider sliderT = creerSlider(0, 360, couleurSelectionnee.getHue());
        Slider sliderS = creerSlider(0, 1,   couleurSelectionnee.getSaturation());
        Slider sliderL = creerSlider(0, 1,   couleurSelectionnee.getBrightness());

        Label lT = new Label("Teinte");
        Label lS = new Label("Saturation");
        Label lL = new Label("Luminosite");
        for (Label l : new Label[]{lT, lS, lL})
            l.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

        Runnable miseAJour = () -> {
            Color c = Color.hsb(sliderT.getValue(), sliderS.getValue(), sliderL.getValue());
            apercu.setFill(c);
            setCouleurSelectionnee(c);
        };
        sliderT.valueProperty().addListener((obs, o, n) -> miseAJour.run());
        sliderS.valueProperty().addListener((obs, o, n) -> miseAJour.run());
        sliderL.valueProperty().addListener((obs, o, n) -> miseAJour.run());

        selecteur.getChildren().addAll(apercu, lT, sliderT, lS, sliderS, lL, sliderL);

        btnOuvrir.setOnAction(e -> {
            boolean ouvert = !selecteur.isVisible();
            selecteur.setVisible(ouvert);
            selecteur.setManaged(ouvert);
        });

        hboxCouleurSelectionnee.getChildren().addAll(btnOuvrir, selecteur);
    }

    private Slider creerSlider(double min, double max, double valeur) {
        Slider s = new Slider(min, max, valeur);
        s.setPrefWidth(180);
        return s;
    }

    //clic sur case
    private void handleClicPixel(int ligne, int col) {
        if (cooldownJoueur > 0) return;
        if (cooldownPixels[ligne][col] > 0) {
            afficherBannierePixelAvecTimer(ligne, col);
            return;
        }
        cellules[ligne][col].setFill(couleurSelectionnee);
        ajouterCouleurRecente(couleurSelectionnee);
        demarrerCooldownJoueur();
        if (networkClient != null)
            networkClient.envoyerPixel(ligne, col, couleurSelectionnee);
    }

    //cooldown joueur 30s
    private void demarrerCooldownJoueur() {
        if (timerBannierePixel != null) {
            timerBannierePixel.cancel();
            timerBannierePixel = null;
        }
        cooldownJoueur = COOLDOWN_JOUEUR_SEC;
        setGrilleDesactivee(true);
        afficherBanniereJoueur(cooldownJoueur);
        timerJoueur = new java.util.Timer();
        timerJoueur.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                cooldownJoueur--;
                if (cooldownJoueur <= 0) {
                    cooldownJoueur = -1;
                    timerJoueur.cancel();
                    Platform.runLater(() -> { setGrilleDesactivee(false); cacherBanniere(); });
                } else {
                    final int restant = cooldownJoueur;
                    Platform.runLater(() -> afficherBanniereJoueur(restant));
                }
            }
        }, 1000, 1000);
    }

    //recevoir update pixel d'un autre joueur
    public void recevoirPixelDistant(int ligne, int col, Color couleur, String pseudo, boolean estMoi) {
        appliquerEtatPixel(ligne, col, couleur, pseudo, COOLDOWN_PIXEL_SEC);
    }

    //recevoir etat initial grille apres connexion
    public void appliquerEtatInitialPixel(int ligne, int col, Color couleur, String pseudo, int secondesRestantes) {
        appliquerEtatPixel(ligne, col, couleur, pseudo, secondesRestantes);
    }

    //appliquer etat pixel sur grille
    private void appliquerEtatPixel(int ligne, int col, Color couleur, String pseudo, int secondesRestantes) {
        if (!estCoordonneeValide(ligne, col)) return;
        cellules[ligne][col].setFill(couleur);
        proprietairesPixels[ligne][col] = pseudo;
        mettreAJourTooltipPixel(ligne, col);
        if (secondesRestantes > 0) {
            cooldownPixels[ligne][col] = secondesRestantes;
            demarrerCooldownPixel(ligne, col);
        } else {
            cooldownPixels[ligne][col] = -1;
        }
    }

    private boolean estCoordonneeValide(int ligne, int col) {
        return ligne >= 0 && ligne < lignes && col >= 0 && col < cols;
    }

    private void demarrerCooldownPixel(int ligne, int col) {
        arreterTimerPixel(ligne, col);
        java.util.Timer t = new java.util.Timer();
        timersPixels[ligne][col] = t;
        t.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                cooldownPixels[ligne][col]--;
                Platform.runLater(() -> mettreAJourTooltipPixel(ligne, col));
                if (cooldownPixels[ligne][col] <= 0) {
                    cooldownPixels[ligne][col] = -1;
                    timersPixels[ligne][col] = null;
                    t.cancel();
                    Platform.runLater(() -> mettreAJourTooltipPixel(ligne, col));
                }
            }
        }, 1000, 1000);
    }

    private void arreterTimerPixel(int ligne, int col) {
        if (timersPixels[ligne][col] != null) {
            timersPixels[ligne][col].cancel();
            timersPixels[ligne][col] = null;
        }
    }

    //mise a jour tooltip au hover sur pixel
    private void mettreAJourTooltipPixel(int ligne, int col) {
        if (!estCoordonneeValide(ligne, col)) return;
        Tooltip tooltip = tooltipsCellules[ligne][col];
        if (tooltip == null) return;

        int restant = cooldownPixels[ligne][col];
        String pseudo = proprietairesPixels[ligne][col];
        String texte;

        if (restant > 0) {
            String auteur = (pseudo == null || pseudo.isBlank()) ? "un joueur" : pseudo;
            texte = "Pixel colorie par : " + auteur
                + "\nModifiable dans : " + restant + " seconde" + (restant > 1 ? "s" : "");
        } else if (pseudo != null && !pseudo.isBlank()) {
            texte = "Dernier joueur : " + pseudo + "\nCe pixel est maintenant modifiable.";
        } else {
            texte = "Pixel libre";
        }

        tooltip.setText(texte);
    }

    private java.util.Timer timerBannierePixel = null;

    private void afficherBannierePixelAvecTimer(int ligne, int col) {
        if (cooldownJoueur > 0) return;
        if (timerBannierePixel != null) { timerBannierePixel.cancel(); timerBannierePixel = null; }
        afficherBannierePixel(ligne, col, cooldownPixels[ligne][col]);
        timerBannierePixel = new java.util.Timer();
        timerBannierePixel.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                int restant = cooldownPixels[ligne][col];
                if (restant <= 0) {
                    timerBannierePixel.cancel();
                    timerBannierePixel = null;
                    Platform.runLater(() -> cacherBanniere());
                } else {
                    Platform.runLater(() -> afficherBannierePixel(ligne, col, restant));
                }
            }
        }, 1000, 1000);
    }

    private void afficherBanniereJoueur(int secondesRestantes) {
        labelBanniere.setText(
            String.format("Vous ne pouvez pas colorier pour l'instant. Il faut attendre : %d seconde%s.",
                secondesRestantes, secondesRestantes > 1 ? "s" : ""));
        hboxBanniere.setVisible(true);
        hboxBanniere.setManaged(true);
    }

    private void afficherBannierePixel(int ligne, int col, int secondesRestantes) {
        String pseudo = proprietairesPixels[ligne][col];
        String auteur = (pseudo == null || pseudo.isBlank()) ? "un autre joueur" : pseudo;
        labelBanniere.setText(
            String.format("Ce pixel a ete colorie par %s. Il faudra attendre : %d seconde%s.",
                auteur, secondesRestantes, secondesRestantes > 1 ? "s" : ""));
        hboxBanniere.setVisible(true);
        hboxBanniere.setManaged(true);
    }

    private void cacherBanniere() {
        hboxBanniere.setVisible(false);
        hboxBanniere.setManaged(false);
    }

    private void ajouterCouleurRecente(Color couleur) {
        if (!couleursRecentes.isEmpty() && couleursRecentes.get(0).equals(couleur)) return;
        couleursRecentes.remove(couleur);
        couleursRecentes.add(0, couleur);
        if (couleursRecentes.size() > MAX_RECENTS)
            couleursRecentes.remove(couleursRecentes.size() - 1);
        rafraichirCouleursRecentes();
    }

    private void rafraichirCouleursRecentes() {
        hboxCouleursRecentes.getChildren().clear();
        for (Color c : couleursRecentes) {
            Rectangle rect = new Rectangle(28, 28, c);
            rect.setArcWidth(4);
            rect.setArcHeight(4);
            rect.setStyle("-fx-stroke: #aaaaaa; -fx-stroke-width: 1; -fx-cursor: hand;");
            rect.setOnMouseClicked(e -> setCouleurSelectionnee(c));
            hboxCouleursRecentes.getChildren().add(rect);
        }
    }

    //mise a jour de la couleur selectionnee apres choix dans ui
    private void setCouleurSelectionnee(Color couleur) {
        couleurSelectionnee = couleur;
        rectCouleurSelectionnee.setFill(couleur);
        labelCouleurSelectionnee.setText(couleurVersNom(couleur));
    }

    private void setGrilleDesactivee(boolean desactivee) {
        grille.setDisable(false);
        grille.setOpacity(desactivee ? 0.65 : 1.0);
    }

    public void setStatutConnecte(boolean connecte) {
        if (connecte) {
            labelStatut.setText("Connecte");
            labelStatut.setStyle("-fx-font-size: 12px; -fx-text-fill: green;");
            cercleStatut.setFill(javafx.scene.paint.Color.GREEN);
        } else {
            labelStatut.setText("Deconnecte");
            labelStatut.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");
            cercleStatut.setFill(javafx.scene.paint.Color.RED);
        }
    }

    public void setPseudo(String pseudo) {
        labelPseudo.setText(pseudo);
    }

    private String couleurVersNom(Color c) {
        if (c.equals(Color.RED))    return "Rouge";
        if (c.equals(Color.BLUE))   return "Bleu";
        if (c.equals(Color.GREEN))  return "Vert";
        if (c.equals(Color.BLACK))  return "Noir";
        if (c.equals(Color.WHITE))  return "Blanc";
        if (c.equals(Color.ORANGE)) return "Orange";
        if (c.equals(Color.CYAN))   return "Cyan";
        if (c.equals(Color.HOTPINK))return "Rose";
        return String.format("#%02X%02X%02X",
            (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    //nettoyage timers
    public void cleanup() {
        if (timerJoueur != null) {
            timerJoueur.cancel();
            timerJoueur = null;
        }
        if (timerBannierePixel != null) {
            timerBannierePixel.cancel();
            timerBannierePixel = null;
        }
        for (int r = 0; r < lignes; r++) {
            for (int c = 0; c < cols; c++) {
                arreterTimerPixel(r, c);
            }
        }
    }

    //deconnexion et retour a la page de connexion
    @FXML
    private void handleDeconnexion() {
        cleanup();
        if (networkClient != null)
            networkClient.deconnecter();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("connexion.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage =
                (javafx.stage.Stage) boutonDeconnexion.getScene().getWindow();

            stage.setFullScreen(false);
            stage.setMaximized(false);

            stage.setScene(new javafx.scene.Scene(root, 900, 650));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //recevoir message de pixel en cooldown apres tentative de colorier
    public void recevoirBusy(int ligne, int col, String pseudo, int secondesRestantes) {
        proprietairesPixels[ligne][col] = pseudo;
        cooldownPixels[ligne][col] = secondesRestantes;
        mettreAJourTooltipPixel(ligne, col);
        demarrerCooldownPixel(ligne, col);
        if (cooldownJoueur <= 0 ) {
            afficherBannierePixelAvecTimer(ligne, col);
        }
    }

    //recevoir message d'erreur reseau
    public void afficherErreurReseau(String message) {
        setStatutConnecte(false);
        afficherBanniereJoueur(0);
        labelBanniere.setText("Erreur reseau : " + message);
        hboxBanniere.setStyle("-fx-background-color: #f8d7da; -fx-border-color: #f5c2c7; "
                            + "-fx-border-width: 0 0 1 0; -fx-padding: 8 15 8 15;");
        labelBanniere.setStyle("-fx-font-size: 13px; -fx-text-fill: #842029;");
    }

    //liaison avec NetworkClient apres connexion pour recevoir les updates du serveur
    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
        this.networkClient.setGrilleController(this);
        setStatutConnecte(true);
    }
}
