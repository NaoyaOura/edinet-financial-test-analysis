# タスクリスト：追加分析3件

## 分析① UNKNOWN業種除外

- [x] 1-1: `AnalyzeCommand.java` に `--exclude-unknown` フラグのパースを追加
- [x] 1-2: フラグ有効時に sector33Code='UNKNOWN' のレコードを除外するロジックを追加
- [x] 1-3: 分析条件セクションに除外件数を出力

## 分析② 前期業績コントロール仕様

- [x] 2-1: `VariableSpec.java` のコントロールセットに「前期営業利益率」仕様を追加
- [x] 2-2: `VariableSpec.java` のコントロールセットに「前期ROA」仕様を追加

## 分析③ 二値言及変数

- [x] 3-1: `MergedRecord.java` に `dxMention / aiMention / anyMention` メソッドを追加
- [x] 3-2: `VariableSpec.java` のキーワード変数リストに二値変数3種を追加

## 動作確認

- [x] C-1: `mvn compile` が通ること
- [ ] C-2: 全業種分析（変数探索に前期業績仕様・二値変数が追加されること）
- [ ] C-3: `--exclude-unknown` で UNKNOWN 除外後の件数が減ること
- [ ] C-4: 情報通信業分析（前期業績コントロール追加後のキーワード係数変化を確認）
- [ ] C-5: 小売業分析（二値変数で計算可能な仕様が増えること）
