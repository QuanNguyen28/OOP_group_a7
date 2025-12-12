package app.controller;

import app.model.service.pipeline.PipelineService;
import app.model.service.pipeline.PipelineService.RunResult;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;

import java.nio.file.Path;

public class RunController {

    @FXML private TextField keywordField;
    @FXML private Button runBtn;
    @FXML private Label statusLabel;

    private PipelineService pipeline;
    private String lastRunId;

    @FXML
    public void initialize() {
        this.pipeline = PipelineService.createDefault();
        if (statusLabel != null) statusLabel.setText("Ready");
        if (keywordField != null) keywordField.setOnAction(e -> onRunIngest());
    }

    @FXML
    private void onRunIngest() {
        final String kw = keywordField != null && keywordField.getText() != null
                ? keywordField.getText().trim() : "";
        if (kw.isEmpty()) {
            if (statusLabel != null) statusLabel.setText("Vui lòng nhập từ khóa");
            return;
        }

        if (runBtn != null) runBtn.setDisable(true);
        if (statusLabel != null) statusLabel.setText("Đang chạy ingest + NLP…");

        Task<RunResult> job = new Task<>() {
            @Override protected RunResult call() {
                return pipeline.run(kw);
            }
        };

        job.setOnSucceeded(ev -> {
            RunResult result = job.getValue();
            lastRunId = result.runId;
            if (statusLabel != null) {
                statusLabel.setText("Ingested: " + result.ingested +
                        " | Analyzed: " + result.analyzed + " ✓ (run=" + lastRunId + ")");
            }
            openDashboard(pipeline, lastRunId);
            if (runBtn != null) runBtn.setDisable(false);
        });

        job.setOnFailed(ev -> {
            Throwable e = job.getException();
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Lỗi chạy pipeline: " + (e != null ? e.getMessage() : "unknown"));
            if (runBtn != null) runBtn.setDisable(false);
        });

        Thread t = new Thread(job, "pipeline-run");
        t.setDaemon(true);
        t.start();
    }

    private void openDashboard(PipelineService pipeline, String runId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/fxml/dashboard.fxml"));
            Parent root = loader.load();
            DashboardController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setRun(runId);
                ctrl.loadData();
            }

            Stage st = (Stage) runBtn.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 720);
            scene.getStylesheets().addAll(
                getClass().getResource("/ui/styles/tokens.css").toExternalForm(),
                getClass().getResource("/ui/styles/app.css").toExternalForm()
            );
            st.setTitle("Dashboard");
            st.setScene(scene);
            st.show();
        } catch (Exception e) {
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Open Dashboard failed: " + e.getMessage());
        }
    }

    /** Mở UI thu thập dữ liệu. Là public để FXML gọi được. */
    @FXML
    public void onOpenCollector() {
        try {
            FXMLLoader l = new FXMLLoader(getClass().getResource("/ui/fxml/collector.fxml"));
            Parent root = l.load();

            Object c = l.getController();
            if (c != null) {
                Path baseDir = Path.of("").toAbsolutePath();
                // Gọi init(Path) nếu có
                try {
                    c.getClass().getMethod("init", Path.class).invoke(c, baseDir);
                } catch (NoSuchMethodException ignore) {
                    // Nếu không có, thử setBaseDir(Path)
                    try {
                        c.getClass().getMethod("setBaseDir", Path.class).invoke(c, baseDir);
                    } catch (NoSuchMethodException ignored) {
                        // Không có phương thức tùy chọn nào -> bỏ qua
                    }
                }
            }

            Stage owner = (Stage) runBtn.getScene().getWindow();
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().addAll(
                getClass().getResource("/ui/styles/tokens.css").toExternalForm(),
                getClass().getResource("/ui/styles/app.css").toExternalForm()
            );

            Stage st = new Stage();
            st.setTitle("Collect Data");
            st.initOwner(owner);
            st.setScene(scene);
            st.show();
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(AlertType.ERROR, "Open Collector failed: " + e.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    @FXML
    private void onOpenLastDashboard() {
        if (lastRunId == null) {
            if (statusLabel != null) statusLabel.setText("Chưa có run nào");
            return;
        }
        openDashboard(pipeline, lastRunId);
    }
}