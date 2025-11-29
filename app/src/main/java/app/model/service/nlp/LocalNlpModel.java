package app.model.service.nlp;

import app.model.domain.SentimentLabel;
import app.model.domain.SentimentResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class LocalNlpModel implements NlpModel {
    private static final Pattern TOKEN = Pattern.compile("\\p{Z}+");

    // Negation & modifiers (không dấu)
    private static final Set<String> VI_NEGATORS = Set.of("khong","chang","chẳng","chả","cha","deo","khg","k","ko","k0","kh");
    private static final Set<String> BOOSTERS = Set.of("rat","rất","very","qua","too","cuc","cực","kha","quite");
    private static final Set<String> DAMPENERS = Set.of("hoi","slightly","a-bit","abit","it","slight");

    private final Lexicon viPos, viNeg;

    public LocalNlpModel() {
        this.viPos = loadLexicon("lexicon/vi_pos.txt",
                List.of("tot","tuyet","vui","ung ho","cam on","giup","ho tro","co ich","an toan"));
        this.viNeg = loadLexicon("lexicon/vi_neg.txt",
                List.of("te","toi","tuc","khong tot","xau","ngap","ngap lut","thiet hai","nguy hiem"));
    }

    @Override public String modelId() { return "rule-local-vi-v1"; }

    // lang được giữ cho tương thích, nhưng bỏ qua (chỉ dùng VI)
    @Override
    public SentimentResult analyzeSentiment(String id, String text, String lang, Instant ts) {
        if (text == null || text.isBlank())
            return new SentimentResult(id, SentimentLabel.neu, 0.0, ts);

        var toks = tokenize(text);
        double score = lexicalScoreVI(toks);

        // booster cảm xúc
        int exclam = countChar(text, '!');
        if (exclam >= 1) score *= 1.0 + Math.min(0.5, exclam * 0.1);
        score += emojiDelta(text);

        // squash về [-1..1]
        double norm = Math.max(-1.0, Math.min(1.0, score / 3.0));
        SentimentLabel label = norm > 0.05 ? SentimentLabel.pos : (norm < -0.05 ? SentimentLabel.neg : SentimentLabel.neu);
        return new SentimentResult(id, label, norm, ts);
    }

    private double lexicalScoreVI(List<String> toks) {
        int pos=0, neg=0;
        for (int i=0;i<toks.size();i++) {
            String t = toks.get(i);
            int hit = (viPos.contains(t)?1:0) - (viNeg.contains(t)?1:0);
            if (hit==0) continue;

            double w = 1.0;
            if (i>0) {
                String prev = toks.get(i-1);
                if (BOOSTERS.contains(prev)) w*=1.25;
                if (DAMPENERS.contains(prev)) w*=0.8;
            }
            boolean negated = false;
            for (int k=Math.max(0,i-3); k<i; k++) if (VI_NEGATORS.contains(toks.get(k))) { negated=true; break; }
            double signed = (hit>0?+1:-1)*w*(negated?-1:1);

            if (signed>0) pos++; else neg++;
        }
        return pos-neg;
    }

    private static int countChar(String s, char c){ int n=0; for (int i=0;i<s.length();i++) if (s.charAt(i)==c) n++; return n; }
    private static double emojiDelta(String s){
        int plus = countAny(s, "🙂","😊","❤️","👍","💪","✨","🎉");
        int minus = countAny(s, "🙁","😢","😞","😡","💔","👎");
        return Math.min(1.0, plus*0.15) - Math.min(1.0, minus*0.2);
    }
    private static int countAny(String s, String... xs){ int n=0; for (var x: xs) if (s.contains(x)) n++; return n; }

    private static List<String> tokenize(String text){
        var raw = TOKEN.split(text.trim().toLowerCase(Locale.ROOT));
        var list = new ArrayList<String>(raw.length);
        for (var w: raw){ w = w.replaceAll("[^\\p{L}\\p{Nd}#]+",""); if (!w.isBlank()) list.add(w); }
        return list;
    }

    private record Lexicon(Set<String> unigrams){ boolean contains(String w){ return unigrams.contains(w); } }
    private static Lexicon loadLexicon(String cp, List<String> fallback){
        try (var is = LocalNlpModel.class.getClassLoader().getResourceAsStream(cp)){
            if (is==null) return new Lexicon(new HashSet<>(fallback));
            var txt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            var set = new HashSet<String>();
            for (String line : txt.split("\\R")){
                line = line.trim().toLowerCase(Locale.ROOT);
                if (line.isEmpty() || line.startsWith("#")) continue;
                set.add(line);
            }
            return new Lexicon(set);
        } catch (Exception e){ return new Lexicon(new HashSet<>(fallback)); }
    }
}