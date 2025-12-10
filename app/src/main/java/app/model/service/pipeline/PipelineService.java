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
import java.util.stream.Stream;

/**
 * Pipeline:
 *  1) fetch từ connectors (Stream)
 *  2) preprocess (chuẩn hoá VI, không dấu)
 *  3) lọc theo keyword sau chuẩn hoá
 *  4) NLP (LocalNlpModel)
 *  5) lưu posts, sentiments
 *  6) aggregate theo ngày (overall_sentiment)
 *  7) Damage/Relief + Task 3 & 4 (ghi trực tiếp DB)
 */
public class PipelineService {

    /* ---------- Factory ---------- */

    public static PipelineService createDefault() {
        Path cwd = Path.of("").toAbsolutePath();

        // ---- DB path: ưu tiên ../data/app.db nếu đang chạy trong app/
        Path dbPathPreferred = cwd.resolveSibling("data").resolve("app.db");   // ../data/app.db
        Path dbPathAlt       = cwd.resolve("data").resolve("app.db");          // ./data/app.db
        Path dbPath = Files.exists(dbPathPreferred.getParent()) ? dbPathPreferred : dbPathAlt;
        try { Files.createDirectories(dbPath.getParent()); } catch (Exception ignore) {}

        // ---- collections root: ưu tiên ../data/collections
        Path collPreferred = cwd.resolveSibling("data").resolve("collections"); // ../data/collections
        Path collAlt       = cwd.resolve("data").resolve("collections");        // ./data/collections
        Path collectionsRoot = Files.isDirectory(collPreferred) ? collPreferred : collAlt;

        System.out.println("[Pipeline] CWD        = " + cwd);
        System.out.println("[Pipeline] DB path    = " + dbPath);
        System.out.println("[Pipeline] Collections= " + collectionsRoot);

        // DB & migrate
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

        return new PipelineService(db, connectors, preprocess, postsRepo, runsRepo, analyticsRepo, nlp);
    }

    /* ---------- Fields ---------- */

    private final SQLite db;
    private final List<SocialConnector> connectors;
    private final PreprocessService preprocess;
    private final PostsRepo postsRepo;
    private final RunsRepo runsRepo;
    private final AnalyticsRepo analyticsRepo;
    private final NlpModel nlp;

    public PipelineService(SQLite db,
                           List<SocialConnector> connectors,
                           PreprocessService preprocess,
                           PostsRepo postsRepo,
                           RunsRepo runsRepo,
                           AnalyticsRepo analyticsRepo,
                           NlpModel nlp) {
        this.db = db;
        this.connectors = connectors;
        this.preprocess = preprocess;
        this.postsRepo = postsRepo;
        this.runsRepo = runsRepo;
        this.analyticsRepo = analyticsRepo;
        this.nlp = nlp;
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

        // keywords sau chuẩn hoá tiếng Việt (không dấu)
        Set<String> keys = expandKeywords(rawKeyword);

        // 1) Ingest
        List<RawPost> raw = fetchFromConnectors(keys);
        System.out.println("[Pipeline] raw from connectors = " + raw.size());

        // 2) Preprocess + lọc theo keyword đã chuẩn hoá
        List<CleanPost> cleaned = raw.stream()
                .map(preprocess::preprocess)
                .filter(cp -> matchByKeyword(cp.textNorm(), keys))
                .toList();
        System.out.println("[Pipeline] cleaned & matched   = " + cleaned.size());

        // 3) Lưu posts
        reflectSavePosts(cleaned, runId);

        // 4) Sentiment cho từng post
        List<SentimentResult> sents = cleaned.stream()
                .map(cp -> nlp.analyzeSentiment(cp.rawId(), cp.textNorm(), cp.lang(), cp.ts()))
                .toList();
        saveSentimentsDirect(sents, runId);

        // 5) Overall sentiment según ngày
        Map<Instant, Counts> daily = aggregateDaily(sents);
        for (var e : daily.entrySet()) {
            Instant bucket = e.getKey();
            Counts c = e.getValue();
            // Direct call to AnalyticsRepo instead of reflection
            analyticsRepo.upsertOverallSentiment(runId, bucket, c.pos, c.neg, c.neu);
        }

        // 6) Damage, Relief + Task 3/4
        int damageRows = 0, reliefRows = 0;
        Map<String,int[]> aggTask3 = new LinkedHashMap<>();           // item -> [pos,neg,neu]
        Map<String,int[]> aggTask4 = new LinkedHashMap<>();           // "day||item" -> [pos,neg,neu]
        ZoneId utc = ZoneOffset.UTC;

        boolean hasLocal = (nlp instanceof LocalNlpModel);
        for (int i = 0; i < cleaned.size(); i++) {
            CleanPost cp = cleaned.get(i);
            SentimentResult sr = sents.get(i);
            int isPos = (sr.label() == SentimentLabel.pos) ? 1 : 0;
            int isNeg = (sr.label() == SentimentLabel.neg) ? 1 : 0;
            int isNeu = (sr.label() == SentimentLabel.neu) ? 1 : 0;

            // bucket day cho Task 4
            LocalDate d = sr.ts().atZone(utc).toLocalDate();
            String dayIso = d.toString();

            // --- detect damage ---
            if (hasLocal) {
                var types = ((LocalNlpModel) nlp).detectDamageTypes(cp.textNorm());
                if (!types.isEmpty()) {
                    damageRows += insertDamageRows(cp.rawId(), types, sr.ts(), runId);
                }
            }

            // --- detect relief + Task 3 & 4 ---
            List<String> items = hasLocal ? ((LocalNlpModel) nlp).detectReliefItems(cp.textNorm()) : List.of();
            if (!items.isEmpty()) {
                reliefRows += insertReliefRows(cp.rawId(), items, sr.ts(), runId);

                for (String it : items) {
                    // Task 3
                    int[] v3 = aggTask3.computeIfAbsent(it, k -> new int[3]);
                    v3[0] += isPos; v3[1] += isNeg; v3[2] += isNeu;

                    // Task 4
                    String key = dayIso + "||" + it;
                    int[] v4 = aggTask4.computeIfAbsent(key, k -> new int[3]);
                    v4[0] += isPos; v4[1] += isNeg; v4[2] += isNeu;
                }
            }
        }

        // Ghi Task 3/4
        saveTask3(runId, aggTask3);
        saveTask4(runId, aggTask4);

        System.out.println("[Pipeline] damage rows = " + damageRows + " | relief rows = " + reliefRows);

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
            try (Stream<RawPost> stream = c.fetch(spec)) {
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

    /* ---------- DB I/O trực tiếp ---------- */

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

    private int insertDamageRows(String postId, Collection<String> types, Instant ts, String runId) {
        if (types == null || types.isEmpty()) return 0;
        String sql = "INSERT INTO damage(id,type,ts,run_id) VALUES (?,?,?,?)";
        int rows = 0;
        try (var conn = db.connect(); var ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (String t : types) {
                ps.setString(1, postId);
                ps.setString(2, t);
                ps.setString(3, ts.toString());
                ps.setString(4, runId);
                ps.addBatch(); rows++;
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            System.err.println("[WARN] insertDamageRows: " + e.getMessage());
        }
        return rows;
    }

    private int insertReliefRows(String postId, Collection<String> items, Instant ts, String runId) {
        if (items == null || items.isEmpty()) return 0;
        String sql = "INSERT INTO relief_items(id,item,ts,run_id) VALUES (?,?,?,?)";
        int rows = 0;
        try (var conn = db.connect(); var ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (String it : items) {
                ps.setString(1, postId);
                ps.setString(2, it);
                ps.setString(3, ts.toString());
                ps.setString(4, runId);
                ps.addBatch(); rows++;
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            System.err.println("[WARN] insertReliefRows: " + e.getMessage());
        }
        return rows;
    }

    private void saveTask3(String runId, Map<String,int[]> agg) {
        if (agg.isEmpty()) return;
        String del = "DELETE FROM relief_sentiment WHERE run_id = ?";
        String ins = "INSERT INTO relief_sentiment(run_id,item,pos,neg,neu) VALUES (?,?,?,?,?)";
        try (var conn = db.connect();
             var pd = conn.prepareStatement(del);
             var pi = conn.prepareStatement(ins)) {
            conn.setAutoCommit(false);
            pd.setString(1, runId);
            pd.executeUpdate();
            for (var e : agg.entrySet()) {
                int[] v = e.getValue();
                pi.setString(1, runId);
                pi.setString(2, e.getKey());
                pi.setInt(3, v[0]); // pos
                pi.setInt(4, v[1]); // neg
                pi.setInt(5, v[2]); // neu
                pi.addBatch();
            }
            pi.executeBatch();
            conn.commit();
        } catch (Exception ex) {
            System.err.println("[Task3] save failed: " + ex.getMessage());
        }
    }

    private void saveTask4(String runId, Map<String,int[]> agg) {
        if (agg.isEmpty()) return;
        String del = "DELETE FROM relief_sentiment_daily WHERE run_id = ?";
        String ins = "INSERT INTO relief_sentiment_daily(run_id,bucket_start,item,pos,neg,neu) VALUES (?,?,?,?,?,?)";
        try (var conn = db.connect();
             var pd = conn.prepareStatement(del);
             var pi = conn.prepareStatement(ins)) {
            conn.setAutoCommit(false);
            pd.setString(1, runId);
            pd.executeUpdate();
            for (var e : agg.entrySet()) {
                String key = e.getKey(); // "day||item"
                String[] parts = key.split("\\|\\|", 2);
                String day = parts[0];
                String item = parts[1];
                int[] v = e.getValue();
                pi.setString(1, runId);
                pi.setString(2, day);   // lưu "yyyy-MM-dd"
                pi.setString(3, item);
                pi.setInt(4, v[0]); // pos
                pi.setInt(5, v[1]); // neg
                pi.setInt(6, v[2]); // neu
                pi.addBatch();
            }
            pi.executeBatch();
            conn.commit();
        } catch (Exception ex) {
            System.err.println("[Task4] save failed: " + ex.getMessage());
        }
    }

    /* ---------- overall_sentiment: Direct API call ---------- */
    // reflectUpsertOverall is now replaced with direct analyticsRepo.upsertOverallSentiment() call

    /* ---------- RunsRepo compat bằng reflection ---------- */

    private void reflectStartRun(String runId, String keyword, Instant started) {
        try {
            Method m = RunsRepo.class.getMethod("startRun", String.class, String.class, Instant.class);
            m.invoke(runsRepo, runId, keyword, started);
        } catch (NoSuchMethodException ignore) {
            // repo cũ không có method này → bỏ qua
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
            // repo cũ không có method này → bỏ qua
        } catch (Exception e) {
            System.err.println("[WARN] finishRun failed: " + e.getMessage());
        }
    }

    private void reflectSavePosts(List<CleanPost> cleaned, String runId) {
        // Đặt runId trước khi gọi saveBatch
        postsRepo.attachRun(runId);
        postsRepo.saveBatch(cleaned);
    }
}