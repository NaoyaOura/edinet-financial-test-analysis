# 設計：業種別分析の追加

## A. --industry オプション

### 変更箇所
- `AnalyzeCommand.java`
  - `--industry` オプションを parseStringOption で受け取る（デフォルト `"all"`）
  - `"all"` 以外の場合、records を `sector33Code` でフィルタしてから各 Analyzer に渡す
  - フィルタ後の業種名をレポートヘッダに出力する

### フィルタロジック
```java
List<MergedRecord> industryFiltered = switch (industry) {
    case "retail" -> records.stream().filter(MergedRecord::isRetail).toList();
    case "it"     -> records.stream().filter(MergedRecord::isIT).toList();
    default       -> records;  // "all"
};
```

### 実行例
```
analyze --industry retail
analyze --industry it
```

---

## B. 交差項仕様の追加

### 変更箇所
- `VariableSpec.java`（または `MultiModelAnalyzer.java` 内の仕様リスト）
  - 交差項列を MergedRecord から計算して VariableSpec に追加できる仕組みを作る

### 交差項の計算方法

MergedRecord に以下のメソッドを追加する：
```java
/** dxScore × 情報通信業ダミーの交差項 */
public double dxScoreXit()     { return dxScore * itDummy(); }
/** aiScore × 情報通信業ダミーの交差項 */
public double aiScoreXit()     { return aiScore * itDummy(); }
/** dxScore × 小売業ダミーの交差項 */
public double dxScoreXretail() { return dxScore * retailDummy(); }
```

### MultiModelAnalyzer の仕様追加

既存の仕様リストに以下を追加（ロジック変更最小で追加できる形）：
- `dxScore×IT_dummy`（+ log(売上高) + 業種D）
- `aiScore×IT_dummy`（同上）
- `dxScore×retailDummy`（同上）

### 出力フォーマット
変数探索テーブルの「キーワード変数」列に `dxScore×IT` のように表示する。

---

## 依存関係・注意点

- サンプルサイズが業種別に激減する可能性がある（特に小売業）→ n を必ず出力
- 業種フィルタ後に定数列除外が増える場合は既存ロジックで対応済み
