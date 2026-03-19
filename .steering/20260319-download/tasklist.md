# タスクリスト：download コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. EdinetApiClient にZIPダウンロードメソッドを追加
- [x] `fetchDocumentZip(docId)` メソッドを追加する

### 2. DocumentDownloader の実装
- [x] `edinet/DocumentDownloader.java` を実装する（ZIP取得・展開・進捗更新）
- [x] `edinet/DocumentDownloaderTest.java` を実装する（ZIP展開ロジックの単体テスト）
- [x] `mvn test` が通ることを確認する

### 3. DownloadCommand の実装
- [x] `command/DownloadCommand.java` を実装する（オプション解析・実行制御）
- [x] `Main.java` の `download` ケースを `DownloadCommand` に接続する

### 4. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ること（17テスト全PASS）
- [ ] `mvn exec:java -Dexec.args="download --year 2023"` で展開されることを確認する
