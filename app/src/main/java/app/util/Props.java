package app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class Props {
    private static volatile Properties CACHE;

    private Props() {}

    private static synchronized void ensureLoaded() {
        if (CACHE != null) return;
        CACHE = new Properties();

        // ƯU TIÊN 1: collector/conf.properties (trong app)
        Path p1 = Paths.get("collector/conf.properties");
        // ƯU TIÊN 2: ../collector/conf.properties (khi chạy từ app/)
        Path p2 = Paths.get("../collector/conf.properties");

        loadFile(p1);
        loadFile(p2);

        // Không ghi đè System properties và Env ở đây — ưu tiên file cao nhất như yêu cầu
        // Nhưng nếu bạn muốn fallback khi file không có key, sẽ đọc ở get()
    }

    private static void loadFile(Path p) {
        try {
            if (Files.exists(p)) {
                try (BufferedReader br = Files.newBufferedReader(p)) {
                    Properties tmp = new Properties();
                    tmp.load(br);
                    // giữ nguyên ưu tiên: nếu key chưa có mới put
                    for (String k : tmp.stringPropertyNames()) {
                        CACHE.putIfAbsent(k, tmp.getProperty(k));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public static String get(String key, String fallback) {
        ensureLoaded();
        String v = CACHE.getProperty(key);
        if (v != null && !v.isBlank()) return v;

        // fallback 2: System properties
        v = System.getProperty(key);
        if (v != null && !v.isBlank()) return v;

        // fallback 3: ENV (YT_API_KEY cho yt.apiKey)
        if ("yt.apiKey".equals(key)) {
            v = System.getenv("YT_API_KEY");
            if (v != null && !v.isBlank()) return v;
        }
        return fallback;
    }
}