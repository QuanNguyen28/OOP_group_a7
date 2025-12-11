package app.collector.sources.news;

import app.collector.config.CollectorConfig;
import app.collector.core.PlatformCollector;
import app.collector.model.RawRecord;
import app.collector.sink.RecordSink;
import app.collector.util.HttpUtil;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class RssCollector implements PlatformCollector {
    @Override public String id() { return "news"; }

    @Override public void collect(CollectorConfig cfg, RecordSink sink) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();

        for (String feed : cfg.newsFeeds) {
            try {
                String xml = HttpUtil.get(feed);
                Document doc = b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                NodeList items = doc.getElementsByTagName("item");
                for (int i=0;i<items.getLength();i++){
                    Element e = (Element) items.item(i);
                    String title = text(e, "title");
                    String desc  = text(e, "description");
                    String link  = text(e, "link");
                    String pub   = text(e, "pubDate");
                    String text  = (title + " " + desc).trim();
                    if (text.isBlank()) continue;
                    String ts = (pub==null || pub.isBlank()) ? Instant.now().toString() : Instant.ofEpochMilli(e.getTextContent().hashCode()).toString(); // best-effort
                    sink.accept(new RawRecord("news_"+Math.abs(link.hashCode()), text, cfg.lang, ts, "news", "", List.of()));
                }
            } catch (Exception ex) {
                System.err.println("[News] feed failed: "+feed+" -> "+ex.getMessage());
            }
        }
    }

    private static String text(Element e, String tag){
        NodeList nl = e.getElementsByTagName(tag);
        if (nl.getLength()==0) return "";
        return nl.item(0).getTextContent().replaceAll("\\s+"," ").trim();
        }
}