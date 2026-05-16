package jp.ac.example.xbrl.analysis;

/**
 * 分析に使用する財務指標とキーワードスコアの統合レコード。
 * jquants_fin_statements（J-Quants財務データ）と keyword_scores を
 * edinet_jquants_mapping 経由でJOINしたデータ。
 */
public record MergedRecord(
    String edinetCode,
    int fiscalYear,
    String sector33Code,         // J-Quants Sector33Code（例: "6100"）
    String sector33CodeName,     // 業種名（例: "小売業"）
    Double netSales,
    Double operatingProfit,
    Double ordinaryProfit,
    Double profit,
    Double totalAssets,
    Double equity,
    Double cashFlowsFromOperating,
    Double cashFlowsFromInvesting,
    Double cashAndEquivalents,
    double totalScore,
    double genAiScore,
    double aiScore,
    double dxScore,
    int documentLength
) {

    // ─── 収益性指標 ───────────────────────────────────────────────

    /** 営業利益率（%）= operatingProfit / netSales × 100 */
    public Double operatingMargin() {
        if (netSales == null || operatingProfit == null || netSales == 0.0) return null;
        return operatingProfit / netSales * 100.0;
    }

    /** ROA（%）= profit / totalAssets × 100 */
    public Double roa() {
        if (profit == null || totalAssets == null || totalAssets == 0.0) return null;
        return profit / totalAssets * 100.0;
    }

    /** ROE（%）= profit / equity × 100 */
    public Double roe() {
        if (profit == null || equity == null || equity == 0.0) return null;
        return profit / equity * 100.0;
    }

    /** 純利益率（%）= profit / netSales × 100 */
    public Double netProfitMargin() {
        if (netSales == null || profit == null || netSales == 0.0) return null;
        return profit / netSales * 100.0;
    }

    // ─── 安全性指標 ───────────────────────────────────────────────

    /** 自己資本比率（%）= equity / totalAssets × 100 */
    public Double equityRatio() {
        if (equity == null || totalAssets == null || totalAssets == 0.0) return null;
        return equity / totalAssets * 100.0;
    }

    // ─── 規模コントロール変数 ─────────────────────────────────────

    /** 売上高の自然対数 */
    public Double logNetSales() {
        if (netSales == null || netSales <= 0.0) return null;
        return Math.log(netSales);
    }

    /** 総資産の自然対数 */
    public Double logTotalAssets() {
        if (totalAssets == null || totalAssets <= 0.0) return null;
        return Math.log(totalAssets);
    }

    // ─── 業種ダミー変数 ───────────────────────────────────────────

    /** 小売業（Sector33Code=6100）の場合 true */
    public boolean isRetail() {
        return "6100".equals(sector33Code);
    }

    /** 情報通信業（Sector33Code=5250）の場合 true */
    public boolean isIT() {
        return "5250".equals(sector33Code);
    }

    /** 小売業ダミー（小売業=1、それ以外=0） */
    public double retailDummy() {
        return isRetail() ? 1.0 : 0.0;
    }

    /** 情報通信業ダミー（IT=1、それ以外=0） */
    public double itDummy() {
        return isIT() ? 1.0 : 0.0;
    }

    // ─── 業種×キーワード 交差項 ──────────────────────────────────

    /** dxScore × 情報通信業ダミー */
    public double dxScoreXit()     { return dxScore * itDummy(); }

    /** aiScore × 情報通信業ダミー */
    public double aiScoreXit()     { return aiScore * itDummy(); }

    /** dxScore × 小売業ダミー */
    public double dxScoreXretail() { return dxScore * retailDummy(); }
}
