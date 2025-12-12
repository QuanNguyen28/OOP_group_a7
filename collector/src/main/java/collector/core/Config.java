package collector.core;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class Config {
    private static volatile Properties cached;

    private Config() {}

    private static Properties props() {
        if (cached != null) return cached;
        Properties p = new Properties();
        try {
            // Tìm theo thứ tự: projectRoot/collector/conf.properties -> collector/conf.properties (cwd) -> app/collector/conf.properties -> classpath
            Path[] candidates = new Path[] {
                Paths.get("collector", "conf.properties"),
                Paths.get("conf.properties"),
                Paths.get("app", "collector", "conf.properties")
            };
            boolean loaded = false;
            for (Path path : candidates) {
                if (Files.exists(path)) {
                    try (InputStream in = Files.newInputStream(path)) {
                        p.load(in);
                        System.out.println("[Config] loaded " + path.toAbsolutePath());
                        loaded = true;
                        break;
                    }
                }
            }
            if (!loaded) {
                try (InputStream in = Config.class.getClassLoader().getResourceAsStream("collector/conf.properties")) {
                    if (in != null) {
                        p.load(in);
                        System.out.println("[Config] loaded classpath: collector/conf.properties");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[Config] WARN: cannot load conf.properties: " + e.getMessage());
        }
        cached = p;
        return cached;
    }

    public static String fileValue(String key) {
        String v = props().getProperty(key);
        return isBlank(v) ? null : v.trim();
    }

    /** Ưu tiên: file -> UI -> -D -> ENV */
    public static String firstFileThen(String key, String uiOverride, String sysProp, String envName) {
        String v = fileValue(key);
        if (!isBlank(v)) { System.out.println("[Config] " + key + " from file"); return v; }
        if (!isBlank(uiOverride)) { System.out.println("[Config] " + key + " from UI"); return uiOverride.trim(); }
        v = System.getProperty(sysProp);
        if (!isBlank(v)) { System.out.println("[Config] " + key + " from -D" + sysProp); return v.trim(); }
        v = System.getenv(envName);
        if (!isBlank(v)) { System.out.println("[Config] " + key + " from env " + envName); return v.trim(); }
        return null;
    }

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}