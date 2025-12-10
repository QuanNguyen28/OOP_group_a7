package app.model.service.insight;

/** Minimal, stable LLM interface. */
public interface LlmClient {
    /** Synchronous one-shot completion. Return plain text. */
    String complete(String prompt) throws Exception;

    // Aliases — để tương thích code cũ (nếu nơi nào lỡ gọi tên khác)
    default String generate(String prompt) throws Exception { return complete(prompt); }
    default String chat(String prompt)     throws Exception { return complete(prompt); }
    default String invoke(String prompt)   throws Exception { return complete(prompt); }
    default String run(String prompt)      throws Exception { return complete(prompt); }
}