package app.model.service.collect;

import collector.core.JsonlWriter;
import collector.core.QuerySpec;
import collector.core.RawPost;
import collector.core.SourceConnector;
import collector.news.NewsRssConnector;
import collector.reddit.RedditJsonConnector;
import collector.youtube.YouTubeApiConnector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class EmbeddedCollectorRunner {

    public record Result(Path outFile, int total, Map<String,Integer> byPlatform) {}

    private final Path collectionsRoot;

    public EmbeddedCollectorRunner(Path appRoot) {
        this.collectionsRoot = appRoot.resolve("data/collections");
    }

    public Result collect(String collection,
                          List<String> keywords,
                          List<String> sourceIds,
                          Instant from, Instant to,
                          int limit,
                          String ytApiKey) throws Exception {

        Objects.requireNonNull(collection);
        Objects.requireNonNull(keywords);
        if (keywords.isEmpty()) throw new IllegalArgumentException("keywords rỗng");
        if (limit <= 0) limit = 100;

        // Đặt API key cho YouTube (YouTubeApiClient đọc System property này)
        if (ytApiKey != null && !ytApiKey.isBlank()) {
            System.setProperty("yt.apiKey", ytApiKey);
        }

        // Chuẩn bị connector theo lựa chọn người dùng
        List<SourceConnector> connectors = new ArrayList<>();
        for (String s : sourceIds) {
            switch (s.trim().toLowerCase()) {
                case "news"    -> connectors.add(new NewsRssConnector());
                case "youtube" -> connectors.add(new YouTubeApiConnector());
                case "reddit"  -> connectors.add(new RedditJsonConnector());
                default -> { /* bỏ qua nguồn lạ */ }
            }
        }
        if (connectors.isEmpty()) throw new IllegalArgumentException("Chưa chọn nguồn dữ liệu");

        // Spec chung
        QuerySpec spec = new QuerySpec(keywords, from, to, limit);

        // Thu thập & gộp (khử trùng theo id)
        Map<String, RawPost> dedup = new LinkedHashMap<>();
        for (SourceConnector c : connectors) {
            try (Stream<RawPost> st = c.fetch(spec)) {
                for (RawPost p : (Iterable<RawPost>) st.limit(limit)::iterator) {
                    dedup.putIfAbsent(p.id(), p);
                    if (dedup.size() >= limit) break;
                }
            } catch (Exception e) {
                // Không fail toàn bộ nếu 1 nguồn lỗi — chỉ log ra
                System.err.println("[Collector] WARN: nguồn " + c.name() + " lỗi: " + e.getMessage());
            }
            if (dedup.size() >= limit) break;
        }

        List<RawPost> finalList = new ArrayList<>(dedup.values());

        // Ghi JSONL vào đúng thư mục app/data/collections/<collection>/
        Path outDir = collectionsRoot.resolve(collection);
        Files.createDirectories(outDir);
        String fname = "raw-" + System.currentTimeMillis() + ".jsonl";
        Path out = outDir.resolve(fname);
        JsonlWriter.write(out, finalList);

        // Thống kê theo platform
        Map<String,Integer> byPlatform = new TreeMap<>();
        for (RawPost p : finalList) {
            byPlatform.merge(p.platform(), 1, Integer::sum);
        }
        return new Result(out, finalList.size(), byPlatform);
    }
}