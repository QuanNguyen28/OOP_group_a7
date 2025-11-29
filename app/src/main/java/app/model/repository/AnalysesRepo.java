package app.model.repository;
import app.model.domain.SentimentResult;
import java.sql.PreparedStatement;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AnalysesRepo {
    private final SQLite db;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    public AnalysesRepo(SQLite db){ this.db = db; }

    public int saveSentiment(String runId, List<SentimentResult> rows){
        String sql = "INSERT OR REPLACE INTO sentiments(id,label,score,ts,run_id) VALUES(?,?,?,?,?)";
        try (var con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            int n = 0;
            for (var r: rows) {
                ps.setString(1, r.id());
                ps.setString(2, r.label().name());
                ps.setDouble(3, r.score());
                ps.setString(4, ISO.format(r.ts()));
                ps.setString(5, runId);
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            return n;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}