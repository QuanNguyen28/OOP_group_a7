package app.collector.sink;

import app.collector.model.RawRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class JsonlSink implements RecordSink, Closeable {
    private final BufferedWriter w;
    public JsonlSink(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        this.w = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }
    @Override public void accept(RawRecord r) throws IOException {
        StringBuilder sb = new StringBuilder(Math.max(256, r.text.length()+128));
        sb.append('{');
        sb.append("\"id\":\"").append(esc(r.id)).append('"');
        sb.append(",\"text\":\"").append(esc(r.text)).append('"');
        sb.append(",\"lang\":\"").append(esc(r.lang)).append('"');
        sb.append(",\"createdAt\":\"").append(esc(r.createdAt)).append('"');
        sb.append(",\"platform\":\"").append(esc(r.platform)).append('"');
        sb.append(",\"userLoc\":\"").append(esc(r.userLoc)).append('"');
        sb.append(",\"hashtags\":[");
        for (int i=0;i<r.hashtags.size();i++){
            if (i>0) sb.append(',');
            sb.append('"').append(esc(r.hashtags.get(i))).append('"');
        }
        sb.append("]}");
        w.write(sb.toString()); w.write('\n');
    }
    @Override public void close() throws IOException { w.close(); }

    private static String esc(String s){
        StringBuilder b=new StringBuilder(s.length()+16);
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){
                case '"' -> b.append("\\\"");
                case '\\'-> b.append("\\\\");
                case '\b'-> b.append("\\b");
                case '\f'-> b.append("\\f");
                case '\n'-> b.append("\\n");
                case '\r'-> b.append("\\r");
                case '\t'-> b.append("\\t");
                default -> { if (c<0x20) b.append(String.format("\\u%04x",(int)c)); else b.append(c); }
            }
        }
        return b.toString();
    }
}