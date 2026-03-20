# edinet-financial-test-analysis

有価証券報告書における生成AI・DX関連キーワードの出現頻度と、日本企業の財務パフォーマンスとの関係を定量的に分析するCLIアプリケーション。

メインの分析対象は**小売業**（生成AIの活用が小売業の成長に寄与するかの検証）とし、**IT企業**（情報通信業）を比較対象として業種横断的な分析を行う。

> 📄 詳細ドキュメントは [GitHub Pages](https://naoyaoura.github.io/edinet-financial-test-analysis/) を参照してください。

---

## ドキュメント

| ドキュメント | 概要 |
|---|---|
| [機能設計書](docs/functional-design.md) | 研究目的・仮説・分析対象・キーワード定義・分析手法 |
| [技術仕様書](docs/architecture.md) | システム構成・CLIコマンド・DB設計・API連携仕様 |
| [リポジトリ構造定義書](docs/repository-structure.md) | ディレクトリ構成・コミット対象・機密情報の管理方針 |
| [開発ガイドライン](docs/development-guidelines.md) | コーディング規約・テスト方針・ブランチ運用・セキュリティ |

---

## 技術スタック

- **言語**: Java 17
- **ビルド**: Maven
- **テスト**: JUnit 5
- **DB**: SQLite
- **統計**: Apache Commons Math
- **データソース**: [EDINET API v2](https://disclosure2dl.edinet-fsa.go.jp/guide/static/disclosure/WZEK0110.html)

---

## セットアップ

### 前提条件

- Java 17 以上
- Maven 3.x 以上
- EDINET APIキー（[EDINET](https://disclosure.edinet-fsa.go.jp/) より取得）

### 環境変数の設定

```bash
export EDINET_API_KEY=your_api_key_here
```

> **注意**: APIキーをソースコードやコミット履歴に含めないこと。

### ビルド

```bash
mvn compile
```

---

## 使い方

各処理フェーズは独立したコマンドとして実行できます。通常は以下の順に実行します。

```bash
# 1. 書類一覧を取得
mvn exec:java -Dexec.args="fetch-list --year 2023"

# 2. 書類をダウンロード（未取得分のみ）
mvn exec:java -Dexec.args="download --year 2023"

# 3. 財務データをパース（業種コードも XBRL から取得）
mvn exec:java -Dexec.args="parse-xbrl --year 2023"

# 4. キーワードスコアを算出
mvn exec:java -Dexec.args="score-keywords --year 2023"

# 5. 統計分析を実行（デフォルトは全分析）
mvn exec:java -Dexec.args="analyze"

# 6. CSV出力（デフォルトは全種類）
mvn exec:java -Dexec.args="export"

# 進捗確認
mvn exec:java -Dexec.args="status"
```

途中で失敗した場合は同じコマンドを再実行すると、完了済みの書類をスキップして続きから処理します。

---

## コマンドリファレンス

### fetch-list

EDINET API から書類一覧を取得して DB に登録します。

```bash
mvn exec:java -Dexec.args="fetch-list --year <年度>"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | ✅ | 対象年度（例: `2023` → 2023-04-01〜2024-03-31） |

### download

書類 ZIP をダウンロードして展開します。完了済みはスキップします。

```bash
mvn exec:java -Dexec.args="download [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象（例: `E01234`） |
| `--force` | | 完了済みも再ダウンロード |

### parse-xbrl

XBRL をパースして財務指標を DB に保存します。同時に業種コードを XBRL から取得して企業マスタを更新します。

```bash
mvn exec:java -Dexec.args="parse-xbrl [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象 |
| `--force` | | 完了済みも再パース |

### score-keywords

XBRL テキストからキーワードスコアを算出して DB に保存します。

```bash
mvn exec:java -Dexec.args="score-keywords [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象 |
| `--force` | | 完了済みも再算出 |

### analyze

統計分析を実行します。

```bash
mvn exec:java -Dexec.args="analyze [--type <分析種別>] [--year <年度>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--type <種別>` | | `group-comparison` / `lag-regression` / `did` / `panel` / `all`（デフォルト: `all`） |
| `--year <年度>` | | 特定年度のみで分析 |

```bash
mvn exec:java -Dexec.args="analyze --type group-comparison"  # グループ比較
mvn exec:java -Dexec.args="analyze --type lag-regression"    # ラグ回帰分析（メイン）
mvn exec:java -Dexec.args="analyze --type did"               # 差分の差分法
mvn exec:java -Dexec.args="analyze --type panel"             # 固定効果モデル
```

### export

分析結果を CSV に出力します。

```bash
mvn exec:java -Dexec.args="export [--type <出力種別>] [--year <年度>] [--output <出力先>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--type <種別>` | | `financial` / `keywords` / `merged` / `all`（デフォルト: `all`） |
| `--year <年度>` | | 対象年度に絞り込み |
| `--output <パス>` | | 出力先ディレクトリ（デフォルト: `./output`） |

```bash
mvn exec:java -Dexec.args="export --type merged --year 2023 --output ./results"
```

### status

各タスクの処理進捗を表示します。

```bash
mvn exec:java -Dexec.args="status"
```

---

## 業種分類

`parse-xbrl` 実行時に XBRL ファイル内の業種コード（東証33業種コード）を取得し、以下のルールで自動分類します。

| 東証33業種コード | 業種名 | 本ツールでの分類 |
|---|---|---|
| `6100` | 小売業 | `RETAIL` |
| `5250` | 情報・通信業 | `IT` |
| その他 | — | `UNKNOWN`（分析対象外） |

> 東証33業種コードの一覧: [J-Quants API ドキュメント](https://jpx-jquants.com/ja/spec/eq-master/sector33code)

---

## 研究仮説

| 仮説 | 内容 |
|---|---|
| H1（主仮説） | 生成AI・DX関連キーワードスコアが高い企業は、翌年の売上高成長率・営業利益率が高い |
| H2 | キーワードスコアの増加率が高い企業は、業績改善が見られる |
| H3（対立仮説） | キーワードスコアと業績に有意な関係はない（過剰なバズワード） |
| H4（業種比較） | キーワードスコアと業績の関係はIT企業において小売業よりも強く現れる |

---

## ライセンス

本リポジトリは研究目的での利用を想定しています。
