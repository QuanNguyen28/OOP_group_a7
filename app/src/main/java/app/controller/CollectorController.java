package app.controller;

import collector.api.CollectorService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CollectorController {

    @FXML private TextField keywordsField;
    @FXML private CheckBox newsCheck;
    @FXML private CheckBox youtubeCheck;
    @FXML private CheckBox redditCheck;

    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;

    @FXML private TextField limitField;
    @FXML private TextField collectionField;

    @FXML private TextField saveDirField;
    @FXML private Button browseBtn;
    @FXML private Button runBtn;
    @FXML private Button closeBtn;

    @FXML private Label statusLabel;
    @FXML private TextArea logArea;

    public void init(Path projectRoot) {
        if (saveDirField != null) {
            Path def = projectRoot.resolve("data/collections");
            saveDirField.setText(def.toAbsolutePath().toString());
        }
        if (fromDate != null && toDate != null) {
            toDate.setValue(LocalDate.now());
            fromDate.setValue(LocalDate.now().minusDays(30));
        }
        if (limitField != null) limitField.setText("300");
        if (collectionField != null) collectionField.setText("default");
        if (statusLabel != null) statusLabel.setText("Sẵn sàng");
        if (logArea != null) logArea.setText("");
        if (newsCheck != null) newsCheck.setSelected(true);
        if (youtubeCheck != null) youtubeCheck.setSelected(true);
    }

    /** Alias cho FXML đang gọi #onChooseSaveDir */
    @FXML
    private void onChooseSaveDir() { onBrowse(); }

    @FXML
    private void onBrowse() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Chọn thư mục lưu dữ liệu");
        Stage st = (Stage) browseBtn.getScene().getWindow();
        var dir = dc.showDialog(st);
        if (dir != null) saveDirField.setText(dir.toPath().toAbsolutePath().toString());
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

            Path saveDir = Path.of(safeText(saveDirField).isBlank() ? "data/collections" : saveText(saveDirField));
            Files.createDirectories(saveDir);
            if (!Files.isWritable(saveDir)) { alert("Thư mục không ghi được: " + saveDir.toAbsolutePath()); return; }

            CollectorService svc = new CollectorService();
            if (newsCheck.isSelected())    svc.add("news");
            if (youtubeCheck.isSelected()) svc.add("youtube");
            if (redditCheck.isSelected())  svc.add("reddit");

            log("[CollectorUI] keywords=" + String.join(", ", keywords)
                + " | sources=" + sourcesText()
                + " | collection=" + collection
                + " | from=" + from + " | to=" + to
                + " | limit=" + limit
                + " | saveDir=" + saveDir.toAbsolutePath());

            final List<String> keywords0 = List.copyOf(keywords);
            final Instant from0 = from, to0 = to;
            final int limit0 = limit;
            final String collection0 = collection;
            final Path saveDir0 = saveDir;

            toggleUi(false);
            if (statusLabel != null) statusLabel.setText("Đang thu thập…");

            Task<Path> job = new Task<>() {
                @Override protected Path call() throws Exception {
                    return svc.run(keywords0, from0, to0, limit0, collection0, saveDir0, null);
                }
            };

            job.setOnSucceeded(e -> {
                toggleUi(true);
                Path out = job.getValue();
                log("[CollectorUI] DONE: " + out.toAbsolutePath());
                if (statusLabel != null) statusLabel.setText("Hoàn tất: " + out.getFileName()
                        + " (Pipeline đọc từ data/collections/" + collection0 + "/)");
            });

            job.setOnFailed(e -> {
                toggleUi(true);
                Throwable ex = job.getException();
                String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                log("[CollectorUI] ERROR: " + msg);
                if (msg.toLowerCase().contains("yt.apikey")) {
                    log("""
                        Gợi ý: Đặt YouTube API key tại collector/conf.properties:
                          yt.apiKey=YOUR_YT_API_KEY
                        (hoặc export YT_API_KEY / hoặc -Dyt.apiKey=...)
                        """);
                }
                if (statusLabel != null) statusLabel.setText("Lỗi: " + msg);
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

    /* helpers */

    private void toggleUi(boolean enable) {
        if (runBtn != null) runBtn.setDisable(!enable);
        if (browseBtn != null) browseBtn.setDisable(!enable);
        if (keywordsField != null) keywordsField.setDisable(!enable);
        if (newsCheck != null) newsCheck.setDisable(!enable);
        if (youtubeCheck != null) youtubeCheck.setDisable(!enable);
        if (redditCheck != null) redditCheck.setDisable(!enable);
        if (fromDate != null) fromDate.setDisable(!enable);
        if (toDate != null) toDate.setDisable(!enable);
        if (limitField != null) limitField.setDisable(!enable);
        if (collectionField != null) collectionField.setDisable(!enable);
        if (saveDirField != null) saveDirField.setDisable(!enable);
        if (closeBtn != null) closeBtn.setDisable(!enable);
    }

    private void log(String s) {
        if (logArea != null) {
            if (!logArea.getText().isBlank()) logArea.appendText("\n");
            logArea.appendText(s);
        } else {
            System.out.println(s);
        }
    }

    private static String safeText(TextField f) {
        return f == null || f.getText() == null ? "" : f.getText().trim();
    }
    private static String saveText(TextField f) {
        return f.getText() == null ? "" : f.getText();
    }
    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
    private String sourcesText() {
        List<String> s = new ArrayList<>();
        if (newsCheck != null && newsCheck.isSelected()) s.add("news");
        if (youtubeCheck != null && youtubeCheck.isSelected()) s.add("youtube");
        if (redditCheck != null && redditCheck.isSelected()) s.add("reddit");
        return s.toString();
    }
}