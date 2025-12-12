package collector.api;

import java.time.Instant;
import java.util.List;

public record QuerySpec(
        List<String> keywords,
        Instant from,
        Instant to,
        int limit
) {}