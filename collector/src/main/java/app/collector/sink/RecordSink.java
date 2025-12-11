package app.collector.sink;

import app.collector.model.RawRecord;

public interface RecordSink {
    void accept(RawRecord r) throws Exception;
}