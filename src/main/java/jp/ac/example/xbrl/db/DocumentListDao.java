package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * document_listテーブルのCRUDを担うクラス。
 */
public class DocumentListDao {

    private final Connection conn;

    public DocumentListDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 書類情報を登録する。同一docIdが存在する場合はスキップする。
     */
    public void insertIfAbsent(String docId, String edinetCode, Integer fiscalYear,
                               String submissionDate, String docDescription) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO document_list (docId, edinetCode, fiscalYear, submissionDate, docDescription)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.setString(2, edinetCode);
            ps.setObject(3, fiscalYear);
            ps.setString(4, submissionDate);
            ps.setString(5, docDescription);
            ps.executeUpdate();
        }
    }

    /**
     * 年度を指定して書類一覧を取得する。
     */
    public List<DocumentRecord> findByFiscalYear(int fiscalYear) throws SQLException {
        String sql = "SELECT * FROM document_list WHERE fiscalYear = ? ORDER BY submissionDate";
        List<DocumentRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fiscalYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new DocumentRecord(
                        rs.getString("docId"),
                        rs.getString("edinetCode"),
                        (Integer) rs.getObject("fiscalYear"),
                        rs.getString("submissionDate"),
                        rs.getString("docDescription")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * EDINETコードを指定して書類一覧を取得する。
     */
    public List<DocumentRecord> findByEdinetCode(String edinetCode) throws SQLException {
        String sql = "SELECT * FROM document_list WHERE edinetCode = ? ORDER BY submissionDate";
        List<DocumentRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new DocumentRecord(
                        rs.getString("docId"),
                        rs.getString("edinetCode"),
                        (Integer) rs.getObject("fiscalYear"),
                        rs.getString("submissionDate"),
                        rs.getString("docDescription")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * 全件数を返す。
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM document_list";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.getInt(1);
        }
    }

    /**
     * 書類情報を保持するレコード。
     */
    public record DocumentRecord(
        String docId,
        String edinetCode,
        Integer fiscalYear,
        String submissionDate,
        String docDescription
    ) {}
}
