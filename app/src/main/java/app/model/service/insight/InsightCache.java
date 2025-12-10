package app.model.service.insight;

import java.util.Optional;

public interface InsightCache {
    Optional<String> get(String key);
    void put(String key, String value);
}