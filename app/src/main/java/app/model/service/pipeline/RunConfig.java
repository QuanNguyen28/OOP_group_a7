package app.model.service.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record RunConfig(
    String runId,
    List<String> connectors,
    List<String> keywords,
    Instant from,
    Instant to,
    Set<String> tasks
) {}