package app.model.domain;

import java.util.Map;

public record RawPost(
    String id,
    String platform,
    String text,
    String lang,
    String createdAt,
    String userLoc,
    Map<String, Object> meta
) {}