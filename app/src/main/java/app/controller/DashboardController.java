package app.controller;

import app.model.repository.AnalyticsRepo;
import app.model.repository.AnalyticsRepo.TagCount;
import app.model.repository.RunsRepo;
import app.model.repository.SQLite;
import app.model.repository.dto.OverallSentimentRow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private ComboBox<String> runSelector;
    @FXML private DatePicker fromDate, toDate;
    @FXML private Spinner<Integer> topNSpinner;
    @FXML private Button btnRefresh, btnExport;
    @FXML private Label statusLabel;

    @FXML private LineChart<String, Number> sentimentChart;
    @FXML private BarChart<String, Number> damageChart;
    @FXML private BarChart<String, Number> reliefChart;
    @FXML private BarChart<String, Number> kwChart;   // keywords
    @FXML private BarChart<String, Number> tagChart;  // hashtags

    private final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private SQLite db;
    private AnalyticsRepo analyticsRepo;
    private RunsRepo runsRepo;

    private boolean controlsInitialized = false;

    @FXML
    public void initialize() {
        initControls();
    }

    private void initControls() {
        if (controlsInitialized) return;
        controlsInitialized = true;
        if (topNSpinner != null) {
            topNSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 100, 20));
        }
        if (btnRefresh != null) btnRefresh.setOnAction(e -> refreshAll());
        if (btnExport != null) btnExport.setOnAction(e -> exportAllCsv());
        if (runSelector != null) runSelector.setOnAction(e -> refreshAll());
    }

    private void loadRunList(String preferredRunId) {
        // Minimal, safe: populate with preferredRunId if provided.
        var ids = new java.util.ArrayList<String>();
        if (preferredRunId != null && !preferredRunId.isBlank()) {
            ids.add(preferredRunId);
        }
        if (runSelector != null) {
            runSelector.getItems().setAll(ids);
            if (!ids.isEmpty()) runSelector.getSelectionModel().select(ids.getFirst());
        }
    }

    // === Methods for RunController wiring (compatibility layer) ===
    public void setAnalyticsRepo(app.model.repository.AnalyticsRepo repo) {
        this.analyticsRepo = repo;
    }
    public void setRun(String runId) {
        // ensure controls are ready
        initControls();
        if (runSelector != null && runId != null && !runId.isBlank()) {
            runSelector.getItems().setAll(java.util.List.of(runId));
            runSelector.getSelectionModel().select(runId);
        }
    }
    public void loadData() {
        // Simply delegate to refreshAll (will read current selection from runSelector)
        refreshAll();
    }

    /** Được gọi từ RunController sau khi load FXML */
    public void setContext(SQLite db, String preferredRunId) {
        this.db = db;
        this.analyticsRepo = new AnalyticsRepo(db);
        this.runsRepo = new RunsRepo(db);

        initControls();
        loadRunList(preferredRunId);
        refreshAll();
    }

    private void refreshAll() {
        if (analyticsRepo == null) {
            if (statusLabel != null) statusLabel.setText("AnalyticsRepo not set.");
            return;
        }
        var runId = runSelector != null ? runSelector.getSelectionModel().getSelectedItem() : null;
        if (runId == null || runId.isBlank()) {
            if (statusLabel != null) statusLabel.setText("No run selected.");
            return;
        }

        // Lọc theo ngày (tùy chọn) – dùng biến final để dùng trong lambda
        final Instant fromI = (fromDate != null && fromDate.getValue() != null)
                ? fromDate.getValue().atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;
        final Instant toI = (toDate != null && toDate.getValue() != null)
                ? toDate.getValue().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;
        final int topNi = (topNSpinner != null ? topNSpinner.getValue() : 20);
        final String runSel = runId;

        // Chạy nền để UI không bị lag
        statusLabel.setText("Loading…");
        new Thread(() -> {
            try {
                loadSentiment(runSel, fromI, toI);
                loadDamage(runSel);
                loadRelief(runSel);
                loadTrends(runSel, topNi);
                Platform.runLater(() -> statusLabel.setText("OK (run=" + runSel + ")"));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                ex.printStackTrace();
            }
        }).start();
    }

    private void loadSentiment(String runId, Instant from, Instant to) {
        var bundle = analyticsRepo.readOverall(runId).overall(); 
        var filtered = bundle.stream()
                .filter(r -> (from == null || !r.bucketStart().isBefore(from)) &&
                             (to   == null || r.bucketStart().isBefore(to)))
                .sorted(Comparator.comparing(OverallSentimentRow::bucketStart))
                .toList();

        var sPos = new XYChart.Series<String, Number>(); sPos.setName("pos");
        var sNeg = new XYChart.Series<String, Number>(); sNeg.setName("neg");
        var sNeu = new XYChart.Series<String, Number>(); sNeu.setName("neu");

        for (var r : filtered) {
            String day = DAY.format(r.bucketStart().atZone(ZoneOffset.UTC).toLocalDate());
            sPos.getData().add(new XYChart.Data<>(day, r.pos()));
            sNeg.getData().add(new XYChart.Data<>(day, r.neg()));
            sNeu.getData().add(new XYChart.Data<>(day, r.neu()));
        }
        Platform.runLater(() -> {
            sentimentChart.setAnimated(false);
            sentimentChart.getData().setAll(sPos, sNeg, sNeu);
        });
    }

    /* ============ (2) DAMAGE BREAKDOWN ============ */
    private void loadDamage(String runId) {
        List<TagCount> dmg = analyticsRepo.readDamageCounts(runId); // tag=count theo type
        var s = new XYChart.Series<String, Number>(); s.setName("damage");
        dmg.forEach(t -> s.getData().add(new XYChart.Data<>(t.tag(), t.cnt())));
        Platform.runLater(() -> {
            damageChart.setAnimated(false);
            damageChart.getData().setAll(s);
        });
    }

    /* ============ (3) RELIEF ITEMS BREAKDOWN ============ */
    private void loadRelief(String runId) {
        List<TagCount> rel = analyticsRepo.readReliefCounts(runId);
        var s = new XYChart.Series<String, Number>(); s.setName("relief");
        rel.forEach(t -> s.getData().add(new XYChart.Data<>(t.tag(), t.cnt())));
        Platform.runLater(() -> {
            reliefChart.setAnimated(false);
            reliefChart.getData().setAll(s);
        });
    }

    /* ============ (4) TRENDS (KEYWORDS / HASHTAGS) ============ */
    private void loadTrends(String runId, int topN) {
        var kws  = analyticsRepo.readTopKeywords(runId, topN);
        var tags = analyticsRepo.readTopHashtags(runId, topN);

        var sKw  = new XYChart.Series<String, Number>(); sKw.setName("keywords");
        var sTag = new XYChart.Series<String, Number>(); sTag.setName("hashtags");

        kws.forEach(t -> sKw.getData().add(new XYChart.Data<>(t.tag(), t.cnt())));
        tags.forEach(t -> sTag.getData().add(new XYChart.Data<>(t.tag(), t.cnt())));

        Platform.runLater(() -> {
            kwChart.setAnimated(false);
            tagChart.setAnimated(false);
            kwChart.getData().setAll(sKw);
            tagChart.getData().setAll(sTag);
        });
    }

    /* ============ EXPORT CSV (đơn giản) ============ */
    private void exportAllCsv() {
        try {
            var runId = runSelector.getSelectionModel().getSelectedItem();
            if (runId == null) return;

            var outDir = Path.of("data", "exports", runId);
            java.nio.file.Files.createDirectories(outDir);

            // damage
            var dmg = analyticsRepo.readDamageCounts(runId);
            writeTagCsv(outDir.resolve("damage.csv"), dmg);

            // relief
            var rel = analyticsRepo.readReliefCounts(runId);
            writeTagCsv(outDir.resolve("relief.csv"), rel);

            // keywords
            var kws = analyticsRepo.readTopKeywords(runId, topNSpinner.getValue());
            writeTagCsv(outDir.resolve("keywords.csv"), kws);

            // hashtags
            var tags = analyticsRepo.readTopHashtags(runId, topNSpinner.getValue());
            writeTagCsv(outDir.resolve("hashtags.csv"), tags);

            // sentiment (by day)
            var overall = analyticsRepo.readOverall(runId).overall();
            var lines = new ArrayList<String>();
            lines.add("day,pos,neg,neu");
            lines.addAll(overall.stream()
                    .map(r -> DAY.format(r.bucketStart().atZone(ZoneOffset.UTC).toLocalDate())
                            + "," + r.pos() + "," + r.neg() + "," + r.neu())
                    .collect(Collectors.toList()));
            java.nio.file.Files.write(outDir.resolve("sentiment.csv"), lines, java.nio.charset.StandardCharsets.UTF_8);

            Platform.runLater(() -> statusLabel.setText("Exported to " + outDir.toString()));
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Export error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void writeTagCsv(Path path, List<TagCount> list) throws Exception {
        var lines = new ArrayList<String>();
        lines.add("tag,count");
        for (var t : list) lines.add(t.tag() + "," + t.cnt());
        java.nio.file.Files.write(path, lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void installTooltipsLine(LineChart<String, Number> chart) {
        Platform.runLater(() -> {
            for (var s : chart.getData()) {
                for (var d : s.getData()) {
                    if (d.getNode() != null) {
                        Tooltip.install(d.getNode(), new Tooltip(d.getXValue() + ": " + d.getYValue()));
                    }
                }
            }
        });
    }

    private void installTooltipsBar(BarChart<String, Number> chart) {
        Platform.runLater(() -> {
            for (var s : chart.getData()) {
                for (var d : s.getData()) {
                    if (d.getNode() != null) {
                        Tooltip.install(d.getNode(), new Tooltip(d.getXValue() + ": " + d.getYValue()));
                    }
                }
            }
        });
    }

}