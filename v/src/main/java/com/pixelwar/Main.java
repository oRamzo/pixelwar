package com.pixelwar;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Point d'entrée de l'application client Pixel War.
 *
 * Lancement avec Maven : mvn javafx:run
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/pixelwar/connexion.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 480, 380);
        primaryStage.setTitle("Pixel War — Connexion");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
