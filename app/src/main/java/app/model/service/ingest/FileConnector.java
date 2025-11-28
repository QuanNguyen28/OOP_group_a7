package app.model.service.ingest;

import app.model.domain.RawPost;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class FileConnector implements SocialConnector {
    private final Path dir;
    private final ObjectMapper om = new ObjectMapper();
    public FileConnector(Path dir){ this.dir = dir; }

    @Override public String id(){ return "file"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) {
        try {
            if (!Files.isDirectory(dir)) return Stream.empty();  // <-- nếu thiếu thư mục, trả stream rỗng
            var files = Files.list(dir).filter(p -> p.toString().endsWith(".jsonl")).toList();
            var normKeywords = spec.keywords().stream()
                    .map(FileConnector::normalize).filter(s -> !s.isBlank()).distinct().toList();

            return files.stream()
                    .flatMap(this::streamJsonl)
                    .filter(p -> withinTime(p.createdAt(), spec.from(), spec.to()))
                    .filter(p -> matchesKeywords(p, normKeywords));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<RawPost> streamJsonl(Path file) {
        try {
            BufferedReader br = Files.newBufferedReader(file);
            return br.lines().map(this::parse).onClose(() -> { try { br.close(); } catch (IOException ignored) {}});
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private RawPost parse(String json){
        try {
            @SuppressWarnings("unchecked")
            Map<String,Object> m = om.readValue(json, Map.class);

            String id = java.util.Objects.toString(m.getOrDefault("id", java.util.UUID.randomUUID().toString()));
            String platform = java.util.Objects.toString(m.getOrDefault("platform","file"));
            String text = java.util.Objects.toString(m.getOrDefault("text",""));
            String lang = java.util.Objects.toString(m.getOrDefault("lang","und"));

            String createdAt = java.util.Objects.toString(
                    m.getOrDefault("createdAt", m.getOrDefault("ts", "1970-01-01T00:00:00Z"))
            );

            String userLoc = (String) m.getOrDefault("userLoc", null);

            return new RawPost(id, platform, text, lang, createdAt, userLoc, m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean withinTime(String createdAt, Instant from, Instant to) {
        try {
            var t = Instant.parse(createdAt);
            return !t.isBefore(from) && !t.isAfter(to);
        } catch (Exception e) { return false; }
    }

    private static boolean matchesKeywords(RawPost p, List<String> normKeywords) {
        if (normKeywords.isEmpty()) return true;
        String combined = normalize(p.text());
        Object hs = p.meta()!=null ? p.meta().get("hashtags") : null;
        if (hs instanceof List<?> list) combined += " " + normalize(String.join(" ", list.stream().map(Object::toString).toList()));
        for (String kw : normKeywords) if (combined.contains(kw)) return true;
        return false;
    }

    private static String normalize(String s){
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+","");
        return n.toLowerCase(Locale.ROOT);
    }
}