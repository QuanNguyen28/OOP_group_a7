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
        
        // QUAN TRỌNG: Lấy connection ra ngoài try(...)
        var con = db.connect(); 
        
        try {
            con.setAutoCommit(false); // Bắt đầu transaction

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
            
            con.commit();            // Commit
            con.setAutoCommit(true); // Reset về auto-commit
            return rows.size();
        } catch (Exception e) {
            try { con.rollback(); } catch (Exception ex) {} // Rollback nếu lỗi
            throw new RuntimeException(e);
        }
    }

    // pipeline sẽ set runId trước khi gọi saveBatch
    private String currentRunId = "unknown";
    public void attachRun(String runId){ this.currentRunId = runId; }
}