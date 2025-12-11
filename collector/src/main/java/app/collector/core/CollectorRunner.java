package app.collector.core;

import app.collector.config.CollectorConfig;
import app.collector.sink.*;
import app.collector.util.TimeUtil;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class CollectorRunner {
    public static Path runAll(CollectorConfig cfg, List<PlatformCollector> collectors) throws Exception {
        Path colDir = cfg.outRoot.resolve(cfg.collection);
        Files.createDirectories(colDir);
        String fileBase = TimeUtil.yearMonth(cfg.after) + "-collector.jsonl";
        Path jsonl = colDir.resolve(fileBase);
        Path yaml  = colDir.resolve("dataset.yaml");

        int total = 0;
        try (JsonlSink sink = new JsonlSink(jsonl)) {
            for (PlatformCollector c : collectors) {
                try {
                    c.collect(cfg, sink);
                } catch (Exception ex) {
                    System.err.println("[WARN] collector '" + c.id() + "' skipped: " + ex.getMessage());
                }
            }
        }
        DatasetYaml.write(yaml, fileBase, cfg.keywords);
        System.out.println("[Collector] Wrote: " + jsonl + " & " + yaml);
        return jsonl;
    }

    public static List<PlatformCollector> buildCollectors(CollectorConfig cfg){
        List<PlatformCollector> list = new ArrayList<>();
        if (cfg.enableYouTube)  list.add(new app.collector.sources.youtube.YouTubeCollector());
        if (cfg.enableTwitter)  list.add(new app.collector.sources.twitter.TwitterCollector());
        if (cfg.enableFacebook) list.add(new app.collector.sources.facebook.FacebookCollector());
        if (cfg.enableNews)     list.add(new app.collector.sources.news.RssCollector());
        return list;
    }
}