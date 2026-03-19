# タスクリスト：fetch-list コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. DocumentListDao の実装
- [x] `db/DocumentListDao.java` を実装する（INSERT・SELECT）

### 2. EdinetApiClient の実装
- [x] `edinet/EdinetApiClient.java` を実装する（HTTP通信・リトライ・JSON デシリアライズ）
- [x] `edinet/EdinetApiClientTest.java` を実装する（モックレスポンスで単体テスト）
- [x] `mvn test` が通ることを確認する

### 3. DocumentListFetcher の実装
- [x] `edinet/DocumentListFetcher.java` を実装する（年度ループ・フィルタ・DB保存）

### 4. FetchListCommand の実装
- [x] `command/FetchListCommand.java` を実装する（`--year` オプション解析・進捗表示）
- [x] `Main.java` の `fetch-list` ケースを `FetchListCommand` に接続する

### 5. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ること（13テスト全PASS）
- [ ] `mvn exec:java -Dexec.args="fetch-list --year 2023"` で書類一覧が取得できることを確認する
