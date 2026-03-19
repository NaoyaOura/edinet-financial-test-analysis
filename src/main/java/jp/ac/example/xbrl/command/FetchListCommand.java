package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.edinet.DocumentListFetcher;
import jp.ac.example.xbrl.edinet.EdinetApiClient;

/**
 * EDINET書類一覧を取得してDBに保存するコマンド。
 *
 * 使い方:
 *   fetch-list --year 2023
 */
public class FetchListCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public FetchListCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) {
        int fiscalYear = parseYear(args);
        if (fiscalYear < 0) {
            System.err.println("使い方: fetch-list --year <年度>");
            System.err.println("例: fetch-list --year 2023");
            return;
        }

        System.out.printf("=== fetch-list 開始: %d年度 ===%n", fiscalYear);
        System.out.printf("対象期間: %d-04-01 〜 %d-03-31%n", fiscalYear, fiscalYear + 1);

        EdinetApiClient apiClient = new EdinetApiClient(config.getEdinetApiKey());
        DocumentListFetcher fetcher = new DocumentListFetcher(apiClient, dbManager);

        try {
            int count = fetcher.fetch(fiscalYear);
            System.out.printf("=== fetch-list 完了: %d件登録 ===%n", count);
        } catch (Exception e) {
            System.err.println("fetch-list 失敗: " + e.getMessage());
        }
    }

    /**
     * 引数から --year の値を取り出す。見つからない場合は -1 を返す。
     */
    private int parseYear(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--year".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
