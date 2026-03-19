package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * パネルデータ分析（固定効果モデル）を行うクラス。
 *
 * within推定量: 企業ごとに各変数の平均を引いた「企業内変動」でOLS回帰を行うことで、
 * 観察されない企業固有の不変要因（固定効果）を除去する。
 *
 * 回帰式（企業内変動ベース）:
 *   (operatingMargin - 企業平均) = β₁×(totalScore - 企業平均) + β₂×(log売上高 - 企業平均) + ε
 *
 * 目的: コーポレートガバナンスや業種・規模の差を除いた上でも
 * キーワードスコアと業績の関係が残るか（仮説H4に対応）を検証する。
 */
public class PanelDataAnalyzer {

    private static final int MIN_OBSERVATIONS = 5;
    /** within推定に必要な最低観測年数（1企業あたり） */
    private static final int MIN_YEARS_PER_COMPANY = 2;

    /**
     * 固定効果モデル（within推定量）を実行して結果テキストを返す。
     *
     * @param records 全年度の統合データ
     * @return 回帰結果テキスト
     */
    public String analyze(List<MergedRecord> records) {
        // 企業別にグループ化
        Map<String, List<MergedRecord>> byCompany = records.stream()
            .collect(Collectors.groupingBy(MergedRecord::edinetCode));

        // within変換: 各変数を企業平均で引く
        List<double[]> demeaned = new ArrayList<>();
        for (List<MergedRecord> compRecords : byCompany.values()) {
            if (compRecords.size() < MIN_YEARS_PER_COMPANY) continue;

            // 企業内平均を計算
            OptionalDouble meanMargin = compRecords.stream()
                .map(MergedRecord::operatingMargin).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();
            OptionalDouble meanScore = compRecords.stream()
                .mapToDouble(MergedRecord::totalScore).average();
            OptionalDouble meanLogSales = compRecords.stream()
                .map(MergedRecord::logNetSales).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();

            if (meanMargin.isEmpty() || meanLogSales.isEmpty()) continue;

            for (MergedRecord r : compRecords) {
                Double margin  = r.operatingMargin();
                Double logSales = r.logNetSales();
                if (margin == null || logSales == null) continue;

                demeaned.add(new double[]{
                    margin   - meanMargin.getAsDouble(),
                    r.totalScore() - meanScore.getAsDouble(),
                    logSales - meanLogSales.getAsDouble()
                });
            }
        }

        return formatReport(demeaned);
    }

    private String formatReport(List<double[]> demeaned) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== パネルデータ分析（固定効果モデル） ===\n");
        sb.append("目的変数: 営業利益率（企業内平均からの乖離）\n");
        sb.append("説明変数: totalScore・log(売上高)（同じく企業内平均乖離）\n\n");

        if (demeaned.size() < MIN_OBSERVATIONS) {
            sb.append(String.format("データが不足しています（%d件、最低%d件必要）。%n",
                demeaned.size(), MIN_OBSERVATIONS));
            return sb.toString();
        }

        double[] y  = demeaned.stream().mapToDouble(d -> d[0]).toArray();
        double[][] x = demeaned.stream()
            .map(d -> new double[]{d[1], d[2]})
            .toArray(double[][]::new);

        try {
            // within推定: 企業平均引き後のデータには定数項が不要なため noIntercept=true
            OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
            reg.setNoIntercept(true);
            reg.newSampleData(y, x);

            double[] params = reg.estimateRegressionParameters();
            double[] stderr = reg.estimateRegressionParametersStandardErrors();
            double   r2     = reg.calculateRSquared();
            int      n2     = y.length;
            int      k      = params.length;

            TDistribution tDist = new TDistribution(n2 - k);

            sb.append(String.format("n = %d（観測数）  R² = %.4f%n%n", n2, r2));
            sb.append(String.format("%-24s %10s %10s %8s %8s%n", "変数", "係数(β)", "SE", "t値", "p値"));
            sb.append("-".repeat(65)).append("\n");

            String[] labels = {"totalScore", "log(売上高)"};
            for (int i = 0; i < params.length; i++) {
                double tStat = params[i] / stderr[i];
                double pVal  = 2.0 * tDist.cumulativeProbability(-Math.abs(tStat));
                sb.append(String.format("%-24s %10.4f %10.4f %8.3f %8.4f%s%n",
                    labels[i], params[i], stderr[i], tStat, pVal, significance(pVal)));
            }
            sb.append("\n  * p<.10  ** p<.05  *** p<.01\n");
        } catch (MathIllegalArgumentException e) {
            sb.append("回帰計算に失敗しました（行列が特異）: ").append(e.getMessage()).append("\n");
            sb.append("ヒント: 変数が企業内で変動しない場合、within変換後が全ゼロになり計算不能になります。\n");
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
