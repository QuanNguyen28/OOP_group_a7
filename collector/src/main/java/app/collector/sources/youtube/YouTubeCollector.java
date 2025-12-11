package app.collector.sources.youtube;

import app.collector.config.CollectorConfig;
import app.collector.core.PlatformCollector;
import app.collector.model.RawRecord;
import app.collector.sink.RecordSink;
import app.collector.util.HttpUtil;
import app.collector.util.JsonUtil;
import app.collector.util.TimeUtil;

import java.time.Instant;
import java.util.*;

public final class YouTubeCollector implements PlatformCollector {
    @Override public String id() { return "youtube"; }

    @Override public void collect(CollectorConfig cfg, RecordSink sink) throws Exception {
        if (cfg.youtubeApiKey==null || cfg.youtubeApiKey.startsWith("PUT_")) {
            System.err.println("[YouTube] No API key. Skipped.");
            return;
        }
        for (String q : cfg.queries) {
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video"
                    + "&maxResults=" + Math.min(cfg.ytMaxVideosPerQuery,50)
                    + "&q=" + HttpUtil.enc(q)
                    + "&publishedAfter=" + cfg.after.toString()
                    + "&publishedBefore=" + cfg.before.toString()
                    + "&key=" + cfg.youtubeApiKey;
            String json = HttpUtil.get(url);

            // Lấy videoIds
            List<String> ids = JsonUtil.allVideoIds(json);
            if (ids.isEmpty()) continue;

            // Query details (snippet) để lấy title/desc/publishedAt
            String idParam = String.join(",", ids);
            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id="+idParam+"&key="+cfg.youtubeApiKey;
            String det = HttpUtil.get(detailUrl);

            // tách block item
            String[] items = det.split("\\{\\s*\"kind\"\\s*:\\s*\"youtube#video\"");
            for (String it : items) {
                String vid = JsonUtil.firstString(it, "\"id\"\\s*:\\s*\"([^\"]+)\"");
                if (vid.isBlank()) continue;
                String text = JsonUtil.titlePlusDesc(it);
                String ts   = JsonUtil.firstString(it, "\"publishedAt\"\\s*:\\s*\"([^\"]+)\"");
                if (ts.isBlank()) ts = TimeUtil.toIsoInstant(Instant.now());
                List<String> tags = JsonUtil.extractHashtags(text);

                sink.accept(new RawRecord(
                        "yt_"+vid,
                        text,
                        cfg.lang,
                        ts,
                        "youtube",
                        "",
                        tags
                ));

                if (cfg.ytIncludeComments) {
                    // commentThreads (top-level)
                    try {
                        String cUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="+vid+"&maxResults=50&key="+cfg.youtubeApiKey;
                        String cj = HttpUtil.get(cUrl);
                        String[] citems = cj.split("\"commentThreads#commentThread\"");
                        for (String cb : citems) {
                            String ctext = JsonUtil.firstString(cb, "\"textDisplay\"\\s*:\\s*\"([^\"]*)\"");
                            if (ctext.isBlank()) continue;
                            String cts = JsonUtil.firstString(cb, "\"publishedAt\"\\s*:\\s*\"([^\"]+)\"");
                            if (cts.isBlank()) cts = ts;
                            List<String> ctags = JsonUtil.extractHashtags(ctext);
                            sink.accept(new RawRecord(
                                    "ytc_"+vid+"_"+Math.abs(cb.hashCode()),
                                    ctext.replaceAll("<[^>]+>",""),
                                    cfg.lang,
                                    cts,
                                    "youtube",
                                    "",
                                    ctags
                            ));
                        }
                    } catch (Exception ex) {
                        System.err.println("[YouTube] comments failed: "+ex.getMessage());
                    }
                }
            }
        }
    }
}