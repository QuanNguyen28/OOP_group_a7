package app.model.service.insight;

public final class LlmSettings {
    public static final String PROVIDER = "gemini";              // "gemini" | "openai" | "ollama" | "mock"
    public static final String API_KEY  = "YOUR_GEMINI_API_KEY"; // <-- điền key thật
    public static final String MODEL    = "gemini-2.5-flash";    // model Gemini bạn muốn
    public static final String BASE_URL = "https://generativelanguage.googleapis.com"; // mặc định Gemini

    private LlmSettings() {}
}