package app.model.service.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.time.Instant;
import java.util.List;

public class AppConfig {
    public final Run run;

    private AppConfig(Run run){ this.run = run; }

    public static AppConfig load() {
        File f = new File("config/app.conf");
        Config root = f.exists() ? ConfigFactory.parseFile(f).resolve()
                                 : ConfigFactory.load(); // fallback classpath
        Config r = root.getConfig("run");
        RunTime time = new RunTime(r.getConfig("time").getString("from"),
                                   r.getConfig("time").getString("to"));
        NLP nlp = new NLP(r.getConfig("nlp").getString("model"),
                          r.getConfig("nlp").getString("endpoint"));
        Taxonomy tx = new Taxonomy(r.getConfig("taxonomy").getString("damage"),
                                   r.getConfig("taxonomy").getString("relief"));
        Run run = new Run(
                r.getStringList("keywords"),
                time,
                r.getStringList("connectors"),
                nlp,
                r.getStringList("tasks"),
                tx
        );
        return new AppConfig(run);
    }

    public Instant fromInstant(){ return Instant.parse(run.time.from); }
    public Instant toInstant(){ return Instant.parse(run.time.to); }

    public static class Run {
        public final List<String> keywords;
        public final RunTime time;
        public final List<String> connectors;
        public final NLP nlp;
        public final List<String> tasks;
        public final Taxonomy taxonomy;
        public Run(List<String> keywords, RunTime time, List<String> connectors, NLP nlp,
                   List<String> tasks, Taxonomy taxonomy) {
            this.keywords = keywords; this.time = time; this.connectors = connectors;
            this.nlp = nlp; this.tasks = tasks; this.taxonomy = taxonomy;
        }
    }
    public static class RunTime { public final String from, to; public RunTime(String f,String t){from=f;to=t;} }
    public static class NLP { public final String model, endpoint; public NLP(String m,String e){model=m;endpoint=e;} }
    public static class Taxonomy { public final String damage, relief; public Taxonomy(String d,String r){damage=d;relief=r;} }
}