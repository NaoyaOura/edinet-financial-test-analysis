# 設計：プロジェクトセットアップ＋コア基盤

## 実装対象ファイル

```
pom.xml
src/main/java/jp/ac/example/xbrl/
├── Main.java
├── command/
│   └── StatusCommand.java
├── config/
│   └── AppConfig.java
└── db/
    ├── DatabaseManager.java
    ├── CompanyDao.java
    ├── FinancialDataDao.java
    ├── KeywordScoreDao.java
    └── TaskProgressDao.java

src/test/java/jp/ac/example/xbrl/
├── config/
│   └── AppConfigTest.java
└── db/
    └── DatabaseManagerTest.java
```

## 各クラスの設計

### `pom.xml`

- Java 17 設定（`maven.compiler.source/target`）
- 依存ライブラリ：`sqlite-jdbc`・`jackson-databind`・`commons-math3`・`junit-jupiter`
- `exec-maven-plugin` で `Main.java` をエントリーポイントとして設定

### `AppConfig`

- コンストラクタで `System.getenv("EDINET_API_KEY")` を読み込む
- 未設定・空文字の場合は `IllegalStateException` をスロー
- `DB_PATH`・`RAW_DATA_DIR`・`OUTPUT_DIR` はデフォルト値付きで読み込む
- シングルトンとして保持

### `DatabaseManager`

- `AppConfig` から `DB_PATH` を受け取り、SQLite接続を確立する
- 初期化時に以下のテーブルをすべて `CREATE TABLE IF NOT EXISTS` で作成する：
  - `companies`
  - `document_list`
  - `financial_data`
  - `keyword_scores`
  - `task_progress`
- `getConnection()` で接続を返す

### `Main`

- 第1引数をサブコマンド名として受け取り、対応する `Command` クラスに処理を委譲する
- 未知のコマンドの場合はコマンド一覧をヘルプ表示して終了する

### `StatusCommand`

- `task_progress` テーブルを集計し、タスク×ステータス別の件数を標準出力に表示する
- データがない場合は「データなし」と表示する
