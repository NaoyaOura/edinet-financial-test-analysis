package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 差分の差分法（DiD）による分析クラス。
 *
 * 処理群: 指定年度（デフォルト2023）にtotalScoreが前年比50%以上増加した企業
 * 対照群: それ以外の企業
 *
 * DiD推定量 = (処理群_後 - 処理群_前) - (対照群_後 - 対照群_前)
 *
 * 目的: キーワードスコアの急増が翌年業績改善に寄与するか（仮説H2）を検証する。
 */
public class DifferenceInDifferences {

    /** デフォルトのベース年度（比較前） */
    public static final int DEFAULT_BASE_YEAR  = 2022;
    /** デフォルトの処理年度（比較後） */
    public static final int DEFAULT_TREAT_YEAR = 2023;
    /** 処理群とみなすtotalScore増加率の閾値（50%以上増加） */
    private static final double TREAT_THRESHOLD = 1.5;

    private static final int MIN_GROUP_SIZE = 3;

    /**
     * DiD分析を実行して結果テキストを返す。
     *
     * @param records  全年度の統合データ
     * @param baseYear 比較前年度
     * @param treatYear 比較後年度
     * @return 分析結果テキスト
     */
    public String analyze(List<MergedRecord> records, int baseYear, int treatYear) {
        Map<String, MergedRecord> baseData = records.stream()
            .filter(r -> r.fiscalYear() == baseYear)
            .collect(Collectors.toMap(MergedRecord::edinetCode, r -> r));
        Map<String, MergedRecord> treatData = records.stream()
            .filter(r -> r.fiscalYear() == treatYear)
            .collect(Collectors.toMap(MergedRecord::edinetCode, r -> r));

        // 両年度にデータが存在する企業のみ対象
        List<String> companies = new ArrayList<>(baseData.keySet());
        companies.retainAll(treatData.keySet());

        // 処理群・対照群の振り分け
        List<String> treatment = companies.stream()
            .filter(code -> {
                double baseScore  = baseData.get(code).totalScore();
                double treatScore = treatData.get(code).totalScore();
                // ベーススコアが0のときはスコア増加率を計算できないため対照群とする
                return baseScore > 0 && treatScore / baseScore >= TREAT_THRESHOLD;
            })
            .collect(Collectors.toList());

        List<String> control = companies.stream()
            .filter(code -> !treatment.contains(code))
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("=== 差分の差分法（DiD）分析 ===\n");
        sb.append(String.format("ベース年度: %d  処理年度: %d%n", baseYear, treatYear));
        sb.append(String.format("処理群（totalScore前年比+50%%以上）: %d社  対照群: %d社%n%n",
            treatment.size(), control.size()));

        if (treatment.size() < MIN_GROUP_SIZE || control.size() < MIN_GROUP_SIZE) {
            sb.append(String.format(
                "データが不足しています（各群最低%d社必要）。%n", MIN_GROUP_SIZE));
            return sb.toString();
        }

        appendDidResult(sb, "営業利益率 (%)", treatment, control, baseData, treatData,
            MergedRecord::operatingMargin);
        appendDidResult(sb, "ROA (%)",       treatment, control, baseData, treatData,
            MergedRecord::roa);

        return sb.toString();
    }

    private void appendDidResult(
            StringBuilder sb,
            String metricName,
            List<String> treatment,
            List<String> control,
            Map<String, MergedRecord> baseData,
            Map<String, MergedRecord> treatData,
            java.util.function.Function<MergedRecord, Double> metric) {

        double treatBefore = mean(treatment, baseData, metric);
        double treatAfter  = mean(treatment, treatData, metric);
        double ctrlBefore  = mean(control,   baseData, metric);
        double ctrlAfter   = mean(control,   treatData, metric);
        double did         = (treatAfter - treatBefore) - (ctrlAfter - ctrlBefore);

        // DiD の有意性検定: 処理群の変化 vs 対照群の変化を t検定
        double[] treatChanges = toChanges(treatment, baseData, treatData, metric);
        double[] ctrlChanges  = toChanges(control,  baseData, treatData, metric);

        sb.append("【").append(metricName).append("】\n");
        sb.append(String.format("  処理群: Before=%.3f  After=%.3f  Δ=%.3f%n",
            treatBefore, treatAfter, treatAfter - treatBefore));
        sb.append(String.format("  対照群: Before=%.3f  After=%.3f  Δ=%.3f%n",
            ctrlBefore, ctrlAfter, ctrlAfter - ctrlBefore));
        sb.append(String.format("  DiD推定量: %.3f%n", did));

        if (treatChanges.length >= 2 && ctrlChanges.length >= 2) {
            try {
                TTest tTest = new TTest();
                double tStat = tTest.t(treatChanges, ctrlChanges);
                double pVal  = tTest.tTest(treatChanges, ctrlChanges);
                sb.append(String.format("  t検定: t=%.3f  p=%.4f%s%n",
                    tStat, pVal, significance(pVal)));
            } catch (Exception e) {
                sb.append("  t検定: 計算不可（サンプル不足）\n");
            }
        } else {
            sb.append("  t検定: データ不足のためスキップ\n");
        }
        sb.append("\n");
    }

    private double mean(List<String> codes, Map<String, MergedRecord> data,
                        java.util.function.Function<MergedRecord, Double> metric) {
        DescriptiveStatistics ds = new DescriptiveStatistics(
            codes.stream()
                .map(data::get)
                .filter(Objects::nonNull)
                .map(metric)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .toArray()
        );
        return ds.getMean();
    }

    private double[] toChanges(List<String> codes,
                               Map<String, MergedRecord> baseData,
                               Map<String, MergedRecord> treatData,
                               java.util.function.Function<MergedRecord, Double> metric) {
        return codes.stream()
            .filter(code -> baseData.containsKey(code) && treatData.containsKey(code))
            .mapToDouble(code -> {
                Double before = metric.apply(baseData.get(code));
                Double after  = metric.apply(treatData.get(code));
                return (before != null && after != null)
                    ? after - before
                    : Double.NaN;
            })
            .filter(v -> !Double.isNaN(v))
            .toArray();
    }

    private static String significance(double p) {
        if (p < 0.01) return " ***";
        if (p < 0.05) return " **";
        if (p < 0.10) return " *";
        return "";
    }
}
