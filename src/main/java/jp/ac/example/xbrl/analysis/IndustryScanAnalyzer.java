package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全業種を対象に「DX/AIキーワード(t) → 翌期業績(t+1)」の効果を業種別に比較するクラス。
 *
 * 各業種にラグOLS回帰を適用し、dxScore・aiScore の係数と有意性を業種横断で比較する。
 * 回帰式: outcome(t+1) = β₀ + β₁×keyword(t) + β₂×log(売上高)(t) + ε
 *
 * MIN_OBS 未満の業種はデータ不足として除外する。
 */
public class IndustryScanAnalyzer {

    private static final int MIN_OBS = 30;

    /**
     * 単一業種・単一指標の回帰結果。
     */
    public record SectorModelResult(
        String sector33Code,
        String sector33CodeName,
        String outcomeLabel,
        String keywordLabel,
        int n,
        double beta,
        double pValue,
        boolean converged
    ) {}

    /**
     * 全業種スキャン分析を実行し、結果テキストを返す。
     *
     * @param records 全年度の統合データ（UNKNOWNを含んでいても自動除外）
     */
    public String analyze(List<MergedRecord> records) {
        // UNKNOWN業種を除外
        List<MergedRecord> known = records.stream()
            .filter(r -> !"UNKNOWN".equals(r.sector33Code()))
            .toList();

        // 業種コード → レコードリスト
        Map<String, List<MergedRecord>> bySector = known.stream()
            .collect(Collectors.groupingBy(MergedRecord::sector33Code));

        // 業種コード → 業種名（最初に見つかった名称を採用）
        Map<String, String> sectorNames = known.stream()
            .collect(Collectors.toMap(
                MergedRecord::sector33Code,
                MergedRecord::sector33CodeName,
                (a, b) -> a
            ));

        // 目的変数・キーワード変数の組み合わせ定義
        record Outcome(String label, java.util.function.Function<MergedRecord, Double> fn) {}
        record Keyword(String label, java.util.function.Function<MergedRecord, Double> fn) {}

        List<Outcome> outcomes = List.of(
            new Outcome("ROA(%)",        MergedRecord::roa),
            new Outcome("営業利益率(%)", MergedRecord::operatingMargin),
            new Outcome("純利益率(%)",   MergedRecord::netProfitMargin)
        );
        List<Keyword> keywords = List.of(
            new Keyword("dxScore",    r -> r.dxScore()),
            new Keyword("aiScore",    r -> r.aiScore()),
            new Keyword("totalScore", r -> r.totalScore())
        );

        // 業種ごとにラグペアを構築
        Map<String, List<double[]>> lagPairsByCode = new LinkedHashMap<>();
        for (String code : new TreeSet<>(bySector.keySet())) {
            List<MergedRecord> sectorRecs = bySector.get(code);
            Map<String, Map<Integer, MergedRecord>> byCompany = sectorRecs.stream()
                .collect(Collectors.groupingBy(
                    MergedRecord::edinetCode,
                    Collectors.toMap(MergedRecord::fiscalYear, r -> r)
                ));

            // 各ペアの列: [roa_t1, opMargin_t1, netMargin_t1, dx_t, ai_t, total_t, logSales_t]
            List<double[]> pairs = new ArrayList<>();
            for (var co : byCompany.entrySet()) {
                var yearMap = co.getValue();
                for (int year : yearMap.keySet()) {
                    MergedRecord cur = yearMap.get(year);
                    MergedRecord nxt = yearMap.get(year + 1);
                    if (nxt == null) continue;

                    Double roa      = nxt.roa();
                    Double opMargin = nxt.operatingMargin();
                    Double netMarg  = nxt.netProfitMargin();
                    Double logSales = cur.logNetSales();

                    // log(売上高)は必須（コントロール変数）
                    if (logSales == null) continue;

                    // 目的変数がすべてnullのペアはスキップ
                    if (roa == null && opMargin == null && netMarg == null) continue;

                    pairs.add(new double[]{
                        roa      != null ? roa      : Double.NaN,
                        opMargin != null ? opMargin : Double.NaN,
                        netMarg  != null ? netMarg  : Double.NaN,
                        cur.dxScore(),
                        cur.aiScore(),
                        cur.totalScore(),
                        logSales
                    });
                }
            }
            lagPairsByCode.put(code, pairs);
        }

        // 全組み合わせで回帰実行
        List<SectorModelResult> allResults = new ArrayList<>();
        for (String code : lagPairsByCode.keySet()) {
            String name = sectorNames.getOrDefault(code, code);
            List<double[]> pairs = lagPairsByCode.get(code);

            for (int oi = 0; oi < outcomes.size(); oi++) {
                Outcome out = outcomes.get(oi);
                for (int ki = 0; ki < keywords.size(); ki++) {
                    Keyword kw = keywords.get(ki);

                    // outcomeカラム: oi, keywordカラム: 3+ki, logSalesカラム: 6
                    final int outcomeCol = oi;
                    final int kwCol      = 3 + ki;

                    List<double[]> valid = pairs.stream()
                        .filter(p -> !Double.isNaN(p[outcomeCol]) && !Double.isNaN(p[kwCol]))
                        .toList();

                    if (valid.size() < MIN_OBS) {
                        allResults.add(new SectorModelResult(
                            code, name, out.label(), kw.label(),
                            valid.size(), Double.NaN, Double.NaN, false
                        ));
                        continue;
                    }

                    double[] y = valid.stream().mapToDouble(p -> p[outcomeCol]).toArray();
                    double[][] x = valid.stream()
                        .map(p -> new double[]{p[kwCol], p[6]}) // keyword, logSales
                        .toArray(double[][]::new);

                    // 定数列チェック
                    if (isConstantColumn(x, 0)) {
                        allResults.add(new SectorModelResult(
                            code, name, out.label(), kw.label(),
                            valid.size(), Double.NaN, Double.NaN, false
                        ));
                        continue;
                    }

                    try {
                        OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
                        reg.newSampleData(y, x);
                        double[] params = reg.estimateRegressionParameters();
                        double[] stderr = reg.estimateRegressionParametersStandardErrors();
                        int n = y.length;
                        int k = params.length;
                        TDistribution tDist = new TDistribution(n - k);
                        double beta = params[1]; // keyword係数
                        double se   = stderr[1];
                        double tVal = beta / se;
                        double pVal = 2.0 * tDist.cumulativeProbability(-Math.abs(tVal));

                        allResults.add(new SectorModelResult(
                            code, name, out.label(), kw.label(),
                            n, beta, pVal, true
                        ));
                    } catch (MathIllegalArgumentException e) {
                        allResults.add(new SectorModelResult(
                            code, name, out.label(), kw.label(),
                            valid.size(), Double.NaN, Double.NaN, false
                        ));
                    }
                }
            }
        }

        return formatReport(allResults, lagPairsByCode, sectorNames);
    }

    private String formatReport(
            List<SectorModelResult> results,
            Map<String, List<double[]>> lagPairs,
            Map<String, String> sectorNames) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== 業種横断スキャン：DX/AIキーワード → 翌期業績 ===\n");
        sb.append("回帰式: outcome(t+1) = β₀ + β₁×keyword(t) + β₂×log(売上高)(t) + ε\n");
        sb.append(String.format("対象: UNKNOWN除外、ラグペア数 ≥ %d 件の業種のみ有効%n%n", MIN_OBS));

        // ── 業種別サマリー表（dxScore → ROA に絞って一覧） ──────────────
        sb.append("■ 業種別サマリー（dxScore → ROA(t+1) | log(売上高)コントロール）\n");
        sb.append(String.format("  %-6s  %-18s  %5s  %9s  %8s%n",
            "コード", "業種名", "n", "β(dxScore)", "p値"));
        sb.append("  " + "─".repeat(55) + "\n");

        List<SectorModelResult> dxRoa = results.stream()
            .filter(r -> "dxScore".equals(r.keywordLabel()) && "ROA(%)".equals(r.outcomeLabel()))
            .sorted(Comparator.comparing(SectorModelResult::sector33Code))
            .toList();

        for (SectorModelResult r : dxRoa) {
            if (!r.converged()) {
                sb.append(String.format("  %-6s  %-18s  %5d  %9s  %8s%n",
                    r.sector33Code(), truncate(r.sector33CodeName(), 18),
                    r.n(), "データ不足", "─"));
            } else {
                sb.append(String.format("  %-6s  %-18s  %5d  %9.4f  %8.4f%s%n",
                    r.sector33Code(), truncate(r.sector33CodeName(), 18),
                    r.n(), r.beta(), r.pValue(), sig(r.pValue())));
            }
        }
        sb.append("  * p<.10  ** p<.05  *** p<.01\n\n");

        // ── 有意業種ランキング（dxScore → ROA p<0.10） ──────────────────
        List<SectorModelResult> sigDxRoa = dxRoa.stream()
            .filter(r -> r.converged() && r.pValue() < 0.10)
            .sorted(Comparator.comparingDouble(SectorModelResult::pValue))
            .toList();

        sb.append("■ dxScore → ROA(t+1) で有意な業種（p<0.10）\n");
        if (sigDxRoa.isEmpty()) {
            sb.append("  有意な業種は検出されませんでした。\n\n");
        } else {
            sb.append(String.format("  %-6s  %-18s  %5s  %9s  %8s%n",
                "コード", "業種名", "n", "β(dxScore)", "p値"));
            sb.append("  " + "─".repeat(55) + "\n");
            for (SectorModelResult r : sigDxRoa) {
                sb.append(String.format("  %-6s  %-18s  %5d  %9.4f  %8.4f%s%n",
                    r.sector33Code(), truncate(r.sector33CodeName(), 18),
                    r.n(), r.beta(), r.pValue(), sig(r.pValue())));
            }
            sb.append("\n");
        }

        // ── 有意業種ランキング（aiScore → ROA p<0.10） ──────────────────
        List<SectorModelResult> sigAiRoa = results.stream()
            .filter(r -> "aiScore".equals(r.keywordLabel()) && "ROA(%)".equals(r.outcomeLabel())
                         && r.converged() && r.pValue() < 0.10)
            .sorted(Comparator.comparingDouble(SectorModelResult::pValue))
            .toList();

        sb.append("■ aiScore → ROA(t+1) で有意な業種（p<0.10）\n");
        if (sigAiRoa.isEmpty()) {
            sb.append("  有意な業種は検出されませんでした。\n\n");
        } else {
            sb.append(String.format("  %-6s  %-18s  %5s  %9s  %8s%n",
                "コード", "業種名", "n", "β(aiScore)", "p値"));
            sb.append("  " + "─".repeat(55) + "\n");
            for (SectorModelResult r : sigAiRoa) {
                sb.append(String.format("  %-6s  %-18s  %5d  %9.4f  %8.4f%s%n",
                    r.sector33Code(), truncate(r.sector33CodeName(), 18),
                    r.n(), r.beta(), r.pValue(), sig(r.pValue())));
            }
            sb.append("\n");
        }

        // ── 全指標 × 全キーワード 業種別詳細 ────────────────────────────
        sb.append("■ 業種別詳細（全目的変数 × 全キーワード変数）\n\n");

        // 業種コードでグループ化
        Map<String, List<SectorModelResult>> bySector = new LinkedHashMap<>();
        for (SectorModelResult r : results) {
            bySector.computeIfAbsent(r.sector33Code(), k -> new ArrayList<>()).add(r);
        }

        for (var entry : bySector.entrySet()) {
            String code = entry.getKey();
            String name = sectorNames.getOrDefault(code, code);
            List<double[]> pairs = lagPairs.getOrDefault(code, List.of());
            int nPairs = pairs.size();

            sb.append(String.format("  [%s] %s（ラグペア数: %d件）%n", code, name, nPairs));
            if (nPairs < MIN_OBS) {
                sb.append(String.format("    → データ不足（%d件 < %d件）のためスキップ%n%n", nPairs, MIN_OBS));
                continue;
            }

            sb.append(String.format("    %-14s  %-12s  %5s  %9s  %8s%n",
                "目的変数(t+1)", "キーワード", "n", "β", "p値"));
            sb.append("    " + "─".repeat(55) + "\n");

            for (SectorModelResult r : entry.getValue()) {
                if (!r.converged()) {
                    sb.append(String.format("    %-14s  %-12s  %5d  %9s  %8s%n",
                        r.outcomeLabel(), r.keywordLabel(), r.n(), "─", "計算不可"));
                } else {
                    sb.append(String.format("    %-14s  %-12s  %5d  %9.4f  %8.4f%s%n",
                        r.outcomeLabel(), r.keywordLabel(), r.n(),
                        r.beta(), r.pValue(), sig(r.pValue())));
                }
            }
            sb.append("\n");
        }

        // ── 統合サマリー ─────────────────────────────────────────────────
        sb.append("■ 統合サマリー\n\n");

        long analyzable = dxRoa.stream().filter(SectorModelResult::converged).count();
        long posSig = dxRoa.stream()
            .filter(r -> r.converged() && r.beta() > 0 && r.pValue() < 0.05).count();
        long negSig = dxRoa.stream()
            .filter(r -> r.converged() && r.beta() < 0 && r.pValue() < 0.05).count();

        sb.append(String.format("  分析可能業種数: %d業種%n", analyzable));
        sb.append(String.format("  dxScore→ROA 正・有意（p<.05）: %d業種%n", posSig));
        sb.append(String.format("  dxScore→ROA 負・有意（p<.05）: %d業種%n", negSig));
        sb.append(String.format("  非有意: %d業種%n%n", analyzable - posSig - negSig));

        if (!sigDxRoa.isEmpty()) {
            sb.append("  dxScoreのROAへの正効果が最も顕著な業種（p<.05・β>0）:\n");
            sigDxRoa.stream()
                .filter(r -> r.beta() > 0 && r.pValue() < 0.05)
                .forEach(r -> sb.append(String.format(
                    "    → %s（%s）: β=%.4f, p=%.4f%s%n",
                    r.sector33CodeName(), r.sector33Code(),
                    r.beta(), r.pValue(), sig(r.pValue()))));
        }

        sb.append("\n  * p<.10  ** p<.05  *** p<.01\n");
        return sb.toString();
    }

    private boolean isConstantColumn(double[][] x, int col) {
        double first = x[0][col];
        for (double[] row : x) {
            if (Math.abs(row[col] - first) > 1e-12) return false;
        }
        return true;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        // 日本語文字はUnicodeで1文字=2バイト相当の幅になるためバイト数で近似
        int len = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            int w = c > 127 ? 2 : 1;
            if (len + w > maxLen) { sb.append("…"); break; }
            sb.append(c);
            len += w;
        }
        // パディング
        while (len < maxLen) { sb.append(' '); len++; }
        return sb.toString();
    }

    private static String sig(double p) {
        if (p < 0.01) return " ***";
        if (p < 0.05) return "  **";
        if (p < 0.10) return "   *";
        return "";
    }
}
