package app.model.service.nlp;

import app.model.domain.SentimentLabel;
import app.model.domain.SentimentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

public class PythonApiNlpModel implements NlpModel {
    private final URI batchUri;
    private final HttpClient client;
    private final ObjectMapper om;
    private final int batchSize;
    private final String modelId;

    public PythonApiNlpModel(String baseUrl, int timeoutMs, int batchSize) {
        this.batchUri = URI.create(baseUrl.endsWith("/") ? baseUrl + "v1/sentiment/batch" : baseUrl + "/v1/sentiment/batch");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.om = new ObjectMapper();
        this.batchSize = Math.max(1, batchSize);
        this.modelId = "python-api";
    }

    @Override public String modelId() { return modelId; }

    // tiện ích gọi batch (có thể dùng cho nơi khác)
    public List<SentimentResult> analyzeBatch(List<Item> items) {
        try {
            var root = om.createObjectNode();
            var arr = om.createArrayNode();
            for (var it : items) {
                ObjectNode n = om.createObjectNode();
                n.put("id", it.id());
                n.put("text", it.text());
                if (it.lang() != null) n.put("lang", it.lang());
                if (it.ts() != null) n.put("ts", it.ts().toString());
                arr.add(n);
            }
            root.set("items", arr);
            var req = HttpRequest.newBuilder(batchUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(root)))
                    .build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
            var json = om.readTree(res.body());
            var out = new ArrayList<SentimentResult>();
            for (var node : json.get("items")) {
                String id = node.get("id").asText();
                String label = node.get("label").asText();
                double score = node.get("score").asDouble();
                // tìm ts từ items (server không trả ts)
                Instant ts = items.stream().filter(i -> i.id().equals(id)).findFirst().map(Item::ts).orElse(Instant.now());
                SentimentLabel lab = switch (label) { case "pos" -> SentimentLabel.pos; case "neg" -> SentimentLabel.neg; default -> SentimentLabel.neu; };
                out.add(new SentimentResult(id, lab, score, ts));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SentimentResult analyzeSentiment(String id, String text, String lang, Instant ts) {
        // gọi batch 1 phần tử để tái dùng pipeline
        return analyzeBatch(List.of(new Item(id, text, lang, ts))).getFirst();
    }

    public record Item(String id, String text, String lang, Instant ts) {}
}