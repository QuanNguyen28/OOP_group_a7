package app.model.repository.dto;
import java.time.Instant;
public record OverallSentimentRow(Instant bucketStart, int pos, int neg, int neu) {}