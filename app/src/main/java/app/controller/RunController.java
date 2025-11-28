package app.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class RunController {
  @FXML private Label envLabel;
  @FXML private Button checkBtn;

  @FXML public void initialize() { envLabel.setText("Ready"); }

  @FXML public void onCheckEnv() {
    envLabel.setText("Java " + System.getProperty("java.version") + " ✓");
  }
}