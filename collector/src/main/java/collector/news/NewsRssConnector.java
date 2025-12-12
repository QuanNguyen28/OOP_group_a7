package collector.news;

import collector.core.QuerySpec;
import collector.core.RawPost;
import collector.core.SourceConnector;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class NewsRssConnector implements SourceConnector {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override public String id()   { return "news"; }
    @Override public String name() { return "Google News RSS"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        List<RawPost> out = new ArrayList<>();
        int limit = spec.limit();
        Instant from = spec.from();
        Instant to   = spec.to();

        for (String kw : spec.keywords()) {
            if (out.size() >= limit) break;

            String url = buildRssUrl(kw);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.out.println("[News] HTTP " + resp.statusCode() + " for " + url);
                continue;
            }

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(resp.body().getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength() && out.size() < limit; i++) {
                Element item = (Element) items.item(i);

                String title = firstTag(item, "title");
                String link  = firstTag(item, "link");
                String pub   = firstTag(item, "pubDate"); // RFC_1123

                Instant ts = Instant.now();
                if (pub != null && !pub.isBlank()) {
                    try { ts = ZonedDateTime.parse(pub, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                    catch (Exception ignore) {}
                }

                if (from != null && ts.isBefore(from)) continue;
                if (to   != null && ts.isAfter(to))   continue;

                String text = (title == null ? "" : title);
                String id   = "news:" + Objects.hash(kw, link, ts.toString());

                out.add(new RawPost(
                        id,
                        "news",
                        text,
                        ts,
                        "",
                        link == null ? "" : link,
                        List.of(),
                        "news-rss"
                ));
            }
        }
        return out.stream();
    }

    private static String buildRssUrl(String kw) {
        String q = URLEncoder.encode(kw, StandardCharsets.UTF_8);
        return "https://news.google.com/rss/search?q=" + q + "&hl=vi&gl=VN&ceid=VN:vi";
    }

    private static String firstTag(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n.getTextContent();
    }
}