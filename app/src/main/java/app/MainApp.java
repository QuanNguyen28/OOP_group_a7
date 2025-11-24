package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    var fxml = getClass().getResource("/ui/fxml/run.fxml");
    var root = FXMLLoader.load(fxml);
    stage.setTitle("Yagi – Humanitarian Analytics (MVC)");
    stage.setScene(new Scene(root, 900, 580));
    stage.show();
  }
  public static void main(String[] args) { launch(args); }
}