package app.collector.util;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HttpUtil {
    private static final HttpClient C = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

    public static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET().header("User-Agent","Collector/1.0").build();
        HttpResponse<String> resp = C.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode()/100 != 2) throw new RuntimeException("HTTP "+resp.statusCode()+": "+resp.body());
        return resp.body();
    }

    public static String get(String url, Map<String,String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        b.header("User-Agent","Collector/1.0");
        if (headers!=null) headers.forEach(b::header);
        HttpResponse<String> resp = C.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode()/100 != 2) throw new RuntimeException("HTTP "+resp.statusCode()+": "+resp.body());
        return resp.body();
    }

    public static String enc(String s){
        try { return java.net.URLEncoder.encode(s,"UTF-8"); } catch(Exception e){ return s; }
    }
}   