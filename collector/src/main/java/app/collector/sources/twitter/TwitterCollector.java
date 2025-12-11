package app.collector.sources.twitter;

import app.collector.config.CollectorConfig;
import app.collector.core.PlatformCollector;
import app.collector.model.RawRecord;
import app.collector.sink.RecordSink;
import app.collector.util.HttpUtil;
import app.collector.util.JsonUtil;

import java.time.Instant;
import java.util.*;

public final class TwitterCollector implements PlatformCollector {
    @Override public String id() { return "twitter"; }

    @Override public void collect(CollectorConfig cfg, RecordSink sink) throws Exception {
        if (cfg.twitterBearer==null || cfg.twitterBearer.startsWith("PUT_")) {
            System.err.println("[Twitter] No bearer token. Skipped.");
            return;
        }
        for (String q : cfg.queries) {
            String url = "https://api.twitter.com/2/tweets/search/recent"
                    + "?max_results=" + Math.min(cfg.twitterMaxPerQuery, 100)
                    + "&query=" + HttpUtil.enc(q)
                    + "&start_time=" + cfg.after.toString()
                    + "&end_time=" + cfg.before.toString()
                    + "&tweet.fields=created_at,lang,geo";
            String json = HttpUtil.get(url, Map.of("Authorization","Bearer " + cfg.twitterBearer));

            // Rất tối giản: tách từng tweet block dựa vào "id"
            String[] blocks = json.split("\\{\\s*\"id\"\\s*:\\s*\"");
            for (String b : blocks) {
                String id = JsonUtil.firstString(b, "^([^\"]+)");
                if (id.isBlank() || !b.contains("\"text\"")) continue;
                String text = JsonUtil.firstString(b, "\"text\"\\s*:\\s*\"([^\"]*)\"");
                String ts   = JsonUtil.firstString(b, "\"created_at\"\\s*:\\s*\"([^\"]+)\"");
                if (ts.isBlank()) ts = Instant.now().toString();
                List<String> tags = JsonUtil.extractHashtags(text);
                sink.accept(new RawRecord("tw_"+id, text, cfg.lang, ts, "twitter", "", tags));
            }
        }
    }
}