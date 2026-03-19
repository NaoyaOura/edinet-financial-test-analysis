# 設計：fetch-list コマンド実装

## 実装対象ファイル

```
src/main/java/jp/ac/example/xbrl/
├── edinet/
│   ├── EdinetApiClient.java       # HTTP通信・レスポンスのデシリアライズ
│   └── DocumentListFetcher.java   # 年度単位の書類一覧取得・DB保存
├── command/
│   └── FetchListCommand.java      # fetch-list サブコマンド
└── db/
    └── DocumentListDao.java       # document_listテーブルのCRUD

src/test/java/jp/ac/example/xbrl/
└── edinet/
    └── EdinetApiClientTest.java   # モックレスポンスで単体テスト
```

## 各クラスの設計

### `EdinetApiClient`

- `java.net.http.HttpClient` でGETリクエストを送信する
- レスポンスJSONを `Jackson` でデシリアライズする
- 4xx/5xxはリトライ（最大3回・1秒間隔）後に例外スロー
- APIキーはクエリパラメータ `Subscription-Key` で渡す

```
GET /api/v2/documents.json?date=YYYY-MM-DD&type=2&Subscription-Key={key}
```

### `DocumentListFetcher`

- 指定年度の4月1日〜翌年3月31日（日本の決算年度）を1日ずつループ
- 土日はスキップ（EDINETは休業日に提出なし）
- `EdinetApiClient` で書類一覧を取得
- `docTypeCode=120` かつ対象業種の書類のみ抽出
- `companies`・`document_list`・`task_progress` テーブルに保存

### 業種フィルタリング

EDINETのAPIレスポンスには `edinetCode` と `filerName` が含まれる。
業種コードは `edinetCode` のプレフィックスではなく、別途 EDINET コードリスト（CSV）から取得するのが正式だが、
初期実装では `industryCategory` を一時的に `UNKNOWN` として保存し、
後続フェーズで業種コードリストと照合して更新する方式とする。

### `FetchListCommand`

- `--year` オプションで対象年度を指定（必須）
- `--industry` オプションで業種を絞り込み（省略時は全業種）
- 進捗を標準出力に表示しながら実行する

### `DocumentListDao`

- `document_list` テーブルへのINSERT（ON CONFLICT IGNORE）とSELECT
