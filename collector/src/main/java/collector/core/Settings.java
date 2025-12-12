// collector/src/main/java/collector/core/Settings.java
package collector.core;

import java.io.InputStream;
import java.util.Properties;

public final class Settings {
    private Settings() {}

    public static Properties load(String... resourceNames) {
        Properties p = new Properties();
        for (String n : resourceNames) {
            try (InputStream in = Settings.class.getClassLoader().getResourceAsStream(n)) {
                if (in != null) p.load(in);
            } catch (Exception ignore) {}
        }
        return p;
    }
}