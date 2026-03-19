# 要求内容：プロジェクトセットアップ＋コア基盤

## 目的

後続のすべての実装の土台となるMavenプロジェクト構成・設定・DB基盤を整備する。

## 要求事項

1. `pom.xml` を作成し、必要な依存ライブラリをすべて定義する
2. Javaパッケージ構成を `docs/architecture.md` のモジュール構成に従って作成する
3. 環境変数 `EDINET_API_KEY` の読み込みと検証を行う `AppConfig` を実装する
4. SQLiteの接続・スキーマ初期化（全テーブル作成）を行う `DatabaseManager` を実装する
5. CLIサブコマンドのディスパッチ処理を行う `Main` を実装する
6. 各コア実装に対応する単体テストを作成する

## 完了条件

- `mvn compile` が通ること
- `mvn test` が通ること
- `EDINET_API_KEY` 未設定時に起動エラーが出ること
- `mvn exec:java -Dexec.args="status"` を実行したとき、進捗テーブルの状態が表示されること
