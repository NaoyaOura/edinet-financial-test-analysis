package jp.ac.example.xbrl;

import jp.ac.example.xbrl.command.AnalyzeCommand;
import jp.ac.example.xbrl.command.DownloadCommand;
import jp.ac.example.xbrl.command.ExportCommand;
import jp.ac.example.xbrl.command.FetchListCommand;
import jp.ac.example.xbrl.command.ParseXbrlCommand;
import jp.ac.example.xbrl.command.ScoreKeywordsCommand;
import jp.ac.example.xbrl.command.StatusCommand;
import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;

/**
 * エントリーポイント。
 * 第1引数をサブコマンド名として受け取り、対応するCommandクラスに処理を委譲する。
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        // 環境変数の検証（APIキー未設定時はここで終了）
        AppConfig config = AppConfig.getInstance();

        // DBの初期化
        DatabaseManager dbManager = new DatabaseManager(config.getDbPath());
        try {
            dbManager.initializeSchema();
        } catch (Exception e) {
            System.err.println("データベースの初期化に失敗しました: " + e.getMessage());
            System.exit(1);
        }

        // サブコマンド以降の引数を渡す
        String command = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);

        switch (command) {
            case "status" -> new StatusCommand(dbManager).execute();
            case "fetch-list" -> new FetchListCommand(config, dbManager).execute(subArgs);
            case "download" -> new DownloadCommand(config, dbManager).execute(subArgs);
            case "parse-xbrl" -> new ParseXbrlCommand(config, dbManager).execute(subArgs);
            case "score-keywords" -> new ScoreKeywordsCommand(config, dbManager).execute(subArgs);
            case "analyze" -> new AnalyzeCommand(config, dbManager).execute(subArgs);
            case "export" -> new ExportCommand(config, dbManager).execute(subArgs);
            default -> {
                System.err.println("不明なコマンド: " + command);
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
            使い方: mvn exec:java -Dexec.args="<コマンド> [オプション]"

            利用可能なコマンド:
              fetch-list      EDINET書類一覧を取得してDBに保存
              download        書類ZIPをダウンロード・展開
              parse-xbrl      XBRLをパースして財務指標をDBに保存
              score-keywords  テキストからキーワードスコアを算出してDBに保存
              analyze         統計分析を実行してレポートを出力
              export          SQLiteのデータをCSV出力
              status          各フェーズの進捗状況を表示
            """);
    }
}
