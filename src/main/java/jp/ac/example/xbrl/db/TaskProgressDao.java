package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * task_progressテーブルのCRUDを担うクラス。
 * 各書類×タスクの処理状態（PENDING/IN_PROGRESS/DONE/ERROR）を管理する。
 */
public class TaskProgressDao {

    public enum Status {
        PENDING, IN_PROGRESS, DONE, ERROR
    }

    private final Connection conn;

    public TaskProgressDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 進捗レコードを登録する。既存の場合は上書きする。
     */
    public void upsert(String docId, String task, Status status, String errorMessage) throws SQLException {
        String sql = """
            INSERT INTO task_progress (docId, task, status, errorMessage, updatedAt)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(docId, task) DO UPDATE SET
                status = excluded.status,
                errorMessage = excluded.errorMessage,
                updatedAt = excluded.updatedAt
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.setString(2, task);
            ps.setString(3, status.name());
            ps.setString(4, errorMessage);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    /**
     * 指定タスクで未完了（DONE以外）の書類IDリストを返す。
     * --forceが指定された場合はすべての書類IDを返す。
     */
    public List<String> findIncompleteDocIds(String task, boolean force) throws SQLException {
        String sql = force
            ? "SELECT docId FROM task_progress WHERE task = ? ORDER BY docId"
            : "SELECT docId FROM task_progress WHERE task = ? AND status != 'DONE' ORDER BY docId";
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("docId"));
                }
            }
        }
        return results;
    }

    /**
     * タスク×ステータス別の件数を返す（status表示用）。
     */
    public List<ProgressSummary> summarize() throws SQLException {
        String sql = """
            SELECT task, status, COUNT(*) as count
            FROM task_progress
            GROUP BY task, status
            ORDER BY task, status
        """;
        List<ProgressSummary> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ProgressSummary(
                    rs.getString("task"),
                    rs.getString("status"),
                    rs.getInt("count")
                ));
            }
        }
        return results;
    }

    /**
     * 進捗集計結果を保持するレコード。
     */
    public record ProgressSummary(String task, String status, int count) {}
}
