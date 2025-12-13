package collector.api;

import collector.core.RawPost;
import collector.core.QuerySpec;
import collector.core.SourceConnector;
import collector.youtube.YouTubeApiConnector;
import collector.news.NewsRssConnector;
import collector.reddit.RedditJsonConnector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectorService {
    private final List<SourceConnector> connectors = new ArrayList<>();

    public CollectorService add(SourceConnector c) {
        connectors.add(c);
        return this;
    }

    /** Bật nguồn theo tên, ưu tiên đọc yt.apiKey từ collector/conf.properties → System property → ENV. */
    public CollectorService add(String name) {
        String n = name == null ? "" : name.trim().toLowerCase();
        switch (n) {
            case "news" -> add(new NewsRssConnector());
            case "reddit" -> add(new RedditJsonConnector());
            case "youtube" -> {
                String key = resolveYtApiKey(null);
                add(new YouTubeApiConnector(key));
            }
            default -> System.err.println("[Collector] WARN: Unknown source: " + name);
        }
        return this;
    }

    /** Giữ tương thích: gọi add(name). */
    public CollectorService addByName(String name) {
        return add(name);
    }

    /** Giữ tương thích cũ: nếu UI đưa ytApiKey thì dùng, nếu không sẽ tự resolve. */
    public CollectorService addByName(String name, String ytApiKeyFromUI) {
        String n = name == null ? "" : name.trim().toLowerCase();
        switch (n) {
            case "news" -> add(new NewsRssConnector());
            case "reddit" -> add(new RedditJsonConnector());
            case "youtube" -> {
                String key = resolveYtApiKey(ytApiKeyFromUI);
                add(new YouTubeApiConnector(key));
            }
            default -> System.err.println("[Collector] WARN: Unknown source: " + name);
        }
        return this;
    }

    /** Chạy thu thập và trả về đường dẫn file JSONL đã lưu. */
    public Path run(List<String> keywords, Instant from, Instant to, int limit,
                    String collection, Path saveDir) throws Exception {
        return run(keywords, from, to, limit, collection, saveDir, null);
    }

    /** Chạy thu thập (giữ chữ ký tương thích với UI/CLI cũ), trả về file đã lưu. */
    public Path run(List<String> keywords, Instant from, Instant to, int limit,
                    String collection, Path saveDir, String ytApiKeyFromUI) throws Exception {
        Objects.requireNonNull(keywords, "keywords");
        if (limit <= 0) limit = 300;
        if (collection == null || collection.isBlank()) collection = "default";
        // Mặc định lưu vào thư mục data/collections/live trong dự án
        if (saveDir == null) saveDir = Path.of("../data/collections/live");

        // Nếu chưa có connector nào thì bật mặc định: news + youtube
        if (connectors.isEmpty()) {
            add("news");
            addByName("youtube", ytApiKeyFromUI);
        }

        QuerySpec spec = new QuerySpec(keywords, from, to, limit);

        List<RawPost> all = new ArrayList<>();
        for (SourceConnector c : connectors) {
            try (Stream<RawPost> st = c.fetch(spec)) {
                st.forEach(all::add);
            }
        }

        // Cắt theo limit tổng
        List<RawPost> finalList = all.stream().limit(limit).toList();

        // Lưu
        if (!Files.exists(saveDir)) Files.createDirectories(saveDir);
        if (collection != null && !collection.isBlank()) {
            saveDir = saveDir.resolve(collection);
            Files.createDirectories(saveDir);
        }
        Path out = saveDir.resolve("raw-" + System.currentTimeMillis() + ".jsonl");
        collector.core.JsonlWriter.write(out, finalList);

        System.out.println("[Collector] saved " + finalList.size() + " rows -> " + out);

        Map<String, Long> byPlatform = finalList.stream()
                .collect(Collectors.groupingBy(RawPost::platform, Collectors.counting()));
        System.out.println("[Collector] by platform: " + byPlatform);

        return out;
    }

    // ================= helpers =================

    /** Ưu tiên: file collector/conf.properties → System property (-Dyt.apiKey) → ENV (YT_API_KEY). */
    private String resolveYtApiKey(String override) {
        if (override != null && !override.isBlank()) return override;

        // 1) collector/conf.properties (ưu tiên)
        String fromFile = loadPropertyFromFiles("yt.apiKey",
                Path.of("../collector/conf.properties"),
                Path.of("conf.properties"),
                Path.of("../app/collector/conf.properties"));
        if (fromFile != null && !fromFile.isBlank()) return fromFile;

        // 2) -Dyt.apiKey
        String fromSys = System.getProperty("yt.apiKey");
        if (fromSys != null && !fromSys.isBlank()) return fromSys;

        // 3) ENV
        String fromEnv = System.getenv("YT_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        throw new IllegalStateException("yt.apiKey is null. Set in collector/conf.properties or -Dyt.apiKey or env YT_API_KEY.");
        }

    private String loadPropertyFromFiles(String key, Path... files) {
        for (Path p : files) {
            try {
                if (Files.exists(p)) {
                    Properties props = new Properties();
                    try (InputStream in = Files.newInputStream(p)) {
                        props.load(in);
                    }
                    String v = props.getProperty(key);
                    if (v != null && !v.isBlank()) {
                        return v.trim();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }
}