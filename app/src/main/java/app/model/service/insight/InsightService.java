package app.model.service.insight;

import app.model.repository.AnalyticsRepo;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class InsightService {
    private final AnalyticsRepo repo;
    private final LlmClient llm;

    public InsightService(AnalyticsRepo repo, LlmClient llm) {
        this.repo = Objects.requireNonNull(repo, "analyticsRepo");
        this.llm  = Objects.requireNonNull(llm,  "llmClient");
    }

    public String summarizeOverall(String runId, LocalDate from, LocalDate to) {
        List<OverallRow> rows = fetchOverallRows(runId, 365);
        if (rows.isEmpty()) {
            // Fallback: tổng hợp từ bảng sentiments
            rows = fetchOverallRowsFromSentiments(runId, 365);
        }
        if (rows.isEmpty()) return noData("Tổng quan cảm xúc", runId);

        rows.sort(Comparator.comparing(OverallRow::day));
        LocalDate min = rows.get(0).day();
        LocalDate max = rows.get(rows.size() - 1).day();
        LocalDate effFrom = (from == null ? min : from);
        LocalDate effTo   = (to   == null ? max : to);

        String csv = rows.stream()
                .map(r -> "%s,%d,%d,%d".formatted(r.day(), r.pos(), r.neg(), r.neu()))
                .collect(Collectors.joining("\n"));

        long pos = rows.stream().mapToLong(OverallRow::pos).sum();
        long neg = rows.stream().mapToLong(OverallRow::neg).sum();
        long neu = rows.stream().mapToLong(OverallRow::neu).sum();

        String prompt = Templates.OVERALL_VI.formatted(runId, effFrom, effTo, pos, neg, neu, csv);
        return safeComplete(prompt, "Tổng quan cảm xúc");
    }

    public String summarizeDamage(String runId) {
        String csv = fetchSimpleCsv(runId, "readDamageCounts", 20,
                new String[]{"tag","label","type","category"},
                new String[]{"cnt","count","value"});
        if (csv.isBlank()) return noData("Thiệt hại phổ biến", runId);
        String prompt = Templates.DAMAGE_VI.formatted(runId, csv);
        return safeComplete(prompt, "Thiệt hại phổ biến");
    }

    public String summarizeRelief(String runId) {
        String csv = fetchSimpleCsv(runId, "readReliefCounts", 20,
                new String[]{"tag","label","item","category"},
                new String[]{"cnt","count","value"});
        if (csv.isBlank()) return noData("Hạng mục cứu trợ", runId);
        String prompt = Templates.RELIEF_VI.formatted(runId, csv);
        return safeComplete(prompt, "Hạng mục cứu trợ");
    }

    public String summarizeTask3(String runId) {
        String csv = fetchTask3Csv(runId, 50);
        if (csv.isBlank()) return noData("Bài toán 3 (hài lòng/không hài lòng)", runId);
        String prompt = Templates.TASK3_VI.formatted(runId, csv);
        return safeComplete(prompt, "Bài toán 3");
    }

    public String summarizeTask4(String runId, LocalDate from, LocalDate to) {
        String csv = fetchTask4Csv(runId, from, to, 200);
        if (csv.isBlank()) return noData("Bài toán 4 (diễn tiến theo thời gian)", runId);
        LocalDate[] eff = deriveRangeFromTask4(csv, from, to);
        String prompt = Templates.TASK4_VI.formatted(runId, eff[0], eff[1], csv);
        return safeComplete(prompt, "Bài toán 4");
    }

    // ---------------- Internal ----------------

    private record OverallRow(LocalDate day, int pos, int neg, int neu) {}

    private List<OverallRow> fetchOverallRows(String runId, int limit) {
        List<Object> raw = callList(repo, "readOverallRows", runId);
        if (raw.isEmpty()) return List.of();
        var out = new ArrayList<OverallRow>();
        for (Object r : raw) {
            LocalDate d = parseLocalDate(getAny(r, "bucketStart","day","date"));
            int pos = parseInt(getAny(r, "pos","positive","posCount"));
            int neg = parseInt(getAny(r, "neg","negative","negCount"));
            int neu = parseInt(getAny(r, "neu","neutral","neuCount"));
            if (d != null) out.add(new OverallRow(d, pos, neg, neu));
        }
        return out.stream().limit(limit).collect(Collectors.toList());
    }

    private List<OverallRow> fetchOverallRowsFromSentiments(String runId, int limit) {
        List<Object> raw = callList(repo, "readOverallFromSentiments", runId);
        if (raw.isEmpty()) return List.of();
        var out = new ArrayList<OverallRow>();
        for (Object r : raw) {
            LocalDate d = parseLocalDate(getAny(r, "day","bucketStart","date"));
            int pos = parseInt(getAny(r, "pos"));
            int neg = parseInt(getAny(r, "neg"));
            int neu = parseInt(getAny(r, "neu"));
            if (d != null) out.add(new OverallRow(d, pos, neg, neu));
        }
        return out.stream().limit(limit).collect(Collectors.toList());
    }

    private String fetchSimpleCsv(String runId, String method, int limit, String[] nameKeys, String[] countKeys) {
        List<Object> rows = callList(repo, method, runId);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String name = str(getAny(r, nameKeys));
            int    cnt  = parseInt(getAny(r, countKeys));
            return "%s,%d".formatted(name, cnt);
        }).collect(Collectors.joining("\n"));
    }

    private String fetchTask3Csv(String runId, int limit) {
        List<Object> rows = callList(repo, "readReliefCategorySentiment", runId);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            String cat = str(getAny(r, "category","item","tag","label"));
            int pos = parseInt(getAny(r, "pos","positive","posCount"));
            int neg = parseInt(getAny(r, "neg","negative","negCount"));
            int net = parseInt(getAny(r, "net","total","totalScore","score"));
            return "%s,%d,%d,%d".formatted(cat, pos, neg, net);
        }).collect(Collectors.joining("\n"));
    }

    private String fetchTask4Csv(String runId, LocalDate from, LocalDate to, int limit) {
        List<Object> rows = callList(repo, "readReliefDaily", runId, from, to);
        if (rows.isEmpty()) rows = callList(repo, "readReliefDaily", runId);
        if (rows.isEmpty()) rows = callList(repo, "readTask4Daily", runId, from, to);
        if (rows.isEmpty()) return "";
        return rows.stream().limit(limit).map(r -> {
            LocalDate day = parseLocalDate(getAny(r, "day","date","bucketStart"));
            String cat = str(getAny(r, "category","item","tag","label"));
            int pos = parseInt(getAny(r, "pos","positive","posCount"));
            int neg = parseInt(getAny(r, "neg","negative","negCount"));
            int net = parseInt(getAny(r, "net","total","totalScore","score"));
            return "%s,%s,%d,%d,%d".formatted(day == null ? "" : day, cat, pos, neg, net);
        }).collect(Collectors.joining("\n"));
    }

    private LocalDate[] deriveRangeFromTask4(String csv, LocalDate from, LocalDate to) {
        if (from != null && to != null) return new LocalDate[]{from, to};
        LocalDate min = null, max = null;
        for (String line : csv.split("\n")) {
            if (line.isBlank()) continue;
            String d = line.split(",", 2)[0].trim();
            LocalDate ld = tryParseDate(d);
            if (ld == null) continue;
            if (min == null || ld.isBefore(min)) min = ld;
            if (max == null || ld.isAfter(max))  max = ld;
        }
        return new LocalDate[]{from == null ? (min == null ? LocalDate.now() : min) : from,
                               to   == null ? (max == null ? LocalDate.now() : max) : to};
    }

    private String safeComplete(String prompt, String title) {
        try {
            String out = llm.complete(prompt);
            if (out != null && !out.isBlank()) return out;
        } catch (Exception ignored) {}
        return "[LocalEcho:local-echo]\n" + title + " — Tự động tóm tắt cục bộ (fallback)."
                + "\n\n(Prompt + dữ liệu — rút gọn)\n"
                + truncate(prompt, 1400);
    }

    private static String noData(String title, String runId) {
        return "Không có dữ liệu cho mục: " + title + " (RUN_ID=" + runId + "). "
             + "Hãy chạy Ingest/Analyze trước hoặc chọn run khác.";
    }

    // ---------- reflection & utils ----------
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

    private static Object getAny(Object o, String... getters) {
        for (String g : getters) {
            Object v = callAny(o, g);
            if (v != null) return v;
        }
        return null;
    }

    private static Object callAny(Object o, String getter) {
        try {
            Method m = o.getClass().getMethod(getter);
            return m.invoke(o);
        } catch (Exception ignore) {
            try {
                String g = "get" + Character.toUpperCase(getter.charAt(0)) + getter.substring(1);
                Method m = o.getClass().getMethod(g);
                return m.invoke(o);
            } catch (Exception ignored) { return null; }
        }
    }

    private static int parseInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private static LocalDate parseLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.LocalDate d) return d;
        if (v instanceof java.time.LocalDateTime dt) return dt.toLocalDate();
        if (v instanceof java.time.Instant ins) return java.time.LocalDateTime.ofInstant(ins, java.time.ZoneOffset.UTC).toLocalDate();
        return tryParseDate(v.toString());
    }

    private static LocalDate tryParseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.substring(0, Math.min(10, s.length()))); } catch (Exception e) { return null; }
    }

    private static String str(Object o) { return o == null ? "N/A" : o.toString(); }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}