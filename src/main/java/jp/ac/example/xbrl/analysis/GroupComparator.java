package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.stat.inference.TTest;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * キーワードスコアの四分位グループ（Q1・中間・Q4）間で財務指標を比較するクラス。
 *
 * 高・中・低スコア群の営業利益率・ROA の平均値を比較し、
 * Q1 vs Q4 の t検定と3群 ANOVA により有意差を検定する。
 */
public class GroupComparator {

    /** 最低サンプル数（これ未満の場合はスキップ） */
    private static final int MIN_GROUP_SIZE = 2;

    /**
     * 各グループの統計量を保持するレコード。
     */
    public record GroupStats(String label, int n, double mean, double std) {
        @Override
        public String toString() {
            return String.format("%-8s n=%3d  平均=%7.3f  SD=%6.3f", label, n, mean, std);
        }
    }

    /**
     * 1指標の比較結果を保持するレコード。
     */
    public record ComparisonResult(
        String metricName,
        GroupStats q1,
        GroupStats mid,
        GroupStats q4,
        double tStatQ1Q4,
        double pValueQ1Q4,
        double fStat,
        double pValueAnova
    ) {}

    /**
     * グループ比較を実行して結果を返す。
     *
     * @param records 分析対象レコード（単一年度 または 全年度）
     * @return 各指標の比較結果リスト
     */
    public List<ComparisonResult> analyze(List<MergedRecord> records) {
        if (records.size() < MIN_GROUP_SIZE * 3) {
            return List.of();
        }

        // totalScore の四分位を計算
        double[] scores = records.stream().mapToDouble(MergedRecord::totalScore).toArray();
        DescriptiveStatistics ds = new DescriptiveStatistics(scores);
        double q25 = ds.getPercentile(25);
        double q75 = ds.getPercentile(75);

        List<MergedRecord> q1Group  = records.stream().filter(r -> r.totalScore() <= q25).toList();
        List<MergedRecord> midGroup = records.stream().filter(r -> r.totalScore() > q25 && r.totalScore() <= q75).toList();
        List<MergedRecord> q4Group  = records.stream().filter(r -> r.totalScore() > q75).toList();

        return List.of(
            compare("営業利益率 (%)", q1Group, midGroup, q4Group, MergedRecord::operatingMargin),
            compare("ROA (%)",       q1Group, midGroup, q4Group, MergedRecord::roa)
        ).stream().filter(Objects::nonNull).toList();
    }

    private ComparisonResult compare(
            String name,
            List<MergedRecord> q1Group,
            List<MergedRecord> midGroup,
            List<MergedRecord> q4Group,
            Function<MergedRecord, Double> metric) {

        double[] q1  = toArray(q1Group, metric);
        double[] mid = toArray(midGroup, metric);
        double[] q4  = toArray(q4Group, metric);

        if (q1.length < MIN_GROUP_SIZE || q4.length < MIN_GROUP_SIZE) return null;

        TTest tTest = new TTest();
        double tStat  = tTest.t(q1, q4);
        double pValue = tTest.tTest(q1, q4);

        double fStat = Double.NaN;
        double pAnova = Double.NaN;
        if (mid.length >= MIN_GROUP_SIZE) {
            try {
                OneWayAnova anova = new OneWayAnova();
                fStat  = anova.anovaFValue(Arrays.asList(q1, mid, q4));
                pAnova = anova.anovaPValue(Arrays.asList(q1, mid, q4));
            } catch (Exception e) {
                // データ不足などでANOVA計算不能の場合はスキップ
            }
        }

        return new ComparisonResult(
            name,
            toGroupStats("Q1（低）", q1),
            toGroupStats("中間",     mid),
            toGroupStats("Q4（高）", q4),
            tStat, pValue,
            fStat, pAnova
        );
    }

    private double[] toArray(List<MergedRecord> group, Function<MergedRecord, Double> metric) {
        return group.stream()
            .map(metric)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
    }

    private GroupStats toGroupStats(String label, double[] values) {
        if (values.length == 0) return new GroupStats(label, 0, Double.NaN, Double.NaN);
        DescriptiveStatistics ds = new DescriptiveStatistics(values);
        return new GroupStats(label, values.length, ds.getMean(), ds.getStandardDeviation());
    }

    /**
     * 分析結果を人間が読めるテキストに整形して返す。
     */
    public String formatReport(List<MergedRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== グループ比較分析 ===\n");
        sb.append(String.format("対象レコード数: %d件%n%n", records.size()));

        List<ComparisonResult> results = analyze(records);
        if (results.isEmpty()) {
            sb.append("データが不足しています（最低").append(MIN_GROUP_SIZE * 3).append("件必要）。\n");
            return sb.toString();
        }

        double[] scores = records.stream().mapToDouble(MergedRecord::totalScore).toArray();
        DescriptiveStatistics ds = new DescriptiveStatistics(scores);
        sb.append(String.format("totalScore 四分位: Q25=%.4f  Q75=%.4f%n%n", ds.getPercentile(25), ds.getPercentile(75)));

        for (ComparisonResult r : results) {
            sb.append("【").append(r.metricName()).append("】\n");
            sb.append("  ").append(r.q1()).append("\n");
            sb.append("  ").append(r.mid()).append("\n");
            sb.append("  ").append(r.q4()).append("\n");
            sb.append(String.format("  t検定 (Q1 vs Q4): t=%.3f  p=%.4f%s%n",
                r.tStatQ1Q4(), r.pValueQ1Q4(), significance(r.pValueQ1Q4())));
            if (!Double.isNaN(r.fStat())) {
                sb.append(String.format("  ANOVA:            F=%.3f  p=%.4f%s%n",
                    r.fStat(), r.pValueAnova(), significance(r.pValueAnova())));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String significance(double p) {
        if (p < 0.01)  return " ***";
        if (p < 0.05)  return " **";
        if (p < 0.10)  return " *";
        return "";
    }
}
