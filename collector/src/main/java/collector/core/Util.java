package collector.core;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class Util {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private Util(){}

    public static String httpGet(String url, int timeoutMs) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "collector/1.0 (+https://example)")
                .build();
        var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode()/100 != 2) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    public static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String sha1(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Document parseXml(String xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        var b = f.newDocumentBuilder();
        return b.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static String firstTag(Node parent, String tag) {
        NodeList list = ((org.w3c.dom.Element) parent).getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    public static Instant parseRfc822(String s) {
        try {
            var fmt = DateTimeFormatter.RFC_1123_DATE_TIME;
            return fmt.parse(s, Instant::from);
        } catch (Exception ignore) { return null; }
    }

    public static String isoDate(Instant tsUtc) {
        return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(tsUtc);
    }
}