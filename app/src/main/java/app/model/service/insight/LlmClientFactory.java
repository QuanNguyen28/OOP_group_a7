package app.model.service.insight;

public final class LlmClientFactory {
    private LlmClientFactory() {}

    public static LlmClient fromSettings(InsightConfig.Settings s) {
        return switch (s.provider()) {
            case "vertex" -> new VertexAiClient(
                    true,                // published model (google/models/…)
                    s.model(),
                    s.project(),
                    s.location(),
                    s.saKeyFile()
            );
            default -> new LocalEchoLlmClient("local-echo");
        };
    }
}