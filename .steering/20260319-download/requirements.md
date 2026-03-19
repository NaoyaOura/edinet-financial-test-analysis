# 要求内容：download コマンド実装

## 目的

`document_list` テーブルに登録済みの書類を EDINET API からZIP形式でダウンロードし、
ローカルに展開して後続の parse-xbrl・score-keywords が利用できる状態にする。

## 要求事項

1. `task_progress` で `DOWNLOAD=PENDING` または `DOWNLOAD=ERROR` の書類のみ処理する
2. EDINET API `/api/v2/documents/{docID}?type=5` でZIPをダウンロードする
3. ZIPを `data/raw/{docId}/` に展開する
4. 成功時は `task_progress` を `DONE` に更新する
5. 失敗時は `task_progress` を `ERROR` に更新してエラーメッセージを保存し、次の書類へ進む
6. `--force` オプション指定時は `DONE` 済みも再ダウンロードする
7. `--year` / `--edinet-code` オプションで処理対象を絞り込める

## 完了条件

- `mvn exec:java -Dexec.args="download --year 2023"` が実行できる
- `data/raw/{docId}/` にXBRL・HTMLファイルが展開されること
- `mvn test` が通ること
