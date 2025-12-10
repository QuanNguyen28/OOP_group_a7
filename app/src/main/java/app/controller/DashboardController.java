package app.controller;

import app.model.repository.AnalyticsRepo; // optional, không bắt buộc dùng
import app.model.service.nlp.LocalNlpModel; // Import model
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardController
 * - Không phụ thuộc DTO tuỳ biến; đọc trực tiếp SQLite để vẽ biểu đồ.
 * - Hỗ trợ 4 bài toán:
 *   Overview (overall_sentiment), Damage (damage), Relief (relief_items)
 *   Task 3: Sentiment by Relief Category (relief_sentiment)
 *   Task 4: Sentiment over Time per Category (relief_sentiment_daily)
 */
public class DashboardController {

    // ====== (A) Node có thể có trong FXML (nếu không có, sẽ null và được bỏ qua) ======
    // Overview tab
    @FXML private LineChart<String, Number> overallChart; // 3 series: pos/neg/neu
    @FXML private Label overallSubtitle;

    // Damage tab
    @FXML private BarChart<String, Number> damageChart;

    // Relief tab
    @FXML private PieChart reliefPie;

    // NEW — Task 3 tab (Satisfaction by Category)
    @FXML private StackedBarChart<String, Number> task3Chart;
    @FXML private Button btnExportTask3;

    // NEW — Task 4 tab (Sentiment over Time per Category)
    @FXML private LineChart<String, Number> task4Chart;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnRefreshTask4;

    // ====== (B) State ======
    private String runId;
    private Path dbPath;                 // tự dò ../data/app.db hoặc ./data/app.db
    private AnalyticsRepo analyticsRepo; // optional: nếu RunController có set, vẫn nhận, nhưng controller không phụ thuộc repo

    // Thêm đối tượng NLP Model để phân tích trực tiếp
    private final LocalNlpModel nlpModel = new LocalNlpModel();

    // ====== (C) Public API từ RunController ======
    public void setRun(String runId) {
        this.runId = runId;
    }
    public void setAnalyticsRepo(AnalyticsRepo repo) { // optional
        this.analyticsRepo = repo;
    }

    /** Convenience: set run & repo, then load. */
    public void initializeWith(String runId, AnalyticsRepo repo) {
        this.runId = runId;
        this.analyticsRepo = repo;
        loadData();
    }

    /** Gọi sau khi FXMLLoader load xong và setRun(runId) đã được gọi. */
    public void loadData() {
        if (this.runId == null || this.runId.isBlank()) {
            this.runId = resolveLatestRunId();
        }
        if (this.runId == null || this.runId.isBlank()) {
            System.err.println("[Dashboard] runId is null/blank; skip loading.");
            return;
        }
        this.dbPath = resolveDbPath();

        // Overview: tổng hợp pos/neg/neu theo ngày (từ overall_sentiment)
        loadOverview();

        // Damage: đếm theo damage.type
        loadDamage();

        // Relief: phân bố relief_items theo item
        loadRelief();

        // Task 3,4: tab mới, dùng DatePicker range mặc định (9/2024 nếu khả dụng)
        initTask34(this.runId);
    }

    // ====== (D) Helper: DB Path & Connection ======
    private Path resolveDbPath() {
        Path cwd = Path.of("").toAbsolutePath();
        Path pref = cwd.resolveSibling("data").resolve("app.db"); // ../data/app.db
        Path alt  = cwd.resolve("data").resolve("app.db");        // ./data/app.db
        Path chosen = Files.exists(pref) ? pref : (Files.exists(alt) ? alt : pref);
        System.out.println("[Dashboard] DB path = " + chosen);
        return chosen;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /** Fallback: pick latest run_id from runs table when none specified. */
    private String resolveLatestRunId() {
        String sql = "SELECT run_id FROM runs ORDER BY started DESC LIMIT 1";
        try (var conn = connect(); var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getString(1);
        } catch (Exception ex) {
            System.err.println("[Dashboard] cannot resolve latest run: " + ex.getMessage());
        }
        return null;
    }

    // ====== (E) Overview ======
    private static final class OverallRow {
        final String dayIso; // yyyy-MM-dd lấy từ bucket_start
        final int pos, neg, neu;
        OverallRow(String dayIso, int pos, int neg, int neu) {
            this.dayIso = dayIso; this.pos = pos; this.neg = neg; this.neu = neu;
        }
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
            // Try the pre-aggregated table first
            try (var ps = conn.prepareStatement(sqlOverall)) {
                ps.setString(1, runId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OverallRow(
                            rs.getString("day"),
                            rs.getInt("pos"),
                            rs.getInt("neg"),
                            rs.getInt("neu")
                        ));
                    }
                }
            }

            if (!out.isEmpty()) return out;

            // Fallback: aggregate from sentiments table (label in ['pos','neg','neu'])
            String sqlFallback = """
                SELECT substr(created_at,1,10) AS day,
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
                        out.add(new OverallRow(
                            rs.getString("day"),
                            rs.getInt("pos"),
                            rs.getInt("neg"),
                            rs.getInt("neu")
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    /** Query min..max day for overall_sentiment of this run. */
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
        overallChart.getData().addAll(sPos, sNeg, sNeu);
        if (overallSubtitle != null) {
            if (rows.isEmpty()) {
                overallSubtitle.setText("No data for run: " + runId);
            } else {
                overallSubtitle.setText("Total analyzed posts: " + total + " (run=" + runId + ")");
            }
        }
    }

    // ====== (F) Damage ======
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
                while (rs.next()) {
                    out.add(new TagCount(rs.getString("tag"), rs.getInt("cnt")));
                }
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
        for (var r : rows) {
            s.getData().add(new XYChart.Data<>(r.tag, r.cnt));
        }
        damageChart.getData().add(s);
    }

    // ====== (G) Relief (distribution) ======
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
                while (rs.next()) {
                    out.add(new TagCount(rs.getString("tag"), rs.getInt("cnt")));
                }
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
        for (var r : rows) {
            reliefPie.getData().add(new PieChart.Data(r.tag, r.cnt));
        }
    }

    // ====== (H) Task 3: Satisfaction by Category (REWRITTEN) ======
    private void loadTask3() {
        if (task3Chart == null) return;
        task3Chart.getData().clear();

        // 1. Lấy dữ liệu thô
        List<RawPost> posts = fetchRawPosts(runId);
        
        // 2. Map chứa thống kê: Item -> [Pos, Neg, Neu]
        Map<String, int[]> stats = new HashMap<>();

        // 3. Chạy lại phân tích
        for (RawPost p : posts) {
            Map<String, Double> sentiments = nlpModel.analyzeReliefSentiment(p.text());
            
            for (var entry : sentiments.entrySet()) {
                String item = entry.getKey();
                double score = entry.getValue();
                
                stats.putIfAbsent(item, new int[]{0, 0, 0});
                if (score > 0.05) stats.get(item)[0]++;      // Pos
                else if (score < -0.05) stats.get(item)[1]++; // Neg
                else stats.get(item)[2]++;                    // Neu
            }
        }

        // 4. Vẽ biểu đồ
        XYChart.Series<String, Number> sPos = new XYChart.Series<>(); sPos.setName("Positive");
        XYChart.Series<String, Number> sNeg = new XYChart.Series<>(); sNeg.setName("Negative");
        XYChart.Series<String, Number> sNeu = new XYChart.Series<>(); sNeu.setName("Neutral");

        for (var entry : stats.entrySet()) {
            sPos.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()[0]));
            sNeg.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()[1]));
            sNeu.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()[2]));
        }
        task3Chart.getData().addAll(sPos, sNeg, sNeu);
    }

    /**
     * Xuất dữ liệu Task 3 ra file CSV.
     */
    private void exportTask3Csv() {
        // Logic xuất file CSV cho Task 3
        // Ví dụ: Ghi ra file "task3_satisfaction.csv"
        System.out.println("Exporting Task 3 data...");
        // TODO: Implement CSV writing logic here if needed
    }

    // ====== (I) Task 4: Sentiment over Time per Category (REWRITTEN) ======
    private void loadTask4() {
        if (task4Chart == null) return;
        task4Chart.getData().clear();

        // 1. Lấy dữ liệu thô
        List<RawPost> posts = fetchRawPosts(runId);

        // 2. Cấu trúc: Item -> Map<Date, List<Score>>
        Map<String, Map<String, List<Double>>> timeSeries = new HashMap<>();

        // 3. Phân tích
        for (RawPost p : posts) {
            Map<String, Double> sentiments = nlpModel.analyzeReliefSentiment(p.text());
            
            for (var entry : sentiments.entrySet()) {
                String item = entry.getKey();
                double score = entry.getValue();
                
                timeSeries.putIfAbsent(item, new TreeMap<>()); // TreeMap để sort ngày
                timeSeries.get(item).putIfAbsent(p.date(), new ArrayList<>());
                timeSeries.get(item).get(p.date()).add(score);
            }
        }

        // 4. Vẽ biểu đồ (Trung bình cộng score theo ngày)
        for (var itemEntry : timeSeries.entrySet()) {
            String item = itemEntry.getKey();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(item);

            for (var dateEntry : itemEntry.getValue().entrySet()) {
                String date = dateEntry.getKey();
                List<Double> scores = dateEntry.getValue();
                double avg = scores.stream().mapToDouble(d -> d).average().orElse(0.0);
                
                series.getData().add(new XYChart.Data<>(date, avg));
            }
            task4Chart.getData().add(series);
        }
    }

    private void initTask34(String runId) {
        // default date range: from DB min..max or fallback static range
        if (dpFrom != null && dpTo != null) {
            if (dpFrom.getValue() == null || dpTo.getValue() == null) {
                LocalDate[] range = queryOverallDateRange(runId);
                if (range[0] != null && range[1] != null) {
                    dpFrom.setValue(range[0]);
                    dpTo.setValue(range[1]);
                } else {
                    // Fallback static range used in datasets
                    dpFrom.setValue(LocalDate.of(2024, 9, 1));
                    dpTo.setValue(LocalDate.of(2024, 9, 30));
                }
            }
        }
        if (btnRefreshTask4 != null) {
            btnRefreshTask4.setOnAction(e -> loadTask4());
        }
        if (btnExportTask3 != null) {
            btnExportTask3.setOnAction(e -> exportTask3Csv());
        }
        loadTask3();
        loadTask4();
    }

    // ====== (J) Optional: gọi tự động khi FXML load xong, tránh NPE nếu chưa set run ======
    @FXML
    private void initialize() {
        // Không làm gì nếu chưa có runId; loadData() sẽ được gọi từ RunController sau khi setRun(runId).
        // Tuy nhiên, có thể set placeholder cho chart titles ở đây nếu muốn.
        if (overallChart != null) overallChart.setTitle("Overall Sentiment (Daily)");
        if (damageChart != null)  damageChart.setTitle("Damage Types");
        if (reliefPie != null)    reliefPie.setTitle("Relief Items Distribution");
        if (task3Chart != null)   task3Chart.setTitle("Satisfaction by Relief Category");
        if (task4Chart != null)   task4Chart.setTitle("Sentiment Over Time per Category");
    }

    // Class nội bộ để chứa dữ liệu thô
    private record RawPost(String text, String date) {}

    private List<RawPost> fetchRawPosts(String runId) {
        List<RawPost> posts = new ArrayList<>();
        // Query: lấy text (đã normalize) và ts (timestamp) từ bảng posts
        String sql = "SELECT text, substr(ts, 1, 10) as day FROM posts WHERE run_id = ? ORDER BY ts ASC";
        
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String txt = rs.getString("text");
                    String d = rs.getString("day");
                    if (txt != null && d != null) {
                        // text từ DB đã normalize, không cần lowercase thêm
                        posts.add(new RawPost(txt, d));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[Dashboard] fetchRawPosts failed: " + ex.getMessage());
            ex.printStackTrace();
        }
        return posts;
    }
}