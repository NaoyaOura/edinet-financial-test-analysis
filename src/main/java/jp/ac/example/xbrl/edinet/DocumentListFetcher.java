package jp.ac.example.xbrl.edinet;

import com.fasterxml.jackson.databind.JsonNode;
import jp.ac.example.xbrl.db.CompanyDao;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.DocumentListDao;
import jp.ac.example.xbrl.db.TaskProgressDao;

import java.sql.Connection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 指定年度の有価証券報告書の書類一覧をEDINET APIから取得してDBに保存するクラス。
 */
public class DocumentListFetcher {

    /** 有価証券報告書の書類種別コード */
    private static final String DOC_TYPE_CODE_ANNUAL_REPORT = "120";

    private final EdinetApiClient apiClient;
    private final DatabaseManager dbManager;

    public DocumentListFetcher(EdinetApiClient apiClient, DatabaseManager dbManager) {
        this.apiClient = apiClient;
        this.dbManager = dbManager;
    }

    /**
     * 指定年度（4月1日〜翌年3月31日）の書類一覧を取得してDBに保存する。
     * 土日はスキップする。
     *
     * @param fiscalYear 対象年度（例: 2023 → 2023-04-01〜2024-03-31）
     * @return 新規登録した書類件数
     */
    public int fetch(int fiscalYear) throws Exception {
        LocalDate start = LocalDate.of(fiscalYear, 4, 1);
        LocalDate end = LocalDate.of(fiscalYear + 1, 3, 31);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        int totalSaved = 0;
        int totalDays = 0;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            // 土日はスキップ
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY
                    || current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }

            String date = current.format(formatter);
            totalDays++;

            try {
                int saved = fetchAndSaveForDate(date, fiscalYear);
                totalSaved += saved;

                if (saved > 0) {
                    System.out.printf("[%s] %d件の有価証券報告書を登録しました%n", date, saved);
                }

                // APIレート制限への配慮
                Thread.sleep(200);

            } catch (Exception e) {
                System.err.printf("[%s] 取得失敗: %s%n", date, e.getMessage());
            }

            current = current.plusDays(1);
        }

        System.out.printf("%n%d営業日を処理し、合計%d件の書類を登録しました%n", totalDays, totalSaved);
        return totalSaved;
    }

    /**
     * 指定日の書類一覧を取得し、有価証券報告書のみDBに保存する。
     */
    private int fetchAndSaveForDate(String date, int fiscalYear) throws Exception {
        JsonNode root = apiClient.fetchDocumentList(date);
        JsonNode results = root.path("results");

        if (!results.isArray()) {
            return 0;
        }

        int savedCount = 0;

        try (Connection conn = dbManager.getConnection()) {
            CompanyDao companyDao = new CompanyDao(conn);
            DocumentListDao documentListDao = new DocumentListDao(conn);
            TaskProgressDao taskProgressDao = new TaskProgressDao(conn);

            for (JsonNode doc : results) {
                String docTypeCode = doc.path("docTypeCode").asText("");

                // 有価証券報告書のみ対象
                if (!DOC_TYPE_CODE_ANNUAL_REPORT.equals(docTypeCode)) {
                    continue;
                }

                String docId = doc.path("docID").asText();
                String edinetCode = doc.path("edinetCode").asText();
                String filerName = doc.path("filerName").asText();
                String docDescription = doc.path("docDescription").asText();

                if (docId.isBlank() || edinetCode.isBlank()) {
                    continue;
                }

                // 企業マスタに登録（業種は後続フェーズで更新するため UNKNOWN として登録）
                companyDao.upsert(edinetCode, filerName, null, "UNKNOWN");

                // 書類一覧に登録（重複はスキップ）
                documentListDao.insertIfAbsent(docId, edinetCode, fiscalYear, date, docDescription);

                // ダウンロードタスクを PENDING で登録
                taskProgressDao.upsert(docId, "DOWNLOAD", TaskProgressDao.Status.PENDING, null);

                savedCount++;
            }
        }

        return savedCount;
    }
}
