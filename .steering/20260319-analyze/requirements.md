# 要求内容：analyze コマンド実装

## 目的

financial_data と keyword_scores の統合データに対して統計分析を行い、
機能設計書の仮説（H1〜H4）を検証するレポートを出力する。

## 要求事項

1. `--type` オプションで分析種別を指定できる
   - `group-comparison`  : 高・中・低スコア群の財務指標比較（t検定・ANOVA）
   - `lag-regression`    : キーワードスコア(t)→翌年業績(t+1)のラグOLS回帰
   - `did`               : 差分の差分法（2023年にスコア急増企業 vs 対照群）
   - `panel`             : 固定効果モデル（within推定量）
   - `all`（デフォルト） : 上記4種すべて実施
2. `--year` で特定年度のみを group-comparison・did の対象とする（未指定は全年度）
3. `--output <ディレクトリ>` でレポートファイルの出力先を指定（デフォルト: AppConfig.getOutputDir()）
4. レポートは stdout に表示し、`output/analysis_report.txt` にも保存する
5. データ不足（n < 5 など）の場合はスキップしてメッセージを出力する

## 分析内容

### group-comparison
- totalScore の四分位でQ1（下位25%）・中間・Q4（上位25%）の3群に分類
- 各群の営業利益率・ROA の平均・標準偏差を算出
- Q1 vs Q4 の t検定（p値）、3群 ANOVA（F値・p値）を実施

### lag-regression
- 同一企業の年度t → 年度t+1 のペアを作成
- 目的変数: 翌年営業利益率 operatingMargin(t+1)
- 説明変数: totalScore(t)、log(売上高)(t)、IT業種ダミー
- OLS 回帰係数・標準誤差・t値・p値・R² を出力

### did
- 処理群: 指定年度（デフォルト2023）にtotalScore が前年比50%以上増加した企業
- 対照群: それ以外
- DiD推定量 = (処理群_後 - 処理群_前) - (対照群_後 - 対照群_前)（目的変数: 営業利益率）
- t検定で有意性を検証

### panel
- within推定量（企業内平均差分）でパネルデータの固定効果を除去
- 目的変数: 営業利益率（企業平均からの乖離）
- 説明変数: totalScore・log(売上高)（同じく企業平均乖離）
- OLS 回帰係数・t値・p値・R² を出力

## 完了条件

- `mvn exec:java -Dexec.args="analyze"` でレポートが出力される
- `mvn test` が通ること
