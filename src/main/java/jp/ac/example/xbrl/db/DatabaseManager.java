package jp.ac.example.xbrl.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite接続の確立と全テーブルの初期化を担うクラス。
 */
public class DatabaseManager {

    private final String dbPath;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * SQLite接続を返す。
     * dbPathが "file:" で始まる場合はインメモリ等の特殊URLとして扱い、ファイル生成をスキップする。
     */
    public Connection getConnection() throws SQLException {
        if (!dbPath.startsWith("file:")) {
            // 通常のファイルパス: 親ディレクトリを作成する
            File dbFile = new File(dbPath);
            if (dbFile.getParentFile() != null) {
                dbFile.getParentFile().mkdirs();
            }
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 全テーブルをCREATE TABLE IF NOT EXISTSで初期化する。
     */
    public void initializeSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // 企業マスタ
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS companies (
                    edinetCode      TEXT PRIMARY KEY,
                    companyName     TEXT NOT NULL,
                    industryCode    TEXT,
                    industryCategory TEXT
                )
            """);

            // 書類一覧
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS document_list (
                    docId           TEXT PRIMARY KEY,
                    edinetCode      TEXT NOT NULL,
                    fiscalYear      INTEGER,
                    submissionDate  TEXT,
                    docDescription  TEXT,
                    FOREIGN KEY (edinetCode) REFERENCES companies(edinetCode)
                )
            """);

            // 財務指標
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS financial_data (
                    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                    edinetCode              TEXT NOT NULL,
                    fiscalYear              INTEGER NOT NULL,
                    netSales                REAL,
                    grossProfit             REAL,
                    operatingIncome         REAL,
                    ordinaryIncome          REAL,
                    profitLoss              REAL,
                    assets                  REAL,
                    currentAssets           REAL,
                    currentLiabilities      REAL,
                    liabilities             REAL,
                    equity                  REAL,
                    cashAndDeposits         REAL,
                    inventories             REAL,
                    sgaExpenses             REAL,
                    personnelExpenses       REAL,
                    numberOfEmployees       INTEGER,
                    researchAndDevelopment  REAL,
                    software                REAL,
                    intangibleAssets        REAL,
                    capitalExpenditure      REAL,
                    operatingCashFlow       REAL,
                    investingCashFlow       REAL,
                    FOREIGN KEY (edinetCode) REFERENCES companies(edinetCode),
                    UNIQUE (edinetCode, fiscalYear)
                )
            """);

            // キーワードスコア
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS keyword_scores (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    edinetCode      TEXT NOT NULL,
                    fiscalYear      INTEGER NOT NULL,
                    genAiScore      REAL,
                    aiScore         REAL,
                    dxScore         REAL,
                    totalScore      REAL,
                    documentLength  INTEGER,
                    FOREIGN KEY (edinetCode) REFERENCES companies(edinetCode),
                    UNIQUE (edinetCode, fiscalYear)
                )
            """);

            // 進捗管理
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_progress (
                    docId           TEXT NOT NULL,
                    task            TEXT NOT NULL,
                    status          TEXT NOT NULL DEFAULT 'PENDING',
                    errorMessage    TEXT,
                    updatedAt       TEXT,
                    PRIMARY KEY (docId, task)
                )
            """);
        }
    }
}
