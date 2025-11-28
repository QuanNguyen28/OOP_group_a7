package app.model.service.ingest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record QuerySpec(
    List<String> keywords,
    Instant from,
    Instant to,
    Optional<String> lang,
    Optional<String> geo,
    int maxItems,
    boolean includeComments
) {}