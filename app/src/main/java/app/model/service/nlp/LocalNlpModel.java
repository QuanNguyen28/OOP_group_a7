package app.model.service.nlp;

import app.model.domain.SentimentLabel;
import app.model.domain.SentimentResult;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * LocalNlpModel (rule-based, offline) cho tiếng Việt.
 *
 * - INPUT là text đã chuẩn hoá (lower-case, bỏ dấu, chuẩn hoá khoảng trắng) từ Preprocess.
 * - Đọc từ ../data/lexicons/vi (hoặc ./data/lexicons/vi nếu chạy từ root):
 *     sentiment_pos.txt, sentiment_neg.txt
 *     damage.yaml, relief.yaml
 *     stopwords.txt
 * - Hỗ trợ: unigram + phrase (ưu tiên cụm dài nhất), negation/booster/dampener, emoji/!
 * - API cho 4 bài toán:
 *     analyzeSentiment, detectDamageTypes, detectReliefItems, tokenizeForTrends
 */
public class LocalNlpModel implements NlpModel {
    private static final Pattern TOKEN = Pattern.compile("\\p{Z}+");

    // KHÔNG DẤU để khớp với textNorm()
    private static final Set<String> VI_NEGATORS = Set.of("khong","chang","cha","deo","khg","k","ko","k0","kh");
    private static final Set<String> BOOSTERS    = Set.of("rat","very","qua","too","cuc","kha","quite");
    private static final Set<String> DAMPENERS   = Set.of("hoi","slightly","abit","it","slight","a-bit");

    /** Lexicon cảm xúc */
    private final Lexicon viPos, viNeg;

    /** Taxonomy cho bài toán 2 & 3 */
    private final Map<String, List<String>> damageMap = new LinkedHashMap<>();
    private final Map<String, List<String>> reliefMap = new LinkedHashMap<>();

    /** Stopwords phục vụ trends */
    private final Set<String> stop = new HashSet<>();

    public LocalNlpModel() {
        Path lexRoot = autoDetectLexiconRoot();

        // Sentiment lexicon (có fallback nếu thiếu file)
        this.viPos = loadLexiconFS(lexRoot.resolve("sentiment_pos.txt"),
                List.of("ung ho","cam on","hy vong","tich cuc","an toan","khac phuc","ho tro","on dinh","tuyet voi"));
        this.viNeg = loadLexiconFS(lexRoot.resolve("sentiment_neg.txt"),
                List.of("nguy hiem","thiet hai","ngap sau","mat dien","gio manh","lu lut","do nat","sap cau","tieu cuc"));

        // Taxonomy yaml-like (tối giản)
        loadYamlLike(lexRoot.resolve("damage.yaml"), "damage_types", damageMap);
        loadYamlLike(lexRoot.resolve("relief.yaml"), "relief_items", reliefMap);

        // Stopwords (optional)
        loadList(lexRoot.resolve("stopwords.txt"), List.of(), stop);

        System.out.println("[LocalNlpModel] lexicon loaded: " +
                "pos{uni=" + viPos.unigrams.size() + ",phr=" + viPos.phrases.size() + "} " +
                "neg{uni=" + viNeg.unigrams.size() + ",phr=" + viNeg.phrases.size() + "} " +
                "damage=" + damageMap.size() + " relief=" + reliefMap.size() + " stop=" + stop.size() +
                " from=" + lexRoot.toAbsolutePath());
    }

    @Override public String modelId() { return "rule-local-vi-v3"; }

    /* ================= Sentiment (Task 1) ================= */

    @Override
    public SentimentResult analyzeSentiment(String id, String textNorm, String lang, Instant ts) {
        if (textNorm == null || textNorm.isBlank())
            return new SentimentResult(id, SentimentLabel.neu, 0.0, ts);

        var toks = tokenize(textNorm);

        double rawScore = lexicalScoreVI(toks);
        // booster: ! và emoji
        int exclam = countChar(textNorm, '!');
        if (exclam >= 1) rawScore *= 1.0 + Math.min(0.5, exclam * 0.1);
        rawScore += emojiDelta(textNorm);

        // squash [-1..1]
        double norm = Math.max(-1.0, Math.min(1.0, rawScore / 3.0));
        SentimentLabel label = norm > 0.05 ? SentimentLabel.pos : (norm < -0.05 ? SentimentLabel.neg : SentimentLabel.neu);
        return new SentimentResult(id, label, norm, ts);
    }

    /* ================= Damage (Task 2) ================= */

    /** Tìm loại thiệt hại xuất hiện trong câu (match phrase không dấu). */
    public List<String> detectDamageTypes(String textNorm) {
        List<String> out = new ArrayList<>();
        if (textNorm == null || textNorm.isBlank()) return out;
        for (var e : damageMap.entrySet()) {
            String type = e.getKey();
            for (String phrase : e.getValue()) {
                if (textNorm.contains(phrase)) { out.add(type); break; }
            }
        }
        return out;
    }

    /* ================= Relief items (Task 3) ================= */

    /** Tìm nhóm vật phẩm cứu trợ xuất hiện trong câu (match phrase không dấu). */
    public List<String> detectReliefItems(String textNorm) {
        List<String> out = new ArrayList<>();
        if (textNorm == null || textNorm.isBlank()) return out;
        for (var e : reliefMap.entrySet()) {
            String item = e.getKey();
            for (String phrase : e.getValue()) {
                if (textNorm.contains(phrase)) { out.add(item); break; }
            }
        }
        return out;
    }

    /* ================= Trends (Task 4) ================= */

    /** Tách token/hashtag (lọc stopwords) để đếm xu hướng. */
    public List<String> tokenizeForTrends(String textNorm) {
        if (textNorm == null) return List.of();
        String t = textNorm.replaceAll("[^a-z0-9#\\s]", " ").replaceAll("\\s+", " ").trim();
        if (t.isBlank()) return List.of();
        return Arrays.stream(t.split(" "))
                .filter(s -> !s.isBlank() && !stop.contains(s) && s.length() > 1)
                .toList();
    }

    /* ================= Nội bộ: chấm điểm lexicon ================= */

    private double lexicalScoreVI(List<String> toks) {
        int pos = 0, neg = 0;

        for (int i = 0; i < toks.size(); i++) {
            Match m = matchAt(toks, i, viPos.phrases, viNeg.phrases);
            if (m.sign == 0) {
                int sign = (viPos.unigrams.contains(toks.get(i)) ? +1 : 0) - (viNeg.unigrams.contains(toks.get(i)) ? 1 : 0);
                if (sign == 0) continue;
                m = new Match(sign, 1);
            }

            double w = 1.0;
            if (i > 0) {
                String prev = toks.get(i - 1);
                if (BOOSTERS.contains(prev)) w *= 1.25;
                if (DAMPENERS.contains(prev)) w *= 0.8;
            }
            boolean negated = false;
            for (int k = Math.max(0, i - 3); k < i; k++) {
                if (VI_NEGATORS.contains(toks.get(k))) { negated = true; break; }
            }
            double signed = (m.sign > 0 ? +1 : -1) * w * (negated ? -1 : 1);

            if (signed > 0) pos++; else neg++;
            i += (m.len - 1);
        }
        return pos - neg;
    }

    private static final class Match { final int sign; final int len; Match(int s,int l){ sign=s; len=l; } }

    private Match matchAt(List<String> toks, int i, List<List<String>> posPhrases, List<List<String>> negPhrases) {
        int bestLen = 0;
        int bestSign = 0;

        for (var p : posPhrases) {
            int L = p.size();
            if (L == 0 || i + L > toks.size()) continue;
            boolean ok = true;
            for (int k = 0; k < L; k++) if (!toks.get(i + k).equals(p.get(k))) { ok = false; break; }
            if (ok && L > bestLen) { bestLen = L; bestSign = +1; }
        }
        for (var p : negPhrases) {
            int L = p.size();
            if (L == 0 || i + L > toks.size()) continue;
            boolean ok = true;
            for (int k = 0; k < L; k++) if (!toks.get(i + k).equals(p.get(k))) { ok = false; break; }
            if (ok && L > bestLen) { bestLen = L; bestSign = -1; }
        }
        return bestLen == 0 ? new Match(0,0) : new Match(bestSign, bestLen);
    }

    /* ================= IO & helpers ================= */

    private static int countChar(String s, char c){ int n=0; for (int i=0;i<s.length();i++) if (s.charAt(i)==c) n++; return n; }
    private static double emojiDelta(String s){
        int plus = countAny(s, "🙂","😊","❤️","👍","💪","✨","🎉");
        int minus = countAny(s, "🙁","😢","😞","😡","💔","👎");
        return Math.min(1.0, plus*0.15) - Math.min(1.0, minus*0.2);
    }
    private static int countAny(String s, String... xs){ int n=0; for (var x: xs) if (s.contains(x)) n++; return n; }

    /** Tokenize KHÔNG DẤU: tách theo khoảng trắng, giữ a-z0-9 và # */
    private static List<String> tokenize(String text){
        var raw = TOKEN.split(text.trim().toLowerCase(Locale.ROOT));
        var list = new ArrayList<String>(raw.length);
        for (var w: raw){
            w = w.replaceAll("[^a-z0-9#]+","");
            if (!w.isBlank()) list.add(w);
        }
        return list;
    }

    private record Lexicon(Set<String> unigrams, List<List<String>> phrases) {}

    private Lexicon loadLexiconFS(Path file, List<String> fallbackPlain) {
        // Chuẩn hoá fallback theo cùng tokenizer
        Set<String> uni = new LinkedHashSet<>();
        List<List<String>> phr = new ArrayList<>();
        for (String line : fallbackPlain) addLineToLexicon(line, uni, phr);

        try {
            if (Files.isRegularFile(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim().toLowerCase(Locale.ROOT);
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        addLineToLexicon(line, uni, phr);
                    }
                }
            }
        } catch (Exception ignore) { /* fallback */ }

        phr.sort((a,b) -> Integer.compare(b.size(), a.size()));
        return new Lexicon(uni, phr);
    }

    private void addLineToLexicon(String line, Set<String> uni, List<List<String>> phr) {
        List<String> toks = tokenize(line);
        if (toks.isEmpty()) return;
        if (toks.size() == 1) uni.add(toks.get(0));
        else phr.add(toks);
    }

    private void loadList(Path path, List<String> fallback, Set<String> target) {
        target.clear();
        target.addAll(fallback);
        try {
            if (Files.isRegularFile(path)) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String t = line.trim().toLowerCase(Locale.ROOT);
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    target.add(t);
                }
            }
        } catch (Exception ignore) { }
    }

    private void loadYamlLike(Path file, String rootKey, Map<String, List<String>> target) {
        if (!Files.isRegularFile(file)) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean inRoot = false;
            String currentType = null;
            for (String raw : lines) {
                String s = raw.stripTrailing();
                String t = s.trim();
                if (t.isBlank() || t.startsWith("#")) continue;

                if (!inRoot) {
                    if (t.equals(rootKey + ":")) inRoot = true;
                    continue;
                }
                if (!Character.isWhitespace(s.charAt(0))) break; // ra khỏi root

                if (t.endsWith(":")) {
                    currentType = t.substring(0, t.length() - 1).trim();
                    target.putIfAbsent(currentType, new ArrayList<>());
                } else if (t.startsWith("-")) {
                    if (currentType == null) continue;
                    String phrase = t.substring(1).trim();
                    if (phrase.startsWith("\"") && phrase.endsWith("\"") && phrase.length() >= 2) {
                        phrase = phrase.substring(1, phrase.length() - 1);
                    }
                    phrase = phrase.toLowerCase(Locale.ROOT);
                    phrase = java.text.Normalizer.normalize(phrase, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}+", "")
                            .replace("đ","d").replace("Đ","D")
                            .replaceAll("\\s+"," ").trim();
                    target.get(currentType).add(phrase);
                }
            }
        } catch (Exception e) {
            System.err.println("[LocalNlpModel] loadYamlLike failed: " + e.getMessage());
        }
    }

    private Path autoDetectLexiconRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path pref = cwd.resolveSibling("data").resolve("lexicons").resolve("vi"); // ../data/lexicons/vi
        Path alt  = cwd.resolve("data").resolve("lexicons").resolve("vi");        // ./data/lexicons/vi
        return Files.isDirectory(pref) ? pref : alt;
    }
}