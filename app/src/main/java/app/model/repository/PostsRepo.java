package app.model.repository;

import app.model.domain.CleanPost;

import java.sql.PreparedStatement;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PostsRepo {
    private final SQLite db;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    public PostsRepo(SQLite db){ this.db = db; }

    public int saveBatch(List<CleanPost> rows){
        String sql = "INSERT OR REPLACE INTO posts(id,platform,text,lang,ts,geo,run_id) VALUES(?,?,?,?,?,?,?)";
        
        var con = db.connect(); 
        
        try {
            con.setAutoCommit(false); 

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int n = 0;
                for (var p: rows) {
                    ps.setString(1, p.rawId());
                    ps.setString(2, "file");
                    ps.setString(3, p.textNorm());
                    ps.setString(4, p.lang());
                    ps.setString(5, java.time.format.DateTimeFormatter.ISO_INSTANT.format(p.ts()));
                    ps.setString(6, p.geo().orElse(null));
                    ps.setString(7, currentRunId);
                    ps.addBatch();
                    n++;
                }
                ps.executeBatch();
            }
            
            con.commit();      
            con.setAutoCommit(true); 
            return rows.size();
        } catch (Exception e) {
            try { con.rollback(); } catch (Exception ex) {} 
            throw new RuntimeException(e);
        }
    }

    // pipeline sẽ set runId trước khi gọi saveBatch
    private String currentRunId = "unknown";
    public void attachRun(String runId){ this.currentRunId = runId; }

    public static record PostBrief(String id, String platform, java.time.Instant createdAt, String text) {}

    public java.util.List<PostBrief> findByDamage(String runId, String type, java.time.LocalDate dayOpt, int limit) {
        var out = new java.util.ArrayList<PostBrief>();
        String sql =
            "SELECT p.id, p.platform, p.createdAt, p.text " +
            "FROM damage d JOIN posts p ON p.id = d.post_id " +
            "WHERE d.run_id = ? AND d.type = ? " +
            (dayOpt != null ? "AND date(p.createdAt) = ? " : "") +
            "ORDER BY p.createdAt DESC LIMIT ?";
        try (var conn = db.connect(); var ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, runId);
            ps.setString(i++, type);
            if (dayOpt != null) ps.setString(i++, dayOpt.toString());
            ps.setInt(i++, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PostBrief(
                        rs.getString(1),
                        rs.getString(2),
                        java.time.Instant.parse(rs.getString(3)),
                        rs.getString(4)
                    ));
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }
}