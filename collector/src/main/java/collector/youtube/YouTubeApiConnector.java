package collector.youtube;

import collector.core.Config;
import collector.core.QuerySpec;
import collector.core.RawPost;
import collector.core.SourceConnector;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public final class YouTubeApiConnector implements SourceConnector {

    private final String uiApiKey; // có thể null

    public YouTubeApiConnector() { this(null); }
    public YouTubeApiConnector(String uiApiKey) { this.uiApiKey = uiApiKey; }

    @Override public String id()   { return "youtube"; }
    @Override public String name() { return "YouTube (API + comments)"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        String apiKey = Config.firstFileThen("yt.apiKey", uiApiKey, "yt.apiKey", "YT_API_KEY");
        Objects.requireNonNull(apiKey, "yt.apiKey is null. Hãy đặt vào collector/conf.properties (ưu tiên), hoặc -Dyt.apiKey, hoặc env YT_API_KEY.");

        YouTubeApiClient client = new YouTubeApiClient(apiKey);
        List<RawPost> out = new ArrayList<>();

        int limit = spec.limit();
        for (String kw : spec.keywords()) {
            if (out.size() >= limit) break;

            String pageToken = null;
            do {
                var res = client.search(kw, spec.from(), spec.to(), pageToken);
                var items = res.has("items") ? res.getAsJsonArray("items") : null;
                if (items == null) break;

                for (var e : items) {
                    var obj = e.getAsJsonObject();
                    var idObj = obj.getAsJsonObject("id");
                    if (idObj == null || !"youtube#video".equals(idObj.get("kind").getAsString())) continue;

                    String vid = idObj.get("videoId").getAsString();
                    var sn = obj.getAsJsonObject("snippet");

                    String title = sn.has("title") ? sn.get("title").getAsString() : "";
                    String desc  = sn.has("description") ? sn.get("description").getAsString() : "";
                    String text  = (title + "\n\n" + desc).trim();
                    Instant ts   = Instant.parse(sn.get("publishedAt").getAsString());

                    String videoUrl = "https://www.youtube.com/watch?v=" + URLEncoder.encode(vid, StandardCharsets.UTF_8);
                    out.add(new RawPost(
                            "yt:" + vid,
                            "youtube",
                            text,
                            ts,
                            "vi",
                            videoUrl,
                            List.of("video:" + vid),
                            "youtube-api"
                    ));
                    if (out.size() >= limit) break;

                    // Lấy thêm top-level comments gộp chung
                    String cpt = null;
                    do {
                        var thr = client.commentThreads(vid, cpt);
                        var cItems = thr.has("items") ? thr.getAsJsonArray("items") : null;
                        if (cItems == null || cItems.size() == 0) break;

                        for (var ce : cItems) {
                            var cObj = ce.getAsJsonObject();
                            var top = cObj.getAsJsonObject("snippet").getAsJsonObject("topLevelComment");
                            var csn = top.getAsJsonObject("snippet");

                            String cid = top.get("id").getAsString();
                            String ctext = csn.has("textOriginal") ? csn.get("textOriginal").getAsString() : "";
                            Instant cts  = csn.has("publishedAt") ? Instant.parse(csn.get("publishedAt").getAsString()) : ts;

                            String commentUrl = videoUrl + "&lc=" + URLEncoder.encode(cid, StandardCharsets.UTF_8);

                            out.add(new RawPost(
                                    "ytc:" + vid + ":" + cid,
                                    "youtube",
                                    ctext,
                                    cts,
                                    "vi",
                                    commentUrl,
                                    List.of("comment", "video:" + vid),
                                    "youtube-api"
                            ));
                            if (out.size() >= limit) break;
                        }

                        cpt = thr.has("nextPageToken") ? thr.get("nextPageToken").getAsString() : null;
                    } while (out.size() < limit && cpt != null);
                }

                pageToken = res.has("nextPageToken") ? res.get("nextPageToken").getAsString() : null;
            } while (out.size() < limit && pageToken != null);
        }

        return out.stream();
    }
}