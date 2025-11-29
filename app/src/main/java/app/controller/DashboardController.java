package app.controller;

import app.model.repository.AnalyticsRepo;
import app.model.repository.SQLite;
import app.model.repository.dto.OverallSentimentRow;
import javafx.fxml.FXML;
import javafx.scene.chart.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {
    @FXML private LineChart<String, Number> sentimentChart;

    private AnalyticsRepo analyticsRepo;
    private String runId;

    public void setAnalyticsRepo(AnalyticsRepo repo) { this.analyticsRepo = repo; }
    public void setRun(String runId) { this.runId = runId; }

    @FXML
    public void initialize() {
        ((CategoryAxis) sentimentChart.getXAxis()).setLabel("Date (UTC)");
        ((NumberAxis) sentimentChart.getYAxis()).setLabel("Count");
        sentimentChart.setCreateSymbols(false);
    }

    public void loadData() {
        if (analyticsRepo == null || runId == null) return;
        var bundle = analyticsRepo.readOverall(runId);
        plot(bundle.overall()); 
    }

    private void plot(List<OverallSentimentRow> rows) {
        sentimentChart.getData().clear();
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        var sPos = new XYChart.Series<String, Number>(); sPos.setName("Positive");
        var sNeg = new XYChart.Series<String, Number>(); sNeg.setName("Negative");
        var sNeu = new XYChart.Series<String, Number>(); sNeu.setName("Neutral");
        for (var r : rows) {
            String x = fmt.format(r.bucketStart());
            sPos.getData().add(new XYChart.Data<>(x, r.pos()));
            sNeg.getData().add(new XYChart.Data<>(x, r.neg()));
            sNeu.getData().add(new XYChart.Data<>(x, r.neu()));
        }
        sentimentChart.getData().addAll(sPos, sNeg, sNeu);
    }
}