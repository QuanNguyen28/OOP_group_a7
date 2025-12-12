package collector.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

public final class YouTubeApiClient {
    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;

    public YouTubeApiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Missing yt.apiKey (pass -Dyt.apiKey=...)");
        this.apiKey = apiKey.trim();
    }

    JsonNode search(String query, Instant publishedAfter, Instant publishedBefore, String pageToken) throws Exception {
        StringBuilder sb = new StringBuilder("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=50");
        sb.append("&q=").append(java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        sb.append("&key=").append(apiKey);
        if (publishedAfter != null)  sb.append("&publishedAfter=").append(publishedAfter.toString());
        if (publishedBefore != null) sb.append("&publishedBefore=").append(publishedBefore.toString());
        if (pageToken != null && !pageToken.isBlank()) sb.append("&pageToken=").append(pageToken);

        var req = HttpRequest.newBuilder(URI.create(sb.toString()))
                .GET()
                .header("User-Agent", "collector/1.0 (+https://example)")
                .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode()/100 != 2) {
            throw new RuntimeException("YouTube API HTTP " + resp.statusCode() + " -> " + resp.body());
        }
        return M.readTree(resp.body());
    }
}