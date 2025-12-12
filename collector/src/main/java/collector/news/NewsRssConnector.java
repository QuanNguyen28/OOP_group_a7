package collector.news;

import collector.api.QuerySpec;
import collector.api.SourceConnector;
import collector.core.RawPost;
import collector.core.Util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class NewsRssConnector implements SourceConnector {
    @Override public String id() { return "news-rss"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        List<RawPost> out = new ArrayList<>();
        for (String kw : spec.keywords()) {
            String url = "https://news.google.com/rss/search?q=" + Util.urlEnc(kw) + "&hl=vi&gl=VN&ceid=VN:vi";
            String xml = Util.httpGet(url, 15000);
            Document doc = Util.parseXml(xml);

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                var item = items.item(i);
                var title = Util.firstTag(item, "title");
                var link  = Util.firstTag(item, "link");
                var pub   = Util.firstTag(item, "pubDate"); // RFC822

                Instant ts = pub != null ? Util.parseRfc822(pub) : null;
                if (ts == null) ts = Instant.now();
                if (spec.from() != null && ts.isBefore(spec.from())) continue;
                if (spec.to()   != null && ts.isAfter(spec.to()))   continue;

                String id = "news:" + Util.sha1(link == null ? (title == null ? String.valueOf(i) : title) : link);
                String text = (title == null ? "" : title);
                String lk = (link == null ? "" : link);

                out.add(new RawPost(
                        id,
                        "news",
                        text,
                        ts,
                        "vi",
                        lk,
                        List.of(kw),
                        id()
                ));

                if (out.size() >= spec.limit()) break;
            }
        }
        return out.stream();
    }
}