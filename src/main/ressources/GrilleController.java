import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.application.Platform;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class GrilleController implements Initializable {

    //parametres fixes de grille
    private static final int COLS         = 55;
    private static final int ROWS         = 27;
    private static final int CELL_SIZE    = 30;
    private static final int MAX_RECENTS  = 5;
    private static final int COOLDOWN_JOUEUR_SEC = 30; //secondes
    private static final int COOLDOWN_PIXEL_SEC  = 60; //1 minute

    //composants fxml
    @FXML private HBox      hboxCouleurSelectionnee;
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

    //etat local ecran
    private Color couleurSelectionnee = Color.BLUE;
    private final List<Color> couleursRecentes = new ArrayList<>();

    //cases affichees dans grille
    private Rectangle[][] cellules = new Rectangle[ROWS][COLS];

    //cooldown global joueur -1 libre
    private int cooldownJoueur = -1;
    private java.util.Timer timerJoueur = null;

    //cooldown par case secondes restantes -1 libre
    private int[][] cooldownPixels = new int[ROWS][COLS];

    private NetworkClient networkClient;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                cooldownPixels[r][c] = -1;
        construireGrille();
        ajouterColorPicker();
        cacherBanniere();
        labelPseudo.setText("Pseudo");
        setStatutConnecte(false);
    }

    private void construireGrille() {
        grille.getChildren().clear();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Rectangle cell = new Rectangle(CELL_SIZE, CELL_SIZE);
                cell.setFill(Color.WHITE);
                cell.setStroke(Color.LIGHTGRAY);
                cell.setStrokeWidth(0.5);
                final int row = r;
                final int col = c;
                cell.setOnMouseClicked(e -> handleClicPixel(row, col));
                cell.setStyle("-fx-cursor: hand;");
                cellules[r][c] = cell;
                grille.add(cell, c, r);
            }
        }
    }

    // Selecteur couleur inline — pas de dialogue systeme donc pas de freeze
    private void ajouterColorPicker() {
        Button btnOuvrir = new Button("\uD83C\uDFA8 Choisir");
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

        Slider sliderH = creerSlider(0, 360, couleurSelectionnee.getHue());
        Slider sliderS = creerSlider(0, 1,   couleurSelectionnee.getSaturation());
        Slider sliderB = creerSlider(0, 1,   couleurSelectionnee.getBrightness());

        Label lH = new Label("Teinte");
        Label lS = new Label("Saturation");
        Label lB = new Label("Luminosite");
        for (Label l : new Label[]{lH, lS, lB})
            l.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

        Runnable miseAJour = () -> {
            Color c = Color.hsb(sliderH.getValue(), sliderS.getValue(), sliderB.getValue());
            apercu.setFill(c);
            setCouleurSelectionnee(c);
        };
        sliderH.valueProperty().addListener((obs, o, n) -> miseAJour.run());
        sliderS.valueProperty().addListener((obs, o, n) -> miseAJour.run());
        sliderB.valueProperty().addListener((obs, o, n) -> miseAJour.run());

        selecteur.getChildren().addAll(apercu, lH, sliderH, lS, sliderS, lB, sliderB);

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

    private void handleClicPixel(int row, int col) {
        if (cooldownJoueur > 0) return;
        if (cooldownPixels[row][col] > 0) {
            afficherBannierePixelAvecTimer(row, col);
            return;
        }
        cellules[row][col].setFill(couleurSelectionnee);
        ajouterCouleurRecente(couleurSelectionnee);
        demarrerCooldownJoueur();
        if (networkClient != null)
            networkClient.envoyerPixel(row, col, couleurSelectionnee);
    }

    private void demarrerCooldownJoueur() {
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

    public void recevoirPixelDistant(int row, int col, Color couleur, boolean estMoi) {
        appliquerEtatPixel(row, col, couleur, COOLDOWN_PIXEL_SEC);
    }

    public void appliquerEtatInitialPixel(int row, int col, Color couleur, int secondesRestantes) {
        appliquerEtatPixel(row, col, couleur, secondesRestantes);
    }

    private void appliquerEtatPixel(int row, int col, Color couleur, int secondesRestantes) {
        if (!estCoordonneeValide(row, col)) return;
        cellules[row][col].setFill(couleur);
        if (secondesRestantes > 0) {
            cooldownPixels[row][col] = secondesRestantes;
            demarrerCooldownPixel(row, col);
        } else {
            cooldownPixels[row][col] = -1;
        }
    }

    private boolean estCoordonneeValide(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    private void demarrerCooldownPixel(int row, int col) {
        java.util.Timer t = new java.util.Timer();
        t.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                cooldownPixels[row][col]--;
                if (cooldownPixels[row][col] <= 0) {
                    cooldownPixels[row][col] = -1;
                    t.cancel();
                }
            }
        }, 1000, 1000);
    }

    private java.util.Timer timerBannierePixel = null;

    private void afficherBannierePixelAvecTimer(int row, int col) {
        if (timerBannierePixel != null) { timerBannierePixel.cancel(); timerBannierePixel = null; }
        afficherBannierePixel(cooldownPixels[row][col]);
        timerBannierePixel = new java.util.Timer();
        timerBannierePixel.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                int restant = cooldownPixels[row][col];
                if (restant <= 0) {
                    timerBannierePixel.cancel();
                    timerBannierePixel = null;
                    Platform.runLater(() -> cacherBanniere());
                } else {
                    Platform.runLater(() -> afficherBannierePixel(restant));
                }
            }
        }, 1000, 1000);
    }

    private void afficherBanniereJoueur(int secondesRestantes) {
        labelBanniere.setText(
            String.format("Vous ne pouvez pas colorier pour l'instant. Attente : %d seconde%s.",
                secondesRestantes, secondesRestantes > 1 ? "s" : ""));
        hboxBanniere.setVisible(true);
        hboxBanniere.setManaged(true);
    }

    private void afficherBannierePixel(int secondesRestantes) {
        labelBanniere.setText(
            String.format("Vous ne pouvez pas colorier ce pixel. Il faudra attendre : %d seconde%s.",
                secondesRestantes, secondesRestantes > 1 ? "s" : ""));
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

    private void setCouleurSelectionnee(Color couleur) {
        couleurSelectionnee = couleur;
        rectCouleurSelectionnee.setFill(couleur);
        labelCouleurSelectionnee.setText(couleurVersNom(couleur));
    }

    private void setGrilleDesactivee(boolean desactivee) {
        grille.setDisable(desactivee);
        grille.setOpacity(desactivee ? 0.5 : 1.0);
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

    public void setPseudo(String pseudo) { labelPseudo.setText(pseudo); }

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

    @FXML
    private void handleDeconnexion() {
        if (networkClient != null) networkClient.deconnecter();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("connexion.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) boutonDeconnexion.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void recevoirBusy(int row, int col, int secondesRestantes) {
        cooldownPixels[row][col] = secondesRestantes;
        demarrerCooldownPixel(row, col);
        afficherBannierePixelAvecTimer(row, col);
    }

    public void afficherErreurReseau(String message) {
        setStatutConnecte(false);
        afficherBanniereJoueur(0);
        labelBanniere.setText("Erreur reseau : " + message);
        hboxBanniere.setStyle("-fx-background-color: #f8d7da; -fx-border-color: #f5c2c7; "
                            + "-fx-border-width: 0 0 1 0; -fx-padding: 8 15 8 15;");
        labelBanniere.setStyle("-fx-font-size: 13px; -fx-text-fill: #842029;");
    }

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
        this.networkClient.setGrilleController(this);
        setStatutConnecte(true);
    }
}