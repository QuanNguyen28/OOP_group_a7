package app.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * ShellController - Quản lý navigation giữa các screens chính
 * - Run (Nạp & Phân Tích)
 * - Dashboard (Bảng Điều Khiển)
 */
public class ShellController {
    @FXML private StackPane contentHost;
    @FXML private ProgressBar progress;
    @FXML private Label statusLabel;
    
    @FXML private ToggleButton navRun;
    @FXML private ToggleButton navDash;
    @FXML private ToggleButton navData;
    @FXML private ToggleButton navSettings;

    private RunController runController;
    private DashboardController dashboardController;

    @FXML
    public void initialize() {
        // Set up navigation
        navRun.setOnAction(e -> showRunScreen());
        navDash.setOnAction(e -> showDashboardScreen());
        navData.setOnAction(e -> showDataScreen());
        navSettings.setOnAction(e -> showSettingsScreen());

        // Mặc định hiển thị Run screen
        showRunScreen();
    }

    private void showRunScreen() {
        selectNavButton(navRun);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/fxml/run.fxml"));
            Parent root = loader.load();
            runController = loader.getController();
            if (runController != null) {
                runController.setShellController(this);
            }
            contentHost.getChildren().setAll(root);
        } catch (Exception e) {
            statusLabel.setText("❌ Lỗi tải screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void showRunScreenPublic() {
        showRunScreen();
    }

    private void showDashboardScreen() {
        selectNavButton(navDash);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/fxml/dashboard.fxml"));
            Parent root = loader.load();
            dashboardController = loader.getController();
            if (dashboardController != null) {
                dashboardController.setShellController(this);
                dashboardController.loadData();
            }
            contentHost.getChildren().setAll(root);
        } catch (Exception e) {
            statusLabel.setText("❌ Lỗi tải screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public DashboardController getDashboardController() {
        return dashboardController;
    }

    public void showDashboardScreenPublic() {
        showDashboardScreen();
    }

    private void showDataScreen() {
        selectNavButton(navData);
        contentHost.getChildren().setAll(createPlaceholder("📁 Dữ Liệu & Từ Điển", "Tính năng sắp có"));
    }

    private void showSettingsScreen() {
        selectNavButton(navSettings);
        contentHost.getChildren().setAll(createPlaceholder("⚙️ Cài Đặt", "Tính năng sắp có"));
    }

    private void loadFXML(String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent root = loader.load();
            contentHost.getChildren().setAll(root);
        } catch (IOException e) {
            statusLabel.setText("❌ Lỗi tải screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Parent createPlaceholder(String title, String message) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -color-primary-solid;");
        
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -on-surface-variant;");
        
        var vbox = new javafx.scene.layout.VBox(16);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-padding: 40;");
        vbox.getChildren().addAll(titleLabel, msgLabel);
        
        return vbox;
    }

    private void selectNavButton(ToggleButton selected) {
        navRun.setSelected(false);
        navDash.setSelected(false);
        navData.setSelected(false);
        navSettings.setSelected(false);
        selected.setSelected(true);
    }
}
