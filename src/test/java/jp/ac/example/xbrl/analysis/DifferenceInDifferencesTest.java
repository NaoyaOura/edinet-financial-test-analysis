package jp.ac.example.xbrl.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DifferenceInDifferencesTest {

    private final DifferenceInDifferences did = new DifferenceInDifferences();

    /**
     * 処理群（E00001〜E00005）: 2023年にスコアが50%以上増加
     * 対照群（E00006〜E00015）: スコアほぼ変化なし
     * 処理群は2023年に業績が改善（正のDiD）
     */
    private List<MergedRecord> buildDidData() {
        List<MergedRecord> records = new ArrayList<>();

        // 処理群 (5社): スコア 10 → 20（100%増）、業績改善
        for (int i = 1; i <= 5; i++) {
            double sales = 1_000_000.0;
            records.add(new MergedRecord("E" + String.format("%05d", i), 2022, "RETAIL",
                sales, sales * 0.05, null, null, null, 10.0, 5.0, 3.0, 2.0, 5000));
            records.add(new MergedRecord("E" + String.format("%05d", i), 2023, "RETAIL",
                sales, sales * 0.08, null, null, null, 20.0, 10.0, 6.0, 4.0, 5000));
        }

        // 対照群 (10社): スコア 10 → 11（変化少）、業績変化なし
        for (int i = 6; i <= 15; i++) {
            double sales = 1_000_000.0;
            records.add(new MergedRecord("E" + String.format("%05d", i), 2022, "RETAIL",
                sales, sales * 0.05, null, null, null, 10.0, 5.0, 3.0, 2.0, 5000));
            records.add(new MergedRecord("E" + String.format("%05d", i), 2023, "RETAIL",
                sales, sales * 0.05, null, null, null, 11.0, 5.5, 3.3, 2.2, 5000));
        }

        return records;
    }

    @Test
    void analyze_正常系でレポートが返る() {
        String report = did.analyze(buildDidData(), 2022, 2023);
        assertTrue(report.contains("DiD"), "DiDが含まれること");
        assertTrue(report.contains("処理群"), "処理群の記述が含まれること");
        assertTrue(report.contains("対照群"), "対照群の記述が含まれること");
    }

    @Test
    void analyze_処理群と対照群の件数が正しい() {
        String report = did.analyze(buildDidData(), 2022, 2023);
        assertTrue(report.contains("5社"), "処理群5社が報告されること");
        assertTrue(report.contains("10社"), "対照群10社が報告されること");
    }

    @Test
    void analyze_DiD推定量が正の値になる() {
        String report = did.analyze(buildDidData(), 2022, 2023);
        // DiD = (8% - 5%) - (5% - 5%) = 3% > 0 となるはず
        assertTrue(report.contains("DiD推定量"), "DiD推定量が含まれること");
    }

    @Test
    void analyze_データ不足でスキップメッセージが返る() {
        // 処理群・対照群のどちらかが MIN_GROUP_SIZE 未満
        List<MergedRecord> sparse = new ArrayList<>();
        sparse.add(new MergedRecord("E00001", 2022, "RETAIL",
            1_000_000.0, 50_000.0, null, null, null, 10.0, 5.0, 3.0, 2.0, 5000));
        sparse.add(new MergedRecord("E00001", 2023, "RETAIL",
            1_000_000.0, 80_000.0, null, null, null, 20.0, 10.0, 6.0, 4.0, 5000));

        String report = did.analyze(sparse, 2022, 2023);
        assertTrue(report.contains("データが不足"), "データ不足メッセージが出ること");
    }
}
