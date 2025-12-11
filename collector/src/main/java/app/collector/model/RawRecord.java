package app.collector.model;

import java.util.List;

public final class RawRecord {
    public final String id;
    public final String text;
    public final String lang;
    public final String createdAt; // ISO-Z
    public final String platform;  // youtube|twitter|facebook|news
    public final String userLoc;
    public final List<String> hashtags;

    public RawRecord(String id, String text, String lang, String createdAt, String platform, String userLoc, List<String> hashtags) {
        this.id = id; this.text = text; this.lang = lang; this.createdAt = createdAt; this.platform = platform; this.userLoc = userLoc; this.hashtags = hashtags;
    }
}