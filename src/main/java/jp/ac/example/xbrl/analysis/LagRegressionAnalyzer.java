package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * キーワードスコア(t) → 翌年業績(t+1) のラグOLS回帰を行うクラス。
 *
 * 回帰式:
 *   operatingMargin(t+1) = β₀ + β₁×totalScore(t) + β₂×log(売上高)(t) + β₃×IT業種ダミー + ε
 *
 * 目的: キーワードスコアの高さが翌年の業績改善と相関するか（仮説H1）を検証する。
 */
public class LagRegressionAnalyzer {

    private static final int MIN_OBSERVATIONS = 5;

    /**
     * ラグ回帰を実行して結果テキストを返す。
     *
     * @param records 全年度の統合データ
     * @return 回帰結果テキスト
     */
    public String analyze(List<MergedRecord> records) {
        // 企業別にデータをグループ化
        Map<String, Map<Integer, MergedRecord>> byCompany = records.stream()
            .collect(Collectors.groupingBy(
                MergedRecord::edinetCode,
                Collectors.toMap(MergedRecord::fiscalYear, r -> r)
            ));

        // 年度ペア (t, t+1) を作成し、有効な観測値を収集
        // [margin(t+1), totalScore(t), logNetSales(t), industryDummy(t)]
        List<double[]> observations = new ArrayList<>();
        for (var entry : byCompany.entrySet()) {
            Map<Integer, MergedRecord> yearMap = entry.getValue();
            for (int year : yearMap.keySet()) {
                MergedRecord current = yearMap.get(year);
                MergedRecord next    = yearMap.get(year + 1);
                if (next == null) continue;

                Double marginNext = next.operatingMargin();
                Double logSales   = current.logNetSales();
                if (marginNext == null || logSales == null) continue;

                observations.add(new double[]{
                    marginNext,
                    current.totalScore(),
                    logSales,
                    current.industryDummy()
                });
            }
        }

        return formatReport(observations);
    }

    private String formatReport(List<double[]> observations) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ラグ回帰分析 ===\n");
        sb.append("目的変数: 翌年営業利益率 (%)\n");
        sb.append("説明変数: totalScore(t), log(売上高)(t), IT業種ダミー\n\n");

        if (observations.size() < MIN_OBSERVATIONS) {
            sb.append(String.format("データが不足しています（%d件、最低%d件必要）。%n",
                observations.size(), MIN_OBSERVATIONS));
            return sb.toString();
        }

        double[] y  = observations.stream().mapToDouble(o -> o[0]).toArray();
        double[][] x = observations.stream()
            .map(o -> new double[]{o[1], o[2], o[3]})
            .toArray(double[][]::new);

        try {
            OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
            reg.newSampleData(y, x);

            double[] params = reg.estimateRegressionParameters();
            double[] stderr = reg.estimateRegressionParametersStandardErrors();
            double   r2     = reg.calculateRSquared();
            int      n2     = y.length;
            int      k      = params.length; // 定数項 + 3説明変数

            TDistribution tDist = new TDistribution(n2 - k);

            sb.append(String.format("n = %d  R² = %.4f%n%n", n2, r2));
            sb.append(String.format("%-24s %10s %10s %8s %8s%n", "変数", "係数(β)", "SE", "t値", "p値"));
            sb.append("-".repeat(65)).append("\n");

            String[] labels = {"定数項", "totalScore(t)", "log(売上高)(t)", "IT業種ダミー"};
            for (int i = 0; i < params.length; i++) {
                double tStat = params[i] / stderr[i];
                double pVal  = 2.0 * tDist.cumulativeProbability(-Math.abs(tStat));
                sb.append(String.format("%-24s %10.4f %10.4f %8.3f %8.4f%s%n",
                    labels[i], params[i], stderr[i], tStat, pVal, significance(pVal)));
            }
            sb.append("\n  * p<.10  ** p<.05  *** p<.01\n");
        } catch (MathIllegalArgumentException e) {
            sb.append("回帰計算に失敗しました（行列が特異または多重共線性の可能性）: ")
              .append(e.getMessage()).append("\n");
            sb.append("ヒント: 業種が1種類のみの場合、IT業種ダミーが定数列になり計算不能になります。\n");
        }

        return sb.toString();
    }

    private static String significance(double p) {
        if (p < 0.01) return " ***";
        if (p < 0.05) return " **";
        if (p < 0.10) return " *";
        return "";
    }
}
