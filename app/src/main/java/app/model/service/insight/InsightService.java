package app.model.service.insight;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

public class InsightService {

    private final Path dbPath;
    private final LlmClient llm;

    public InsightService(Path dbPath) {
        this.dbPath = dbPath;
        this.llm = LlmClientFactory.fromFixedDefault();
    }

    // ======= Public API =======
    public String summarizeSentiment(String runId) {
        return runLLM("overall", () -> fetchOverallRows(runId), Templates.OVERALL_SENTIMENT);
    }

    public String summarizeDamage(String runId) {
        return runLLM("damage", () -> fetchDamageRows(runId), Templates.DAMAGE);
    }

    public String summarizeRelief(String runId) {
        return runLLM("relief", () -> fetchReliefRows(runId), Templates.RELIEF);
    }

    public String summarizeTask3(String runId) {
        return runLLM("task3", () -> fetchTask3Rows(runId), Templates.TASK3);
    }

    public String summarizeTask4(String runId, LocalDate from, LocalDate to) {
        return runLLM("task4", () -> fetchTask4Rows(runId, from, to), Templates.TASK4);
    }

    // ======= Core =======
    private String runLLM(String tag, Supplier<List<String>> dataSupplier, String template) {
        List<String> rows = dataSupplier.get();
        String prompt = template + "\n\nDATA:\n" + String.join("\n", rows);
        try {
            return llm.complete(prompt);
        } catch (Exception ex) {
            // Fallback an toàn: trả về tóm tắt tối giản + lỗi
            return "⚠️ LLM unavailable (" + ex.getMessage() + ").\n\n"
                 + "Quick fallback summary from local data (" + tag + "):\n"
                 + quickFallback(rows);
        }
    }

    private static String quickFallback(List<String> rows) {
        if (rows == null || rows.isEmpty()) return "(no data)";
        int n = Math.min(rows.size(), 10);
        StringBuilder sb = new StringBuilder("Top " + n + " lines:\n");
        for (int i = 0; i < n; i++) sb.append("- ").append(rows.get(i)).append('\n');
        return sb.toString();
    }

    // ======= SQL fetchers (ví dụ; giữ đúng tên bảng của bạn) =======
    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private List<String> fetchOverallRows(String runId) {
        String sql = """
            SELECT substr(bucket_start,1,10) AS day, pos, neg, neu
            FROM overall_sentiment WHERE run_id=? ORDER BY day
        """;
        List<String> out = new ArrayList<>();
        try (var c = connect(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(String.format("%s | pos=%d neg=%d neu=%d",
                            rs.getString("day"), rs.getInt("pos"), rs.getInt("neg"), rs.getInt("neu")));
                }
            }
        } catch (Exception e) { out.add("(overall error: " + e.getMessage() + ")"); }
        return out;
    }

    private List<String> fetchDamageRows(String runId) {
        String sql = "SELECT type, COUNT(*) cnt FROM damage WHERE run_id=? GROUP BY type ORDER BY cnt DESC";
        List<String> out = new ArrayList<>();
        try (var c = connect(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("type") + "|" + rs.getInt("cnt"));
            }
        } catch (Exception e) { out.add("(damage error: " + e.getMessage() + ")"); }
        return out;
    }

    private List<String> fetchReliefRows(String runId) {
        String sql = "SELECT item, COUNT(*) cnt FROM relief_items WHERE run_id=? GROUP BY item ORDER BY cnt DESC";
        List<String> out = new ArrayList<>();
        try (var c = connect(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("item") + "|" + rs.getInt("cnt"));
            }
        } catch (Exception e) { out.add("(relief error: " + e.getMessage() + ")"); }
        return out;
    }

    private List<String> fetchTask3Rows(String runId) {
        // Nếu bạn đã có bảng tổng hợp task3, đọc ở đây; nếu chưa, có thể kết hợp từ sentiments + items
        String sql = """
            SELECT item, SUM(CASE WHEN label='pos' THEN 1 ELSE 0 END) pos,
                         SUM(CASE WHEN label='neg' THEN 1 ELSE 0 END) neg,
                         SUM(CASE WHEN label='neu' THEN 1 ELSE 0 END) neu
            FROM sentiments s
            JOIN relief_items r ON s.run_id=r.run_id AND s.raw_id=r.raw_id
            WHERE s.run_id=?
            GROUP BY item
            ORDER BY (pos+neg+neu) DESC
        """;
        List<String> out = new ArrayList<>();
        try (var c = connect(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(String.format("%s | pos=%d neg=%d neu=%d",
                            rs.getString("item"), rs.getInt("pos"), rs.getInt("neg"), rs.getInt("neu")));
                }
            }
        } catch (Exception e) { out.add("(task3 error: " + e.getMessage() + ")"); }
        return out;
    }

    private List<String> fetchTask4Rows(String runId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT substr(s.created_at,1,10) AS day, r.item,
                   SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) pos,
                   SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) neg,
                   SUM(CASE WHEN s.label='neu' THEN 1 ELSE 0 END) neu
            FROM sentiments s
            JOIN relief_items r ON s.run_id=r.run_id AND s.raw_id=r.raw_id
            WHERE s.run_id=?
              AND date(substr(s.created_at,1,10)) BETWEEN date(?) AND date(?)
            GROUP BY day, r.item
            ORDER BY day, r.item
        """;
        List<String> out = new ArrayList<>();
        try (var c = connect(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(String.format("%s | %s | pos=%d neg=%d neu=%d",
                            rs.getString("day"), rs.getString("item"),
                            rs.getInt("pos"), rs.getInt("neg"), rs.getInt("neu")));
                }
            }
        } catch (Exception e) { out.add("(task4 error: " + e.getMessage() + ")"); }
        return out;
    }

    // ======= Prompt templates (rút gọn ví dụ) =======
    public static final class Templates {
        public static final String OVERALL_SENTIMENT =
                "You are a disaster-response analyst. Summarize overall daily sentiment trends (pos/neg/neu).";
        public static final String DAMAGE =
                "Summarize public reports of damage categories. Identify the most severe and actionable needs.";
        public static final String RELIEF =
                "Summarize distribution of relief items and highlight shortages or oversupply.";
        public static final String TASK3 =
                "Assess satisfaction (positive/negative/neutral) by relief category (housing, transport, food, medical, cash).";
        public static final String TASK4 =
                "Track sentiment over time per relief category; explain which categories improve or worsen and why.";
    }
}