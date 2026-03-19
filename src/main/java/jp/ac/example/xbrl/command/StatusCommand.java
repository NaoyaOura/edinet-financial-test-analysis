package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.TaskProgressDao;

import java.sql.Connection;
import java.util.List;

/**
 * 各タスクの進捗状況を標準出力に表示するコマンド。
 */
public class StatusCommand {

    private final DatabaseManager dbManager;

    public StatusCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void execute() {
        try (Connection conn = dbManager.getConnection()) {
            TaskProgressDao dao = new TaskProgressDao(conn);
            List<TaskProgressDao.ProgressSummary> summaries = dao.summarize();

            if (summaries.isEmpty()) {
                System.out.println("進捗データがありません。fetch-list コマンドから開始してください。");
                return;
            }

            System.out.println("=== 処理進捗 ===");
            System.out.printf("%-20s %-15s %s%n", "タスク", "ステータス", "件数");
            System.out.println("-".repeat(50));

            String currentTask = null;
            for (TaskProgressDao.ProgressSummary s : summaries) {
                // タスクが切り替わったら空行を挿入する
                if (!s.task().equals(currentTask)) {
                    if (currentTask != null) System.out.println();
                    currentTask = s.task();
                }
                System.out.printf("%-20s %-15s %d%n", s.task(), s.status(), s.count());
            }
        } catch (Exception e) {
            System.err.println("進捗の取得に失敗しました: " + e.getMessage());
        }
    }
}
