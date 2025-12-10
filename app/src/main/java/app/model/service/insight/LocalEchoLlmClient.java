// app/src/main/java/app/model/service/insight/LocalEchoLlmClient.java
package app.model.service.insight;

/**
 * Local echo client — dùng để dev/test offline.
 * Tuân thủ interface LlmClient mới: chỉ cần complete(String).
 */
public class LocalEchoLlmClient implements LlmClient {

    private final String name;

    public LocalEchoLlmClient() {
        this("local-echo");
    }

    public LocalEchoLlmClient(String name) {
        this.name = (name == null || name.isBlank()) ? "local-echo" : name;
    }

    @Override
    public String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "🤖 [" + name + "] (empty prompt)";
        }
        String s = prompt.trim();
        // Giới hạn độ dài để tránh bơm quá nhiều vào UI log
        if (s.length() > 1200) {
            s = s.substring(0, 1200) + "...";
        }
        // Có thể thêm logic tóm tắt nhanh tại chỗ nếu muốn
        return "🤖 [" + name + "]\n" + s;
    }
}