package collector.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Util {
    private Util() {}

    public static String loadConf(String key) {
        Path p1 = Paths.get("collector/conf.properties");
        Path p2 = Paths.get("conf.properties");
        String v = loadFromFile(p1, key);
        if (v != null) return v;
        v = loadFromFile(p2, key);
        return v;
    }

    private static String loadFromFile(Path path, String key) {
        if (!Files.exists(path)) return null;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            String v = props.getProperty(key);
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (IOException e) {
            return null;
        }
    }
}