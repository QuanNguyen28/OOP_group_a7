package app.model.service.insight;

public interface LlmClient {
    String modelId();
    String complete(String prompt) throws Exception;
}