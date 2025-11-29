package app.model.service.preprocess;

import app.model.domain.CleanPost;
import app.model.domain.RawPost;

import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class DefaultPreprocessService implements PreprocessService {

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((https?|ftp)://|www\\.)\\S+"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b"
    );
    private static final Pattern MENTION = Pattern.compile("@[\\p{L}0-9_]+");
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}0-9_]+)");
    private static final Pattern MULTI_WS = Pattern.compile("\\s+");
    private static final Pattern REPEAT_LETTERS = Pattern.compile("([\\p{L}])\\1{2,}");
    private static final Pattern REPEAT_PUNCT = Pattern.compile("([!?])\\1+");

    @Override
    public CleanPost preprocess(RawPost raw) {
        String rawText = Optional.ofNullable(raw.text()).orElse("");
        Map<String, Object> meta = Optional.ofNullable(raw.meta()).orElse(Map.of());

        String cleaned = rawText;
        cleaned = URL.matcher(cleaned).replaceAll(" ");        
        cleaned = EMAIL.matcher(cleaned).replaceAll(" ");       
        cleaned = MENTION.matcher(cleaned).replaceAll(" ");      
        cleaned = HASHTAG.matcher(cleaned).replaceAll("$1");    
        cleaned = cleaned.toLowerCase(Locale.ROOT);               
        cleaned = stripDiacritics(cleaned);                       
        cleaned = REPEAT_LETTERS.matcher(cleaned).replaceAll("$1$1"); 
        cleaned = REPEAT_PUNCT.matcher(cleaned).replaceAll("$1");    
        cleaned = cleaned.replace('\u00A0', ' ');                 
        cleaned = MULTI_WS.matcher(cleaned).replaceAll(" ").trim();

        String lang = "vi";

        String createdAt = firstNonBlank(raw.createdAt(),
                                         Objects.toString(meta.get("createdAt"), null),
                                         Objects.toString(meta.get("ts"), null));
        Instant ts = parseInstantSafe(createdAt).orElseGet(Instant::now);

        String geo = firstNonBlank(raw.userLoc(),
                                   Objects.toString(meta.get("geo"), null),
                                   Objects.toString(meta.get("location"), null));

        return new CleanPost(
                raw.id(),        
                cleaned,         
                lang,           
                ts,              
                Optional.ofNullable(geo).filter(s -> !s.isBlank()) 
        );
    }

    private static String stripDiacritics(String s) {
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "")
                  .replace("đ", "d")
                  .replace("Đ", "D");
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String x : xs) {
            if (x != null && !x.isBlank()) return x;
        }
        return null;
    }

    private static Optional<Instant> parseInstantSafe(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        String x = s.trim();

        if (x.matches("^\\d{13}$")) { 
            try { return Optional.of(Instant.ofEpochMilli(Long.parseLong(x))); }
            catch (NumberFormatException ignore) {}
        }
        if (x.matches("^\\d{10}$")) { 
            try { return Optional.of(Instant.ofEpochSecond(Long.parseLong(x))); }
            catch (NumberFormatException ignore) {}
        }

        try { return Optional.of(Instant.parse(x)); }
        catch (DateTimeParseException ignore) {}

        try {
            var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            var ldt = LocalDateTime.parse(x, fmt);
            return Optional.of(ldt.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignore) {}

        try {
            var fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            var ldt = LocalDateTime.parse(x, fmt);
            return Optional.of(ldt.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignore) {}

        return Optional.empty();
    }
}