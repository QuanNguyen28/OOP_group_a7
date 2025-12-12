package collector.reddit;

import collector.core.QuerySpec;
import collector.core.RawPost;
import collector.core.SourceConnector;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public final class RedditJsonConnector implements SourceConnector {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    @Override public String id()   { return "reddit"; }
    @Override public String name() { return "reddit (json)"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        List<RawPost> out = new ArrayList<>();
        int limit = spec.limit();
        Instant from = spec.from();
        Instant to   = spec.to();

        for (String kw : spec.keywords()) {
            if (out.size() >= limit) break;

            String after = null;
            final int pageSize = Math.min(100, Math.max(20, limit));
            for (int page = 0; page < 20 && out.size() < limit; page++) {
                String url = buildSearchUrl(kw, pageSize, after);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "oop-group-a7-collector/1.0")
                        .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) break;

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonObject data = root.has("data") && root.get("data").isJsonObject()
                        ? root.getAsJsonObject("data") : new JsonObject();
                JsonArray children = data.has("children") && data.get("children").isJsonArray()
                        ? data.getAsJsonArray("children") : new JsonArray();

                for (JsonElement e : children) {
                    if (out.size() >= limit) break;
                    if (!e.isJsonObject()) continue;
                    JsonObject ro = e.getAsJsonObject().getAsJsonObject("data");
                    if (ro == null) continue;

                    long created = ro.has("created_utc") && !ro.get("created_utc").isJsonNull()
                            ? ro.get("created_utc").getAsLong() : 0L;
                    Instant ts = created > 0 ? Instant.ofEpochSecond(created) : Instant.now();
                    if (from != null && ts.isBefore(from)) continue;
                    if (to   != null && ts.isAfter(to))   continue;

                    String id       = "rd:" + sval(ro, "id");
                    String title    = sval(ro, "title");
                    String selftext = sval(ro, "selftext");
                    String text     = ((title == null ? "" : title) + " " + (selftext == null ? "" : selftext)).trim();
                    if (text.isEmpty()) text = title == null ? "" : title;

                    String permalink = sval(ro, "permalink");
                    String urlPost   = (permalink == null || permalink.isBlank())
                            ? sval(ro, "url")
                            : ("https://www.reddit.com" + permalink);

                    // RawPost signature: (id, platform, text, ts, lang, link, List<String> hashtags, source)
                    out.add(new RawPost(
                            id,
                            "reddit",
                            text == null ? "" : text,
                            ts,
                            "",                       // lang: để trống
                            urlPost == null ? "" : urlPost,
                            List.of(),               // hashtags rỗng
                            "reddit-json"
                    ));
                }

                after = data.has("after") && !data.get("after").isJsonNull()
                        ? data.get("after").getAsString() : null;
                if (after == null || after.isBlank()) break;

                Thread.sleep(600);
            }
        }
        return out.stream();
    }

    private static String buildSearchUrl(String kw, int pageSize, String after) {
        String q = URLEncoder.encode(kw, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("https://www.reddit.com/search.json");
        sb.append("?q=").append(q);
        sb.append("&sort=new&t=all&restrict_sr=0");
        sb.append("&limit=").append(pageSize);
        if (after != null && !after.isBlank()) sb.append("&after=").append(after);
        return sb.toString();
    }

    private static String sval(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : "";
    }
}