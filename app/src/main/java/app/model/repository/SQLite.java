package app.model.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLite {
    private Connection connection;
    private final String dbPath; // Lưu lại đường dẫn để dùng khi cần kết nối lại

    public SQLite(String dbPath) {
        this.dbPath = dbPath;
    }

    // FIX: Tự động kết nối lại nếu connection bị null hoặc đã bị đóng
    public Connection connect() {
        try {
            if (connection == null || connection.isClosed()) {
                String url = "jdbc:sqlite:" + dbPath;
                connection = DriverManager.getConnection(url);
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void migrate() {
        try {
            connect(); // Đảm bảo kết nối đang mở
            try (var inputStream = getClass().getResourceAsStream("/ui/sql/migrate.sql")) {
                if (inputStream == null) throw new RuntimeException("Migration file not found at /ui/sql/migrate.sql");
                var sql = new String(inputStream.readAllBytes());
                
                try (var stmt = connection.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}