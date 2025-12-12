package collector.youtube;

import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class YouTubeApiClient {
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public YouTubeApiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey is null/blank");
        this.apiKey = apiKey;
    }

    public JsonObject search(String query, Instant after, Instant before, String pageToken) throws Exception {
        StringBuilder url = new StringBuilder("https://www.googleapis.com/youtube/v3/search")
            .append("?part=snippet&type=video&maxResults=50")
            .append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
            .append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        if (after  != null) url.append("&publishedAfter=").append(after.toString());
        if (before != null) url.append("&publishedBefore=").append(before.toString());
        if (pageToken != null) url.append("&pageToken=").append(pageToken);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString())).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    public JsonObject commentThreads(String videoId, String pageToken) throws Exception {
        StringBuilder url = new StringBuilder("https://www.googleapis.com/youtube/v3/commentThreads")
            .append("?part=snippet&maxResults=100&textFormat=plainText")
            .append("&videoId=").append(URLEncoder.encode(videoId, StandardCharsets.UTF_8))
            .append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        if (pageToken != null) url.append("&pageToken=").append(pageToken);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString())).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
}