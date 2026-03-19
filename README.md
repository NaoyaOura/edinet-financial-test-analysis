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

---

## 技術スタック

- **言語**: Java 17
- **ビルド**: Maven
- **テスト**: JUnit 5
- **DB**: SQLite
- **統計**: Apache Commons Math
- **データソース**: [EDINET API v2](https://disclosure.edinet-fsa.go.jp/api/v2/)

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

各処理フェーズは独立したコマンドとして実行できます。

```bash
# 1. 書類一覧を取得
mvn exec:java -Dexec.args="fetch-list --year 2023"

# 2. 書類をダウンロード（未取得分のみ）
mvn exec:java -Dexec.args="download --year 2023"

# 3. 財務データをパース
mvn exec:java -Dexec.args="parse-xbrl --year 2023"

# 4. キーワードスコアを算出
mvn exec:java -Dexec.args="score-keywords --year 2023"

# 5. 統計分析を実行
mvn exec:java -Dexec.args="analyze --type lag-regression"

# 6. CSV出力
mvn exec:java -Dexec.args="export"

# 進捗確認
mvn exec:java -Dexec.args="status"
```

途中で失敗した場合は同じコマンドを再実行すると、完了済みの書類をスキップして続きから処理します。
強制的に再処理する場合は `--force` オプションを追加してください。

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
