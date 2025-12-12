package collector.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record QuerySpec(
        List<String> keywords,
        Instant from,
        Instant to,
        int limit
) {
    // Chuẩn hoá dữ liệu ngay trong canonical constructor
    public QuerySpec {
        List<String> safe = new ArrayList<>();
        if (keywords != null) {
            for (String s : keywords) {
                if (s == null) continue;
                String t = s.trim();
                if (!t.isEmpty()) safe.add(t);
            }
        }
        // distinct giữ nguyên thứ tự
        List<String> dedup = safe.stream().distinct().toList();
        keywords = List.copyOf(dedup);

        // from/to có thể null (OK)
        if (limit <= 0) limit = 1;
    }
}