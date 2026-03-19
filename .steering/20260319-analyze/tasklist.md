# タスクリスト：analyze コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. 共通データ層
- [x] `analysis/MergedRecord.java` を実装する（統合データレコード・派生指標）
- [x] `analysis/AnalysisDataLoader.java` を実装する（DB → List<MergedRecord>）

### 2. 各アナライザの実装
- [x] `analysis/GroupComparator.java` を実装する（四分位グループ比較・t検定・ANOVA）
- [x] `analysis/LagRegressionAnalyzer.java` を実装する（ラグOLS回帰）
- [x] `analysis/DifferenceInDifferences.java` を実装する（DiD）
- [x] `analysis/PanelDataAnalyzer.java` を実装する（within推定量）

### 3. レポート出力・コマンド
- [x] `report/TextReporter.java` を実装する（stdout + ファイル出力）
- [x] `command/AnalyzeCommand.java` を実装する（オプション解析・各アナライザ呼び出し）
- [x] `Main.java` の `analyze` ケースを接続する

### 4. テスト
- [x] `analysis/GroupComparatorTest.java` を実装する
- [x] `analysis/LagRegressionAnalyzerTest.java` を実装する
- [x] `analysis/DifferenceInDifferencesTest.java` を実装する
- [x] `analysis/PanelDataAnalyzerTest.java` を実装する

### 5. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ることを確認する（69テストすべてPASS）
- [ ] `mvn exec:java -Dexec.args="analyze"` でレポートが出力されることを確認する
