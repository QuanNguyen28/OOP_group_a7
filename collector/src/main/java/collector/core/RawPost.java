package collector.core;

import java.time.Instant;
import java.util.List;

public record RawPost(
        String id,
        String platform,
        String text,
        Instant ts,
        String lang,
        String link,
        List<String> keywords,
        String source
) {}