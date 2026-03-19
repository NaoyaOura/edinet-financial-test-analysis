package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * financial_dataテーブルのCRUDを担うクラス。
 */
public class FinancialDataDao {

    private final Connection conn;

    public FinancialDataDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 財務指標を登録する。同一企業・年度が存在する場合は上書きする。
     */
    public void upsert(FinancialRecord record) throws SQLException {
        String sql = """
            INSERT INTO financial_data (
                edinetCode, fiscalYear,
                netSales, grossProfit, operatingIncome, ordinaryIncome, profitLoss,
                assets, currentAssets, currentLiabilities, liabilities, equity, cashAndDeposits,
                inventories, sgaExpenses, personnelExpenses, numberOfEmployees,
                researchAndDevelopment, software, intangibleAssets, capitalExpenditure,
                operatingCashFlow, investingCashFlow
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(edinetCode, fiscalYear) DO UPDATE SET
                netSales = excluded.netSales,
                grossProfit = excluded.grossProfit,
                operatingIncome = excluded.operatingIncome,
                ordinaryIncome = excluded.ordinaryIncome,
                profitLoss = excluded.profitLoss,
                assets = excluded.assets,
                currentAssets = excluded.currentAssets,
                currentLiabilities = excluded.currentLiabilities,
                liabilities = excluded.liabilities,
                equity = excluded.equity,
                cashAndDeposits = excluded.cashAndDeposits,
                inventories = excluded.inventories,
                sgaExpenses = excluded.sgaExpenses,
                personnelExpenses = excluded.personnelExpenses,
                numberOfEmployees = excluded.numberOfEmployees,
                researchAndDevelopment = excluded.researchAndDevelopment,
                software = excluded.software,
                intangibleAssets = excluded.intangibleAssets,
                capitalExpenditure = excluded.capitalExpenditure,
                operatingCashFlow = excluded.operatingCashFlow,
                investingCashFlow = excluded.investingCashFlow
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.edinetCode());
            ps.setInt(2, record.fiscalYear());
            ps.setObject(3, record.netSales());
            ps.setObject(4, record.grossProfit());
            ps.setObject(5, record.operatingIncome());
            ps.setObject(6, record.ordinaryIncome());
            ps.setObject(7, record.profitLoss());
            ps.setObject(8, record.assets());
            ps.setObject(9, record.currentAssets());
            ps.setObject(10, record.currentLiabilities());
            ps.setObject(11, record.liabilities());
            ps.setObject(12, record.equity());
            ps.setObject(13, record.cashAndDeposits());
            ps.setObject(14, record.inventories());
            ps.setObject(15, record.sgaExpenses());
            ps.setObject(16, record.personnelExpenses());
            ps.setObject(17, record.numberOfEmployees());
            ps.setObject(18, record.researchAndDevelopment());
            ps.setObject(19, record.software());
            ps.setObject(20, record.intangibleAssets());
            ps.setObject(21, record.capitalExpenditure());
            ps.setObject(22, record.operatingCashFlow());
            ps.setObject(23, record.investingCashFlow());
            ps.executeUpdate();
        }
    }

    /**
     * 企業・年度を指定して財務指標を取得する。存在しない場合はnullを返す。
     */
    public FinancialRecord findByEdinetCodeAndYear(String edinetCode, int fiscalYear) throws SQLException {
        String sql = "SELECT * FROM financial_data WHERE edinetCode = ? AND fiscalYear = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            ps.setInt(2, fiscalYear);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * 年度を指定して全企業の財務指標を取得する。
     */
    public List<FinancialRecord> findByFiscalYear(int fiscalYear) throws SQLException {
        String sql = "SELECT * FROM financial_data WHERE fiscalYear = ? ORDER BY edinetCode";
        List<FinancialRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fiscalYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    private FinancialRecord mapRow(ResultSet rs) throws SQLException {
        return new FinancialRecord(
            rs.getString("edinetCode"),
            rs.getInt("fiscalYear"),
            (Double) rs.getObject("netSales"),
            (Double) rs.getObject("grossProfit"),
            (Double) rs.getObject("operatingIncome"),
            (Double) rs.getObject("ordinaryIncome"),
            (Double) rs.getObject("profitLoss"),
            (Double) rs.getObject("assets"),
            (Double) rs.getObject("currentAssets"),
            (Double) rs.getObject("currentLiabilities"),
            (Double) rs.getObject("liabilities"),
            (Double) rs.getObject("equity"),
            (Double) rs.getObject("cashAndDeposits"),
            (Double) rs.getObject("inventories"),
            (Double) rs.getObject("sgaExpenses"),
            (Double) rs.getObject("personnelExpenses"),
            (Integer) rs.getObject("numberOfEmployees"),
            (Double) rs.getObject("researchAndDevelopment"),
            (Double) rs.getObject("software"),
            (Double) rs.getObject("intangibleAssets"),
            (Double) rs.getObject("capitalExpenditure"),
            (Double) rs.getObject("operatingCashFlow"),
            (Double) rs.getObject("investingCashFlow")
        );
    }

    /**
     * 財務指標データを保持するレコード。null許容フィールドはXBRLから取得できない場合がある。
     */
    public record FinancialRecord(
        String edinetCode,
        int fiscalYear,
        Double netSales,
        Double grossProfit,
        Double operatingIncome,
        Double ordinaryIncome,
        Double profitLoss,
        Double assets,
        Double currentAssets,
        Double currentLiabilities,
        Double liabilities,
        Double equity,
        Double cashAndDeposits,
        Double inventories,
        Double sgaExpenses,
        Double personnelExpenses,
        Integer numberOfEmployees,
        Double researchAndDevelopment,
        Double software,
        Double intangibleAssets,
        Double capitalExpenditure,
        Double operatingCashFlow,
        Double investingCashFlow
    ) {}
}
