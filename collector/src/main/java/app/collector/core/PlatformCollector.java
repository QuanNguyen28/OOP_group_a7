package app.collector.core;

import app.collector.config.CollectorConfig;
import app.collector.sink.RecordSink;

public interface PlatformCollector {
    void collect(CollectorConfig cfg, RecordSink sink) throws Exception;
    String id();
}