package jp.ac.example.xbrl.edinet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * EDINET API との HTTP 通信を担うクラス。
 * APIキーはクエリパラメータ Subscription-Key で渡す。
 */
public class EdinetApiClient {

    private static final String BASE_URL = "https://disclosure.edinet-fsa.go.jp/api/v2";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 1000L;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EdinetApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 指定日付の書類一覧を取得する。
     * @param date YYYY-MM-DD 形式
     * @return レスポンスのルートJsonNode
     */
    public JsonNode fetchDocumentList(String date) throws IOException, InterruptedException {
        String url = String.format("%s/documents.json?date=%s&type=2&Subscription-Key=%s",
            BASE_URL, date, apiKey);
        return getWithRetry(url);
    }

    /**
     * 指定書類IDのZIPファイルをバイト配列で取得する。
     * @param docId EDINET書類ID
     * @return ZIPファイルのバイト配列
     */
    public byte[] fetchDocumentZip(String docId) throws IOException, InterruptedException {
        String url = String.format("%s/documents/%s?type=1&Subscription-Key=%s",
            BASE_URL, docId, apiKey);
        return getBytesWithRetry(url);
    }

    /**
     * 指定URLにGETリクエストを送り、レスポンスをバイト配列で返す。
     * 4xx/5xx の場合は最大MAX_RETRY回リトライする。
     */
    private byte[] getBytesWithRetry(String url) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            }

            long waitMs = response.statusCode() == 429 ? RETRY_INTERVAL_MS * 5 : RETRY_INTERVAL_MS;
            lastException = new IOException(
                String.format("EDINET API エラー: HTTPステータス %d (試行 %d/%d) URL=%s",
                    response.statusCode(), attempt, MAX_RETRY, url)
            );

            if (attempt < MAX_RETRY) {
                Thread.sleep(waitMs);
            }
        }
        throw lastException;
    }

    /**
     * 指定URLにGETリクエストを送り、レスポンスをJsonNodeで返す。
     * 4xx/5xx の場合は最大MAX_RETRY回リトライする。
     */
    private JsonNode getWithRetry(String url) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }

            // レート制限（429）はより長く待つ
            long waitMs = response.statusCode() == 429 ? RETRY_INTERVAL_MS * 5 : RETRY_INTERVAL_MS;
            String location = response.headers().firstValue("Location").orElse("(なし)");
            lastException = new IOException(
                String.format("EDINET API エラー: HTTPステータス %d (試行 %d/%d) URL=%s Location=%s",
                    response.statusCode(), attempt, MAX_RETRY, url, location)
            );

            if (attempt < MAX_RETRY) {
                Thread.sleep(waitMs);
            }
        }
        throw lastException;
    }
}
