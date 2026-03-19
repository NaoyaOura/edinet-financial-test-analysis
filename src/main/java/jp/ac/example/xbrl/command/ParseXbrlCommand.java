package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.CompanyDao;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.DocumentListDao;
import jp.ac.example.xbrl.db.FinancialDataDao;
import jp.ac.example.xbrl.db.TaskProgressDao;
import jp.ac.example.xbrl.db.FinancialDataDao.FinancialRecord;
import jp.ac.example.xbrl.edinet.IndustryClassifier;
import jp.ac.example.xbrl.xbrl.FinancialDataExtractor;
import jp.ac.example.xbrl.xbrl.XbrlParser;

import java.io.File;
import java.sql.Connection;
import java.util.List;

/**
 * XBRLをパースして財務指標をDBに保存するコマンド。
 *
 * 使い方:
 *   parse-xbrl [--year <年度>] [--edinet-code <コード>] [--force]
 */
public class ParseXbrlCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public ParseXbrlCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) {
        int fiscalYear = parseIntOption(args, "--year", 0);
        String edinetCode = parseStringOption(args, "--edinet-code");
        boolean force = hasFlag(args, "--force");

        System.out.println("=== parse-xbrl 開始 ===");

        FinancialDataExtractor extractor = new FinancialDataExtractor(new XbrlParser());
        List<DocumentListDao.DocumentRecord> targets = resolveTargets(fiscalYear, edinetCode, force);

        if (targets.isEmpty()) {
            System.out.println("処理対象の書類がありません。");
            return;
        }

        System.out.printf("処理対象: %d件%n", targets.size());

        int success = 0;
        int error = 0;

        for (DocumentListDao.DocumentRecord doc : targets) {
            try {
                updateProgress(doc.docId(), TaskProgressDao.Status.IN_PROGRESS, null);

                File docDir = new File(config.getRawDataDir(), doc.docId());
                FinancialRecord record = extractor.extract(docDir, doc.edinetCode(), doc.fiscalYear());

                if (record == null) {
                    throw new Exception("XBRLファイルが見つかりません: " + docDir.getAbsolutePath());
                }

                try (Connection conn = dbManager.getConnection()) {
                    new FinancialDataDao(conn).upsert(record);
                }

                // 業種コードを XBRL から抽出して companies テーブルを更新
                String industryCode = extractor.extractIndustryCode(docDir);
                if (!industryCode.isBlank()) {
                    String industryCategory = IndustryClassifier.classify(industryCode);
                    try (Connection conn = dbManager.getConnection()) {
                        new CompanyDao(conn).updateIndustry(doc.edinetCode(), industryCode, industryCategory);
                    }
                }

                updateProgress(doc.docId(), TaskProgressDao.Status.DONE, null);
                success++;
                System.out.printf("[%d/%d] %s (%s) 完了%n",
                    success + error, targets.size(), doc.docId(), doc.edinetCode());

            } catch (Exception e) {
                error++;
                updateProgress(doc.docId(), TaskProgressDao.Status.ERROR, e.getMessage());
                System.err.printf("[エラー] %s: %s%n", doc.docId(), e.getMessage());
            }
        }

        System.out.printf("%n完了: %d件 / エラー: %d件%n", success, error);
    }

    private List<DocumentListDao.DocumentRecord> resolveTargets(int fiscalYear, String edinetCode, boolean force) {
        try (Connection conn = dbManager.getConnection()) {
            DocumentListDao docDao = new DocumentListDao(conn);
            TaskProgressDao progressDao = new TaskProgressDao(conn);

            List<DocumentListDao.DocumentRecord> allDocs = fiscalYear > 0
                ? docDao.findByFiscalYear(fiscalYear)
                : (edinetCode != null ? docDao.findByEdinetCode(edinetCode) : List.of());

            return allDocs.stream()
                .filter(doc -> {
                    try {
                        boolean downloadDone = progressDao.isDone(doc.docId(), "DOWNLOAD");
                        boolean parseDone = progressDao.isDone(doc.docId(), "PARSE_XBRL");
                        return downloadDone && (force || !parseDone);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
        } catch (Exception e) {
            System.err.println("対象書類の取得に失敗しました: " + e.getMessage());
            return List.of();
        }
    }

    private void updateProgress(String docId, TaskProgressDao.Status status, String errorMessage) {
        try (Connection conn = dbManager.getConnection()) {
            new TaskProgressDao(conn).upsert(docId, "PARSE_XBRL", status, errorMessage);
        } catch (Exception e) {
            System.err.println("進捗更新失敗: " + e.getMessage());
        }
    }

    private int parseIntOption(String[] args, String key, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                try { return Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException e) { return defaultValue; }
            }
        }
        return defaultValue;
    }

    private String parseStringOption(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (flag.equals(arg)) return true;
        return false;
    }
}
