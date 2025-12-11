package app.collector.sources.facebook;

import app.collector.config.CollectorConfig;
import app.collector.core.PlatformCollector;
import app.collector.model.RawRecord;
import app.collector.sink.RecordSink;
import app.collector.util.HttpUtil;
import app.collector.util.JsonUtil;

import java.time.Instant;
import java.util.*;

public final class FacebookCollector implements PlatformCollector {
    @Override public String id() { return "facebook"; }

    @Override public void collect(CollectorConfig cfg, RecordSink sink) throws Exception {
        if (cfg.fbAccessToken==null || cfg.fbAccessToken.startsWith("PUT_") || cfg.facebookPageIds.isEmpty()) {
            System.err.println("[Facebook] No access token or no pages. Skipped.");
            return;
        }
        for (String pageId : cfg.facebookPageIds) {
            String url = "https://graph.facebook.com/v19.0/" + pageId
                    + "/posts?fields=message,created_time,id&limit=50&access_token=" + HttpUtil.enc(cfg.fbAccessToken);
            String json = HttpUtil.get(url);
            // Tách post block đơn giản
            String[] posts = json.split("\\{\\s*\"id\"\\s*:\\s*\"");
            for (String p : posts) {
                String id = JsonUtil.firstString(p, "^([^\"]+)");
                if (id.isBlank()) continue;
                String text = JsonUtil.firstString(p, "\"message\"\\s*:\\s*\"([^\"]*)\"");
                if (text.isBlank()) continue;
                String ts = JsonUtil.firstString(p, "\"created_time\"\\s*:\\s*\"([^\"]+)\"");
                if (ts.isBlank()) ts = Instant.now().toString();
                List<String> tags = JsonUtil.extractHashtags(text);
                sink.accept(new RawRecord("fb_"+id, text, cfg.lang, ts, "facebook", "", tags));
            }
        }
    }
}