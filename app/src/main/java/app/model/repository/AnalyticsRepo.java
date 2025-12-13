package app.model.repository;

import app.model.repository.dto.AnalyticsBundle;
import app.model.repository.dto.OverallSentimentRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsRepo {
    private final SQLite db;

    public AnalyticsRepo(SQLite db){ this.db = db; }

    /* =========================
       0) API cũ – giữ nguyên
       ========================= */

    public int saveOverallSentiment(String runId, List<OverallSentimentRow> rows) {
        final String sql = "INSERT OR REPLACE INTO overall_sentiment(run_id,bucket_start,pos,neg,neu) VALUES(?,?,?,?,?)";
        try (var con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            int n = 0;
            for (var r : rows) {
                ps.setString(1, runId);
                ps.setString(2, r.bucketStart().toString());
                ps.setInt(3, r.pos());
                ps.setInt(4, r.neg());
                ps.setInt(5, r.neu());
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            return n;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void upsertOverallSentiment(String runId, Instant bucketStart, int pos, int neg, int neu) {
        final String sql = "INSERT OR REPLACE INTO overall_sentiment(run_id,bucket_start,pos,neg,neu) VALUES(?,?,?,?,?)";
        try (var con = db.connect(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, bucketStart.toString());
            ps.setInt(3, pos);
            ps.setInt(4, neg);
            ps.setInt(5, neu);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void saveOverallSentiment(String runId, Instant bucketStart, int pos, int neg, int neu) {
        upsertOverallSentiment(runId, bucketStart, pos, neg, neu);
    }

    public AnalyticsBundle readOverall(String runId) {
        final String sql = "SELECT bucket_start,pos,neg,neu FROM overall_sentiment WHERE run_id=? ORDER BY bucket_start";
        List<OverallSentimentRow> list = new ArrayList<>();
        try (var con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var bucket = Instant.parse(rs.getString(1));
                    list.add(new OverallSentimentRow(bucket, rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return new AnalyticsBundle(list);
    }

    public List<TagCount> readDamageCounts(String runId) {
        final String sql = "SELECT type, COUNT(*) AS c FROM damage WHERE run_id = ? GROUP BY type ORDER BY c DESC";
        List<TagCount> out = new ArrayList<>();
        try (var con = db.connect(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString(1), rs.getInt(2)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public List<TagCount> readReliefCounts(String runId) {
        final String sql = "SELECT item, COUNT(*) AS c FROM relief_items WHERE run_id = ? GROUP BY item ORDER BY c DESC";
        List<TagCount> out = new ArrayList<>();
        try (var con = db.connect(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString(1), rs.getInt(2)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public List<TagCount> readTopKeywords(String runId, int topN) {
        final String sql = "SELECT token, SUM(cnt) AS c FROM keyword_counts WHERE run_id = ? GROUP BY token ORDER BY c DESC LIMIT ?";
        List<TagCount> out = new ArrayList<>();
        try (var con = db.connect(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setInt(2, Math.max(1, topN));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString(1), rs.getInt(2)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public List<TagCount> readTopHashtags(String runId, int topN) {
        final String sql = "SELECT tag, SUM(cnt) AS c FROM hashtag_counts WHERE run_id = ? GROUP BY tag ORDER BY c DESC LIMIT ?";
        List<TagCount> out = new ArrayList<>();
        try (var con = db.connect(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setInt(2, Math.max(1, topN));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TagCount(rs.getString(1), rs.getInt(2)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static record TagCount(String tag, int cnt) {}

    /* ===========================================
       1) API “LLM-ready” – Overall (fallback OK)
       =========================================== */

    public record OverallRow(LocalDate day, int pos, int neg, int neu) {}

    public List<OverallRow> readOverallRows(String runId) {
        List<OverallRow> out = new ArrayList<>();
        // Ưu tiên overall_sentiment
        final String q1 =
            "SELECT substr(bucket_start,1,10) AS d, SUM(pos), SUM(neg), SUM(neu) " +
            "FROM overall_sentiment WHERE run_id = ? GROUP BY d ORDER BY d";
        try (var con = db.connect(); var ps = con.prepareStatement(q1)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OverallRow(LocalDate.parse(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        } catch (Exception ignore) {}

        if (!out.isEmpty()) return out;

        // Fallback: tính trực tiếp từ sentiments (dựa trên ts)
        final String q2 =
            "SELECT substr(ts,1,10) AS d, " +
            "SUM(CASE WHEN label='pos' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN label='neg' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN label='neu' THEN 1 ELSE 0 END) " +
            "FROM sentiments WHERE run_id = ? GROUP BY d ORDER BY d";
        try (var con = db.connect(); var ps = con.prepareStatement(q2)) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OverallRow(LocalDate.parse(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        } catch (Exception ignore) {}

        return out;
    }

    /* =========================================================
       2) API “LLM-ready” – Task 3 (sentiment theo nhóm cứu trợ)
       ========================================================= */

    public record ReliefCategorySentiment(String category, int pos, int neg, int net) {}

    public List<ReliefCategorySentiment> readReliefCategorySentiment(String runId) {
        // Chiến lược 0: relief_sentiment table (pre-aggregated by category)
        final String qSentiment =
            "SELECT item AS category, SUM(pos) AS pos, SUM(neg) AS neg, SUM(pos - neg) AS net FROM relief_sentiment " +
            "WHERE run_id = ? GROUP BY item ORDER BY item";
        var fromSentiment = tryQueryReliefCategory(runId, qSentiment);
        if (!fromSentiment.isEmpty()) return fromSentiment;

        // Chiến lược 1: nếu đã có relief_daily thì gộp theo category
        final String qDaily =
            "SELECT category, SUM(pos), SUM(neg), SUM(net) FROM relief_daily " +
            "WHERE run_id = ? GROUP BY category ORDER BY category";
        var byDaily = tryQueryReliefCategory(runId, qDaily);
        if (!byDaily.isEmpty()) return byDaily;

        // Chiến lược 2: join relief_items -> posts -> sentiments (khóa post_id -> posts.id -> sentiments.raw_id)
        final String qJoinViaPosts =
            "SELECT ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN posts p ON p.run_id = ri.run_id AND p.id = ri.post_id " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = p.raw_id " +
            "WHERE ri.run_id = ? " +
            "GROUP BY ri.item ORDER BY ri.item";
        var viaPosts = tryQueryReliefCategory(runId, qJoinViaPosts);
        if (!viaPosts.isEmpty()) return viaPosts;

        // Chiến lược 3: join trực tiếp item.post_id ~ sentiments.raw_id (nếu schema bạn lưu như vậy)
        final String qJoinDirect =
            "SELECT ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = ri.post_id " +
            "WHERE ri.run_id = ? " +
            "GROUP BY ri.item ORDER BY ri.item";
        var direct = tryQueryReliefCategory(runId, qJoinDirect);
        if (!direct.isEmpty()) return direct;

        // Chiến lược 4: nếu không join được thì suy giảm – đếm theo relief_items (không có sentiment)
        // để tránh “Không có dữ liệu…”, vẫn trả về skeleton cho LLM (pos=neg=net=0).
        var counts = readReliefCounts(runId);
        if (!counts.isEmpty()) {
            List<ReliefCategorySentiment> out = new ArrayList<>();
            for (var tc : counts) out.add(new ReliefCategorySentiment(tc.tag(), 0, 0, 0));
            return out;
        }

        return List.of();
    }

    private List<ReliefCategorySentiment> tryQueryReliefCategory(String runId, String sql) {
        List<ReliefCategorySentiment> out = new ArrayList<>();
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ReliefCategorySentiment(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    /* =====================================================================
       3) API “LLM-ready” – Task 4 (theo thời gian cho từng nhóm cứu trợ)
       ===================================================================== */

    public record ReliefDailyRow(LocalDate day, String category, int pos, int neg, int net) {}

    public List<ReliefDailyRow> readReliefDaily(String runId) {
        // First try: relief_sentiment_daily table (pre-aggregated)
        final String qSentimentDaily =
            "SELECT bucket_start AS date, item AS category, pos, neg, (pos - neg) AS net FROM relief_sentiment_daily " +
            "WHERE run_id = ? ORDER BY bucket_start, item";
        var fromSentimentDaily = tryQueryReliefDaily(runId, qSentimentDaily, null, null);
        if (!fromSentimentDaily.isEmpty()) return fromSentimentDaily;

        final String qDaily =
            "SELECT date, category, pos, neg, net FROM relief_daily " +
            "WHERE run_id = ? ORDER BY date, category";
        var byDaily = tryQueryReliefDaily(runId, qDaily, null, null);
        if (!byDaily.isEmpty()) return byDaily;

        // Fallback: tính từ join sentiments × posts × relief_items theo ngày
        final String qJoinViaPosts =
            "SELECT substr(p.created_at,1,10) AS d, ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN posts p ON p.run_id = ri.run_id AND p.id = ri.post_id " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = p.raw_id " +
            "WHERE ri.run_id = ? " +
            "GROUP BY d, ri.item ORDER BY d, ri.item";
        var viaPosts = tryQueryReliefDaily(runId, qJoinViaPosts, null, null);
        if (!viaPosts.isEmpty()) return viaPosts;

        final String qJoinDirect =
            "SELECT substr(s.ts,1,10) AS d, ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = ri.post_id " +
            "WHERE ri.run_id = ? " +
            "GROUP BY d, ri.item ORDER BY d, ri.item";
        var direct = tryQueryReliefDaily(runId, qJoinDirect, null, null);
        return direct;
    }

    public List<ReliefDailyRow> readReliefDaily(String runId, LocalDate from, LocalDate to) {
        // First try: relief_sentiment_daily table (pre-aggregated) with date filtering
        final String qSentimentDaily =
            "SELECT bucket_start AS date, item AS category, pos, neg, (pos - neg) AS net FROM relief_sentiment_daily " +
            "WHERE run_id = ? AND bucket_start BETWEEN ? AND ? ORDER BY bucket_start, item";
        var fromSentimentDaily = tryQueryReliefDaily(runId, qSentimentDaily, from, to);
        if (!fromSentimentDaily.isEmpty()) return fromSentimentDaily;

        final String qDaily =
            "SELECT date, category, pos, neg, net FROM relief_daily " +
            "WHERE run_id = ? AND date BETWEEN ? AND ? ORDER BY date, category";
        var byDaily = tryQueryReliefDaily(runId, qDaily, from, to);
        if (!byDaily.isEmpty()) return byDaily;

        final String qJoinViaPosts =
            "SELECT substr(p.created_at,1,10) AS d, ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN posts p ON p.run_id = ri.run_id AND p.id = ri.post_id " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = p.raw_id " +
            "WHERE ri.run_id = ? AND substr(p.created_at,1,10) BETWEEN ? AND ? " +
            "GROUP BY d, ri.item ORDER BY d, ri.item";
        var viaPosts = tryQueryReliefDaily(runId, qJoinViaPosts, from, to);
        if (!viaPosts.isEmpty()) return viaPosts;

        final String qJoinDirect =
            "SELECT substr(s.ts,1,10) AS d, ri.item AS category, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 ELSE 0 END) AS pos, " +
            "SUM(CASE WHEN s.label='neg' THEN 1 ELSE 0 END) AS neg, " +
            "SUM(CASE WHEN s.label='pos' THEN 1 WHEN s.label='neg' THEN -1 ELSE 0 END) AS net " +
            "FROM relief_items ri " +
            "JOIN sentiments s ON s.run_id = ri.run_id AND s.raw_id = ri.post_id " +
            "WHERE ri.run_id = ? AND substr(s.ts,1,10) BETWEEN ? AND ? " +
            "GROUP BY d, ri.item ORDER BY d, ri.item";
        return tryQueryReliefDaily(runId, qJoinDirect, from, to);
    }

    private List<ReliefDailyRow> tryQueryReliefDaily(String runId, String sql, LocalDate from, LocalDate to) {
        List<ReliefDailyRow> out = new ArrayList<>();
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            if (from != null && to != null) {
                ps.setString(2, from.toString());
                ps.setString(3, to.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dayStr = rs.getString(1);
                    out.add(new ReliefDailyRow(LocalDate.parse(dayStr), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    /* =========================
       4) Tiện ích nội bộ
       ========================= */

    public static Instant truncateToDayUTC(Instant ts){
        return ZonedDateTime.ofInstant(ts, ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /* =====
       Bonus: tổng hợp nhanh Task3 từ Task4 (nếu cần)
       ===== */
    public List<ReliefCategorySentiment> aggregateTask3FromTask4(List<ReliefDailyRow> rows) {
        Map<String, int[]> acc = new LinkedHashMap<>();
        for (var r : rows) {
            var a = acc.computeIfAbsent(r.category(), k -> new int[3]);
            a[0] += r.pos();
            a[1] += r.neg();
            a[2] += r.net();
        }
        List<ReliefCategorySentiment> out = new ArrayList<>();
        for (var e : acc.entrySet()) {
            int[] v = e.getValue();
            out.add(new ReliefCategorySentiment(e.getKey(), v[0], v[1], v[2]));
        }
        return out;
    }
}