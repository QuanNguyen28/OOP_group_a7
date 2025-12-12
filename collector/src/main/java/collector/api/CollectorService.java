package collector.api;

import collector.core.JsonlWriter;
import collector.core.RawPost;
import collector.core.Util;
import collector.news.NewsRssConnector;
import collector.youtube.YouTubeApiConnector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CollectorService {
    private final List<SourceConnector> connectors = new ArrayList<>();

    public CollectorService() {}

    public CollectorService add(SourceConnector c) {
        this.connectors.add(c);
        return this;
    }

    public static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s.trim()); } catch (Exception e) { return null; }
    }

    public void run(List<String> keywords, Instant from, Instant to, int limit, String collection, List<String> sourceNames) throws Exception {
        if (sourceNames == null || sourceNames.isEmpty()) {
            // default: news + youtube
            add(new NewsRssConnector());
            add(new YouTubeApiConnector());
        } else {
            for (String name : sourceNames) {
                switch (name.trim()) {
                    case "news"    -> add(new NewsRssConnector());
                    case "youtube" -> add(new YouTubeApiConnector());
                    // case "facebook" -> add(new FacebookHtmlConnector()); // stub nếu cần
                    default -> System.err.println("[Collector] WARN: Unknown source: " + name);
                }
            }
        }

        QuerySpec spec = new QuerySpec(keywords, from, to, limit);

        List<RawPost> all = new ArrayList<>();
        for (SourceConnector c : connectors) {
            try (Stream<RawPost> st = c.fetch(spec)) {
                st.limit(Math.max(1, limit)).forEach(all::add);
            } catch (Exception e) {
                System.err.println("[Collector] Source failed: " + c.id() + " -> " + e.getMessage());
            }
        }

        // Deduplicate (by id)
        Map<String, RawPost> uniq = new LinkedHashMap<>();
        for (RawPost p : all) uniq.put(p.id(), p);
        List<RawPost> finalList = new ArrayList<>(uniq.values());

        long now = System.currentTimeMillis();
        Path out = Path.of("collector/data/collections", collection, "raw-" + now + ".jsonl");
        JsonlWriter.write(out, finalList);

        // Summary by platform
        var byPlat = finalList.stream().collect(Collectors.groupingBy(RawPost::platform, LinkedHashMap::new, Collectors.counting()));
        System.out.println("[Collector] saved " + finalList.size() + " rows -> " + out);
        System.out.println("[Collector] by platform: " + byPlat);
    }

    public void run(List<String> keywords, Instant from, Instant to, int limit, String collection) throws Exception {
        run(keywords, from, to, limit, collection, List.of());
    }
}