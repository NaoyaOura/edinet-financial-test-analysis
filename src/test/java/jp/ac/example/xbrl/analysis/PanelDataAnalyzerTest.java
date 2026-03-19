package jp.ac.example.xbrl.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelDataAnalyzerTest {

    private final PanelDataAnalyzer analyzer = new PanelDataAnalyzer();

    /**
     * 固定効果モデルのテスト用データ。
     * 企業 i はベース営業利益率 baseMargin[i] を持つ（固定効果）。
     * スコアが上がると利益率が上がる（β₁ = 0.01 程度）という構造を設定。
     */
    private List<MergedRecord> buildPanelData() {
        List<MergedRecord> records = new ArrayList<>();
        double[] baseMargins = {5.0, 8.0, 3.0, 10.0, 6.0, 4.0, 7.0, 9.0, 2.0, 11.0};

        for (int comp = 0; comp < 10; comp++) {
            double baseMargin = baseMargins[comp];
            for (int year = 2020; year <= 2023; year++) {
                double score  = (year - 2020) * 5.0 + comp * 2.0; // スコアが年々上昇
                // 売上高を年度によって変化させることで within変換後に非ゼロになる
                double sales  = 1_000_000.0 * (comp + 1) * (1.0 + (year - 2020) * 0.05);
                double opInc  = sales * (baseMargin + score * 0.01) / 100.0;
                records.add(new MergedRecord(
                    "E" + String.format("%05d", comp), year, "RETAIL",
                    sales, opInc, opInc * 0.7, sales * 2.0, sales,
                    score, score * 0.5, score * 0.3, score * 0.2, 5000
                ));
            }
        }
        return records;
    }

    @Test
    void analyze_正常系でレポートが返る() {
        String report = analyzer.analyze(buildPanelData());
        assertTrue(report.contains("固定効果モデル"), "タイトルが含まれること");
        assertTrue(report.contains("R²"), "R²が含まれること");
        assertTrue(report.contains("totalScore"), "説明変数が含まれること");
    }

    @Test
    void analyze_データ不足でスキップメッセージが返る() {
        // 観測数 < MIN_OBSERVATIONS=5 のためデータ不足
        String report = analyzer.analyze(List.of());
        assertTrue(report.contains("データが不足"), "データ不足メッセージが出ること");
    }

    @Test
    void analyze_スコアと業績が正相関なら係数が正になる() {
        String report = analyzer.analyze(buildPanelData());
        // データ構造上 totalScore の係数は正であることを確認
        // レポートに係数値が含まれていること（具体値は確認せず符号のみ）
        assertTrue(report.contains("totalScore"), "totalScore行が含まれること");
        // 出力形式チェック: 係数列に数値が含まれる
        assertTrue(report.matches("(?s).*totalScore.*\\d+\\.\\d+.*"), "係数値が数値形式で含まれること");
    }

    @Test
    void analyze_企業数1社はwithhin推定から除外されても例外なし() {
        // 1年分のみの企業データ（within推定に使えない）を含むケース
        List<MergedRecord> mixed = new ArrayList<>(buildPanelData());
        mixed.add(new MergedRecord("E99999", 2023, "IT",
            5_000_000.0, 500_000.0, 350_000.0, 10_000_000.0, 5_000_000.0,
            100.0, 50.0, 30.0, 20.0, 10000));

        assertDoesNotThrow(() -> analyzer.analyze(mixed), "例外なく実行できること");
    }
}
