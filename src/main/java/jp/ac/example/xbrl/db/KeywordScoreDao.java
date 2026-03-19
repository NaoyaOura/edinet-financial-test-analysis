package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * keyword_scoresテーブルのCRUDを担うクラス。
 */
public class KeywordScoreDao {

    private final Connection conn;

    public KeywordScoreDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * キーワードスコアを登録する。同一企業・年度が存在する場合は上書きする。
     */
    public void upsert(String edinetCode, int fiscalYear, double genAiScore, double aiScore,
                       double dxScore, double totalScore, int documentLength) throws SQLException {
        String sql = """
            INSERT INTO keyword_scores (edinetCode, fiscalYear, genAiScore, aiScore, dxScore, totalScore, documentLength)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(edinetCode, fiscalYear) DO UPDATE SET
                genAiScore = excluded.genAiScore,
                aiScore = excluded.aiScore,
                dxScore = excluded.dxScore,
                totalScore = excluded.totalScore,
                documentLength = excluded.documentLength
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            ps.setInt(2, fiscalYear);
            ps.setDouble(3, genAiScore);
            ps.setDouble(4, aiScore);
            ps.setDouble(5, dxScore);
            ps.setDouble(6, totalScore);
            ps.setInt(7, documentLength);
            ps.executeUpdate();
        }
    }

    /**
     * 年度を指定して全企業のキーワードスコアを取得する。
     */
    public List<KeywordScoreRecord> findByFiscalYear(int fiscalYear) throws SQLException {
        String sql = "SELECT * FROM keyword_scores WHERE fiscalYear = ? ORDER BY totalScore DESC";
        List<KeywordScoreRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fiscalYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new KeywordScoreRecord(
                        rs.getString("edinetCode"),
                        rs.getInt("fiscalYear"),
                        rs.getDouble("genAiScore"),
                        rs.getDouble("aiScore"),
                        rs.getDouble("dxScore"),
                        rs.getDouble("totalScore"),
                        rs.getInt("documentLength")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * キーワードスコアデータを保持するレコード。
     */
    public record KeywordScoreRecord(
        String edinetCode,
        int fiscalYear,
        double genAiScore,
        double aiScore,
        double dxScore,
        double totalScore,
        int documentLength
    ) {}
}
