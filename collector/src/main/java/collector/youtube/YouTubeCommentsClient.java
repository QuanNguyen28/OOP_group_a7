package collector.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class YouTubeCommentsClient {
    public record Comment(String text, Instant ts) {}

    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public YouTubeCommentsClient(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "yt.apiKey is null. Pass -Dyt.apiKey=... or set in conf.");
    }

    public List<Comment> fetch(String videoId, int max) throws Exception {
        if (videoId == null || videoId.isBlank()) return List.of();
        if (max <= 0) max = 100;

        List<Comment> out = new ArrayList<>();
        String pageToken = null;

        while (out.size() < max) {
            JsonObject root = commentThreads(videoId, pageToken);
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();

            for (int i = 0; i < items.size() && out.size() < max; i++) {
                JsonObject obj = items.get(i).getAsJsonObject();
                JsonObject snippet = obj.getAsJsonObject("snippet");
                if (snippet == null || !snippet.has("topLevelComment")) continue;

                JsonObject tlc = snippet.getAsJsonObject("topLevelComment");
                if (tlc == null || !tlc.has("snippet")) continue;

                JsonObject sn = tlc.getAsJsonObject("snippet");
                String text = sn.has("textDisplay") ? sn.get("textDisplay").getAsString() : "";
                if (text.isBlank()) continue;

                String publishedAt = sn.has("publishedAt") ? sn.get("publishedAt").getAsString() : null;
                Instant ts = (publishedAt != null) ? Instant.parse(publishedAt) : Instant.now();

                out.add(new Comment(text, ts));
            }

            pageToken = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : null;
            if (pageToken == null || items.size() == 0) break;
        }
        return out;
    }

    private JsonObject commentThreads(String videoId, String pageToken) throws Exception {
        StringBuilder sb = new StringBuilder(
                "https://www.googleapis.com/youtube/v3/commentThreads" +
                "?part=snippet&textFormat=plainText&maxResults=100&order=time");
        sb.append("&videoId=").append(URLEncoder.encode(videoId, StandardCharsets.UTF_8));
        if (pageToken != null && !pageToken.isBlank()) {
            sb.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        sb.append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(sb.toString()))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("YouTube commentThreads HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
}