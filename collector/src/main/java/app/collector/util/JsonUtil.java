package app.collector.util;

import java.util.*;
import java.util.regex.*;

public final class JsonUtil {
    // Trích mảng hashtags từ text
    private static final Pattern TAG = Pattern.compile("(?<!\\w)#([\\p{L}\\p{N}_-]+)");

    public static List<String> extractHashtags(String text){
        if (text==null) return List.of();
        List<String> hs = new ArrayList<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) hs.add("#"+m.group(1));
        return hs;
    }

    // Lấy tất cả "videoId":"..." trong JSON YouTube Search
    public static List<String> allVideoIds(String json) {
        Matcher m = Pattern.compile("\"videoId\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        List<String> ids = new ArrayList<>();
        while (m.find()) ids.add(m.group(1));
        return ids;
    }

    // Lấy title & description từ snippet
    public static String titlePlusDesc(String jsonBlock) {
        String title = firstString(jsonBlock, "\"title\"\\s*:\\s*\"([^\"]*)\"");
        String desc  = firstString(jsonBlock, "\"description\"\\s*:\\s*\"([^\"]*)\"");
        return (title + " " + desc).trim();
    }

    // Lấy value đầu tiên cho key regex
    public static String firstString(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? unescape(m.group(1)) : "";
    }

    private static String unescape(String s){
        // rất tối giản: thay \\n, \\" ...
        return s.replace("\\n","\n").replace("\\r","\r").replace("\\t","\t").replace("\\\"","\"").replace("\\\\","\\");
    }
}