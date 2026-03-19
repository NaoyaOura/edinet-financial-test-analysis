package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.edinet.DocumentDownloader;
import jp.ac.example.xbrl.edinet.EdinetApiClient;

/**
 * EDINET 書類 ZIP をダウンロード・展開するコマンド。
 *
 * 使い方:
 *   download [--year <年度>] [--edinet-code <コード>] [--force]
 */
public class DownloadCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public DownloadCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) {
        int fiscalYear = parseIntOption(args, "--year", 0);
        String edinetCode = parseStringOption(args, "--edinet-code");
        boolean force = hasFlag(args, "--force");

        System.out.println("=== download 開始 ===");
        if (fiscalYear > 0) System.out.printf("年度: %d%n", fiscalYear);
        if (edinetCode != null) System.out.printf("EDINETコード: %s%n", edinetCode);
        if (force) System.out.println("--force: 完了済みも再ダウンロードします");

        EdinetApiClient apiClient = new EdinetApiClient(config.getEdinetApiKey());
        DocumentDownloader downloader = new DocumentDownloader(apiClient, dbManager, config.getRawDataDir());

        try {
            int count = downloader.download(fiscalYear, edinetCode, force);
            System.out.printf("=== download 完了: %d件 ===%n", count);
        } catch (Exception e) {
            System.err.println("download 失敗: " + e.getMessage());
        }
    }

    private int parseIntOption(String[] args, String key, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private String parseStringOption(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }
}
