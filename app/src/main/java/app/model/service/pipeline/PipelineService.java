package app.model.service.pipeline;

import app.model.domain.CleanPost;
import app.model.domain.RawPost;
import app.model.domain.SentimentLabel;
import app.model.domain.SentimentResult;
import app.model.repository.AnalyticsRepo;
import app.model.repository.PostsRepo;
import app.model.repository.RunsRepo;
import app.model.repository.SQLite;
import app.model.service.ingest.FileConnector;
import app.model.service.ingest.QuerySpec;
import app.model.service.ingest.SocialConnector;
import app.model.service.nlp.LocalNlpModel;
import app.model.service.nlp.NlpModel;
import app.model.service.preprocess.DefaultPreprocessService;
import app.model.service.preprocess.PreprocessService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline:
 *  1) fetch từ connectors (stream)
 *  2) preprocess (VI, không dấu)
 *  3) lọc theo keyword sau chuẩn hoá
 *  4) NLP (LocalNlpModel)
 *  5) lưu posts, sentiments
 *  6) aggregate theo ngày (overall_sentiment)
 *  7) (mới) damage/relief/trends -> ghi vào DB cho 4 bài toán
 */
public class PipelineService {

    /* ---------- Factory ---------- */

    public static PipelineService createDefault() {
        Path cwd = Path.of("").toAbsolutePath();

        // ---- Chọn DB path: ưu tiên ../data/app.db nếu đang chạy trong app/
        Path dbPathPreferred = cwd.resolveSibling("data").resolve("app.db");   // ../data/app.db
        Path dbPathAlt       = cwd.resolve("data").resolve("app.db");          // ./data/app.db
        Path dbPath = Files.exists(dbPathPreferred.getParent()) ? dbPathPreferred : dbPathAlt;
        try { Files.createDirectories(dbPath.getParent()); } catch (Exception ignore) {}

        // ---- Chọn collections root: ưu tiên ../data/collections nếu tồn tại
        Path collPreferred = cwd.resolveSibling("data").resolve("collections"); // ../data/collections
        Path collAlt       = cwd.resolve("data").resolve("collections");        // ./data/collections
        Path collectionsRoot = Files.isDirectory(collPreferred) ? collPreferred : collAlt;

        System.out.println("[Pipeline] CWD        = " + cwd);
        System.out.println("[Pipeline] DB path    = " + dbPath);
        System.out.println("[Pipeline] Collections= " + collectionsRoot);

        // SQLite của bạn nhận String path
        SQLite db = new SQLite(dbPath.toString());
        db.migrate();

        // connectors: offline drop-folder
        var connectors = List.<SocialConnector>of(new FileConnector(collectionsRoot));

        // services
        PreprocessService preprocess = new DefaultPreprocessService();
        NlpModel nlp = new LocalNlpModel();

        // repos
        PostsRepo postsRepo = new PostsRepo(db);
        AnalyticsRepo analyticsRepo = new AnalyticsRepo(db);
        RunsRepo runsRepo = new RunsRepo(db);

        return new PipelineService(db, connectors, preprocess, postsRepo, runsRepo, nlp, analyticsRepo);
    }

    /* ---------- Fields ---------- */

    private final SQLite db;
    private final List<SocialConnector> connectors;
    private final PreprocessService preprocess;
    private final PostsRepo postsRepo;
    private final RunsRepo runsRepo;
    private final NlpModel nlp;
    private final AnalyticsRepo analyticsRepo;

    public PipelineService(SQLite db,
                           List<SocialConnector> connectors,
                           PreprocessService preprocess,
                           PostsRepo postsRepo,
                           RunsRepo runsRepo,
                           NlpModel nlp,
                           AnalyticsRepo analyticsRepo) {
        this.db = db;
        this.connectors = connectors;
        this.preprocess = preprocess;
        this.postsRepo = postsRepo;
        this.runsRepo = runsRepo;
        this.nlp = nlp;
        this.analyticsRepo = analyticsRepo;
    }

    /** Cho Dashboard lấy đúng repo/DB. */
    public AnalyticsRepo analyticsRepo() { return analyticsRepo; }

    /* ---------- Result ---------- */

    public static final class RunResult {
        public final String runId;
        public final int ingested;
        public final int analyzed;
        public RunResult(String runId, int ingested, int analyzed) {
            this.runId = runId; this.ingested = ingested; this.analyzed = analyzed;
        }
    }

    /* ---------- Public API ---------- */

    public RunResult run(String rawKeyword) {
        String runId = "run_" + System.currentTimeMillis();
        Instant started = Instant.now();
        reflectStartRun(runId, rawKeyword, started);

        Set<String> keys = expandKeywords(rawKeyword);
        List<RawPost> raw = fetchFromConnectors(keys);
        System.out.println("[Pipeline] raw from connectors = " + raw.size());

        List<CleanPost> cleaned = raw.stream()
                .map(preprocess::preprocess)
                .filter(cp -> matchByKeyword(cp.textNorm(), keys))
                .toList();
        System.out.println("[Pipeline] cleaned & matched   = " + cleaned.size());

        // 1) Lưu posts
        reflectSavePosts(cleaned, runId);

        // 2) Sentiment
        List<SentimentResult> sents = cleaned.stream()
                .map(cp -> nlp.analyzeSentiment(cp.rawId(), cp.textNorm(), cp.lang(), cp.ts()))
                .toList();
        saveSentimentsDirect(sents, runId);

        // 3) Overall sentiment by day
        Map<Instant, Counts> daily = aggregateDaily(sents);
        for (var e : daily.entrySet()) {
            Instant bucket = e.getKey();
            Counts c = e.getValue();
            reflectUpsertOverall(runId, bucket, c.pos, c.neg, c.neu);
        }

        // 4) Damage & Relief (bài toán 2 & 3)
        int dmgRows = saveDamageDirect(cleaned, runId);
        int rlfRows = saveReliefDirect(cleaned, runId);
        System.out.println("[Pipeline] damage rows = " + dmgRows + " | relief rows = " + rlfRows);

        // 5) Trends (bài toán 4)
        aggregateTrends(cleaned, runId);

        reflectFinishRun(runId, started, Instant.now(), cleaned.size(), sents.size());
        return new RunResult(runId, cleaned.size(), sents.size());
    }

    public RunResult runIngest(String keyword) { return run(keyword); }

    /* ---------- Helpers ---------- */

    private List<RawPost> fetchFromConnectors(Set<String> keys) {
        if (connectors == null || connectors.isEmpty()) return List.of();
        QuerySpec spec = buildQuerySpec(keys);
        List<RawPost> all = new ArrayList<>();
        for (var c : connectors) {
            try (var stream = c.fetch(spec)) {              // stream AutoCloseable? (an toàn)
                if (stream != null) stream.forEach(all::add);
            } catch (Exception ex) {
                System.err.println("[WARN] Connector " + c.getClass().getSimpleName() + " failed: " + ex.getMessage());
            }
        }
        return all;
    }

    private Map<Instant, Counts> aggregateDaily(List<SentimentResult> sents) {
        Map<Instant, Counts> map = new HashMap<>();
        ZoneId utc = ZoneOffset.UTC;
        for (var s : sents) {
            LocalDate d = s.ts().atZone(utc).toLocalDate();
            Instant bucketStart = d.atStartOfDay(utc).toInstant();
            Counts c = map.computeIfAbsent(bucketStart, k -> new Counts());
            if (s.label() == SentimentLabel.pos) c.pos++;
            else if (s.label() == SentimentLabel.neg) c.neg++;
            else c.neu++;
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a,b) -> a, LinkedHashMap::new));
    }

    private static final class Counts { int pos, neg, neu; }

    private boolean matchByKeyword(String textNorm, Collection<String> keys) {
        if (textNorm == null || textNorm.isBlank()) return false;
        for (var k : keys) if (textNorm.contains(k)) return true;
        return false;
    }

    private Set<String> expandKeywords(String input) {
        String k = vnNormalize(input);
        Set<String> out = new LinkedHashSet<>();
        if (!k.isBlank()) {
            out.add(k);
            out.add("#" + k);
            out.add("bao " + k);
            out.add("#bao " + k);
        }
        return out;
    }

    private String vnNormalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).trim();
        String nfd = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "")
                  .replace("đ","d").replace("Đ","D")
                  .replaceAll("\\s+", " ").trim();
    }

    private QuerySpec buildQuerySpec(Set<String> keys) {
        // Ưu tiên static ofKeywords(Collection)
        try {
            Method m = QuerySpec.class.getMethod("ofKeywords", Collection.class);
            return (QuerySpec) m.invoke(null, keys);
        } catch (Throwable ignore) {}
        // static fromKeywords(Collection)
        try {
            Method m = QuerySpec.class.getMethod("fromKeywords", Collection.class);
            return (QuerySpec) m.invoke(null, keys);
        } catch (Throwable ignore) {}
        // ctor(Collection)
        try {
            Constructor<QuerySpec> c = QuerySpec.class.getDeclaredConstructor(Collection.class);
            c.setAccessible(true);
            return c.newInstance(keys);
        } catch (Throwable ignore) {}
        throw new RuntimeException("Cannot build QuerySpec from keywords.");
    }

    /* ---------- DB I/O trực tiếp cho sentiments & compat AnalyticsRepo ---------- */

    private void saveSentimentsDirect(List<SentimentResult> sents, String runId) {
        if (sents.isEmpty()) return;
        String sql = "INSERT OR REPLACE INTO sentiments (id,label,score,ts,run_id) VALUES (?,?,?,?,?)";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (var s : sents) {
                ps.setString(1, s.id());
                ps.setString(2, s.label().name());
                ps.setDouble(3, s.score());
                ps.setString(4, s.ts().toString());
                ps.setString(5, runId);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException("saveSentiments failed: " + e.getMessage(), e);
        }
    }

    private void reflectUpsertOverall(String runId, Instant bucketStart, int pos, int neg, int neu) {
        // 1) upsertOverallSentiment(String, Instant, int,int,int)
        try {
            Method m = AnalyticsRepo.class.getMethod(
                    "upsertOverallSentiment", String.class, Instant.class, int.class, int.class, int.class);
            m.invoke(analyticsRepo, runId, bucketStart, pos, neg, neu);
            return;
        } catch (NoSuchMethodException ignore) { } catch (Exception e) { throw wrap(e); }

        // 2) saveOverallSentiment(String, Instant, int,int,int)
        try {
            Method m = AnalyticsRepo.class.getMethod(
                    "saveOverallSentiment", String.class, Instant.class, int.class, int.class, int.class);
            m.invoke(analyticsRepo, runId, bucketStart, pos, neg, neu);
            return;
        } catch (NoSuchMethodException ignore) { } catch (Exception e) { throw wrap(e); }

        // 3) Dùng String time
        try {
            Method m = AnalyticsRepo.class.getMethod(
                    "upsertOverallSentiment", String.class, String.class, int.class, int.class, int.class);
            m.invoke(analyticsRepo, runId, bucketStart.toString(), pos, neg, neu);
            return;
        } catch (NoSuchMethodException ignore) { } catch (Exception e) { throw wrap(e); }

        // 4) Fallback: ghi trực tiếp
        String sql = "INSERT OR REPLACE INTO overall_sentiment(run_id,bucket_start,pos,neg,neu) VALUES (?,?,?,?,?)";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, bucketStart.toString());
            ps.setInt(3, pos);
            ps.setInt(4, neg);
            ps.setInt(5, neu);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("upsert overall_sentiment failed: " + e.getMessage(), e);
        }
    }

    private RuntimeException wrap(Exception e) {
        return new RuntimeException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }

    /* ---------- RunsRepo compat bằng reflection ---------- */

    private void reflectStartRun(String runId, String keyword, Instant started) {
        try {
            Method m = RunsRepo.class.getMethod("startRun", String.class, String.class, Instant.class);
            m.invoke(runsRepo, runId, keyword, started);
        } catch (NoSuchMethodException ignore) {
            // not mandatory
        } catch (Exception e) {
            System.err.println("[WARN] startRun failed: " + e.getMessage());
        }
    }

    private void reflectFinishRun(String runId, Instant started, Instant ended, int ingested, int analyzed) {
        try {
            Method m = RunsRepo.class.getMethod("finishRun",
                    String.class, Instant.class, Instant.class, int.class, int.class);
            m.invoke(runsRepo, runId, started, ended, ingested, analyzed);
        } catch (NoSuchMethodException ignore) {
            // not mandatory
        } catch (Exception e) {
            System.err.println("[WARN] finishRun failed: " + e.getMessage());
        }
    }

    private void reflectSavePosts(List<CleanPost> cleaned, String runId) {
        // Ưu tiên saveBatch(List<CleanPost>, String)
        try {
            Method m = PostsRepo.class.getMethod("saveBatch", List.class, String.class);
            m.invoke(postsRepo, cleaned, runId);
            return;
        } catch (NoSuchMethodException ignore) { } catch (Exception e) { throw wrap(e); }

        // Fallback: saveBatch(List<CleanPost>)
        try {
            Method m = PostsRepo.class.getMethod("saveBatch", List.class);
            m.invoke(postsRepo, cleaned);
        } catch (Exception e) {
            throw new RuntimeException("PostsRepo.saveBatch failed: " + e.getMessage(), e);
        }
    }

    /* ---------- NEW: Bài toán 2,3,4 ghi trực tiếp DB ---------- */

    private int saveDamageDirect(List<CleanPost> cleaned, String runId) {
        if (cleaned.isEmpty()) return 0;
        if (!(nlp instanceof LocalNlpModel model)) return 0;

        String sql = "INSERT INTO damage(id,type,ts,run_id) VALUES (?,?,?,?)";
        int rows = 0;
        try (var conn = db.connect(); var ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (var cp : cleaned) {
                var types = model.detectDamageTypes(cp.textNorm());
                for (String t : types) {
                    ps.setString(1, cp.rawId());
                    ps.setString(2, t);
                    ps.setString(3, cp.ts().toString());
                    ps.setString(4, runId);
                    ps.addBatch(); rows++;
                }
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            System.err.println("[WARN] saveDamageDirect: " + e.getMessage());
        }
        return rows;
    }

    private int saveReliefDirect(List<CleanPost> cleaned, String runId) {
        if (cleaned.isEmpty()) return 0;
        if (!(nlp instanceof LocalNlpModel model)) return 0;

        String sql = "INSERT INTO relief_items(id,item,ts,run_id) VALUES (?,?,?,?)";
        int rows = 0;
        try (var conn = db.connect(); var ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (var cp : cleaned) {
                var items = model.detectReliefItems(cp.textNorm());
                for (String it : items) {
                    ps.setString(1, cp.rawId());
                    ps.setString(2, it);
                    ps.setString(3, cp.ts().toString());
                    ps.setString(4, runId);
                    ps.addBatch(); rows++;
                }
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            System.err.println("[WARN] saveReliefDirect: " + e.getMessage());
        }
        return rows;
    }

    private void aggregateTrends(List<CleanPost> cleaned, String runId) {
        if (cleaned.isEmpty()) return;
        if (!(nlp instanceof LocalNlpModel model)) return;

        Map<String, Map<String,Integer>> tokenDaily = new LinkedHashMap<>();
        Map<String, Map<String,Integer>> hashtagDaily = new LinkedHashMap<>();
        var utc = ZoneOffset.UTC;

        for (var cp : cleaned) {
            String day = cp.ts().atZone(utc).toLocalDate().atStartOfDay(utc).toInstant().toString();

            for (String tok : model.tokenizeForTrends(cp.textNorm())) {
                if (tok.startsWith("#")) {
                    hashtagDaily.computeIfAbsent(day, k -> new HashMap<>()).merge(tok, 1, Integer::sum);
                } else {
                    tokenDaily.computeIfAbsent(day, k -> new HashMap<>()).merge(tok, 1, Integer::sum);
                }
            }
        }

        String sqlTok = "INSERT OR REPLACE INTO keyword_counts(run_id,bucket_start,token,cnt) VALUES (?,?,?,?)";
        String sqlHash = "INSERT OR REPLACE INTO hashtag_counts(run_id,bucket_start,tag,cnt) VALUES (?,?,?,?)";

        try (var conn = db.connect();
             var pst = conn.prepareStatement(sqlTok);
             var psh = conn.prepareStatement(sqlHash)) {
            conn.setAutoCommit(false);

            for (var e : tokenDaily.entrySet()) {
                String day = e.getKey();
                for (var t : e.getValue().entrySet()) {
                    pst.setString(1, runId);
                    pst.setString(2, day);
                    pst.setString(3, t.getKey());
                    pst.setInt(4, t.getValue());
                    pst.addBatch();
                }
            }
            for (var e : hashtagDaily.entrySet()) {
                String day = e.getKey();
                for (var t : e.getValue().entrySet()) {
                    psh.setString(1, runId);
                    psh.setString(2, day);
                    psh.setString(3, t.getKey());
                    psh.setInt(4, t.getValue());
                    psh.addBatch();
                }
            }
            pst.executeBatch();
            psh.executeBatch();
            conn.commit();
        } catch (Exception ex) {
            System.err.println("[WARN] aggregateTrends: " + ex.getMessage());
        }
    }

}