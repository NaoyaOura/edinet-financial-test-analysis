package jp.ac.example.xbrl.config;

/**
 * 環境変数・設定値の読み込みを担うクラス。
 * 起動時に必須の環境変数が設定されていない場合は即時エラーとする。
 */
public class AppConfig {

    private static AppConfig instance;

    private final String edinetApiKey;
    private final String dbPath;
    private final String rawDataDir;
    private final String outputDir;

    private AppConfig() {
        // 必須: APIキー
        String apiKey = System.getenv("EDINET_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "環境変数 EDINET_API_KEY が設定されていません。" +
                "実行前に export EDINET_API_KEY=your_api_key を設定してください。"
            );
        }
        this.edinetApiKey = apiKey;

        // 任意: DBパス（デフォルト: ./data/xbrl.db）
        String dbPathEnv = System.getenv("DB_PATH");
        this.dbPath = (dbPathEnv != null && !dbPathEnv.isBlank()) ? dbPathEnv : "./data/xbrl.db";

        // 任意: ZIPダウンロード展開先（デフォルト: ./data/raw）
        String rawDataDirEnv = System.getenv("RAW_DATA_DIR");
        this.rawDataDir = (rawDataDirEnv != null && !rawDataDirEnv.isBlank()) ? rawDataDirEnv : "./data/raw";

        // 任意: 出力先ディレクトリ（デフォルト: ./output）
        String outputDirEnv = System.getenv("OUTPUT_DIR");
        this.outputDir = (outputDirEnv != null && !outputDirEnv.isBlank()) ? outputDirEnv : "./output";
    }

    /**
     * シングルトンインスタンスを返す。
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    /**
     * テスト用: インスタンスをリセットする。
     */
    static void resetInstance() {
        instance = null;
    }

    public String getEdinetApiKey() {
        return edinetApiKey;
    }

    public String getDbPath() {
        return dbPath;
    }

    public String getRawDataDir() {
        return rawDataDir;
    }

    public String getOutputDir() {
        return outputDir;
    }
}
