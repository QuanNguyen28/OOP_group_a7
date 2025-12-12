package collector.youtube;

import collector.core.QuerySpec;
import collector.core.RawPost;
import collector.core.SourceConnector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class YouTubeCommentsConnector implements SourceConnector {
    private final String apiKey = System.getProperty("yt.apiKey", System.getenv("YT_API_KEY"));
    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Override public String id()   { return "youtube-comments"; }
    @Override public String name() { return "YouTube (comments)"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        List<RawPost> out = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[YouTubeComments] Missing API key (-Dyt.apiKey=... or env YT_API_KEY). Skip.");
            return Stream.empty();
        }

        for (String kw : spec.keywords()) {
            if (out.size() >= spec.limit()) break;

            String nextPage = null;
            while (out.size() < spec.limit()) {
                JsonNode search = call(buildSearchUrl(kw, spec.from(), spec.to(), nextPage));
                JsonNode items = search.path("items");
                if (!items.isArray() || items.isEmpty()) break;

                // với mỗi video: kéo threads bình luận
                for (JsonNode it : items) {
                    String vid = it.path("id").path("videoId").asText("");
                    if (vid.isBlank()) continue;

                    String cPage = null;
                    while (out.size() < spec.limit()) {
                        JsonNode threads = call(buildCommentsUrl(vid, cPage));
                        JsonNode tItems = threads.path("items");
                        if (!tItems.isArray() || tItems.isEmpty()) break;

                        for (JsonNode th : tItems) {
                            JsonNode top = th.path("snippet").path("topLevelComment").path("snippet");
                            String cId   = th.path("id").asText("");
                            String text  = top.path("textOriginal").asText("").trim();
                            String at    = top.path("publishedAt").asText("");
                            if (text.isBlank() || at.isBlank()) continue;

                            Instant ts = Instant.parse(at);
                            if (spec.from()!=null && ts.isBefore(spec.from())) continue;
                            if (spec.to()!=null   && ts.isAfter(spec.to()))   continue;

                            String link = "https://www.youtube.com/watch?v=" + vid + "&lc=" + cId;

                            out.add(new RawPost(
                                    "ytc:" + vid + ":" + cId,
                                    "youtube",
                                    text,
                                    ts,
                                    "vi",                 // có thể để "und" nếu bạn muốn
                                    link,
                                    List.of(kw),
                                    id()
                            ));
                            if (out.size() >= spec.limit()) break;
                        }

                        cPage = threads.path("nextPageToken").asText("");
                        if (cPage.isBlank()) break;
                    }

                    if (out.size() >= spec.limit()) break;
                }

                nextPage = search.path("nextPageToken").asText("");
                if (nextPage.isBlank()) break;
            }
        }

        return out.stream();
    }

    private String buildSearchUrl(String q, Instant from, Instant to, String pageToken) {
        StringBuilder sb = new StringBuilder("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=50");
        sb.append("&q=").append(urlEnc(q));
        if (from != null) sb.append("&publishedAfter=").append(from.toString());
        if (to   != null) sb.append("&publishedBefore=").append(to.toString());
        if (pageToken != null && !pageToken.isBlank()) sb.append("&pageToken=").append(pageToken);
        sb.append("&key=").append(apiKey);
        return sb.toString();
    }

    private String buildCommentsUrl(String videoId, String pageToken) {
        StringBuilder sb = new StringBuilder("https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&maxResults=100&order=time");
        sb.append("&videoId=").append(urlEnc(videoId));
        if (pageToken != null && !pageToken.isBlank()) sb.append("&pageToken=").append(pageToken);
        sb.append("&key=").append(apiKey);
        return sb.toString();
    }

    private JsonNode call(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("[YouTubeComments] HTTP " + resp.statusCode() + " for " + url);
            // đừng ném exception => không làm fail cả collector, trả về rỗng
            return M.readTree("{\"items\":[]}");
        }
        return M.readTree(resp.body());
    }

    private static String urlEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return s; }
    }
}