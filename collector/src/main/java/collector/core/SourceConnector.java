package collector.core;

import java.util.stream.Stream;

public interface SourceConnector {
    String id();

    /** Tên hiển thị ngắn gọn của connector (ví dụ: "YouTube Data API v3"). */
    String name();

    /** Mô tả dài (tuỳ chọn). Mặc định dùng name() để không phá vỡ code cũ. */
    default String desc() { return name(); }

    Stream<RawPost> fetch(QuerySpec spec) throws Exception;
}