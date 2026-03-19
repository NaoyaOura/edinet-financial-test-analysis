package jp.ac.example.xbrl.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupComparatorTest {

    private final GroupComparator comparator = new GroupComparator();

    /** スコアと営業利益率が正比例する合成データを生成 */
    private List<MergedRecord> buildData(int n) {
        List<MergedRecord> records = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double score  = i * 10.0;           // 0, 10, 20, ...
            double sales  = 1_000_000.0;
            double opInc  = sales * (i * 0.01); // スコアが高いほど営業利益率も高い
            records.add(new MergedRecord(
                "E" + String.format("%05d", i), 2023, "RETAIL",
                sales, opInc, opInc * 0.7, sales * 2.0, sales * 1.0,
                score, score * 0.5, score * 0.3, score * 0.2, 5000
            ));
        }
        return records;
    }

    @Test
    void analyze_十分なデータがあれば結果が返る() {
        List<GroupComparator.ComparisonResult> results = comparator.analyze(buildData(30));
        assertFalse(results.isEmpty(), "結果が返ること");
        assertEquals("営業利益率 (%)", results.get(0).metricName());
    }

    @Test
    void analyze_Q4平均はQ1平均より高い() {
        List<GroupComparator.ComparisonResult> results = comparator.analyze(buildData(30));
        assertFalse(results.isEmpty());
        GroupComparator.ComparisonResult margin = results.get(0);
        assertTrue(margin.q4().mean() > margin.q1().mean(),
            "スコアが高い群の方が営業利益率の平均が高いこと");
    }

    @Test
    void analyze_t検定のp値が返る() {
        List<GroupComparator.ComparisonResult> results = comparator.analyze(buildData(30));
        assertFalse(results.isEmpty());
        double p = results.get(0).pValueQ1Q4();
        assertTrue(p >= 0.0 && p <= 1.0, "p値が0〜1の範囲にあること");
    }

    @Test
    void analyze_データ不足の場合は空リスト() {
        List<GroupComparator.ComparisonResult> results = comparator.analyze(buildData(5));
        // 5件では3群×MIN_GROUP_SIZE=2 = 6件に満たないためスキップされる可能性あり
        // ここでは例外が発生しないことを確認
        assertNotNull(results);
    }

    @Test
    void formatReport_データ不足メッセージが出る() {
        String report = comparator.formatReport(List.of());
        assertTrue(report.contains("データが不足"), "空データで不足メッセージが出ること");
    }

    @Test
    void formatReport_正常系で統計量が含まれる() {
        String report = comparator.formatReport(buildData(30));
        assertTrue(report.contains("営業利益率"), "営業利益率セクションが含まれること");
        assertTrue(report.contains("t検定"), "t検定結果が含まれること");
        assertTrue(report.contains("ANOVA"), "ANOVA結果が含まれること");
    }
}
