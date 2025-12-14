package app.model.repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Map;

public class RunsRepo {
    private final SQLite db;
    private final ObjectMapper om = new ObjectMapper();
    public RunsRepo(SQLite db){ this.db = db; }

    public void saveRun(String runId, Instant startedAt, Map<String,Object> params){
        String sql = "INSERT OR REPLACE INTO runs(run_id, started_at, params_json) VALUES(?,?,?)";
        try (var con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, startedAt.toString());
            ps.setString(3, om.writeValueAsString(params));
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void createRun(String runId) {
        String sql = "INSERT INTO runs(id, ts) VALUES(?, ?)";
        
        var con = db.connect();
        
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, java.time.Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static record RunSummary(String id, java.time.Instant started) {}

    public java.util.List<RunSummary> listRunsOrderByStartedDesc(int limit) {
        return java.util.Collections.emptyList();
    }

}