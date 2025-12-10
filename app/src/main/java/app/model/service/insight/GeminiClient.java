package app.model.service.insight;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Minimal Gemini client for text-only prompts. */
public class GeminiClient implements LlmClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    public GeminiClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model  = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model;
        this.baseUrl= (baseUrl == null || baseUrl.isBlank()) ? "https://generativelanguage.googleapis.com" : baseUrl;
    }

    @Override
    public String complete(String prompt) throws Exception {
        // Endpoint: {BASE}/v1beta/models/{model}:generateContent?key=API_KEY
        String url = String.format("%s/v1beta/models/%s:generateContent?key=%s",
                baseUrl, model, apiKey);

        String body = "{\"contents\":[{\"parts\":[{\"text\":"
                + toJsonString(prompt)
                + "}]}]}";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Gemini HTTP " + resp.statusCode() + ": " + resp.body());
        }
        // Naive parse: lấy "text": "..."
        String text = extractFirstText(resp.body());
        return (text == null || text.isBlank()) ? "(empty response)" : text.trim();
    }

    // ---- helpers ----
    private static String toJsonString(String s) {
        if (s == null) s = "";
        // Escape tối thiểu
        s = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "\"" + s + "\"";
    }

    /** Trích text đầu tiên từ JSON phản hồi Gemini. */
    private static String extractFirstText(String json) {
        // Tìm trường "text": "...", đơn giản cho response mặc định
        int i = json.indexOf("\"text\"");
        if (i < 0) return null;
        i = json.indexOf(':', i);
        if (i < 0) return null;
        int q1 = json.indexOf('"', i + 1);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int k = q1 + 1; k < json.length(); k++) {
            char c = json.charAt(k);
            if (esc) {
                if (c == 'n') sb.append('\n');
                else sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}