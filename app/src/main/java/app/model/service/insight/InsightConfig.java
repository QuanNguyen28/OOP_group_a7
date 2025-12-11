package app.model.service.insight;

public final class InsightConfig {

    public record Settings(
            String provider,   // "vertex" | "local"
            String model,      // vd: "gemini-2.5-flash"
            String project,    // GCP project id
            String location,   // vd: "us-central1" hoặc "asia-southeast1"
            String saKeyFile   // đường dẫn tuyệt đối đến service-account JSON
    ) {}

    // MẶC ĐỊNH: DÙNG VERTEX AI
    public static final Settings ACTIVE = new Settings(
            "vertex",
            "gemini-2.5-flash",
            "gen-lang-client-0294487240",
            "us-central1",
            "/Users/hoangquannguyen/Documents/Code./OOP/OOP_group_a7/app/config/sa.json"
    );

    // Nếu muốn chạy offline thử UI (không gọi LLM)
    public static final Settings LOCAL_ECHO = new Settings(
            "local", "local-echo", null, null, null
    );

    private InsightConfig() {}
}