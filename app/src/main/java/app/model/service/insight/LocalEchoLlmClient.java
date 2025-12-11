package app.model.service.insight;

public class LocalEchoLlmClient implements LlmClient {
    private final String modelId;

    public LocalEchoLlmClient(String modelId) {
        this.modelId = modelId;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String complete(String prompt) {
        // Trả về rỗng/blank để Dashboard dùng fallback (tự tính từ DB),
        // thay vì in ra "Prompt preview".
        return "";
    }
}