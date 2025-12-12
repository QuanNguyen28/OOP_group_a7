package collector.api;

import collector.core.RawPost;
import java.util.stream.Stream;

public interface SourceConnector extends AutoCloseable {
    String id();
    Stream<RawPost> fetch(QuerySpec spec) throws Exception;
    default void close() throws Exception {}
}