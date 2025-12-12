package collector.cli;

import collector.api.CollectorService;

import java.time.Instant;
import java.util.*;

public final class CollectorCLI {
    public static void main(String[] args) {
        Map<String, String> arg = parseArgs(args);

        String kwRaw = arg.getOrDefault("--keywords", "");
        List<String> keywords = Arrays.stream(kwRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Instant from       = CollectorService.parseInstant(arg.get("--from"));
        Instant to         = CollectorService.parseInstant(arg.get("--to"));
        int limit          = parseIntSafe(arg.get("--limit"), 1000);
        String collection  = arg.getOrDefault("--collection", "raw");
        List<String> sources = Arrays.stream(arg.getOrDefault("--sources", "news,youtube").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        if (keywords.isEmpty()) {
            System.err.println("[Collector] ERROR: Thiếu --keywords");
            printUsage();
            System.exit(2);
        }

        var svc = new CollectorService();
        try {
            svc.run(keywords, from, to, limit, collection, sources);
        } catch (Exception e) {
            System.err.println("[Collector] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String val = "";
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) val = args[++i];
                m.put(a, val);
            }
        }
        return m;
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return def; }
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              ./gradlew :collector:run --args="\\
                --keywords 'bão yagi,bao yagi,typhoon yagi' \\
                --sources news,youtube \\
                --collection yagi-sept-raw \\
                --from 2024-09-01T00:00:00Z \\
                --to   2024-09-30T23:59:59Z \\
                --limit 300" \\
              -Dyt.apiKey=YOUR_YT_API_KEY
            """);
    }
}