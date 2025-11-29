package app.controller;

import app.model.service.pipeline.PipelineService;
import app.model.service.pipeline.PipelineService.RunResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;   // quan trọng: dùng javafx.scene.Parent
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RunController {

    @FXML private TextField keywordField;
    @FXML private Button runBtn;
    @FXML private Label statusLabel;

    private PipelineService pipeline;
    private String lastRunId;

    @FXML
    public void initialize() {
        // Khởi tạo pipeline mặc định (DB: data/app.db, FileConnector: data/collections)
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
                return pipeline.run(kw); // chạy pipeline ở background
            }
        };

        job.setOnSucceeded(ev -> {
            RunResult result = job.getValue();
            lastRunId = result.runId;
            if (statusLabel != null) {
                statusLabel.setText("Ingested: " + result.ingested +
                        " | Analyzed: " + result.analyzed + " ✓ (run=" + lastRunId + ")");
            }
            // Mở Dashboard cho run vừa xong
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

    /** Mở cửa sổ Dashboard và truyền đúng repo + runId. */
    private void openDashboard(PipelineService pipeline, String runId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/fxml/dashboard.fxml"));
            Parent root = loader.load();
            // Wire controller
            DashboardController ctrl = loader.getController();
            if (ctrl != null) {
                // Use existing API in your project
                ctrl.setAnalyticsRepo(pipeline.analyticsRepo());
                ctrl.setRun(runId);
                ctrl.loadData();
            }

            // Reuse current stage instead of opening new window
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

    /** Nút phụ: mở lại dashboard của lần chạy gần nhất. */
    @FXML
    private void onOpenLastDashboard() {
        if (lastRunId == null) {
            if (statusLabel != null) statusLabel.setText("Chưa có run nào");
            return;
        }
        // mở lại
        openDashboard(pipeline, lastRunId);
    }
}