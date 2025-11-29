package app.model.domain;
import java.time.Instant;
public record SentimentResult(String id, SentimentLabel label, double score, Instant ts) {}