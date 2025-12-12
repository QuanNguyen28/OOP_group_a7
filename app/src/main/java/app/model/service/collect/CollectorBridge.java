// app/src/main/java/app/model/service/collect/CollectorBridge.java
package app.model.service.collect;

import collector.api.CollectorService;

import java.time.LocalDate;
import java.util.List;

public class CollectorBridge {

    private final CollectorService service = new CollectorService();

    public CollectorService.Result collect(String collection,
                                           List<String> keywords,
                                           LocalDate from, LocalDate to,
                                           int limitPerSource,
                                           List<String> sources) {
        try {
            return service.collect(collection, keywords, from, to, limitPerSource, sources);
        } catch (Exception e) {
            throw new RuntimeException("Collector failed: " + e.getMessage(), e);
        }
    }
}