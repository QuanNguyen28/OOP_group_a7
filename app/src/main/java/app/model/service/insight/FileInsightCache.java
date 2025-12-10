package app.model.service.insight;

import java.nio.file.*; import java.security.*; import java.util.*;
import java.io.*;

public class FileInsightCache implements InsightCache {
    private final Path root;
    public FileInsightCache(Path root) { this.root = root; }

    @Override public Optional<String> get(String key) {
        try {
            Path f = fileOf(key);
            if (Files.exists(f)) return Optional.of(Files.readString(f));
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override public void put(String key, String value) {
        try {
            Path f = fileOf(key);
            Files.createDirectories(f.getParent());
            Files.writeString(f, value);
        } catch (Exception ignored) {}
    }

    private Path fileOf(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] dig = md.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hex = java.util.HexFormat.of().formatHex(dig);
        return root.resolve(hex.substring(0,2)).resolve(hex+".md");
    }
}