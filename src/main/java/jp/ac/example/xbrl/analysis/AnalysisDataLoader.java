package jp.ac.example.xbrl.analysis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteから分析用の統合データ（MergedRecord）を読み込むクラス。
 * financial_data・keyword_scores・companies の3テーブルをJOINして返す。
 */
public class AnalysisDataLoader {

    private final Connection conn;

    public AnalysisDataLoader(Connection conn) {
        this.conn = conn;
    }

    /**
     * 分析用データを読み込む。
     *
     * @param fiscalYear フィルタする年度（0 の場合は全年度）
     * @return MergedRecord のリスト
     */
    public List<MergedRecord> load(int fiscalYear) throws SQLException {
        String sql = """
            SELECT
                f.edinetCode,
                f.fiscalYear,
                COALESCE(c.industryCategory, 'UNKNOWN') AS industryCategory,
                f.netSales,
                f.operatingIncome,
                f.profitLoss,
                f.assets,
                f.equity,
                k.totalScore,
                k.genAiScore,
                k.aiScore,
                k.dxScore,
                k.documentLength
            FROM financial_data f
            INNER JOIN keyword_scores k
                ON f.edinetCode = k.edinetCode AND f.fiscalYear = k.fiscalYear
            LEFT JOIN companies c
                ON f.edinetCode = c.edinetCode
            """ + (fiscalYear > 0 ? "WHERE f.fiscalYear = " + fiscalYear : "") + """

            ORDER BY f.edinetCode, f.fiscalYear
            """;

        List<MergedRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new MergedRecord(
                    rs.getString("edinetCode"),
                    rs.getInt("fiscalYear"),
                    rs.getString("industryCategory"),
                    (Double) rs.getObject("netSales"),
                    (Double) rs.getObject("operatingIncome"),
                    (Double) rs.getObject("profitLoss"),
                    (Double) rs.getObject("assets"),
                    (Double) rs.getObject("equity"),
                    rs.getDouble("totalScore"),
                    rs.getDouble("genAiScore"),
                    rs.getDouble("aiScore"),
                    rs.getDouble("dxScore"),
                    rs.getInt("documentLength")
                ));
            }
        }
        return results;
    }
}
