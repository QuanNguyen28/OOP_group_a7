package app.model.domain;

import java.time.Instant;
import java.util.Optional;

public record CleanPost(
    String rawId,
    String textNorm,
    String lang,
    Instant ts,
    Optional<String> geo
) {}