package app.model.service.nlp;
import app.model.domain.SentimentResult;
public interface NlpModel {
    String modelId();
    SentimentResult analyzeSentiment(String id, String text, String lang, java.time.Instant ts);
}