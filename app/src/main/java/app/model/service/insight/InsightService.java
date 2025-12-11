package app.model.service.insight;

import app.model.repository.AnalyticsRepo;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Sinh phân tích tóm tắt bằng LLM; kèm dữ liệu nền (CSV) để mô hình dựa vào. */
public class InsightService {
    private final AnalyticsRepo repo;
    private final LlmClient llm;

    public InsightService(AnalyticsRepo repo, LlmClient llm) {
        this.repo = Objects.requireNonNull(repo, "analyticsRepo");
        this.llm  = Objects.requireNonNull(llm,  "llmClient");
    }

    // ===================== Public API (giữ nguyên chữ ký) =====================

    public String summarizeOverall(String runId, LocalDate from, LocalDate to) {
        String csv = fetchOverallCsv(runId, 60); // tối đa 60 dòng gần nhất
        String prompt = Templates.OVERALL_VI.formatted(runId, str(from), str(to), orDash(csv));
        return safeComplete(prompt, "Tổng quan cảm xúc");
    }

    public String summarizeDamage(String runId) {
        String csv = fetchDamageCsv(runId, 12);  // top 12 loại
        String prompt = Templates.DAMAGE_VI.formatted(runId, orDash(csv));
        return safeComplete(prompt, "Thiệt hại phổ biến");
    }

    public String summarizeRelief(String runId) {
        String csv = fetchReliefCsv(runId, 12);
        String prompt = Templates.RELIEF_VI.formatted(runId, orDash(csv));
        return safeComplete(prompt, "Hạng mục cứu trợ");
    }

    public String summarizeTask3(String runId) {
        String csv = fetchTask3Csv(runId, 20);
        String prompt = Templates.TASK3_VI.formatted(runId, orDash(csv));
        return safeComplete(prompt, "Bài toán 3");
    }

    public String summarizeTask4(String runId, LocalDate from, LocalDate to) {
        String csv = fetchTask4Csv(runId, from, to, 120);
        String prompt = Templates.TASK4_VI.formatted(runId, str(from), str(to), orDash(csv));
        return safeComplete(prompt, "Bài toán 4");
    }

    // ===================== Prompt call with fallback =====================

    private String safeComplete(String prompt, String title) {
        try {
            String out = llm.complete(prompt);
            if (out != null && !out.isBlank()) return out;
        } catch (Exception ignored) {}
        return "[LocalEcho:local-echo]\n"
                + title + " — Tự động tóm tắt cục bộ (fallback). Kiểm tra cấu hình Vertex AI.\n\n"
                + "(Prompt kèm dữ liệu — preview)\n"
                + truncate(prompt, 1200);
    }

    // ===================== CSV builders (best-effort qua reflection) =====================

    /** Overall sentiment by day: day,pos,neg,neu */
    private String fetchOverallCsv(String runId, int limit) {
        List<Object> rows = callList(repo, "readOverallRows", runId);
        if (rows.isEmpty()) return "";
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        return rows.stream().limit(limit).map(r -> {
            String day = getDate(r, "bucketStart", "day", "date");
            String pos = getNum(r, "pos","positive","posCount");
            String neg = getNum(r, "neg","negative","negCount");
            String neu = getNum(r, "neu","neutral","neuCount");
            return "%s,%s,%s,%s".formatted(day, pos, neg, neu);
        }).collect(Collectors.joining("\n"));
    }

    /** Damage counts: tag,count */
    private String fetchDamageCsv(String runId, int limit) {
        List<Object> rows = callList(repo, "readDamageCounts", runId);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String tag = getStr(r, "tag","label","type","category");
            String cnt = getNum(r, "cnt","count","value");
            return "%s,%s".formatted(tag, cnt);
        }).collect(Collectors.joining("\n"));
    }

    /** Relief counts: tag,count */
    private String fetchReliefCsv(String runId, int limit) {
        List<Object> rows = callList(repo, "readReliefCounts", runId);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String tag = getStr(r, "tag","label","item","category");
            String cnt = getNum(r, "cnt","count","value");
            return "%s,%s".formatted(tag, cnt);
        }).collect(Collectors.joining("\n"));
    }

    /** Task3 per category sentiment: cat,pos,neg,net */
    private String fetchTask3Csv(String runId, int limit) {
        List<Object> rows = callList(repo, "readReliefCategorySentiment", runId);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String cat = getStr(r, "category","item","tag","label");
            String pos = getNum(r, "pos","positive","posCount");
            String neg = getNum(r, "neg","negative","negCount");
            String net = getNum(r, "net","total","totalScore","score");
            return "%s,%s,%s,%s".formatted(cat, pos, neg, net);
        }).collect(Collectors.joining("\n"));
    }

    /** Task4 per day & category: day,cat,pos,neg,net */
    private String fetchTask4Csv(String runId, LocalDate from, LocalDate to, int limit) {
        // thử các method phổ biến; ưu tiên có tham số from/to
        List<Object> rows = callList(repo, "readReliefDaily", runId, from, to);
        if (rows.isEmpty()) rows = callList(repo, "readReliefDaily", runId);
        if (rows.isEmpty()) rows = callList(repo, "readTask4Daily", runId, from, to);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String day = getDate(r, "day","date","bucketStart");
            String cat = getStr(r, "category","item","tag","label");
            String pos = getNum(r, "pos","positive","posCount");
            String neg = getNum(r, "neg","negative","negCount");
            String net = getNum(r, "net","total","totalScore","score");
            return "%s,%s,%s,%s,%s".formatted(day, cat, pos, neg, net);
        }).collect(Collectors.joining("\n"));
    }

    // ===================== Small helpers =====================

    private static String str(LocalDate d) { return d == null ? "N/A" : d.toString(); }
    private static String orDash(String s)  { return (s == null || s.isBlank()) ? "-" : s; }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    // Reflection-safe list call with different signatures
    @SuppressWarnings("unchecked")
    private static List<Object> callList(Object target, String method, Object... args) {
        try {
            Method m = resolveMethod(target.getClass(), method, args);
            if (m == null) return List.of();
            Object val = m.invoke(target, args);
            if (val instanceof List<?> l) return (List<Object>) l;
            if (val instanceof Collection<?> c) return new ArrayList<>(c);
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Method resolveMethod(Class<?> cls, String name, Object... args) {
        outer:
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            var ps = m.getParameterTypes();
            if (ps.length != args.length) continue;
            for (int i = 0; i < ps.length; i++) {
                if (args[i] == null) continue;
                if (!wrap(ps[i]).isAssignableFrom(args[i].getClass())) continue outer;
            }
            return m;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        return switch (c.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "boolean" -> Boolean.class;
            default -> c;
        };
    }

    // field extractors
    private static String getStr(Object o, String... names) {
        for (String n : names) {
            String v = callString(o, n);
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String getNum(Object o, String... names) {
        for (String n : names) {
            Number v = callNumber(o, n);
            if (v != null) return trimZero(v.doubleValue());
        }
        return "0";
    }

    private static String getDate(Object o, String... names) {
        for (String n : names) {
            Object v = callAny(o, n);
            if (v == null) continue;
            if (v instanceof java.time.Instant ins) return ins.toString().substring(0, 10);
            if (v instanceof java.time.LocalDate ld) return ld.toString();
            if (v instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate().toString();
            String s = v.toString();
            if (s.length() >= 10) return s.substring(0,10);
            return s;
        }
        return "";
    }

    private static String trimZero(double d) {
        String s = Double.toString(d);
        if (s.endsWith(".0")) return s.substring(0, s.length()-2);
        return s;
    }

    private static Object callAny(Object o, String getter) {
        try {
            Method m = o.getClass().getMethod(getter);
            return m.invoke(o);
        } catch (Exception ignore) {
            // thử dạng JavaBean: getXxx()
            try {
                String g = "get" + Character.toUpperCase(getter.charAt(0)) + getter.substring(1);
                Method m = o.getClass().getMethod(g);
                return m.invoke(o);
            } catch (Exception ignored) { return null; }
        }
    }

    private static String callString(Object o, String getter) {
        Object v = callAny(o, getter);
        return v == null ? null : v.toString();
    }

    private static Number callNumber(Object o, String getter) {
        Object v = callAny(o, getter);
        if (v instanceof Number n) return n;
        try { return v == null ? null : Double.parseDouble(v.toString()); }
        catch (Exception e) { return null; }
    }
}