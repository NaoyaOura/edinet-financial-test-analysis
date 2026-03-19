package jp.ac.example.xbrl.edinet;

import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.DocumentListDao;
import jp.ac.example.xbrl.db.TaskProgressDao;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * EDINET から書類 ZIP をダウンロードし、ローカルに展開するクラス。
 * task_progress テーブルで進捗を管理し、途中失敗時は再実行で続きから処理できる。
 */
public class DocumentDownloader {

    private final EdinetApiClient apiClient;
    private final DatabaseManager dbManager;
    private final String rawDataDir;

    public DocumentDownloader(EdinetApiClient apiClient, DatabaseManager dbManager, String rawDataDir) {
        this.apiClient = apiClient;
        this.dbManager = dbManager;
        this.rawDataDir = rawDataDir;
    }

    /**
     * 未ダウンロードの書類を処理する。
     *
     * @param fiscalYear    対象年度（0以下の場合は全年度）
     * @param edinetCode    対象企業（nullの場合は全企業）
     * @param force         trueの場合はDONE済みも再ダウンロード
     * @return 処理した書類件数
     */
    public int download(int fiscalYear, String edinetCode, boolean force) throws Exception {
        List<String> targetDocIds = resolveTargetDocIds(fiscalYear, edinetCode, force);

        if (targetDocIds.isEmpty()) {
            System.out.println("ダウンロード対象の書類がありません。");
            return 0;
        }

        System.out.printf("ダウンロード対象: %d件%n", targetDocIds.size());

        int success = 0;
        int error = 0;

        for (String docId : targetDocIds) {
            try {
                updateProgress(docId, TaskProgressDao.Status.IN_PROGRESS, null);
                downloadAndExtract(docId);
                updateProgress(docId, TaskProgressDao.Status.DONE, null);
                success++;
                System.out.printf("[%d/%d] %s 完了%n", success + error, targetDocIds.size(), docId);

                // APIレート制限への配慮
                Thread.sleep(300);

            } catch (Exception e) {
                error++;
                String message = e.getMessage();
                updateProgress(docId, TaskProgressDao.Status.ERROR, message);
                System.err.printf("[エラー] %s: %s%n", docId, message);
            }
        }

        System.out.printf("%n完了: %d件 / エラー: %d件%n", success, error);
        return success;
    }

    /**
     * 1件の書類をダウンロードして展開する。
     */
    void downloadAndExtract(String docId) throws IOException, InterruptedException {
        byte[] zipBytes = apiClient.fetchDocumentZip(docId);
        File destDir = new File(rawDataDir, docId);
        extractZip(zipBytes, destDir);
    }

    /**
     * ZIPバイト配列を指定ディレクトリに展開する。
     */
    void extractZip(byte[] zipBytes, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("展開先ディレクトリの作成に失敗しました: " + destDir.getAbsolutePath());
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    new File(destDir, entry.getName()).mkdirs();
                    continue;
                }

                File outFile = new File(destDir, entry.getName());

                // ZIPスリップ攻撃対策: 展開先が destDir の外に出ないことを確認する
                if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("不正なZIPエントリを検出しました: " + entry.getName());
                }

                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    zis.transferTo(fos);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 処理対象の書類IDリストを決定する。
     */
    private List<String> resolveTargetDocIds(int fiscalYear, String edinetCode, boolean force)
            throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            TaskProgressDao taskProgressDao = new TaskProgressDao(conn);
            DocumentListDao documentListDao = new DocumentListDao(conn);

            if (edinetCode != null) {
                // 特定企業の書類IDを document_list から取得して未完了分を返す
                return documentListDao.findByEdinetCode(edinetCode).stream()
                    .map(DocumentListDao.DocumentRecord::docId)
                    .filter(docId -> {
                        try {
                            return force || !taskProgressDao.isDone(docId, "DOWNLOAD");
                        } catch (Exception e) {
                            return true;
                        }
                    })
                    .toList();
            }

            return taskProgressDao.findIncompleteDocIds("DOWNLOAD", force);
        }
    }

    /**
     * task_progress を更新する。
     */
    private void updateProgress(String docId, TaskProgressDao.Status status, String errorMessage) {
        try (Connection conn = dbManager.getConnection()) {
            new TaskProgressDao(conn).upsert(docId, "DOWNLOAD", status, errorMessage);
        } catch (Exception e) {
            System.err.println("進捗更新失敗: " + e.getMessage());
        }
    }
}
