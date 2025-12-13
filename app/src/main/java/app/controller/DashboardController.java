package app.controller;

import app.model.service.insight.LlmClientFactory;
import app.model.repository.AnalyticsRepo;
import app.model.repository.SQLite;
import app.model.service.nlp.LocalNlpModel;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import app.model.service.insight.InsightConfig;
import app.model.service.insight.InsightService;
import app.model.service.insight.LocalEchoLlmClient;
import app.model.service.insight.LlmClient;
import app.model.service.insight.VertexAiClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Supplier;

public class DashboardController {

    @FXML private LineChart<String, Number> overallChart;
    @FXML private Label overallSubtitle;
    @FXML private BarChart<String, Number> damageChart;
    @FXML private PieChart reliefPie;
    @FXML private StackedBarChart<String, Number> task3Chart;
    @FXML private Button btnExportTask3;
    @FXML private LineChart<String, Number> task4Chart;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnRefreshTask4;
    @FXML private TextArea txtOverallSummary;
    @FXML private TextArea txtDamageSummary;
    @FXML private TextArea txtReliefSummary;
    @FXML private TextArea txtTask3Summary;
    @FXML private TextArea txtTask4Summary;
    @FXML private Button btnSummarizeOverall;
    @FXML private Button btnSummarizeDamage;
    @FXML private Button btnSummarizeRelief;
    @FXML private Button btnSummarizeTask3;
    @FXML private Button btnSummarizeTask4;

    private String runId;
    private Path dbPath;
    private AnalyticsRepo analyticsRepo;
    private InsightService insights;
    private final LocalNlpModel nlpModel = new LocalNlpModel();

    public void setRun(String runId) { this.runId = runId; }
    public void setAnalyticsRepo(AnalyticsRepo repo) { this.analyticsRepo = repo; }

    public void initializeWith(String runId, AnalyticsRepo repo) {
        this.runId = runId;
        this.analyticsRepo = repo;
        loadData();
    }

    public void loadData() {
        if (this.runId == null || this.runId.isBlank()) this.runId = resolveLatestRunId();
        if (this.runId == null || this.runId.isBlank()) {
            System.err.println("[Dashboard] runId is null/blank; skip loading.");
            return;
        }
        if (this.dbPath == null) {
            this.dbPath = resolveDbPath();
        }
        this.analyticsRepo = Objects.requireNonNullElseGet(
                this.analyticsRepo,
                () -> new AnalyticsRepo(new SQLite(this.dbPath.toString()))
        );
        System.out.println("[Dashboard] analyticsRepo=" + (this.analyticsRepo == null ? "null" : "ok"));
        try {
            this.insights = new InsightService(this.analyticsRepo, LlmClientFactory.fromSettings(InsightConfig.ACTIVE));
        } catch (Exception ex) {
            System.err.println("[Dashboard] InsightService init failed: " + ex.getMessage());
            this.insights = null;
        }
        wireSummaryButtons();
        loadOverview();
        loadDamage();
        loadRelief();
        initTask34(this.runId);
    }

    private LlmClient createLlmClient() {
        try {
            Path keyPath = Path.of("").toAbsolutePath().resolveSibling("data").resolve("keys").resolve("vertex_sa.json");
            if (!Files.exists(keyPath)) {
                System.err.println("[Dashboard] vertex_sa.json not found → LocalEcho");
                return new LocalEchoLlmClient("local-echo");
            }
            String json = Files.readString(keyPath);
            String projectId = extractProjectId(json);
            if (projectId == null || projectId.isBlank()) {
                System.err.println("[Dashboard] project_id missing → LocalEcho");
                return new LocalEchoLlmClient("local-echo");
            }
            String model  = "gemini-2.5-flash";
            String region = "us-central1";
            System.out.println("[Dashboard] LLM=VertexAI model=" + model + " region=" + region + " project=" + projectId);
            return new VertexAiClient(true, model, region, projectId, keyPath.toString());
        } catch (Exception ex) {
            System.err.println("[Dashboard] createLlmClient error → LocalEcho: " + ex);
            return new LocalEchoLlmClient("local-echo");
        }
    }

    private static String extractProjectId(String json) {
        try {
            Matcher m = Pattern.compile("\"project_id\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolveDbPath() {
        Path cwd = Path.of("").toAbsolutePath();
        Path pref = cwd.resolveSibling("data").resolve("app.db");
        Path alt  = cwd.resolve("data").resolve("app.db");
        Path chosen = Files.exists(pref) ? pref : (Files.exists(alt) ? alt : pref);
        System.out.println("[Dashboard] DB path = " + chosen);
        return chosen;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private String resolveLatestRunId() {
        String sql = "SELECT run_id FROM runs ORDER BY started_at DESC LIMIT 1";
        try (var conn = connect(); var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getString(1);
        } catch (Exception ex) {
            System.err.println("[Dashboard] cannot resolve latest run: " + ex.getMessage());
        }
        return null;
    }

    private static final class OverallRow {
        final String dayIso;
        final int pos, neg, neu;
        OverallRow(String dayIso, int pos, int neg, int neu) { this.dayIso = dayIso; this.pos = pos; this.neg = neg; this.neu = neu; }
    }

    private List<OverallRow> queryOverall(String runId) {
        String sqlOverall = """
            SELECT substr(bucket_start,1,10) AS day,
                   COALESCE(pos,0) AS pos,
                   COALESCE(neg,0) AS neg,
                   COALESCE(neu,0) AS neu
            FROM overall_sentiment
            WHERE run_id = ?
            ORDER BY day
        """;
        List<OverallRow> out = new ArrayList<>();
        try (var conn = connect()) {
            try (var ps = conn.prepareStatement(sqlOverall)) {
                ps.setString(1, runId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OverallRow(rs.getString("day"), rs.getInt("pos"), rs.getInt("neg"), rs.getInt("neu")));
                    }
                }
            }
            if (!out.isEmpty()) return out;
            String sqlFallback = """
                SELECT substr(ts,1,10) AS day,
                       SUM(CASE WHEN label='pos' THEN 1 ELSE 0 END) AS pos,
                       SUM(CASE WHEN label='neg' THEN 1 ELSE 0 END) AS neg,
                       SUM(CASE WHEN label='neu' THEN 1 ELSE 0 END) AS neu
                FROM sentiments
                WHERE run_id = ?
                GROUP BY day
                ORDER BY day
            """;
            try (var ps2 = conn.prepareStatement(sqlFallback)) {
                ps2.setString(1, runId);
                try (var rs = ps2.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OverallRow(rs.getString("day"), rs.getInt("pos"), rs.getInt("neg"), rs.getInt("neu")));
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private LocalDate[] queryOverallDateRange(String runId) {
        String sql = "SELECT MIN(substr(bucket_start,1,10)) AS minDay, MAX(substr(bucket_start,1,10)) AS maxDay FROM overall_sentiment WHERE run_id = ?";
        LocalDate min = null, max = null;
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String a = rs.getString("minDay");
                    String b = rs.getString("maxDay");
                    if (a != null) min = LocalDate.parse(a);
                    if (b != null) max = LocalDate.parse(b);
                }
            }
        } catch (Exception ex) {
            System.err.println("[Task4] range query failed: " + ex.getMessage());
        }
        return new LocalDate[]{min, max};
    }

    private void loadOverview() {
        if (overallChart == null) return;
        overallChart.getData().clear();
        var rows = queryOverall(runId);
        XYChart.Series<String, Number> sPos = new XYChart.Series<>(); sPos.setName("Positive");
        XYChart.Series<String, Number> sNeg = new XYChart.Series<>(); sNeg.setName("Negative");
        XYChart.Series<String, Number> sNeu = new XYChart.Series<>(); sNeu.setName("Neutral");
        int total = 0;
        for (var r : rows) {
            sPos.getData().add(new XYChart.Data<>(r.dayIso, r.pos));
            sNeg.getData().add(new XYChart.Data<>(r.dayIso, r.neg));
            sNeu.getData().add(new XYChart.Data<>(r.dayIso, r.neu));
            total += r.pos + r.neg + r.neu;
        }
        String fromDay = rows.isEmpty() ? null : rows.get(0).dayIso;
        String toDay   = rows.isEmpty() ? null : rows.get(rows.size() - 1).dayIso;
        overallChart.getData().addAll(sPos, sNeg, sNeu);
        if (overallSubtitle != null) {
            if (rows.isEmpty()) {
                overallSubtitle.setText("No data for run: " + runId);
            } else {
                String range = (fromDay != null && toDay != null) ? (" | Range: " + fromDay + " → " + toDay) : "";
                overallSubtitle.setText("Total analyzed posts: " + total + " (run=" + runId + ")" + range);
            }
        }
    }

    private static final class TagCount {
        final String tag;
        final int cnt;
        TagCount(String tag, int cnt) { this.tag = tag; this.cnt = cnt; }
    }

    private List<TagCount> queryDamage(String runId) {
        String sql = """
            SELECT type AS tag, COUNT(*) AS cnt
            FROM damage
            WHERE run_id = ?
            GROUP BY type
            ORDER BY cnt DESC
        """;
        List<TagCount> out = new ArrayList<>();
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString("tag"), rs.getInt("cnt")));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private void loadDamage() {
        if (damageChart == null) return;
        damageChart.getData().clear();
        var rows = queryDamage(runId);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Damage Types");
        for (var r : rows) s.getData().add(new XYChart.Data<>(r.tag, r.cnt));
        damageChart.getData().add(s);
    }

    private List<TagCount> queryRelief(String runId) {
        String sql = """
            SELECT item AS tag, COUNT(*) AS cnt
            FROM relief_items
            WHERE run_id = ?
            GROUP BY item
            ORDER BY cnt DESC
        """;
        List<TagCount> out = new ArrayList<>();
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString("tag"), rs.getInt("cnt")));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    private void loadRelief() {
        if (reliefPie == null) return;
        reliefPie.getData().clear();
        var rows = queryRelief(runId);
        for (var r : rows) reliefPie.getData().add(new PieChart.Data(r.tag, r.cnt));
    }

    private void loadTask3() {
        if (task3Chart == null) return;
        task3Chart.getData().clear();
        LocalDate from = (dpFrom != null) ? dpFrom.getValue() : null;
        LocalDate to   = (dpTo   != null) ? dpTo.getValue()   : null;
        List<RawPost> posts = fetchRawPosts(runId, from, to);
        Map<String, int[]> stats = computeTask3Stats(posts);
        List<Map.Entry<String, int[]>> ordered = new ArrayList<>(stats.entrySet());
        ordered.sort((a,b) -> Integer.compare(
                (b.getValue()[0]+b.getValue()[1]+b.getValue()[2]),
                (a.getValue()[0]+a.getValue()[1]+a.getValue()[2])));
        XYChart.Series<String, Number> sPos = new XYChart.Series<>(); sPos.setName("Positive");
        XYChart.Series<String, Number> sNeg = new XYChart.Series<>(); sNeg.setName("Negative");
        XYChart.Series<String, Number> sNeu = new XYChart.Series<>(); sNeu.setName("Neutral");
        for (var e : ordered) {
            String item = e.getKey();
            int[] v = e.getValue();
            sPos.getData().add(new XYChart.Data<>(item, v[0]));
            sNeg.getData().add(new XYChart.Data<>(item, v[1]));
            sNeu.getData().add(new XYChart.Data<>(item, v[2]));
        }
        task3Chart.getData().addAll(sPos, sNeg, sNeu);
    }

    private Map<String, int[]> computeTask3Stats(List<RawPost> posts) {
        Map<String, int[]> stats = new HashMap<>();
        for (RawPost p : posts) {
            Map<String, Double> sentiments = nlpModel.analyzeReliefSentiment(p.text());
            for (var entry : sentiments.entrySet()) {
                String item = entry.getKey();
                double score = entry.getValue();
                stats.putIfAbsent(item, new int[]{0,0,0});
                if (score > 0.05)      stats.get(item)[0]++;
                else if (score < -0.05) stats.get(item)[1]++;
                else                    stats.get(item)[2]++;
            }
        }
        return stats;
    }

    private void exportTask3Csv() {
        try {
            LocalDate from = (dpFrom != null) ? dpFrom.getValue() : null;
            LocalDate to   = (dpTo   != null) ? dpTo.getValue()   : null;
            List<RawPost> posts = fetchRawPosts(runId, from, to);
            Map<String, int[]> stats = computeTask3Stats(posts);
            Path baseDir = (dbPath != null ? dbPath.getParent() : Path.of("data"));
            Path outDir = baseDir.resolve("exports");
            Files.createDirectories(outDir);
            String ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
            Path out = outDir.resolve("task3_" + runId + "_" + ts + ".csv");
            try (var w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                w.write("item,positive,negative,neutral,total\n");
                List<Map.Entry<String,int[]>> ordered = new ArrayList<>(stats.entrySet());
                ordered.sort((a,b) -> Integer.compare(
                    (b.getValue()[0]+b.getValue()[1]+b.getValue()[2]),
                    (a.getValue()[0]+a.getValue()[1]+a.getValue()[2])));
                for (var e : ordered) {
                    int[] v = e.getValue();
                    int total = v[0]+v[1]+v[2];
                    w.write(e.getKey()+","+v[0]+","+v[1]+","+v[2]+","+total+"\n");
                }
            }
            System.out.println("[Dashboard] Task3 CSV exported -> " + out.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("[Dashboard] exportTask3Csv failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadTask4() {
        if (task4Chart == null) return;
        task4Chart.getData().clear();
        LocalDate from = (dpFrom != null) ? dpFrom.getValue() : null;
        LocalDate to   = (dpTo   != null) ? dpTo.getValue()   : null;
        List<RawPost> posts = fetchRawPosts(runId, from, to);
        Map<String, Map<String, List<Double>>> timeSeries = new HashMap<>();
        for (RawPost p : posts) {
            Map<String, Double> sentiments = nlpModel.analyzeReliefSentiment(p.text());
            for (var entry : sentiments.entrySet()) {
                String item = entry.getKey();
                double score = entry.getValue();
                timeSeries.computeIfAbsent(item, k -> new TreeMap<>())
                          .computeIfAbsent(p.date(), k -> new ArrayList<>())
                          .add(score);
            }
        }
        for (var itemEntry : timeSeries.entrySet()) {
            String item = itemEntry.getKey();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(item);
            for (var dateEntry : itemEntry.getValue().entrySet()) {
                List<Double> scores = dateEntry.getValue();
                double avg = scores.stream().mapToDouble(d -> d).average().orElse(0.0);
                series.getData().add(new XYChart.Data<>(dateEntry.getKey(), avg));
            }
            task4Chart.getData().add(series);
        }
    }

    private void initTask34(String runId) {
        if (dpFrom != null && dpTo != null) {
            if (dpFrom.getValue() == null || dpTo.getValue() == null) {
                LocalDate[] range = queryOverallDateRange(runId);
                if (range[0] != null && range[1] != null) {
                    dpFrom.setValue(range[0]);
                    dpTo.setValue(range[1]);
                } else {
                    dpFrom.setValue(LocalDate.of(2024, 9, 1));
                    dpTo.setValue(LocalDate.of(2024, 9, 30));
                }
            }
        }
        if (btnRefreshTask4 != null) {
            btnRefreshTask4.setOnAction(e -> { loadTask3(); loadTask4(); });
        }
        if (btnExportTask3 != null) {
            btnExportTask3.setOnAction(e -> exportTask3Csv());
        }
        loadTask3();
        loadTask4();
    }

    private void wireSummaryButtons() {
        if (btnSummarizeOverall != null) {
            btnSummarizeOverall.setOnAction(e -> {
                String s;
                try {
                    if (insights != null) {
                        LocalDate f = dpFrom != null ? dpFrom.getValue() : null;
                        LocalDate t = dpTo   != null ? dpTo.getValue()   : null;
                        s = insights.summarizeOverall(this.runId, f, t);
                        s = safeOrFallback(s, this::summarizeOverallFallback);
                    } else {
                        s = summarizeOverallFallback();
                    }
                } catch (Exception ex) {
                    s = summarizeOverallFallback();
                }
                if (txtOverallSummary != null) txtOverallSummary.setText(s);
            });
        }
        if (btnSummarizeDamage != null) {
            btnSummarizeDamage.setOnAction(e -> {
                String s;
                try {
                    if (insights != null) {
                        s = insights.summarizeDamage(this.runId);
                        s = safeOrFallback(s, this::summarizeDamageFallback);
                    } else {
                        s = summarizeDamageFallback();
                    }
                } catch (Exception ex) {
                    s = summarizeDamageFallback();
                }
                if (txtDamageSummary != null) txtDamageSummary.setText(s);
            });
        }
        if (btnSummarizeRelief != null) {
            btnSummarizeRelief.setOnAction(e -> {
                String s;
                try {
                    if (insights != null) {
                        s = insights.summarizeRelief(this.runId);
                        s = safeOrFallback(s, this::summarizeReliefFallback);
                    } else {
                        s = summarizeReliefFallback();
                    }
                } catch (Exception ex) {
                    s = summarizeReliefFallback();
                }
                if (txtReliefSummary != null) txtReliefSummary.setText(s);
            });
        }
        if (btnSummarizeTask3 != null) {
            btnSummarizeTask3.setOnAction(e -> {
                String s;
                try {
                    if (insights != null) {
                        s = insights.summarizeTask3(this.runId);
                        s = safeOrFallback(s, () ->
                            summarizeTask3Fallback(
                                dpFrom != null ? dpFrom.getValue() : null,
                                dpTo   != null ? dpTo.getValue()   : null
                            )
                        );
                    } else {
                        s = summarizeTask3Fallback(
                                dpFrom != null ? dpFrom.getValue() : null,
                                dpTo   != null ? dpTo.getValue()   : null
                        );
                    }
                } catch (Exception ex) {
                    s = summarizeTask3Fallback(
                            dpFrom != null ? dpFrom.getValue() : null,
                            dpTo   != null ? dpTo.getValue()   : null
                    );
                }
                if (txtTask3Summary != null) txtTask3Summary.setText(s);
            });
        }
        if (btnSummarizeTask4 != null) {
            btnSummarizeTask4.setOnAction(e -> {
                LocalDate from = dpFrom != null ? dpFrom.getValue() : null;
                LocalDate to   = dpTo   != null ? dpTo.getValue()   : null;
                if (from == null || to == null) {
                    LocalDate[] r = queryOverallDateRange(this.runId);
                    if (from == null) from = (r[0] != null ? r[0] : LocalDate.now().minusDays(30));
                    if (to   == null) to   = (r[1] != null ? r[1] : LocalDate.now());
                }
                String s;
                try {
                    if (insights != null) {
                        s = insights.summarizeTask4(this.runId, from, to);
                        final LocalDate f = from; final LocalDate t = to;
                        s = safeOrFallback(s, () -> summarizeTask4Fallback(f, t));
                    } else {
                        s = summarizeTask4Fallback(from, to);
                    }
                } catch (Exception ex) {
                    s = summarizeTask4Fallback(from, to);
                }
                if (txtTask4Summary != null) txtTask4Summary.setText(s);
            });
        }
    }

    private static String safeOrFallback(String s, Supplier<String> fallback) {
        return (s == null || s.isBlank()) ? fallback.get() : s;
    }

    private String summarizeOverallFallback() {
        var rows = queryOverall(runId);
        int pos = 0, neg = 0, neu = 0;
        for (var r : rows) { pos += r.pos; neg += r.neg; neu += r.neu; }
        int total = pos + neg + neu;
        String range = rows.isEmpty() ? "N/A" : rows.get(0).dayIso + " → " + rows.get(rows.size()-1).dayIso;
        double posPct = total == 0 ? 0 : (pos * 100.0 / total);
        double negPct = total == 0 ? 0 : (neg * 100.0 / total);
        return """
            Overall Sentiment (fallback)
            Run: %s | Range: %s
            Total analyzed posts: %d
            Positive: %d (%.1f%%)
            Negative: %d (%.1f%%)
            Neutral : %d (%.1f%%)
            Key take: Overall mood is %s.
            """.formatted(
                runId, range, total, pos, posPct, neg, negPct, neu, total==0?0.0:(neu*100.0/total),
                (posPct >= negPct ? "more positive than negative" : "more negative than positive")
            );
    }

    private String summarizeDamageFallback() {
        var rows = queryDamage(runId);
        if (rows.isEmpty()) return "No damage records for run: " + runId;
        int total = rows.stream().mapToInt(r -> r.cnt).sum();
        var top3 = rows.stream().limit(3).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append("Damage Summary (fallback)\n");
        sb.append("Run: ").append(runId).append("\n");
        sb.append("Total damage mentions: ").append(total).append("\n");
        sb.append("Top categories:\n");
        for (var r : top3) sb.append(" - ").append(r.tag).append(": ").append(r.cnt).append("\n");
        return sb.toString();
    }

    private String summarizeReliefFallback() {
        var rows = queryRelief(runId);
        if (rows.isEmpty()) return "No relief item records for run: " + runId;
        int total = rows.stream().mapToInt(r -> r.cnt).sum();
        var top3 = rows.stream().limit(3).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append("Relief Items Summary (fallback)\n");
        sb.append("Run: ").append(runId).append("\n");
        sb.append("Total relief-related posts: ").append(total).append("\n");
        sb.append("Most discussed items:\n");
        for (var r : top3) sb.append(" - ").append(r.tag).append(": ").append(r.cnt).append("\n");
        return sb.toString();
    }

    private String summarizeTask3Fallback(LocalDate from, LocalDate to) {
        List<RawPost> posts = fetchRawPosts(runId, from, to);
        Map<String, int[]> stats = computeTask3Stats(posts);
        if (stats.isEmpty()) return "No satisfaction data in the selected range.";
        var byNeg = stats.entrySet().stream().sorted((a,b) -> Integer.compare(b.getValue()[1], a.getValue()[1])).limit(3).toList();
        var byPos = stats.entrySet().stream().sorted((a,b) -> Integer.compare(b.getValue()[0], a.getValue()[0])).limit(3).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Task 3 — Satisfaction by Category (fallback)\n");
        if (from != null || to != null) sb.append("Range: ").append(from).append(" → ").append(to).append("\n");
        sb.append("Most negative sentiment:\n");
        for (var e : byNeg) { int[] v = e.getValue(); sb.append(" - ").append(e.getKey()).append(": neg=").append(v[1]).append(", pos=").append(v[0]).append(", neu=").append(v[2]).append("\n"); }
        sb.append("Most positive sentiment:\n");
        for (var e : byPos) { int[] v = e.getValue(); sb.append(" - ").append(e.getKey()).append(": pos=").append(v[0]).append(", neg=").append(v[1]).append(", neu=").append(v[2]).append("\n"); }
        sb.append("Implication: Prioritize resources for categories with persistent negatives; maintain/scale efforts for positive ones.");
        return sb.toString();
    }

    private String summarizeTask4Fallback(LocalDate from, LocalDate to) {
        List<RawPost> posts = fetchRawPosts(runId, from, to);
        Map<String, Map<String, List<Double>>> timeSeries = new HashMap<>();
        for (RawPost p : posts) {
            Map<String, Double> sentiments = nlpModel.analyzeReliefSentiment(p.text());
            for (var entry : sentiments.entrySet()) {
                String item = entry.getKey();
                double score = entry.getValue();
                timeSeries.computeIfAbsent(item, k -> new TreeMap<>())
                        .computeIfAbsent(p.date(), k -> new ArrayList<>())
                        .add(score);
            }
        }
        if (timeSeries.isEmpty()) return "No time-series sentiment found in the selected range.";
        record Agg(double avg, double first, double last){};
        Map<String, Agg> agg = new LinkedHashMap<>();
        for (var e : timeSeries.entrySet()) {
            List<Map.Entry<String,List<Double>>> days = new ArrayList<>(e.getValue().entrySet());
            if (days.isEmpty()) continue;
            double sum=0; int n=0;
            for (var d : days) { for (double v : d.getValue()) { sum += v; n++; } }
            double avg = n==0?0:sum/n;
            double first = days.get(0).getValue().stream().mapToDouble(v->v).average().orElse(0);
            double last  = days.get(days.size()-1).getValue().stream().mapToDouble(v->v).average().orElse(0);
            agg.put(e.getKey(), new Agg(avg, first, last));
        }
        var improving = agg.entrySet().stream().filter(x -> x.getValue().last - x.getValue().first > 0.05).map(Map.Entry::getKey).toList();
        var worsening = agg.entrySet().stream().filter(x -> x.getValue().last - x.getValue().first < -0.05).map(Map.Entry::getKey).toList();
        var positive = agg.entrySet().stream().filter(x -> x.getValue().avg > 0.05).map(Map.Entry::getKey).toList();
        var negative = agg.entrySet().stream().filter(x -> x.getValue().avg < -0.05).map(Map.Entry::getKey).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Task 4 — Sentiment over Time per Category (fallback)\n");
        if (from != null || to != null) sb.append("Range: ").append(from).append(" → ").append(to).append("\n");
        sb.append("Categories improving: ").append(improving.isEmpty()? "—" : String.join(", ", improving)).append("\n");
        sb.append("Categories worsening: ").append(worsening.isEmpty()? "—" : String.join(", ", worsening)).append("\n");
        sb.append("Average positive sentiment: ").append(positive.isEmpty()? "—" : String.join(", ", positive)).append("\n");
        sb.append("Average negative sentiment: ").append(negative.isEmpty()? "—" : String.join(", ", negative)).append("\n");
        sb.append("Interpretation: Focus on consistently negative or worsening categories (e.g., housing/transport), sustain efforts where averages are positive (e.g., food/medical).");
        return sb.toString();
    }

    @FXML
    private void initialize() {
        if (overallChart != null) overallChart.setTitle("Overall Sentiment (Daily)");
        if (damageChart != null)  damageChart.setTitle("Damage Types");
        if (reliefPie != null)    reliefPie.setTitle("Relief Items Distribution");
        if (task3Chart != null)   task3Chart.setTitle("Satisfaction by Relief Category");
        if (task4Chart != null)   task4Chart.setTitle("Sentiment Over Time per Category");
    }

    private record RawPost(String text, String date) {}

    private List<RawPost> fetchRawPosts(String runId) { return fetchRawPosts(runId, null, null); }

    private List<RawPost> fetchRawPosts(String runId, LocalDate from, LocalDate to) {
        List<RawPost> posts = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT text, substr(ts, 1, 10) as day FROM posts WHERE run_id = ?");
        if (from != null) sb.append(" AND substr(ts,1,10) >= ?");
        if (to   != null) sb.append(" AND substr(ts,1,10) <= ?");
        sb.append(" ORDER BY ts ASC");
        try (var conn = connect(); var ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            ps.setString(idx++, runId);
            if (from != null) ps.setString(idx++, from.toString());
            if (to   != null) ps.setString(idx++, to.toString());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String txt = rs.getString("text");
                    String d   = rs.getString("day");
                    if (txt != null && d != null) posts.add(new RawPost(txt, d));
                }
            }
        } catch (Exception ex) {
            System.err.println("[Dashboard] fetchRawPosts failed: " + ex.getMessage());
            ex.printStackTrace();
        }
        return posts;
    }
}