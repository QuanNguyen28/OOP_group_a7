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
}