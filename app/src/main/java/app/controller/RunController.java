package app.controller;

import app.model.service.config.AppConfig;
import app.model.service.pipeline.PipelineService;
import app.model.service.pipeline.RunConfig;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.Instant;
import java.util.*;

public class RunController {
    @FXML private Label envLabel;
    @FXML private Button checkEnvBtn;
    @FXML private TextField keywordsField;
    @FXML private Button runIngestBtn;

    private AppConfig config;
    private PipelineService pipeline;

    @FXML
    public void initialize() {
        try {
            config = AppConfig.load();
            // FIX: Xóa tham số 'config' vì hàm createDefault() không nhận tham số nào
            pipeline = PipelineService.createDefault();
            envLabel.setText("Ready. Edit keywords or press Run Ingest.");
        } catch (Exception e) {
            e.printStackTrace();
            envLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    public void onCheckEnv() {
        String javaVer = System.getProperty("java.version");
        envLabel.setText("Java " + javaVer + " ✓");
    }

    private static List<String> parseKeywords(String raw){
        if (raw == null) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).distinct().toList();
    }

    @FXML
    public void onRunIngest() {
        try {
            var uiKeywords = parseKeywords(keywordsField.getText());
            var chosen = uiKeywords.isEmpty() ? config.run.keywords : uiKeywords;
            var rc = new RunConfig(
                    UUID.randomUUID().toString(),
                    config.run.connectors,
                    chosen,
                    Instant.parse(config.run.time.from),
                    Instant.parse(config.run.time.to),
                    Set.copyOf(config.run.tasks)
            );
            var summary = pipeline.run(rc);
            envLabel.setText("Ingested: " + summary.ingested() + " rows ✓");
        } catch (Exception e) {
            e.printStackTrace();
            envLabel.setText("Error: " + e.getMessage());
        }
    }
}