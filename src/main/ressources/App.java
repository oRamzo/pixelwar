import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("connexion.fxml"));
        primaryStage.setTitle("Pixel Battle");
        primaryStage.setScene(new Scene(root, 900, 650));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


/**
 * Compiler en faisant :
 * javac --module-path ~/javafx-sdk-21/lib --add-modules javafx.controls,javafx.fxml *.java
 * Executer en faisant : 
 * java --module-path ~/javafx-sdk-21/lib --add-modules javafx.controls,javafx.fxml App
 * Demarrer le serveur en faisant :
 * java Server 7777
 */