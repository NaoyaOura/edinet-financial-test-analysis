package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * companiesテーブルのCRUDを担うクラス。
 */
public class CompanyDao {

    private final Connection conn;

    public CompanyDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 企業を登録する。既存の場合は上書きする。
     */
    public void upsert(String edinetCode, String companyName, String industryCode, String industryCategory)
            throws SQLException {
        String sql = """
            INSERT INTO companies (edinetCode, companyName, industryCode, industryCategory)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(edinetCode) DO UPDATE SET
                companyName = excluded.companyName,
                industryCode = excluded.industryCode,
                industryCategory = excluded.industryCategory
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            ps.setString(2, companyName);
            ps.setString(3, industryCode);
            ps.setString(4, industryCategory);
            ps.executeUpdate();
        }
    }

    /**
     * 業種区分で企業一覧を取得する。
     * @param industryCategory "RETAIL" または "IT"
     */
    public List<String> findEdinetCodesByCategory(String industryCategory) throws SQLException {
        String sql = "SELECT edinetCode FROM companies WHERE industryCategory = ?";
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, industryCategory);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("edinetCode"));
                }
            }
        }
        return results;
    }

    /**
     * EDINETコードで企業を1件取得する。存在しない場合はnullを返す。
     */
    public String findCompanyName(String edinetCode) throws SQLException {
        String sql = "SELECT companyName FROM companies WHERE edinetCode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("companyName");
                }
            }
        }
        return null;
    }
}
