package collector.core;

import com.google.gson.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Ghi JSONL an toàn với JDK21: đăng ký TypeAdapter cho java.time.Instant */
public final class JsonlWriter implements AutoCloseable {
    private final Path outfile;
    private final BufferedWriter writer;

    // Gson cấu hình: Instant -> ISO-8601
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, t, ctx) ->
                    src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, t, ctx) ->
                    json == null || json.isJsonNull() ? null : Instant.parse(json.getAsString()))
            .create();

    public JsonlWriter(Path outfile) throws IOException {
        this.outfile = outfile;
        Files.createDirectories(outfile.getParent());
        this.writer = Files.newBufferedWriter(outfile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void write(RawPost p) throws IOException {
        String json = GSON.toJson(p);
        writer.write(json);
        writer.newLine();
    }

    public void writeAll(Iterable<RawPost> posts) throws IOException {
        for (RawPost p : posts) write(p);
    }

    public Path path() { return outfile; }

    @Override public void close() throws IOException { writer.close(); }

    /* -------- Giữ API cũ: tiện ích tĩnh -------- */
    public static void write(Path outfile, java.util.List<RawPost> posts) throws IOException {
        try (JsonlWriter jw = new JsonlWriter(outfile)) {
            jw.writeAll(posts);
        }
    }
}