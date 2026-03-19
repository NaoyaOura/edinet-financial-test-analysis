# 要求内容：fetch-list コマンド実装

## 目的

EDINET APIから指定年度の有価証券報告書の書類一覧を取得し、
対象企業（小売業・IT企業）をフィルタリングしてDBに保存する。

## 要求事項

1. EDINET API `/api/v2/documents.json` を日付単位で呼び出し、書類一覧を取得する
2. `docTypeCode=120`（有価証券報告書）のみを対象とする
3. 業種フィルタリング：EDINETの業種コードで小売業・IT企業を絞り込む
4. 取得した企業情報を `companies` テーブルに保存する
5. 取得した書類情報を `document_list` テーブルに保存する
6. 各書類の `task_progress` に `DOWNLOAD=PENDING` を登録する
7. 指定年度の全営業日分を順次取得する（1日1リクエスト）
8. すでに取得済みの日付はスキップする（再実行対応）

## 完了条件

- `mvn exec:java -Dexec.args="fetch-list --year 2023"` が実行できる
- DBの `document_list` に書類が保存されること
- `mvn test` が通ること
