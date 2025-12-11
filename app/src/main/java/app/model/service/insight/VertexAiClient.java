package app.model.service.insight;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VertexAI client tối giản (REST) cho text generation (generateContent).
 * - Ký JWT RS256 từ Service Account JSON -> đổi access_token (OAuth JWT flow).
 * - Gọi REST /publishers/google/models/{model}:generateContent (publishedModel=true)
 *   hoặc /models/{model}:generateContent (publishedModel=false).
 * - Không dùng thư viện ngoài.
 */
public final class VertexAiClient implements LlmClient {
    private final boolean publishedModel;
    private final String modelId;
    private final String projectId;
    private final String location;
    private final Path saKeyFile;

    private final HttpClient http = HttpClient.newHttpClient();
    private volatile String cachedToken;
    private volatile long cachedTokenExpEpoch;

    public VertexAiClient(boolean publishedModel, String modelId, String projectId, String location, String saKeyFile) {
        this.publishedModel = publishedModel;
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.location = Objects.requireNonNull(location, "location");
        this.saKeyFile = Path.of(Objects.requireNonNull(saKeyFile, "saKeyFile"));
    }

    @Override public String modelId() { return modelId; }

    @Override
    public String complete(String prompt) {
        try {
            String token = getAccessTokenOrThrow();
            String url = buildEndpointUrl();
            String body = """
            {
              "contents": [
                { "role": "user", "parts": [ { "text": %s } ] }
              ]
            }
            """.formatted(jsonQuote(prompt == null ? "" : prompt));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                return "⚠️ VertexAI HTTP " + res.statusCode() + ":\n" + res.body();
            }

            String json = res.body();
            String text = extractFirstText(json);
            if (text == null || text.isBlank()) {
                return "⚠️ VertexAI: empty or unparseable response.";
            }
            return text.replace("\\n", "\n");
        } catch (Exception e) {
            return "⚠️ VertexAI token error: " + e.getMessage();
        }
    }

    private String buildEndpointUrl() {
        String base = "https://" + location + "-aiplatform.googleapis.com/v1"
                + "/projects/" + projectId
                + "/locations/" + location;
        return publishedModel
                ? base + "/publishers/google/models/" + modelId + ":generateContent"
                : base + "/models/" + modelId + ":generateContent";
    }

    private String getAccessTokenOrThrow() throws Exception {
        if (!Files.exists(saKeyFile)) {
            throw new IllegalStateException("SA key not found: " + saKeyFile.toAbsolutePath());
        }
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && (cachedTokenExpEpoch - 60) > now) {
            return cachedToken;
        }
        Token t = fetchAccessTokenFromSa(saKeyFile);
        if (t == null || t.accessToken == null || t.accessToken.isBlank()) {
            throw new IllegalStateException("cannot acquire access token (null)");
        }
        cachedToken = t.accessToken;
        cachedTokenExpEpoch = now + (t.expiresInSec > 0 ? t.expiresInSec : 3300);
        return cachedToken;
    }

    private static Token fetchAccessTokenFromSa(Path saJsonPath) throws Exception {
        String json = Files.readString(saJsonPath, StandardCharsets.UTF_8);

        // Lấy các trường bắt buộc từ SA JSON
        String clientEmail = jsonPick(json, "\"client_email\"\\s*:\\s*\"([^\"]+)\"");
        String privateKeyPem = jsonPick(json, "\"private_key\"\\s*:\\s*\"([^\"]+)\"");
        String tokenUri = jsonPick(json, "\"token_uri\"\\s*:\\s*\"([^\"]+)\"");
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = "https://oauth2.googleapis.com/token";
        }
        if (clientEmail == null || privateKeyPem == null) {
            throw new IllegalStateException("SA JSON missing client_email/private_key.");
        }

        // Chuẩn hoá PEM -> DER
        byte[] der = pemToDer(privateKeyPem);

        long iat = Instant.now().getEpochSecond();
        long exp = iat + 3600;

        String header = b64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claim = b64Url("{"
                + "\"iss\":\"" + esc(clientEmail) + "\","
                + "\"scope\":\"https://www.googleapis.com/auth/cloud-platform\","
                + "\"aud\":\"" + esc(tokenUri) + "\","
                + "\"exp\":" + exp + ","
                + "\"iat\":" + iat
                + "}");
        String unsigned = header + "." + claim;

        Signature sig = Signature.getInstance("SHA256withRSA");
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        sig.initSign(privateKey);
        sig.update(unsigned.getBytes(StandardCharsets.UTF_8));
        String signature = b64Url(sig.sign());
        String assertion = unsigned + "." + signature;

        String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + assertion;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("token HTTP " + res.statusCode() + ": " + res.body());
        }
        String body = res.body();
        String accessToken = jsonPick(body, "\"access_token\"\\s*:\\s*\"([^\"]+)\"");
        String expStr = jsonPick(body, "\"expires_in\"\\s*:\\s*(\\d+)");
        int expiresIn = 0;
        try { if (expStr != null) expiresIn = Integer.parseInt(expStr); } catch (Exception ignore) {}
        return new Token(accessToken, expiresIn);
    }

    private static String extractFirstText(String json) {
        // Trích đoạn đầu tiên của "text"
        Pattern p = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String jsonPick(String src, String regex) {
        Matcher m = Pattern.compile(regex).matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private static byte[] pemToDer(String privateKeyPem) {
        // privateKeyPem có thể chứa \n (escaped) hoặc xuống dòng thật
        String s = privateKeyPem.replace("\\n", "\n");
        s = s.replace("-----BEGIN PRIVATE KEY-----", "")
             .replace("-----END PRIVATE KEY-----", "")
             .replace("-----BEGIN RSA PRIVATE KEY-----", "")
             .replace("-----END RSA PRIVATE KEY-----", "")
             .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(s);
    }

    private static String b64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonQuote(String s) {
        String e = s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");
        return "\"" + e + "\"";
    }

    private record Token(String accessToken, int expiresInSec) {}
}