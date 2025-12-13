package app.model.service.insight;

import java.nio.file.Path;

public final class InsightConfig {

    public record Settings(
            String provider,   
            String model,    
            String project,    
            String location,   
            String saKeyFile   
    ) {}

    public static final Settings ACTIVE = new Settings(
            "vertex",
            "gemini-2.5-flash",
            "gen-lang-client-0294487240",
            "us-central1",
            "../app/config/sa.json"
            
    );

    public static final Settings LOCAL_ECHO = new Settings(
            "local", "local-echo", null, null, null
    );

    private InsightConfig() {}
}