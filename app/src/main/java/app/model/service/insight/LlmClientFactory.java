package app.model.service.insight;

public final class LlmClientFactory {

    private LlmClientFactory() {}

    public static LlmClient fromFixedDefault() {
        String provider = LlmSettings.PROVIDER == null ? "mock" : LlmSettings.PROVIDER.trim().toLowerCase();
        switch (provider) {
            case "gemini":
                return new GeminiClient(LlmSettings.API_KEY, LlmSettings.MODEL, LlmSettings.BASE_URL);
            // Bạn có thể thêm OpenAIClient/OllamaClient nếu cần:
            // case "openai": return new OpenAIClient(LlmSettings.API_KEY, LlmSettings.MODEL, LlmSettings.BASE_URL);
            // case "ollama": return new OllamaClient(LlmSettings.MODEL, LlmSettings.BASE_URL);
            default:
                return new MockLlmClient(); // fallback an toàn khi chưa cấu hình
        }
    }

    /** Mock tối giản để dev/test offline. */
    static class MockLlmClient implements LlmClient {
        @Override public String complete(String prompt) {
            // rút gọn 1-2 câu từ prompt, tránh gọi mạng
            String p = prompt == null ? "" : prompt.trim();
            if (p.length() > 600) p = p.substring(0, 600) + "...";
            return "📝 (Mock summary)\n" + p;
        }
    }
}