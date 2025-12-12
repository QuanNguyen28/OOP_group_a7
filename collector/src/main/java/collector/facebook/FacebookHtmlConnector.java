package collector.facebook;

import collector.core.RawPost;
import collector.core.SourceConnector;
import collector.core.QuerySpec;
import collector.core.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

public class FacebookHtmlConnector implements SourceConnector {

    // Mbasic endpoints (đỡ JS)
    private static final String BASE = "https://mbasic.facebook.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    // story anchors trong trang timeline (mbasic)
    private static final Pattern STORY_ANCHOR = Pattern.compile("<a\\s+href=\"(/story\\.php\\?[^\"<>]+)\"");
    // timestamp epoch (khi mở trang story)
    private static final Pattern DATA_UTIME   = Pattern.compile("data-utime\\s*=\\s*\"(\\d{9,})\"");
    // thô sơ lấy text trong tag
    private static final Pattern TAG          = Pattern.compile("<[^>]+>");
    private static final Pattern AMP          = Pattern.compile("&amp;");

    // Đọc danh sách page từ file (nếu ko có -> default)
    private List<String> pages() {
        List<String> pages = Util.loadLines("facebook/pages.txt");
        if (pages == null || pages.isEmpty()) {
            pages = List.of(
                "vtv24news",          // ví dụ
                "kenh14.vn",
                "VnExpress",
                "thanhnien"
            );
        }
        return pages;
    }

    private String fbCookie() {
        // Ưu tiên system property, sau đó file config (config/fb_cookie.txt)
        String v = System.getProperty("fb.cookie");
        if (v != null && !v.isBlank()) return v;
        String file = Util.loadConf("fb.cookie");
        return file == null ? "" : file.trim();
    }

    @Override
    public String id() { return "facebook"; }

    @Override
    public String name() { return "facebook-html"; }

    @Override
    public Stream<RawPost> fetch(QuerySpec spec) throws Exception {
        List<RawPost> out = new ArrayList<>();
        List<String> pages = pages();
        if (pages.isEmpty()) return out.stream();

        String cookie = fbCookie();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        if (!cookie.isBlank()) headers.put("Cookie", cookie);
        headers.put("Accept-Language", "vi,en;q=0.8");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        // Chuẩn bị keywords
        List<String> kws = spec.keywords();
        List<String> kwsLower = new ArrayList<>();
        for (String k : kws) if (k != null && !k.isBlank()) kwsLower.add(k.toLowerCase(Locale.ROOT));

        for (String page : pages) {
            if (out.size() >= spec.limit()) break;

            String pagePath = page.startsWith("/") ? page.substring(1) : page;
            String url = BASE + "/" + urlEnc(pagePath) + "?v=timeline";
            String html = Util.httpGet(url, 15000, headers);
            if (html == null || html.isBlank()) continue;
            if (html.toLowerCase(Locale.ROOT).contains("login") && cookie.isBlank()) {
                System.err.println("[FacebookHtml] Bị chặn login cho page " + page + ". Hãy cấu hình Cookie (fb.cookie).");
                continue;
            }

            // Tìm các story anchors trên timeline
            Matcher m = STORY_ANCHOR.matcher(html);
            int hitsOnPage = 0;
            while (m.find()) {
                if (out.size() >= spec.limit()) break;
                if (++hitsOnPage > 50) break; // tránh quá nhiều per page

                String href = m.group(1);
                String storyUrl = BASE + href;
                String storyHtml = Util.httpGet(storyUrl, 15000, headers);
                if (storyHtml == null || storyHtml.isBlank()) continue;

                // Timestamp
                Instant ts = extractTs(storyHtml);
                // Nếu không parse được -> tạm lấy now (để không loại bỏ vì null)
                if (ts == null) ts = Instant.now();

                // Lọc theo from/to nếu có
                if (spec.from() != null && ts.isBefore(spec.from())) continue;
                if (spec.to()   != null && ts.isAfter(spec.to()))   continue;

                // Văn bản
                String text = extractText(storyHtml);
                if (text == null) text = "";
                String textLower = text.toLowerCase(Locale.ROOT);

                boolean ok = kwsLower.isEmpty();
                if (!ok) {
                    for (String k : kwsLower) {
                        if (textLower.contains(k)) { ok = true; break; }
                    }
                }
                if (!ok) continue;

                // link chuyển qua www cho đẹp
                String wwwLink = "https://www.facebook.com" + href;

                // RawPost: (id, platform, text, ts, lang, link, keywords, source)
                String id = "fb:" + Util.sha1(wwwLink);
                RawPost rp = new RawPost(
                        id,
                        "facebook",
                        text.trim(),
                        ts,
                        "vi",
                        wwwLink,
                        kws,
                        "facebook-html:" + pagePath
                );
                out.add(rp);
            }
        }

        return out.stream();
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Instant extractTs(String html) {
        Matcher tm = DATA_UTIME.matcher(html);
        if (tm.find()) {
            try {
                long epoch = Long.parseLong(tm.group(1));
                return Instant.ofEpochSecond(epoch);
            } catch (Exception ignore) { }
        }
        return null;
    }

    private static String extractText(String html) {
        // đơn giản: bỏ tag, giải mã &amp;
        String s = AMP.matcher(html).replaceAll("&");
        s = TAG.matcher(s).replaceAll(" ");
        s = s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&lt;","<").replace("&gt;",">").replace("&apos;","'");
        // nén khoảng trắng
        s = s.replaceAll("\\s+", " ").trim();
        // cắt ngắn quá dài
        if (s.length() > 2000) s = s.substring(0, 2000) + " ...";
        return s;
    }
}