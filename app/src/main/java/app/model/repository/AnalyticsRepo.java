package app.model.repository;

import app.model.repository.dto.AnalyticsBundle;
import app.model.repository.dto.OverallSentimentRow;

import java.sql.PreparedStatement;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsRepo {
    private final SQLite db;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    public AnalyticsRepo(SQLite db){ this.db = db; }

    public int saveOverallSentiment(String runId, List<OverallSentimentRow> rows) {
        String sql = "INSERT OR REPLACE INTO overall_sentiment(run_id,bucket_start,pos,neg,neu) VALUES(?,?,?,?,?)";
        try (var con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            int n = 0;
            for (var r : rows) {
                ps.setString(1, runId);
                ps.setString(2, ISO.format(r.bucketStart()));
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
            ps.setString(2, ISO.format(bucketStart));
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
        String sql = "SELECT bucket_start,pos,neg,neu FROM overall_sentiment WHERE run_id=? ORDER BY bucket_start";
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

    public static Instant truncateToDayUTC(Instant ts){
        return ZonedDateTime.ofInstant(ts, ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
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
}