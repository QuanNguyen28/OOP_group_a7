package app.model.service.ingest;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class QuerySpec {
    private final Set<String> keywords;
    private final Instant from;
    private final Instant to;
    private final boolean allDatasets;


    public static QuerySpec ofKeywords(Collection<String> keys) {
        return new QuerySpec(keys, null, null, true);
    }

    public static QuerySpec fromKeywords(Collection<String> keys) {
        return ofKeywords(keys);
    }


    public QuerySpec(Collection<String> keys) {
        this(keys, null, null, true);
    }


    private QuerySpec(Collection<String> keys, Instant from, Instant to, boolean allDatasets) {
        this.keywords = toSet(keys);
        this.from = from;
        this.to = to;
        this.allDatasets = allDatasets;
    }

    private static Set<String> toSet(Collection<String> keys) {
        Set<String> out = new LinkedHashSet<>();
        if (keys != null) {
            for (String k : keys) {
                if (k != null) {
                    String s = k.trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        }
        return out;
    }


    public Set<String> keywords() { return keywords; }
    public Optional<Instant> from() { return Optional.ofNullable(from); }
    public Optional<Instant> to()   { return Optional.ofNullable(to); }
    public boolean allDatasets()    { return allDatasets; }


    public QuerySpec withWindow(Instant newFrom, Instant newTo) {
        return new QuerySpec(this.keywords, newFrom, newTo, this.allDatasets);
    }

    public QuerySpec allDatasets(boolean flag) {
        return new QuerySpec(this.keywords, this.from, this.to, flag);
    }

    @Override public String toString() {
        return "QuerySpec{keywords=" + keywords + ", from=" + from + ", to=" + to + ", allDatasets=" + allDatasets + "}";
    }

    @Override public int hashCode() {
        return Objects.hash(keywords, from, to, allDatasets);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuerySpec that)) return false;
        return allDatasets == that.allDatasets
                && Objects.equals(keywords, that.keywords)
                && Objects.equals(from, that.from)
                && Objects.equals(to, that.to);
    }
}