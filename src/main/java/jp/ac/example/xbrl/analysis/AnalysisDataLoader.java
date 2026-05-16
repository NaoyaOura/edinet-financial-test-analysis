package jp.ac.example.xbrl.analysis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteから分析用の統合データ（MergedRecord）を読み込むクラス。
 *
 * J-Quants財務データ（jquants_fin_statements）が存在する場合はそちらを一次ソースとして使用。
 * データが未取得の場合は financial_data（EDINET由来）にフォールバックする。
 * 分析期間は2022〜2025年度（通期のみ）。
 */
public class AnalysisDataLoader {

    private final Connection conn;

    public AnalysisDataLoader(Connection conn) {
        this.conn = conn;
    }

    /**
     * 分析用データを読み込む。
     * J-Quantsデータが存在しない場合は financial_data にフォールバックする。
     *
     * @param fiscalYear フィルタする年度（0 の場合は2022〜2025年度全件）
     * @return MergedRecord のリスト
     */
    public List<MergedRecord> load(int fiscalYear) throws SQLException {
        if (hasJQuantsData()) {
            System.out.println("[INFO] J-Quants財務データを使用します");
            return loadFromJQuants(fiscalYear);
        } else {
            System.out.println("[INFO] J-Quantsデータが未取得のため financial_data（EDINET）にフォールバックします");
            return loadFromFinancialData(fiscalYear);
        }
    }

    private boolean hasJQuantsData() throws SQLException {
        String sql = "SELECT COUNT(*) FROM jquants_fin_statements WHERE fiscalYear BETWEEN 2022 AND 2025";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private List<MergedRecord> loadFromJQuants(int fiscalYear) throws SQLException {
        String yearFilter = fiscalYear > 0
            ? "AND j.fiscalYear = " + fiscalYear
            : "AND j.fiscalYear BETWEEN 2022 AND 2025";

        String sql = """
            SELECT
                m.edinetCode,
                j.fiscalYear,
                COALESCE(jl.sector33Code, 'UNKNOWN')     AS sector33Code,
                COALESCE(jl.sector33CodeName, 'UNKNOWN') AS sector33CodeName,
                j.netSales,
                j.operatingProfit,
                j.ordinaryProfit,
                j.profit,
                j.totalAssets,
                j.equity,
                j.cashFlowsFromOperating,
                j.cashFlowsFromInvesting,
                j.cashAndEquivalents,
                k.totalScore,
                k.genAiScore,
                k.aiScore,
                k.dxScore,
                k.documentLength
            FROM edinet_jquants_mapping m
            INNER JOIN jquants_fin_statements j
                ON m.jquantsCode = j.localCode
            INNER JOIN keyword_scores k
                ON m.edinetCode = k.edinetCode AND j.fiscalYear = k.fiscalYear
            LEFT JOIN jquants_listed_info jl
                ON m.jquantsCode = jl.code
            WHERE j.typeOfDocument = 'FY'
            """ + yearFilter + """

            ORDER BY m.edinetCode, j.fiscalYear
            """;

        return executeQuery(sql);
    }

    private List<MergedRecord> loadFromFinancialData(int fiscalYear) throws SQLException {
        String yearFilter = fiscalYear > 0
            ? "AND f.fiscalYear = " + fiscalYear
            : "AND f.fiscalYear BETWEEN 2022 AND 2025";

        String sql = """
            SELECT
                f.edinetCode,
                f.fiscalYear,
                COALESCE(jl.sector33Code,
                    CASE c.industryCategory
                        WHEN 'RETAIL' THEN '6100'
                        WHEN 'IT'     THEN '5250'
                        ELSE 'UNKNOWN'
                    END
                ) AS sector33Code,
                COALESCE(jl.sector33CodeName,
                    CASE c.industryCategory
                        WHEN 'RETAIL' THEN '小売業'
                        WHEN 'IT'     THEN '情報・通信業'
                        ELSE 'UNKNOWN'
                    END
                ) AS sector33CodeName,
                f.netSales,
                f.operatingIncome  AS operatingProfit,
                f.ordinaryIncome   AS ordinaryProfit,
                f.profitLoss       AS profit,
                f.assets           AS totalAssets,
                f.equity,
                f.operatingCashFlow  AS cashFlowsFromOperating,
                f.investingCashFlow  AS cashFlowsFromInvesting,
                NULL                 AS cashAndEquivalents,
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
            LEFT JOIN edinet_jquants_mapping m
                ON f.edinetCode = m.edinetCode
            LEFT JOIN jquants_listed_info jl
                ON m.jquantsCode = jl.code
            WHERE 1=1
            """ + yearFilter + """

            ORDER BY f.edinetCode, f.fiscalYear
            """;

        return executeQuery(sql);
    }

    private List<MergedRecord> executeQuery(String sql) throws SQLException {
        List<MergedRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new MergedRecord(
                    rs.getString("edinetCode"),
                    rs.getInt("fiscalYear"),
                    rs.getString("sector33Code"),
                    rs.getString("sector33CodeName"),
                    (Double) rs.getObject("netSales"),
                    (Double) rs.getObject("operatingProfit"),
                    (Double) rs.getObject("ordinaryProfit"),
                    (Double) rs.getObject("profit"),
                    (Double) rs.getObject("totalAssets"),
                    (Double) rs.getObject("equity"),
                    (Double) rs.getObject("cashFlowsFromOperating"),
                    (Double) rs.getObject("cashFlowsFromInvesting"),
                    (Double) rs.getObject("cashAndEquivalents"),
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
