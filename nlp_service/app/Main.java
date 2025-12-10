// Main.java
// Chuyển nguyên logic từ script Python sang Java (không thay đổi logic)
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    // --- CẤU HÌNH ---
    // Chọn: "Model 1", "Model 2", hoặc "Model 3"
    static final String SELECTED_MODEL = "Model 2";

    // --- KHỐI 2: TỪ KHÓA ---
    static final Map<String, List<String>> IMPACT_FEATURES = Map.of(
        "Người bị ảnh hưởng", List.of("người chết", "tử vong", "mất tích", "bị thương", "nạn nhân", "thiệt mạng", "người dân", "hộ dân", "sơ tán"),
        "Gián đoạn kinh tế", List.of("ngừng trệ", "gián đoạn", "sản xuất", "kinh doanh", "nghỉ học", "cấm biển"),
        "Nhà cửa hư hỏng", List.of("nhà sập", "tốc mái", "ngập", "hư hỏng", "đổ nát", "vùi lấp"),
        "Tài sản cá nhân mất", List.of("tài sản", "xe máy", "ô tô", "gia súc", "mất trắng"),
        "Cơ sở hạ tầng hư hỏng", List.of("cầu", "đường", "điện", "trường học", "bệnh viện", "sạt lở")
    );

    static final Map<String, List<String>> SENTIMENT_FEATURES = Map.of(
        "nhà ở", List.of("nhà", "chỗ ở", "lều", "tái định cư"),
        "giao thông", List.of("đường", "xe", "đi lại", "cầu", "thuyền", "ùn tắc"),
        "thực phẩm", List.of("ăn", "uống", "gạo", "mì tôm", "nước", "lương thực"),
        "hỗ trợ y tế", List.of("thuốc", "bác sĩ", "bệnh viện", "đau", "ốm", "cứu thương"),
        "tiền mặt", List.of("tiền", "hỗ trợ", "vốn", "vay", "ngân hàng")
    );

    static final List<String> POS_WORDS = List.of("cảm ơn", "tốt", "kịp thời", "nhanh chóng", "an toàn", "hỗ trợ", "giúp đỡ", "biết ơn");
    static final List<String> NEG_WORDS = List.of("chậm", "kém", "thiếu", "khổ", "buồn", "đau thương", "mất mát", "lo lắng", "tệ", "tang tóc");
    static final List<String> URGENT_WORDS = List.of("khẩn cấp", "cứu", "nguy hiểm", "ngay lập tức");

    static final Random RAND = new Random();

    // --- KHỐI 1: TẢI DỮ LIỆU ---
    static class CsvUtil {
        // Đọc CSV trả về list of map (header -> value). trim header names
        static List<Map<String, String>> readCsv(String path) throws IOException {
            List<String> lines = Files.readAllLines(Paths.get(path));
            if (lines.isEmpty()) return Collections.emptyList();
            String headerLine = lines.get(0);
            String[] headers = splitCsvLine(headerLine).stream().map(String::trim).toArray(String[]::new);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] cols = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    String key = headers[j];
                    String val = j < cols.length ? cols[j] : "";
                    row.put(key, val);
                }
                rows.add(row);
            }
            return rows;
        }

        // Rời rạc: đơn giản, xử lý dấu phẩy trong quote cơ bản
        static String[] splitCsvLine(String line) {
            List<String> cols = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuote = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    inQuote = !inQuote;
                } else if (c == ',' && !inQuote) {
                    cols.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
            cols.add(cur.toString());
            return cols.toArray(new String[0]);
        }

        static void writeCsv(String path, List<String> headers, List<Map<String, Object>> rows) throws IOException {
            try (BufferedWriter w = Files.newBufferedWriter(Paths.get(path))) {
                w.write(String.join(",", headers));
                w.newLine();
                for (Map<String, Object> r : rows) {
                    List<String> out = new ArrayList<>();
                    for (String h : headers) {
                        Object v = r.getOrDefault(h, "");
                        String s = v == null ? "" : v.toString();
                        if (s.contains(",") || s.contains("\"")) {
                            s = "\"" + s.replace("\"", "\"\"") + "\"";
                        }
                        out.add(s);
                    }
                    w.write(String.join(",", out));
                    w.newLine();
                }
            }
        }
    }

    static List<Map<String, String>> load_data() {
        try {
            List<Map<String, String>> df_trans = CsvUtil.readCsv("youtube_transcripts_auto_miniproject.csv");
            List<Map<String, String>> df_comm = CsvUtil.readCsv("yt_comments_miniproject.csv");

            // Extract Storm Type from keyword in comments
            for (Map<String, String> row : df_comm) {
                String keyword = row.getOrDefault("keyword", "");
                row.put("storm_type", extract_storm(keyword));
            }

            // Merge metadata to Transcripts:
            // meta = df_comm.groupby('video_url').agg({'keyword':'first', 'published':'min'})
            Map<String, Map<String, String>> meta = new HashMap<>();
            for (Map<String, String> row : df_comm) {
                String videoUrl = row.getOrDefault("video_url", "");
                if (!meta.containsKey(videoUrl)) {
                    Map<String, String> m = new HashMap<>();
                    m.put("keyword", row.getOrDefault("keyword", ""));
                    m.put("published", row.getOrDefault("published", ""));
                    m.put("storm_type", extract_storm(row.getOrDefault("keyword", "")));
                    meta.put(videoUrl, m);
                } else {
                    // lấy published min (như min string chronologically)
                    String existing = meta.get(videoUrl).getOrDefault("published", "");
                    String candidate = row.getOrDefault("published", "");
                    // so sánh theo parse datetime nếu có, fallback lexicographical
                    LocalDateTime exDate = parseDateTime(existing);
                    LocalDateTime caDate = parseDateTime(candidate);
                    if (exDate == null && caDate != null) {
                        meta.get(videoUrl).put("published", candidate);
                    } else if (exDate != null && caDate != null) {
                        if (caDate.isBefore(exDate)) meta.get(videoUrl).put("published", candidate);
                    } else {
                        if (candidate != null && !candidate.isEmpty() && (existing == null || existing.isEmpty() || candidate.compareTo(existing) < 0)) {
                            meta.get(videoUrl).put("published", candidate);
                        }
                    }
                }
            }

            // Merge into df_trans
            for (Map<String, String> row : df_trans) {
                String videoUrl = row.getOrDefault("video_url", "");
                Map<String, String> m = meta.get(videoUrl);
                if (m != null) {
                    row.put("storm_type", m.getOrDefault("storm_type", "Unknown"));
                    row.put("published", m.getOrDefault("published", ""));
                } else {
                    row.put("storm_type", "Unknown");
                }
            }

            return Stream.of(
                // we will encode two lists into a single returned list by storing them in a special wrapper?
                // But Java method returns only transcripts; we'll store comments globally variable style by copying to a field.
                df_trans, df_comm
            ).flatMap(List::stream).collect(Collectors.toList()); // This return is not used; we'll instead set globals.
        } catch (Exception e) {
            System.out.println("Lỗi tải file: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Helper fields to hold loaded data because load_data needs to fill both lists
    static List<Map<String, String>> DF_TRANS = new ArrayList<>();
    static List<Map<String, String>> DF_COMM = new ArrayList<>();

    static void load_data_into_globals() {
        try {
            DF_TRANS = CsvUtil.readCsv("youtube_transcripts_auto_miniproject.csv");
            DF_COMM = CsvUtil.readCsv("yt_comments_miniproject.csv");

            // Clean column names already trimmed in readCsv.

            // Extract Storm Type for comments
            for (Map<String, String> row : DF_COMM) {
                String keyword = row.getOrDefault("keyword", "");
                row.put("storm_type", extract_storm(keyword));
            }

            // Build meta grouped by video_url
            Map<String, Map<String, String>> meta = new HashMap<>();
            for (Map<String, String> row : DF_COMM) {
                String videoUrl = row.getOrDefault("video_url", "");
                if (!meta.containsKey(videoUrl)) {
                    Map<String, String> m = new HashMap<>();
                    m.put("keyword", row.getOrDefault("keyword", ""));
                    m.put("published", row.getOrDefault("published", ""));
                    m.put("storm_type", extract_storm(row.getOrDefault("keyword", "")));
                    meta.put(videoUrl, m);
                } else {
                    String existing = meta.get(videoUrl).getOrDefault("published", "");
                    String candidate = row.getOrDefault("published", "");
                    LocalDateTime exDate = parseDateTime(existing);
                    LocalDateTime caDate = parseDateTime(candidate);
                    if (exDate == null && caDate != null) {
                        meta.get(videoUrl).put("published", candidate);
                    } else if (exDate != null && caDate != null) {
                        if (caDate.isBefore(exDate)) meta.get(videoUrl).put("published", candidate);
                    } else {
                        if (candidate != null && !candidate.isEmpty() && (existing == null || existing.isEmpty() || candidate.compareTo(existing) < 0)) {
                            meta.get(videoUrl).put("published", candidate);
                        }
                    }
                }
            }

            // Merge into transcripts
            for (Map<String, String> row : DF_TRANS) {
                String videoUrl = row.getOrDefault("video_url", "");
                Map<String, String> m = meta.get(videoUrl);
                if (m != null) {
                    row.put("storm_type", m.getOrDefault("storm_type", "Unknown"));
                    row.put("published", m.getOrDefault("published", ""));
                } else {
                    row.put("storm_type", row.getOrDefault("storm_type", "Unknown"));
                }
            }
        } catch (Exception e) {
            System.out.println("Lỗi tải file: " + e.getMessage());
            DF_TRANS = new ArrayList<>();
            DF_COMM = new ArrayList<>();
        }
    }

    static String extract_storm(String val) {
        if (val == null) return "Unknown";
        String s = val.trim();
        if (s.isEmpty()) return "Unknown";
        String[] parts = s.split("\\s+");
        return parts[parts.length - 1];
    }

    static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        // Try common ISO formats, then try date only
        List<DateTimeFormatter> fmts = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        );
        for (DateTimeFormatter f : fmts) {
            try {
                if (f == DateTimeFormatter.ISO_DATE) {
                    LocalDate d = LocalDate.parse(s, f);
                    return d.atStartOfDay();
                } else {
                    return LocalDateTime.parse(s, f);
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }

    // --- KHỐI 3: LOGIC & MODEL ---
    static double get_weight(String source, double likes, String text, String model) {
        String t = text == null ? "" : text.toLowerCase();
        if ("Model 1".equals(model)) return 1.0;
        double base = 1.0;
        if ("Model 2".equals(model)) {
            base = source.equals("transcript") ? 10.0 : (1.0 + likes);
        } else if ("Model 3".equals(model)) {
            base = source.equals("transcript") ? 20.0 : (1.0 + likes) * 2.0;
            for (String w : URGENT_WORDS) {
                if (t.contains(w)) {
                    base *= 2.0;
                    break;
                }
            }
        }
        return base;
    }

    static boolean check_contain(String text, List<String> kws) {
        if (text == null) return false;
        String t = text.toLowerCase();
        for (String k : kws) {
            if (k != null && !k.isEmpty() && t.contains(k)) return true;
        }
        return false;
    }

    static void process_tasks(String model_name) {
        System.out.println("--- Đang chạy " + model_name + " (Có cột Storm Type) ---");

        List<Map<String, Object>> impact_data = new ArrayList<>();
        List<Map<String, Object>> sentiment_data = new ArrayList<>();

        // Processor function
        class Processor {
            void process_row(Map<String, String> row, String source) {
                String text = "";
                if ("transcript".equals(source)) {
                    text = row.getOrDefault("transcripts", "");
                } else {
                    // comment source
                    text = row.getOrDefault("comment", row.getOrDefault("comments", ""));
                }
                String storm = row.getOrDefault("storm_type", "Unknown");
                String date = row.getOrDefault("published", "");
                double likes = 0.0;
                if ("comment".equals(source)) {
                    String l = row.getOrDefault("like", row.getOrDefault("likes", "0"));
                    try { likes = Double.parseDouble(l); } catch (Exception e) { likes = 0.0; }
                }
                double w = get_weight(source, likes, text, model_name);

                // === TASK 2: IMPACT ===
                boolean found_impact = false;
                for (Map.Entry<String, List<String>> en : IMPACT_FEATURES.entrySet()) {
                    String feat = en.getKey();
                    List<String> kws = en.getValue();
                    if (check_contain(text, kws)) {
                        Map<String, Object> r = new HashMap<>();
                        r.put("storm_type", storm);
                        r.put("feature", feat);
                        r.put("count", w);
                        impact_data.add(r);
                        found_impact = true;
                    }
                }

                // Random Impact
                if (!found_impact) {
                    List<String> keys = new ArrayList<>(IMPACT_FEATURES.keySet());
                    String random_feat = keys.get(RAND.nextInt(keys.size()));
                    Map<String, Object> r = new HashMap<>();
                    r.put("storm_type", storm);
                    r.put("feature", random_feat);
                    r.put("count", w);
                    impact_data.add(r);
                }

                // === TASK 3 & 4: SENTIMENT ===
                // 1. Tìm Features
                List<String> found_feats = new ArrayList<>();
                for (Map.Entry<String, List<String>> en : SENTIMENT_FEATURES.entrySet()) {
                    String feat = en.getKey();
                    List<String> kws = en.getValue();
                    if (check_contain(text, kws)) {
                        found_feats.add(feat);
                    }
                }
                // Random Feature Sentiment
                if (found_feats.isEmpty()) {
                    List<String> keys = new ArrayList<>(SENTIMENT_FEATURES.keySet());
                    found_feats.add(keys.get(RAND.nextInt(keys.size())));
                }

                // 2. Tính điểm Pos/Neg
                int pos_raw = 0;
                for (String wpos : POS_WORDS) if (text.toLowerCase().contains(wpos)) pos_raw++;
                int neg_raw = 0;
                for (String wneg : NEG_WORDS) if (text.toLowerCase().contains(wneg)) neg_raw++;

                // Random Score
                if (pos_raw == 0 && neg_raw == 0) {
                    if (RAND.nextBoolean()) pos_raw = 1;
                    else neg_raw = 1;
                }

                // Ghi dữ liệu
                for (String feat : found_feats) {
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("storm_type", storm);
                    rec.put("published", date);
                    rec.put("feature", feat);
                    rec.put("positive_score", pos_raw * w);
                    rec.put("negative_score", neg_raw * w);
                    rec.put("total_score", (pos_raw - neg_raw) * w);
                    sentiment_data.add(rec);
                }
            }
        }

        Processor proc = new Processor();

        // Execute Loop
        for (Map<String, String> row : DF_TRANS) proc.process_row(row, "transcript");
        for (Map<String, String> row : DF_COMM) proc.process_row(row, "comment");

        // --- TỔNG HỢP & LƯU CSV ---
        // Task 2: group by storm_type + feature sum(count)
        if (!impact_data.isEmpty()) {
            Map<String, Double> agg = new LinkedHashMap<>();
            for (Map<String, Object> r : impact_data) {
                String storm = Objects.toString(r.get("storm_type"), "Unknown");
                String feat = Objects.toString(r.get("feature"), "");
                double cnt = ((Number) r.get("count")).doubleValue();
                String key = storm + "||" + feat;
                agg.put(key, agg.getOrDefault(key, 0.0) + cnt);
            }
            List<Map<String, Object>> outRows = new ArrayList<>();
            for (Map.Entry<String, Double> e : agg.entrySet()) {
                String[] parts = e.getKey().split("\\|\\|", 2);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("storm_type", parts[0]);
                r.put("feature", parts[1]);
                r.put("count", e.getValue());
                outRows.add(r);
            }
            try {
                String f = "task2_" + model_name.replace(" ", "") + ".csv";
                CsvUtil.writeCsv(f, List.of("storm_type", "feature", "count"), outRows);
                System.out.println("Task 2 Done.");
            } catch (IOException ex) {
                System.out.println("Lỗi khi viết Task2: " + ex.getMessage());
            }
        }

        // Task 3 & 4
        if (!sentiment_data.isEmpty()) {
            // Task 3: Group by STORM + FEATURE sum positive/negative/total
            Map<String, double[]> agg3 = new LinkedHashMap<>();
            for (Map<String, Object> r : sentiment_data) {
                String storm = Objects.toString(r.get("storm_type"), "Unknown");
                String feat = Objects.toString(r.get("feature"), "");
                double pos = ((Number) r.get("positive_score")).doubleValue();
                double neg = ((Number) r.get("negative_score")).doubleValue();
                double tot = ((Number) r.get("total_score")).doubleValue();
                String key = storm + "||" + feat;
                double[] cur = agg3.getOrDefault(key, new double[]{0.0, 0.0, 0.0});
                cur[0] += pos; cur[1] += neg; cur[2] += tot;
                agg3.put(key, cur);
            }
            List<Map<String, Object>> out3 = new ArrayList<>();
            for (Map.Entry<String, double[]> e : agg3.entrySet()) {
                String[] parts = e.getKey().split("\\|\\|", 2);
                double[] v = e.getValue();
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("storm_type", parts[0]);
                r.put("feature", parts[1]);
                r.put("positive_score", v[0]);
                r.put("negative_score", v[1]);
                r.put("total_score", v[2]);
                out3.add(r);
            }
            try {
                String f3 = "task3_" + model_name.replace(" ", "") + ".csv";
                CsvUtil.writeCsv(f3, List.of("storm_type", "feature", "positive_score", "negative_score", "total_score"), out3);
                System.out.println("Task 3 Done (Có cột storm_type).");
            } catch (IOException ex) {
                System.out.println("Lỗi khi viết Task3: " + ex.getMessage());
            }

            // Task 4: Group by STORM + DATE + FEATURE
            // Convert published -> date (yyyy-MM-dd) where possible
            Map<String, double[]> agg4 = new LinkedHashMap<>();
            DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
            for (Map<String, Object> r : sentiment_data) {
                String storm = Objects.toString(r.get("storm_type"), "Unknown");
                String published = Objects.toString(r.get("published"), "");
                LocalDateTime dt = parseDateTime(published);
                String dateStr;
                if (dt != null) dateStr = dt.toLocalDate().toString();
                else {
                    // try to parse date only fallback: take published as-is date-like
                    dateStr = published;
                }
                String feat = Objects.toString(r.get("feature"), "");
                double pos = ((Number) r.get("positive_score")).doubleValue();
                double neg = ((Number) r.get("negative_score")).doubleValue();
                double tot = ((Number) r.get("total_score")).doubleValue();
                String key = storm + "||" + dateStr + "||" + feat;
                double[] cur = agg4.getOrDefault(key, new double[]{0.0, 0.0, 0.0});
                cur[0] += pos; cur[1] += neg; cur[2] += tot;
                agg4.put(key, cur);
            }
            List<Map<String, Object>> out4 = new ArrayList<>();
            // sort by storm_type, date
            List<String> keysSorted = new ArrayList<>(agg4.keySet());
            keysSorted.sort((a, b) -> {
                String[] pa = a.split("\\|\\|", 3);
                String[] pb = b.split("\\|\\|", 3);
                int cmp = pa[0].compareTo(pb[0]);
                if (cmp != 0) return cmp;
                return pa[1].compareTo(pb[1]);
            });
            for (String key : keysSorted) {
                String[] parts = key.split("\\|\\|", 3);
                double[] v = agg4.get(key);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("storm_type", parts[0]);
                r.put("date", parts[1]);
                r.put("feature", parts[2]);
                r.put("positive_score", v[0]);
                r.put("negative_score", v[1]);
                r.put("total_score", v[2]);
                out4.add(r);
            }
            try {
                String f4 = "task4_" + model_name.replace(" ", "") + ".csv";
                CsvUtil.writeCsv(f4, List.of("storm_type", "date", "feature", "positive_score", "negative_score", "total_score"), out4);
                System.out.println("Task 4 Done (Có cột storm_type).");
            } catch (IOException ex) {
                System.out.println("Lỗi khi viết Task4: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Tải data vào globals
        load_data_into_globals();
        if (DF_TRANS == null || DF_TRANS.isEmpty()) {
            System.out.println("Không có dữ liệu transcripts. Kết thúc.");
            return;
        }
        process_tasks(SELECTED_MODEL);
    }
}