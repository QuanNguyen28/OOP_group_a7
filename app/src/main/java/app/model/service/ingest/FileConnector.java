package app.model.service.ingest;

import app.model.domain.RawPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * FileConnector: đọc dữ liệu offline theo mô hình "drop-folder".
 * Có debug chi tiết để soi pipeline.
 */
public class FileConnector implements SocialConnector {

    private static final boolean DEBUG = true;

    private final Path collectionsRoot;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileConnector(Path collectionsRoot) {
        this.collectionsRoot = collectionsRoot;
    }

    @Override public String id() { return "file"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) {
        if (collectionsRoot == null || !Files.isDirectory(collectionsRoot)) {
            System.err.println("[FileConnector] collectionsRoot not found: " + collectionsRoot);
            return Stream.empty();
        }

        final Set<String> kws = spec.keywords(); // keys đã normalize từ Pipeline
        final List<RawPost> out = new ArrayList<>();

        final AtomicInteger fileCnt   = new AtomicInteger();
        final AtomicInteger lineCnt   = new AtomicInteger();
        final AtomicInteger parsedCnt = new AtomicInteger();
        final AtomicInteger timeOkCnt = new AtomicInteger();
        final AtomicInteger kwOkCnt   = new AtomicInteger();

        try (Stream<Path> files = Files.walk(collectionsRoot, Integer.MAX_VALUE)) {
            List<Path> jsonlFiles = files
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jsonl"))
                    .toList();

            fileCnt.addAndGet(jsonlFiles.size());

            for (Path f : jsonlFiles) {
                if (DEBUG) System.out.println("[FileConnector] reading " + f);

                try (Stream<String> lines = readJsonlLinesSafely(f)) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        lineCnt.incrementAndGet();

                        RawPost rp = parseJsonSafely(line);
                        if (rp == null) continue;
                        parsedCnt.incrementAndGet();

                        // Lọc time
                        boolean timeOk = withinTime(parseInstantSafe(rp.createdAt()), spec.from(), spec.to());
                        if (!timeOk) continue;
                        timeOkCnt.incrementAndGet();

                        // Lọc keyword (nếu có)
                        boolean kwOk = (kws == null || kws.isEmpty())
                                || matchByKeyword(normalizeVi(rp.text()), kws);
                        if (!kwOk) continue;
                        kwOkCnt.incrementAndGet();

                        out.add(rp);
                    }
                } catch (Exception e) {
                    System.err.println("[FileConnector] read lines failed: " + f + " | " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[FileConnector] walk failed: " + e.getMessage());
        }

        if (DEBUG) {
            System.out.println("[FileConnector] summary " +
                    "| files=" + fileCnt.get() +
                    " | lines=" + lineCnt.get() +
                    " | parsed=" + parsedCnt.get() +
                    " | timeOK=" + timeOkCnt.get() +
                    " | kwOK=" + kwOkCnt.get() +
                    " | result=" + out.size());
            if (kws != null) System.out.println("[FileConnector] keywords=" + kws);
            System.out.println("[FileConnector] root=" + collectionsRoot.toAbsolutePath());
        }

        return out.stream();
    }

    /* ========================== Helpers / parsing ========================== */

    private Stream<String> readJsonlLinesSafely(Path file) throws IOException {
        BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        return br.lines()
                 .filter(s -> s != null && !s.isBlank())
                 .onClose(() -> { try { br.close(); } catch (IOException ignore) {} });
    }

    private RawPost parseJsonSafely(String line) {
        try {
            JsonNode n = mapper.readTree(line);

            String id        = stringOr(nodeGet(n, "id"), uuid());
            String text      = stringOr(nodeGet(n, "text"), "");
            String createdAt = stringOr(nodeGet(n, "createdAt"), null);

            String platform  = stringOr(nodeGet(n, "platform"), null);
            String lang      = stringOr(nodeGet(n, "lang"), null);
            String userLoc   = stringOr(nodeGet(n, "userLoc"), null);

            Map<String, Object> meta = new LinkedHashMap<>();
            putIfPresent(meta, "type",     nodeGet(n, "type"));
            putIfPresent(meta, "threadId", nodeGet(n, "threadId"));
            putIfPresent(meta, "replyTo",  nodeGet(n, "replyTo"));
            if (n.has("hashtags") && n.get("hashtags").isArray()) {
                List<String> tags = new ArrayList<>();
                for (JsonNode t : n.get("hashtags")) {
                    if (t != null && !t.isNull()) tags.add(t.asText());
                }
                meta.put("hashtags", tags);
            }

            return new RawPost(id, platform, text, lang, createdAt, userLoc, meta);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nodeGet(JsonNode n, String field) {
        if (n == null || field == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s != null && !s.isBlank()) ? s : null;
    }

    private static void putIfPresent(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    private static String stringOr(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String uuid() {
        return "gen_" + UUID.randomUUID();
    }

    /* ========================== Keyword & time helpers ========================== */

    private static boolean matchByKeyword(String textNorm, Collection<String> keys) {
        if (textNorm == null || textNorm.isBlank()) return false;
        for (String k : keys) if (textNorm.contains(k)) return true;
        return false;
    }

    private static String normalizeVi(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).trim();
        String nfd = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "")
                  .replace("đ", "d").replace("Đ", "D")
                  .replaceAll("[^a-z0-9#\\s]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private static boolean withinTime(Optional<Instant> tsOpt,
                                      Optional<Instant> fromOpt,
                                      Optional<Instant> toOpt) {
        if (tsOpt.isEmpty()) return true;
        Instant ts = tsOpt.get();
        boolean geFrom = fromOpt.map(f -> !ts.isBefore(f)).orElse(true);
        boolean leTo   = toOpt.map(t -> !ts.isAfter(t)).orElse(true);
        return geFrom && leTo;
    }

    private static Optional<Instant> parseInstantSafe(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        String x = s.trim();

        if (x.matches("^\\d{13}$")) {
            try { return Optional.of(Instant.ofEpochMilli(Long.parseLong(x))); }
            catch (NumberFormatException ignore) {}
        }
        if (x.matches("^\\d{10}$")) {
            try { return Optional.of(Instant.ofEpochSecond(Long.parseLong(x))); }
            catch (NumberFormatException ignore) {}
        }
        try { return Optional.of(Instant.parse(x)); }
        catch (DateTimeParseException ignore) {}

        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime ldt = LocalDateTime.parse(x, fmt);
            return Optional.of(ldt.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignore) {}

        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime ldt = LocalDateTime.parse(x, fmt);
            return Optional.of(ldt.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignore) {}

        return Optional.empty();
    }
}