package app.controller;

import collector.api.CollectorService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class CollectorController {
    private Path projectRoot = Path.of("").toAbsolutePath();

    @FXML private TextField keywordsField;
    @FXML private CheckBox newsCheck;
    @FXML private CheckBox youtubeCheck;
    @FXML private CheckBox redditCheck;

    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;

    @FXML private TextField limitField;
    @FXML private TextField collectionField;

    @FXML private Button runBtn;
    @FXML private Button closeBtn;

    // UI mới: không còn TextArea log hay chọn thư mục.
    @FXML private HBox runningBox;          // khối “Đang thu thập…”
    @FXML private Label statusLabel;        // dòng trạng thái ngắn
    @FXML private ListView<String> sampleList; // hiển thị mẫu dữ liệu mới

    public void init(Path projectRoot) {
        this.projectRoot = (projectRoot == null) ? Path.of("").toAbsolutePath() : projectRoot;

        if (fromDate != null && toDate != null) {
            toDate.setValue(LocalDate.now());
            fromDate.setValue(LocalDate.now().minusDays(30));
        }
        if (limitField != null) limitField.setText("300");
        if (collectionField != null) collectionField.setText("default");

        if (statusLabel != null) statusLabel.setText("Sẵn sàng");
        if (runningBox != null) { runningBox.setVisible(false); runningBox.setManaged(false); }
        if (sampleList != null) sampleList.getItems().clear();

        if (newsCheck != null) newsCheck.setSelected(true);
        if (youtubeCheck != null) youtubeCheck.setSelected(true);
    }

    @FXML
    private void onRun() {
        try {
            String kwRaw = safeText(keywordsField);
            if (kwRaw.isBlank()) { alert("Vui lòng nhập từ khóa (phân tách bằng dấu phẩy)."); return; }

            List<String> keywords = new ArrayList<>();
            Arrays.stream(kwRaw.split(",")).map(String::trim).filter(s -> !s.isBlank()).forEach(keywords::add);

            if (!newsCheck.isSelected() && !youtubeCheck.isSelected() && !redditCheck.isSelected()) {
                alert("Vui lòng chọn ít nhất 1 nguồn dữ liệu (news / youtube / reddit)."); return;
            }

            LocalDate f = fromDate.getValue(), t = toDate.getValue();
            if (f == null || t == null) { alert("Vui lòng chọn khoảng ngày (từ / đến)."); return; }

            Instant from = f.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant to   = t.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);

            int limit;
            try { limit = Integer.parseInt(safeText(limitField)); if (limit <= 0) limit = 300; }
            catch (NumberFormatException ex) { limit = 300; }

            String collection = safeText(collectionField);
            if (collection.isBlank()) collection = "default";

            // Lưu mặc định vào ../data/collections (CollectorService tự nối /<collection>)
            Path saveDir = projectRoot.resolve("../data/collections");
            Files.createDirectories(saveDir);

            CollectorService svc = new CollectorService();
            if (newsCheck.isSelected())    svc.add("news");
            if (youtubeCheck.isSelected()) svc.add("youtube");
            if (redditCheck.isSelected())  svc.add("reddit");

            final List<String> keywords0 = List.copyOf(keywords);
            final Instant from0 = from, to0 = to;
            final int limit0 = limit;
            final String collection0 = collection;
            final Path saveDir0 = saveDir;

            toggleUi(false);
            showRunning(true, "Đang thu thập…");

            Task<Path> job = new Task<>() {
                @Override protected Path call() throws Exception {
                    return svc.run(keywords0, from0, to0, limit0, collection0, saveDir0, null);
                }
            };

            job.setOnSucceeded(e -> {
                toggleUi(true);
                showRunning(false, null);
                Path out = job.getValue();
                status("Hoàn tất: " + out.getFileName() + " (Pipeline đọc từ data/collections/" + collection0 + "/)");
                showSample(out, 30);
            });

            job.setOnFailed(e -> {
                toggleUi(true);
                showRunning(false, null);
                Throwable ex = job.getException();
                String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                if (msg.toLowerCase().contains("yt.apikey")) {
                    msg += "\n\nGợi ý: Đặt YouTube API key tại collector/conf.properties:\nyt.apiKey=YOUR_YT_API_KEY";
                }
                status("Lỗi: " + msg);
                alert("Thu thập thất bại:\n" + msg);
            });

            Thread tRun = new Thread(job, "collector-ui-run");
            tRun.setDaemon(true);
            tRun.start();

        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Lỗi: " + ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage st = (Stage) closeBtn.getScene().getWindow();
        st.close();
    }

    /* ===== helpers ===== */

    private void toggleUi(boolean enable) {
        if (runBtn != null) runBtn.setDisable(!enable);
        if (keywordsField != null) keywordsField.setDisable(!enable);
        if (newsCheck != null) newsCheck.setDisable(!enable);
        if (youtubeCheck != null) youtubeCheck.setDisable(!enable);
        if (redditCheck != null) redditCheck.setDisable(!enable);
        if (fromDate != null) fromDate.setDisable(!enable);
        if (toDate != null) toDate.setDisable(!enable);
        if (limitField != null) limitField.setDisable(!enable);
        if (collectionField != null) collectionField.setDisable(!enable);
        if (closeBtn != null) closeBtn.setDisable(!enable);
    }

    private void showRunning(boolean show, String text) {
        if (runningBox != null) {
            runningBox.setVisible(show);
            runningBox.setManaged(show);
        }
        if (statusLabel != null && text != null) statusLabel.setText(text);
    }

    private void status(String s) {
        if (statusLabel != null) statusLabel.setText(s);
    }

    private static String safeText(TextField f) {
        return f == null || f.getText() == null ? "" : f.getText().trim();
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /* Hiển thị mẫu dữ liệu: đọc file JSONL, rút gọn các trường chính */
    private void showSample(Path jsonlFile, int maxLines) {
        if (sampleList == null) return;
        sampleList.getItems().clear();

        try (Stream<String> lines = Files.lines(jsonlFile)) {
            lines.limit(maxLines).map(this::formatSampleLine).forEach(sampleList.getItems()::add);
        } catch (Exception e) {
            sampleList.getItems().add("Không thể đọc mẫu dữ liệu: " + e.getMessage());
        }
    }

    private String formatSampleLine(String json) {
        String platform = jget(json, "platform");
        String ts       = jget(json, "ts");
        String text     = jget(json, "text");
        if (text.length() > 140) text = text.substring(0, 140) + "…";
        return "[" + platform + "] " + ts + " — " + text;
    }

    /* Trích chuỗi JSON đơn giản (không phụ thuộc thư viện JSON) */
    private static String jget(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return "";
        i = json.indexOf(':', i);
        if (i < 0) return "";
        int q1 = json.indexOf('"', i + 1);
        if (q1 < 0) return "";
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int k = q1 + 1; k < json.length(); k++) {
            char c = json.charAt(k);
            if (esc) {
                if (c == 'n') sb.append(' ');
                else sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}