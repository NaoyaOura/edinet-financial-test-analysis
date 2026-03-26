package jp.ac.example.xbrl.xbrl;

import jp.ac.example.xbrl.db.FinancialDataDao.FinancialRecord;

import java.io.File;
import java.util.Map;

/**
 * XBRLパース結果（要素名→値マップ）から FinancialRecord を生成するクラス。
 * architecture.md の財務指標対応表に従ってタクソノミ要素名をマッピングする。
 */
public class FinancialDataExtractor {

    private final XbrlParser parser;

    public FinancialDataExtractor(XbrlParser parser) {
        this.parser = parser;
    }

    /**
     * 展開済み書類ディレクトリから財務指標を抽出して FinancialRecord を返す。
     *
     * @param docDir     data/raw/{docId}/ ディレクトリ
     * @param edinetCode EDINETコード
     * @param fiscalYear 決算年度
     * @return 抽出した FinancialRecord（XBRLが見つからない場合は null）
     */
    public FinancialRecord extract(File docDir, String edinetCode, int fiscalYear) throws Exception {
        File xbrlFile = parser.findXbrlFile(docDir);
        if (xbrlFile == null) return null;

        Map<String, Double> values = parser.parse(xbrlFile);

        return new FinancialRecord(
            edinetCode,
            fiscalYear,
            get(values, "NetSales"),
            get(values, "GrossProfit"),
            get(values, "OperatingIncome", "OperatingProfit"),
            get(values, "OrdinaryIncome", "OrdinaryProfit"),
            get(values, "ProfitLoss", "NetIncome", "ProfitLossAttributableToOwnersOfParent"),
            get(values, "Assets"),
            get(values, "CurrentAssets"),
            get(values, "CurrentLiabilities"),
            get(values, "Liabilities"),
            get(values, "Equity", "NetAssets"),
            get(values, "CashAndDeposits", "CashAndCashEquivalents"),
            get(values, "Inventories"),
            // EDINET XBRL は "SellingGeneralAndAdministrativeExpenses"（"And" あり）を使用する
            get(values, "SellingGeneralAndAdministrativeExpenses", "SellingGeneralAdministrativeExpenses"),
            // EDINET XBRL は "SalariesAndAllowancesSGA" を使用する
            get(values, "SalariesAndAllowancesSGA", "PersonnelExpenses", "WagesAndSalaries"),
            getInt(values, "NumberOfEmployees"),
            get(values, "ResearchAndDevelopmentExpenses"),
            get(values, "Software"),
            get(values, "IntangibleAssets"),
            // EDINET XBRL はキャッシュフロー計算書の要素に "InvCF" サフィックスが付く
            get(values, "PurchaseOfPropertyPlantAndEquipmentAndIntangibleAssets",
                        "PurchaseOfPropertyPlantAndEquipmentInvCF",
                        "PurchaseOfPropertyPlantAndEquipment"),
            get(values, "NetCashProvidedByUsedInOperatingActivities", "CashFlowsFromOperatingActivities"),
            // EDINET XBRL は "InvestmentActivities"（Investing ではなく Investment）を使用する
            get(values, "NetCashProvidedByUsedInInvestmentActivities",
                        "NetCashProvidedByUsedInInvestingActivities",
                        "CashFlowsFromInvestingActivities")
        );
    }

    /**
     * 展開済み書類ディレクトリから業種コードを抽出する。
     *
     * @param docDir data/raw/{docId}/ ディレクトリ
     * @return 業種コード（例: "6100"）。XBRLが見つからない場合や要素がない場合は空文字
     */
    public String extractIndustryCode(File docDir) throws Exception {
        File xbrlFile = parser.findXbrlFile(docDir);
        if (xbrlFile == null) return "";
        return parser.extractIndustryCode(xbrlFile);
    }

    /**
     * 複数の候補キーから最初に見つかった値を返す。見つからない場合は null。
     */
    private Double get(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double v = values.get(key);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * 複数の候補キーから最初に見つかった値をIntegerで返す。見つからない場合は null。
     */
    private Integer getInt(Map<String, Double> values, String... keys) {
        Double v = get(values, keys);
        return v != null ? v.intValue() : null;
    }
}
