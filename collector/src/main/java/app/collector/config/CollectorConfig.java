package app.collector.config;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class CollectorConfig {
    public final Path outRoot;         // ví dụ data/collections
    public final String collection;    // ví dụ yagi-sept-raw
    public final List<String> queries; // dùng cho YouTube/Twitter/News
    public final List<String> keywords;// ghi dataset.yaml
    public final Instant after;
    public final Instant before;
    public final String lang;

    public final boolean enableYouTube;
    public final boolean enableTwitter;
    public final boolean enableFacebook;
    public final boolean enableNews;

    public final int ytMaxVideosPerQuery;
    public final boolean ytIncludeComments;
    public final boolean ytIncludeTranscripts;
    public final int twitterMaxPerQuery;

    // API Keys (cố định tại đây – có thể thay sau này bằng file .conf)
    public final String youtubeApiKey;
    public final String twitterBearer;
    public final String fbAccessToken;

    public final List<String> facebookPageIds;
    public final List<String> newsFeeds;

    public CollectorConfig(
            Path outRoot, String collection,
            List<String> queries, List<String> keywords,
            Instant after, Instant before, String lang,
            boolean enableYouTube, boolean enableTwitter, boolean enableFacebook, boolean enableNews,
            int ytMaxVideosPerQuery, boolean ytIncludeComments, boolean ytIncludeTranscripts,
            int twitterMaxPerQuery,
            String youtubeApiKey, String twitterBearer, String fbAccessToken,
            List<String> facebookPageIds, List<String> newsFeeds
    ) {
        this.outRoot = outRoot;
        this.collection = collection;
        this.queries = queries;
        this.keywords = keywords;
        this.after = after;
        this.before = before;
        this.lang = lang;
        this.enableYouTube = enableYouTube;
        this.enableTwitter = enableTwitter;
        this.enableFacebook = enableFacebook;
        this.enableNews = enableNews;
        this.ytMaxVideosPerQuery = ytMaxVideosPerQuery;
        this.ytIncludeComments = ytIncludeComments;
        this.ytIncludeTranscripts = ytIncludeTranscripts;
        this.twitterMaxPerQuery = twitterMaxPerQuery;
        this.youtubeApiKey = youtubeApiKey;
        this.twitterBearer = twitterBearer;
        this.fbAccessToken = fbAccessToken;
        this.facebookPageIds = facebookPageIds;
        this.newsFeeds = newsFeeds;
    }

    public static CollectorConfig fromArgs(String[] args) {
        Map<String,String> m = parseArgs(args);

        Path outRoot = Paths.get(m.getOrDefault("--outDir", "data/collections")).toAbsolutePath();
        String collection = m.getOrDefault("--collection","manual-collect");

        List<String> queries = csv(m.getOrDefault("--queries", "yagi,#yagi,bao yagi,#bao yagi"));
        List<String> keywords= csv(m.getOrDefault("--keywords", String.join(",", queries)));

        Instant after  = iso(m.getOrDefault("--after",  "2024-09-01T00:00:00Z"));
        Instant before = iso(m.getOrDefault("--before", "2024-10-01T00:00:00Z"));
        String lang = m.getOrDefault("--lang", "vi");

        boolean yt = !"false".equalsIgnoreCase(m.getOrDefault("--youtube","true"));
        boolean tw = !"false".equalsIgnoreCase(m.getOrDefault("--twitter","true"));
        boolean fb = !"false".equalsIgnoreCase(m.getOrDefault("--facebook","true"));
        boolean nw = !"false".equalsIgnoreCase(m.getOrDefault("--news","true"));

        int ytMax = Integer.parseInt(m.getOrDefault("--ytMaxVideos","80"));
        boolean ytC = !"false".equalsIgnoreCase(m.getOrDefault("--ytComments","true"));
        boolean ytT = !"false".equalsIgnoreCase(m.getOrDefault("--ytTranscripts","true"));
        int twMax = Integer.parseInt(m.getOrDefault("--twMax","100"));

        // Keys cố định (có thể đặt thẳng ở đây, hoặc truyền qua args)
        String ytKey = m.getOrDefault("--ytKey", "PUT_YOUTUBE_API_KEY");
        String twBearer = m.getOrDefault("--twBearer", "PUT_TWITTER_BEARER");
        String fbToken  = m.getOrDefault("--fbToken", "PUT_FACEBOOK_GRAPH_ACCESS_TOKEN");

        List<String> pageIds = csv(m.getOrDefault("--fbPages",""));
        if (pageIds.isEmpty()) pageIds = List.of(); // thêm ID page khi cần

        List<String> feeds = csv(m.getOrDefault("--feeds",
                "https://vnexpress.net/rss/tin-moi-nhat.rss,https://tuoitre.vn/rss/tin-moi-nhat.rss"));

        return new CollectorConfig(
                outRoot, collection, queries, keywords, after, before, lang,
                yt, tw, fb, nw,
                ytMax, ytC, ytT, twMax,
                ytKey, twBearer, fbToken,
                pageIds, feeds
        );
    }

    private static Map<String,String> parseArgs(String[] args){
        Map<String,String> m = new LinkedHashMap<>();
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String v = (i+1<args.length && !args[i+1].startsWith("--")) ? args[++i] : "true";
                m.put(a, v);
            }
        }
        return m;
    }
    private static List<String> csv(String s){
        if (s==null || s.isBlank()) return List.of();
        String[] arr = s.split("\\s*,\\s*");
        List<String> out = new ArrayList<>();
        for (String x : arr) if (!x.isBlank()) out.add(x);
        return out;
    }
    private static Instant iso(String s){
        try { return Instant.parse(s); } catch(Exception e){ return Instant.now(); }
    }
}