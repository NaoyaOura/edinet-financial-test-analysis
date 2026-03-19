# タスクリスト：プロジェクトセットアップ＋コア基盤

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. Mavenプロジェクト設定
- [x] `pom.xml` を作成する（Java 17・依存ライブラリ・exec-maven-plugin）
- [x] `src/main/java/jp/ac/example/xbrl/` ディレクトリ構成を作成する
- [x] `src/test/java/jp/ac/example/xbrl/` ディレクトリ構成を作成する
- [x] `mvn compile` が通ることを確認する

### 2. AppConfig の実装
- [x] `config/AppConfig.java` を実装する
- [x] `config/AppConfigTest.java` を実装する（APIキー未設定時の例外・デフォルト値の検証）
- [x] `mvn test` が通ることを確認する

### 3. DatabaseManager の実装
- [x] `db/DatabaseManager.java` を実装する（接続・全テーブルのCREATE TABLE IF NOT EXISTS）
- [x] `db/DatabaseManagerTest.java` を実装する（一時ファイルSQLiteでスキーマ検証）
- [x] `mvn test` が通ることを確認する

### 4. DAOクラスの実装
- [x] `db/CompanyDao.java` を実装する（INSERT・SELECT）
- [x] `db/FinancialDataDao.java` を実装する（INSERT・SELECT）
- [x] `db/KeywordScoreDao.java` を実装する（INSERT・SELECT）
- [x] `db/TaskProgressDao.java` を実装する（INSERT・UPDATE・SELECT by status）

### 5. CLIエントリーポイントの実装
- [x] `Main.java` を実装する（サブコマンドのディスパッチ）
- [x] `command/StatusCommand.java` を実装する（進捗集計の表示）

### 6. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ることを確認する（10テスト全PASS）
- [ ] `EDINET_API_KEY` 未設定で起動エラーが出ることを確認する
- [ ] `mvn exec:java -Dexec.args="status"` でデータなし表示が出ることを確認する
