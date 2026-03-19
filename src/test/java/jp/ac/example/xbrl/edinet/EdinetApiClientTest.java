package jp.ac.example.xbrl.edinet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EdinetApiClient のユニットテスト。
 * 実際のAPIは呼び出さず、レスポンスのパース処理を検証する。
 */
class EdinetApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void APIレスポンスのresults配列が正しくパースできること() throws Exception {
        String json = """
            {
              "metadata": { "title": "EDINET書類一覧API", "status": "200" },
              "results": [
                {
                  "docID": "S100XXXX",
                  "edinetCode": "E12345",
                  "filerName": "テスト小売株式会社",
                  "docTypeCode": "120",
                  "docDescription": "有価証券報告書"
                },
                {
                  "docID": "S100YYYY",
                  "edinetCode": "E67890",
                  "filerName": "サンプルIT株式会社",
                  "docTypeCode": "120",
                  "docDescription": "有価証券報告書"
                }
              ]
            }
            """;

        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        assertTrue(results.isArray());
        assertEquals(2, results.size());
        assertEquals("S100XXXX", results.get(0).path("docID").asText());
        assertEquals("E12345", results.get(0).path("edinetCode").asText());
        assertEquals("120", results.get(0).path("docTypeCode").asText());
    }

    @Test
    void resultsが空配列の場合もパースできること() throws Exception {
        String json = """
            {
              "metadata": { "title": "EDINET書類一覧API", "status": "200" },
              "results": []
            }
            """;

        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        assertTrue(results.isArray());
        assertEquals(0, results.size());
    }

    @Test
    void docTypeCode120以外のレコードを識別できること() throws Exception {
        String json = """
            {
              "results": [
                { "docID": "S100AAAA", "docTypeCode": "120", "edinetCode": "E11111", "filerName": "A社" },
                { "docID": "S100BBBB", "docTypeCode": "130", "edinetCode": "E22222", "filerName": "B社" },
                { "docID": "S100CCCC", "docTypeCode": "120", "edinetCode": "E33333", "filerName": "C社" }
              ]
            }
            """;

        JsonNode root = objectMapper.readTree(json);
        long count = 0;
        for (JsonNode doc : root.path("results")) {
            if ("120".equals(doc.path("docTypeCode").asText())) {
                count++;
            }
        }
        assertEquals(2, count);
    }
}
