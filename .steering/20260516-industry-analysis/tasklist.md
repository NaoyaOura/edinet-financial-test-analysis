# タスクリスト：業種別分析の追加

## A. 業種別フィルタ（--industry オプション）

- [x] A-1: `AnalyzeCommand.java` に `--industry` オプションのパースを追加
- [x] A-2: `--industry retail/it` でレコードをフィルタするロジックを追加
- [x] A-3: フィルタ後のレコード数・業種名をコンソールとレポートに出力

## B. 業種×キーワード交差項

- [x] B-1: `MergedRecord.java` に交差項メソッドを追加（dxScoreXit / aiScoreXit / dxScoreXretail）
- [x] B-2: `VariableSpec.java` の仕様リストに交差項仕様を追加
- [ ] B-3: レポートの変数探索テーブルで交差項変数名が正しく表示されることを確認

## 動作確認

- [x] C-1: `mvn compile` が通ること
- [ ] C-2: `analyze` (全業種) が従来どおり動くこと
- [ ] C-3: `analyze --industry retail` が小売業のみで分析されること
- [ ] C-4: `analyze --industry it` が情報通信業のみで分析されること
- [ ] C-5: レポートに交差項の結果が出力されること
