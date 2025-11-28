package app.model.service.pipeline;

import app.model.service.config.AppConfig;
import app.model.service.ingest.QuerySpec;
import app.model.service.ingest.SocialConnector;
import app.model.service.ingest.FileConnector;
import app.model.service.preprocess.PreprocessService;
import app.model.service.preprocess.DefaultPreprocessService;
import app.model.repository.SQLite;
import app.model.repository.PostsRepo;
import app.model.repository.RunsRepo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PipelineService {
    private final List<SocialConnector> connectors;
    private final PreprocessService preprocess;
    private final PostsRepo postsRepo;
    private final RunsRepo runsRepo;

    public PipelineService(List<SocialConnector> connectors,
                           PreprocessService preprocess,
                           PostsRepo postsRepo,
                           RunsRepo runsRepo) {
        this.connectors = connectors;
        this.preprocess = preprocess;
        this.postsRepo = postsRepo;
        this.runsRepo = runsRepo;
    }

    public RunSummary run(RunConfig rc) {
        Instant started = Instant.now();
        Map<String,Object> params = Map.of(
            "keywords", rc.keywords(),
            "from", rc.from().toString(),
            "to", rc.to().toString(),
            "connectors", rc.connectors()
        );
        runsRepo.saveRun(rc.runId(), started, params);

        QuerySpec spec = new QuerySpec(rc.keywords(), rc.from(), rc.to(),
                                       Optional.empty(), Optional.empty(),
                                       1_000_000, true);

        var selected = connectors.stream()
                .filter(c -> rc.connectors().contains(c.id()))
                .toList();

        var clean = selected.stream()
                .flatMap(c -> c.fetch(spec))
                .map(preprocess::preprocess)
                .collect(Collectors.toList());

        postsRepo.attachRun(rc.runId());
        int saved = postsRepo.saveBatch(clean);

        return new RunSummary(rc.runId(), saved, 0, started, "n/a");
    }
    private static Path resolveBaseDataDir() {
        Path p1 = Path.of("data");
        if (Files.isDirectory(p1)) return p1;
        Path p2 = Path.of("../data");
        if (Files.isDirectory(p2)) return p2;
        try { Files.createDirectories(p1); return p1; }
        catch (Exception e){ throw new RuntimeException(e); }
    }

    private static Path ensureDemoDir(Path base) {
        Path d = base.resolve("demo");
        try { Files.createDirectories(d); return d; }
        catch (Exception e){ throw new RuntimeException(e); }
    }

    public static PipelineService createDefault() {
        Path base = resolveBaseDataDir();                 // <-- dùng base linh hoạt
        var db = new SQLite(base.resolve("app.db").toString());      // data/app.db (hoặc ../data/app.db)
        db.migrate();

        var postsRepo = new PostsRepo(db);
        var runsRepo  = new RunsRepo(db);

        List<SocialConnector> connectors = new ArrayList<>();
        connectors.add(new FileConnector(ensureDemoDir(base))); // data/demo

        PreprocessService preprocess = new DefaultPreprocessService();
        return new PipelineService(connectors, preprocess, postsRepo, runsRepo);
    }
}