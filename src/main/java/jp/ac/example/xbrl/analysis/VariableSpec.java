package jp.ac.example.xbrl.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分析に使用する変数の組み合わせを定義する仕様クラス。
 *
 * ラグ回帰・パネル分析・グループ比較の各分析で共通して利用する。
 *
 * 回帰式:
 *   outcome(t+1) = β₀ + β₁×keyword(t) + β₂×control1(t) + ... + ε
 */
public record VariableSpec(
    String outcomeLabel,
    Function<MergedRecord, Double> outcome,
    String keywordLabel,
    Function<MergedRecord, Double> keyword,
    List<String> controlLabels,
    List<Function<MergedRecord, Double>> controls
) {

    /** コントロール変数を "+" 区切りで連結した文字列（比較表の表示用） */
    public String controlSetLabel() {
        return String.join("+", controlLabels);
    }

    /**
     * デフォルト仕様（従来の固定変数）。
     * 目的変数: 営業利益率(t+1)
     * キーワード: totalScore
     * コントロール: log(売上高) + 小売業D（参照グループ: IT業・その他）
     *
     * 注意: 小売業D と IT業D を同時投入するとダミー変数トラップが生じるため、
     * どちらか片方のみを投入する。
     */
    public static VariableSpec defaultSpec() {
        return new VariableSpec(
            "営業利益率(%)",
            MergedRecord::operatingMargin,
            "totalScore",
            r -> r.totalScore(),
            List.of("log(売上高)", "小売業D"),
            List.of(MergedRecord::logNetSales, r -> r.retailDummy())
        );
    }

    /**
     * 全変数組み合わせを生成する。
     *
     * 目的変数（4種） × キーワード変数（4種） × コントロール変数セット（3種）= 最大48仕様
     */
    public static List<VariableSpec> allCombinations() {
        record OutcomeVar(String label, Function<MergedRecord, Double> fn) {}
        record KeywordVar(String label, Function<MergedRecord, Double> fn) {}
        record ControlSet(List<String> labels, List<Function<MergedRecord, Double>> fns) {}

        List<OutcomeVar> outcomes = List.of(
            new OutcomeVar("営業利益率(%)", MergedRecord::operatingMargin),
            new OutcomeVar("ROA(%)",       MergedRecord::roa),
            new OutcomeVar("ROE(%)",       MergedRecord::roe),
            new OutcomeVar("純利益率(%)",  MergedRecord::netProfitMargin)
        );

        List<KeywordVar> keywords = List.of(
            new KeywordVar("totalScore",  r -> r.totalScore()),
            new KeywordVar("genAiScore",  r -> r.genAiScore()),
            new KeywordVar("aiScore",     r -> r.aiScore()),
            new KeywordVar("dxScore",     r -> r.dxScore())
        );

        // 業種ダミーはダミー変数トラップを避けるため片方のみ投入（小売業D を採用）
        List<ControlSet> controlSets = List.of(
            new ControlSet(
                List.of("log(売上高)", "小売業D"),
                List.of(MergedRecord::logNetSales, r -> r.retailDummy())
            ),
            new ControlSet(
                List.of("log(総資産)", "小売業D"),
                List.of(MergedRecord::logTotalAssets, r -> r.retailDummy())
            ),
            new ControlSet(
                List.of("log(売上高)", "自己資本比率", "小売業D"),
                List.of(MergedRecord::logNetSales, MergedRecord::equityRatio,
                        r -> r.retailDummy())
            )
        );

        List<VariableSpec> specs = new ArrayList<>();
        for (OutcomeVar outcome : outcomes) {
            for (KeywordVar keyword : keywords) {
                for (ControlSet cs : controlSets) {
                    specs.add(new VariableSpec(
                        outcome.label(), outcome.fn(),
                        keyword.label(), keyword.fn(),
                        cs.labels(), cs.fns()
                    ));
                }
            }
        }

        // 業種×キーワード 交差項仕様（主効果をコントロールに含む）
        List<KeywordVar> crossTerms = List.of(
            new KeywordVar("dxScore×IT",     MergedRecord::dxScoreXit),
            new KeywordVar("aiScore×IT",     MergedRecord::aiScoreXit),
            new KeywordVar("dxScore×retail", MergedRecord::dxScoreXretail)
        );
        // 交差項ごとに主効果（スコア本体 + 業種ダミー）をコントロールに追加
        List<ControlSet> crossControlSets = List.of(
            new ControlSet(
                List.of("log(売上高)", "dxScore", "IT_dummy"),
                List.of(MergedRecord::logNetSales, r -> r.dxScore(), r -> r.itDummy())
            ),
            new ControlSet(
                List.of("log(売上高)", "aiScore", "IT_dummy"),
                List.of(MergedRecord::logNetSales, r -> r.aiScore(), r -> r.itDummy())
            ),
            new ControlSet(
                List.of("log(売上高)", "dxScore", "retail_dummy"),
                List.of(MergedRecord::logNetSales, r -> r.dxScore(), r -> r.retailDummy())
            )
        );
        for (OutcomeVar outcome : outcomes) {
            for (int i = 0; i < crossTerms.size(); i++) {
                KeywordVar kw = crossTerms.get(i);
                ControlSet cs = crossControlSets.get(i);
                specs.add(new VariableSpec(
                    outcome.label(), outcome.fn(),
                    kw.label(), kw.fn(),
                    cs.labels(), cs.fns()
                ));
            }
        }

        return specs;
    }
}
