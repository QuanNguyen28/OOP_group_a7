package app.collector.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    public static String toIsoInstant(Instant t){ return DateTimeFormatter.ISO_INSTANT.format(t); }
    public static String yearMonth(Instant t){ return t.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM")); }
}