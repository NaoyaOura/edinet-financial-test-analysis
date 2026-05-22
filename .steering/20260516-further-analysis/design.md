# 設計：追加分析3件

## 分析① --exclude-unknown フラグ

### 変更箇所: AnalyzeCommand.java

```java
boolean excludeUnknown = hasFlag(args, "--exclude-unknown");
if (excludeUnknown) {
    records = records.stream()
        .filter(r -> !"UNKNOWN".equals(r.sector33Code()))
        .toList();
}
```

分析条件セクションにも除外件数を出力する。

### フラグ組み合わせ例
```
analyze --exclude-unknown
analyze --exclude-unknown --industry it
analyze --exclude-unknown --industry retail
```

---

## 分析② 前期業績コントロール仕様

### 変更箇所: VariableSpec.java allCombinations()

現在のコントロールセット（3種）に加え、以下2種を追加:

```java
new ControlSet(
    List.of("log(売上高)", "前期営業利益率", "小売業D"),
    List.of(MergedRecord::logNetSales,
            MergedRecord::operatingMargin,  // t 期の値がコントロール
            r -> r.retailDummy())
),
new ControlSet(
    List.of("log(売上高)", "前期ROA", "小売業D"),
    List.of(MergedRecord::logNetSales,
            MergedRecord::roa,
            r -> r.retailDummy())
)
```

注意: MultiModelAnalyzer の runLagRegression では
current（t期）の値がコントロールに使われるため、
これは「前期（t期）の営業利益率・ROA」を意味し、
逆因果検証のコントロール変数として正しく機能する。

---

## 分析③ 二値言及変数

### 変更箇所: MergedRecord.java

```java
// ─── 二値言及変数 ──────────────────────────────────────
public double dxMention()  { return dxScore  > 0 ? 1.0 : 0.0; }
public double aiMention()  { return aiScore  > 0 ? 1.0 : 0.0; }
public double anyMention() { return totalScore > 0 ? 1.0 : 0.0; }
```

### 変更箇所: VariableSpec.java allCombinations()

キーワード変数リストに追加:

```java
new KeywordVar("dxMention",  MergedRecord::dxMention),
new KeywordVar("aiMention",  MergedRecord::aiMention),
new KeywordVar("anyMention", MergedRecord::anyMention)
```

これにより 4→7 キーワード × 5 コントロールセット × 4 目的変数 = 最大 140 仕様になる。
（定数列除去により実際の計算仕様数はこれより少ない）

---

## 実装順序

1. MergedRecord.java に二値変数メソッドを追加（B-1 と同時）
2. VariableSpec.java に前期業績コントロールセット + 二値キーワードを追加
3. AnalyzeCommand.java に --exclude-unknown フラグを追加
4. mvn compile で確認
