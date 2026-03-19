package jp.ac.example.xbrl.analysis;

/**
 * 分析に使用する財務指標とキーワードスコアの統合レコード。
 * financial_data と keyword_scores を (edinetCode, fiscalYear) でJOINしたデータ。
 */
public record MergedRecord(
    String edinetCode,
    int fiscalYear,
    String industryCategory,   // companies テーブルから（RETAIL / IT / UNKNOWN）
    Double netSales,
    Double operatingIncome,
    Double profitLoss,
    Double assets,
    Double equity,
    double totalScore,
    double genAiScore,
    double aiScore,
    double dxScore,
    int documentLength
) {

    /**
     * 営業利益率（%）= operatingIncome / netSales × 100
     * netSales または operatingIncome が null / 0 の場合は null を返す。
     */
    public Double operatingMargin() {
        if (netSales == null || operatingIncome == null || netSales == 0.0) return null;
        return operatingIncome / netSales * 100.0;
    }

    /**
     * ROA（%）= profitLoss / assets × 100
     */
    public Double roa() {
        if (profitLoss == null || assets == null || assets == 0.0) return null;
        return profitLoss / assets * 100.0;
    }

    /**
     * 売上高の自然対数（規模コントロール変数）
     */
    public Double logNetSales() {
        if (netSales == null || netSales <= 0.0) return null;
        return Math.log(netSales);
    }

    /**
     * IT業種ダミー（IT=1, それ以外=0）
     */
    public int industryDummy() {
        return "IT".equalsIgnoreCase(industryCategory) ? 1 : 0;
    }
}
