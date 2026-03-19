package jp.ac.example.xbrl.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    // テスト用の一時ファイルDBを使用する（接続をまたいでテーブルが保持される）
    private File tempDbFile;
    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        tempDbFile = File.createTempFile("xbrl_test_", ".db");
        tempDbFile.deleteOnExit();
        dbManager = new DatabaseManager(tempDbFile.getAbsolutePath());
        dbManager.initializeSchema();
    }

    @AfterEach
    void tearDown() {
        if (tempDbFile != null) {
            tempDbFile.delete();
        }
    }

    @Test
    void スキーマ初期化後に全テーブルが存在すること() throws SQLException {
        List<String> tables = getTableNames();
        assertTrue(tables.contains("companies"));
        assertTrue(tables.contains("document_list"));
        assertTrue(tables.contains("financial_data"));
        assertTrue(tables.contains("keyword_scores"));
        assertTrue(tables.contains("task_progress"));
    }

    @Test
    void initializeSchemaを複数回呼んでもエラーにならないこと() {
        assertDoesNotThrow(() -> dbManager.initializeSchema());
        assertDoesNotThrow(() -> dbManager.initializeSchema());
    }

    @Test
    void CompanyDaoでINSERTとSELECTができること() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            CompanyDao dao = new CompanyDao(conn);
            dao.upsert("E12345", "テスト小売株式会社", "5711", "RETAIL");

            String name = dao.findCompanyName("E12345");
            assertEquals("テスト小売株式会社", name);
        }
    }

    @Test
    void CompanyDaoで同一EDINETコードをUPSERTできること() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            CompanyDao dao = new CompanyDao(conn);
            dao.upsert("E12345", "旧社名", "5711", "RETAIL");
            dao.upsert("E12345", "新社名", "5711", "RETAIL");

            assertEquals("新社名", dao.findCompanyName("E12345"));
        }
    }

    @Test
    void TaskProgressDaoでステータス更新と集計ができること() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            TaskProgressDao dao = new TaskProgressDao(conn);
            dao.upsert("DOC001", "DOWNLOAD", TaskProgressDao.Status.DONE, null);
            dao.upsert("DOC002", "DOWNLOAD", TaskProgressDao.Status.ERROR, "接続タイムアウト");
            dao.upsert("DOC003", "DOWNLOAD", TaskProgressDao.Status.PENDING, null);

            List<TaskProgressDao.ProgressSummary> summaries = dao.summarize();
            assertFalse(summaries.isEmpty());

            long doneCount = summaries.stream()
                .filter(s -> s.task().equals("DOWNLOAD") && s.status().equals("DONE"))
                .mapToInt(TaskProgressDao.ProgressSummary::count)
                .sum();
            assertEquals(1, doneCount);
        }
    }

    @Test
    void TaskProgressDaoでforceなしの場合DONE以外が返ること() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            TaskProgressDao dao = new TaskProgressDao(conn);
            dao.upsert("DOC001", "DOWNLOAD", TaskProgressDao.Status.DONE, null);
            dao.upsert("DOC002", "DOWNLOAD", TaskProgressDao.Status.ERROR, "エラー");
            dao.upsert("DOC003", "DOWNLOAD", TaskProgressDao.Status.PENDING, null);

            List<String> incomplete = dao.findIncompleteDocIds("DOWNLOAD", false);
            assertEquals(2, incomplete.size());
            assertFalse(incomplete.contains("DOC001"));
        }
    }

    @Test
    void TaskProgressDaoでforceありの場合全件が返ること() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            TaskProgressDao dao = new TaskProgressDao(conn);
            dao.upsert("DOC001", "DOWNLOAD", TaskProgressDao.Status.DONE, null);
            dao.upsert("DOC002", "DOWNLOAD", TaskProgressDao.Status.ERROR, "エラー");

            List<String> all = dao.findIncompleteDocIds("DOWNLOAD", true);
            assertEquals(2, all.size());
        }
    }

    private List<String> getTableNames() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
}
