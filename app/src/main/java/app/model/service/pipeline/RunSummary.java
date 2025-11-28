package app.model.service.pipeline;

import java.time.Instant;

public record RunSummary(
    String runId, int ingested, int analyzed, Instant startedAt, String modelId
) {}