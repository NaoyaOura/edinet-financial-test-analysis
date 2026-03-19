package jp.ac.example.xbrl.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LagRegressionAnalyzerTest {

    private final LagRegressionAnalyzer analyzer = new LagRegressionAnalyzer();

    /**
     * totalScore(t) が翌年営業利益率と正の線形関係を持つ合成パネルデータ。
     * 小売業5社 + IT業5社を混在させて業種ダミーが定数列にならないようにする。
     */
    private List<MergedRecord> buildLagData() {
        List<MergedRecord> records = new ArrayList<>();
        double[] noises = {0.2, -0.1, 0.3, -0.2, 0.1, 0.4, -0.3, 0.2, -0.1, 0.3};
        String[] industries = {"RETAIL", "RETAIL", "RETAIL", "RETAIL", "RETAIL",
                               "IT", "IT", "IT", "IT", "IT"};
        for (int comp = 0; comp < 10; comp++) {
            for (int year = 2021; year <= 2023; year++) {
                double score  = comp * 5.0;
                double sales  = 1_000_000.0 * (comp + 1) * (1.0 + (year - 2021) * 0.05);
                double opInc  = sales * (0.05 + 0.001 * score + noises[comp % noises.length] * 0.01);
                records.add(new MergedRecord(
                    "E" + String.format("%05d", comp), year, industries[comp],
                    sales, opInc, opInc * 0.7, sales * 2.0, sales,
                    score, score * 0.5, score * 0.3, score * 0.2, 5000
                ));
            }
        }
        return records;
    }

    @Test
    void analyze_正常系でレポートが返る() {
        String report = analyzer.analyze(buildLagData());
        assertTrue(report.contains("ラグ回帰"), "タイトルが含まれること");
        assertTrue(report.contains("R²"), "R²が含まれること");
        assertTrue(report.contains("totalScore"), "説明変数名が含まれること");
    }

    @Test
    void analyze_データ不足でスキップメッセージが返る() {
        String report = analyzer.analyze(List.of());
        assertTrue(report.contains("データが不足"), "データ不足メッセージが出ること");
    }

    @Test
    void analyze_係数とp値が出力される() {
        String report = analyzer.analyze(buildLagData());
        // 回帰係数と p値の列が出力されること
        assertTrue(report.contains("定数項"), "定数項が含まれること");
        assertTrue(report.contains("log(売上高)"), "log(売上高)が含まれること");
    }
}
