package collector.cli;

import collector.api.CollectorService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class CollectorCLI {
    public static void main(String[] args) throws Exception {
        Map<String,String> arg = parse(args);

        List<String> keywords = Arrays.stream(arg.getOrDefault("--keywords", "").split(","))
                .map(String::trim).filter(s->!s.isBlank()).toList();
        List<String> sources  = Arrays.stream(arg.getOrDefault("--sources", "").split(","))
                .map(String::trim).filter(s->!s.isBlank()).toList();
        String collection     = arg.getOrDefault("--collection", "default");
        Instant from          = arg.containsKey("--from") ? Instant.parse(arg.get("--from")) : null;
        Instant to            = arg.containsKey("--to")   ? Instant.parse(arg.get("--to"))   : null;
        int limit             = Integer.parseInt(arg.getOrDefault("--limit", "300"));
        Path saveDir          = Path.of(arg.getOrDefault("--saveDir", "data/collections/live"));
        String ytKeyUI        = arg.getOrDefault("--yt.apiKey", null);

        System.out.println("[Collector] keywords=" + String.join(", ", keywords)
                + " | sources=" + sources
                + " | collection=" + collection
                + " | from=" + (from==null?"N/A":from)
                + " | to="   + (to==null?"N/A":to)
                + " | limit=" + limit
                + " | saveDir=" + saveDir);

        CollectorService svc = new CollectorService();
        sources.forEach(s -> svc.addByName(s, ytKeyUI));
        svc.run(keywords, from, to, limit, collection, saveDir, ytKeyUI);
    }

    private static Map<String,String> parse(String[] args) {
        Map<String,String> m = new LinkedHashMap<>();
        String k = null;
        for (String a : args) {
            if (a.startsWith("--")) { k = a; m.put(k, ""); }
            else if (k != null)     { m.put(k, a); k = null; }
        }
        return m;
    }
}