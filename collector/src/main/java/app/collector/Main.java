package app.collector;

import app.collector.config.CollectorConfig;
import app.collector.core.CollectorRunner;

public final class Main {
    public static void main(String[] args) throws Exception {
        CollectorConfig cfg = CollectorConfig.fromArgs(args);
        var collectors = CollectorRunner.buildCollectors(cfg);
        System.out.println("[Collector] enabled: " + collectors.stream().map(c->c.id()).toList());
        CollectorRunner.runAll(cfg, collectors);
    }
}