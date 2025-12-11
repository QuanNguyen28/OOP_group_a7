package app.model.service.insight;

import app.model.repository.AnalyticsRepo;

import java.time.LocalDate;
import java.util.Objects;

/** Dịch vụ sinh phân tích tóm tắt bằng LLM (có fallback cục bộ). */
public class InsightService {
    private final AnalyticsRepo repo;
    private final LlmClient llm;

    public InsightService(AnalyticsRepo repo, LlmClient llm) {
        this.repo = Objects.requireNonNull(repo, "analyticsRepo");
        this.llm = Objects.requireNonNull(llm, "llmClient");
    }

    public String summarizeOverall(String runId, LocalDate from, LocalDate to) {
        String prompt = Templates.OVERALL_VI.formatted(
                runId, from == null ? "N/A" : from.toString(), to == null ? "N/A" : to.toString()
        );
        return safeComplete(prompt, "Tổng quan cảm xúc");
    }

    public String summarizeDamage(String runId) {
        String prompt = Templates.DAMAGE_VI.formatted(runId);
        return safeComplete(prompt, "Thiệt hại phổ biến");
    }

    public String summarizeRelief(String runId) {
        String prompt = Templates.RELIEF_VI.formatted(runId);
        return safeComplete(prompt, "Hạng mục cứu trợ");
    }

    public String summarizeTask3(String runId) {
        String prompt = Templates.TASK3_VI.formatted(runId);
        return safeComplete(prompt, "Bài toán 3 (Hài lòng/Không hài lòng)");
    }

    public String summarizeTask4(String runId, LocalDate from, LocalDate to) {
        String prompt = Templates.TASK4_VI.formatted(
                runId, from == null ? "N/A" : from.toString(), to == null ? "N/A" : to.toString()
        );
        return safeComplete(prompt, "Bài toán 4 (Cảm xúc theo thời gian)");
    }

    private String safeComplete(String prompt, String title) {
        try {
            String out = llm.complete(prompt);
            if (out != null && !out.isBlank()) return out;
        } catch (Exception ignored) { }
        return "[LocalEcho:local-echo]\n"
                + title + " — Tự động tóm tắt cục bộ (fallback).\n"
                + "Hãy cấu hình LLM thật (Vertex AI) để nhận kết quả giàu thông tin hơn.\n\n"
                + "(Preview prompt)\n"
                + truncate(prompt, 900);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}