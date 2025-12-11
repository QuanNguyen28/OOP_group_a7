package app.collector.sink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class DatasetYaml {
    public static void write(Path yaml, String dataFileName, List<String> keywords) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(yaml, StandardCharsets.UTF_8)) {
            w.write("version: 1\n");
            w.write("name: " + dataFileName.replace('"','-') + "\n");
            w.write("files:\n");
            w.write("  - path: " + dataFileName + "\n");
            w.write("    format: jsonl\n");
            w.write("keywords:\n");
            for (String k : keywords) {
                w.write("  - \"" + k.replace("\"","\\\"") + "\"\n");
            }
        }
    }
}