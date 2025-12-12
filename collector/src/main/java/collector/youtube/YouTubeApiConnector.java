package collector.youtube;

import collector.api.QuerySpec;
import collector.api.SourceConnector;
import collector.core.RawPost;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class YouTubeApiConnector implements SourceConnector {
    private final String apiKey = System.getProperty("yt.apiKey", System.getenv("YT_API_KEY"));

    @Override public String id() { return "youtube-api"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        var client = new YouTubeApiClient(apiKey);
        List<RawPost> out = new ArrayList<>();

        for (String kw : spec.keywords()) {
            String pageToken = null;
            while (out.size() < spec.limit()) {
                JsonNode root = client.search(kw, spec.from(), spec.to(), pageToken);
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) break;

                for (JsonNode it : items) {
                    var id = it.path("id").path("videoId").asText("");
                    var sn = it.path("snippet");
                    var title = sn.path("title").asText("");
                    var desc  = sn.path("description").asText("");
                    var publishedAt = sn.path("publishedAt").asText("");
                    if (id.isBlank() || publishedAt.isBlank()) continue;

                    Instant ts = Instant.parse(publishedAt);
                    if (spec.from() != null && ts.isBefore(spec.from())) continue;
                    if (spec.to()   != null && ts.isAfter(spec.to()))   continue;

                    String link = "https://www.youtube.com/watch?v=" + id;
                    String text = (title + " " + desc).trim();

                    out.add(new RawPost(
                            "yt:" + id,
                            "youtube",
                            text,
                            ts,
                            "vi",
                            link,
                            List.of(kw),
                            id()
                    ));

                    if (out.size() >= spec.limit()) break;
                }

                pageToken = root.path("nextPageToken").asText("");
                if (pageToken.isBlank()) break;
            }
        }

        return out.stream();
    }
}