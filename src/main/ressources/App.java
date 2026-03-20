import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stagePrincipal) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("connexion.fxml"));
        stagePrincipal.setTitle("PixelWar");
        stagePrincipal.setScene(new Scene(root, 900, 650));
        stagePrincipal.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


//compile :
//javac --module-path ~/javafx-sdk-21/lib --add-modules javafx.controls,javafx.fxml *.java
//run app :
//java --module-path ~/javafx-sdk-21/lib --add-modules javafx.controls,javafx.fxml App
//run serveur :
//java Server 7777