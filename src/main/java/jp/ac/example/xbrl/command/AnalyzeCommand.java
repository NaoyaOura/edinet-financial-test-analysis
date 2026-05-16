package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.analysis.AnalysisDataLoader;
import jp.ac.example.xbrl.analysis.DifferenceInDifferences;
import jp.ac.example.xbrl.analysis.GroupComparator;
import jp.ac.example.xbrl.analysis.LagRegressionAnalyzer;
import jp.ac.example.xbrl.analysis.MergedRecord;
import jp.ac.example.xbrl.analysis.MultiModelAnalyzer;
import jp.ac.example.xbrl.analysis.PanelDataAnalyzer;
import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.report.TextReporter;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 統計分析を実行してレポートを出力するコマンド。
 *
 * 使い方:
 *   analyze [--type group-comparison|lag-regression|did|panel|all]
 *           [--year <年度>] [--output <ディレクトリ>]
 *
 * --type のデフォルトは all。
 * --year は group-comparison・did のデータフィルタに使用する。
 */
public class AnalyzeCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public AnalyzeCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) {
        String type       = parseStringOption(args, "--type", "all");
        int fiscalYear    = parseIntOption(args, "--year", 0);
        String outputPath = parseStringOption(args, "--output", config.getOutputDir());
        String industry   = parseStringOption(args, "--industry", "all");

        System.out.println("=== analyze 開始 ===");

        List<MergedRecord> records;
        try (Connection conn = dbManager.getConnection()) {
            AnalysisDataLoader loader = new AnalysisDataLoader(conn);
            // lag-regression / panel / explore / all は全年度のデータが必要
            boolean needAllYears = type.equals("lag-regression")
                || type.equals("panel")
                || type.equals("explore")
                || type.equals("all");
            records = loader.load(needAllYears ? 0 : fiscalYear);
        } catch (Exception e) {
            System.err.println("データの読み込みに失敗しました: " + e.getMessage());
            return;
        }

        // 業種フィルタ
        records = switch (industry) {
            case "retail" -> records.stream().filter(MergedRecord::isRetail).toList();
            case "it"     -> records.stream().filter(MergedRecord::isIT).toList();
            default       -> records;
        };
        String industryLabel = switch (industry) {
            case "retail" -> "小売業のみ";
            case "it"     -> "情報通信業のみ";
            default       -> "全業種";
        };
        System.out.printf("業種フィルタ: %s%n", industryLabel);
        System.out.printf("読み込みレコード数: %d件%n", records.size());

        if (records.isEmpty()) {
            System.out.println("分析対象データがありません。");
            System.out.println("以下のいずれかを確認してください:");
            System.out.println("  [財務データ] jquants-fetch-info → jquants-fetch-fins --year 2022/2023/2024/2025");
            System.out.println("  [テキストデータ] parse-xbrl → score-keywords が完了しているか確認");
            return;
        }

        // group-comparison と did で年度フィルタが必要な場合は別途フィルタ
        List<MergedRecord> filteredRecords = (fiscalYear > 0)
            ? records.stream().filter(r -> r.fiscalYear() == fiscalYear).toList()
            : records;

        try {
            List<String> sections = new ArrayList<>();

            // 分析条件を先頭セクションとして追加
            sections.add(String.format(
                "=== 分析条件 ===%n業種フィルタ: %s%n対象レコード数: %d件%n",
                industryLabel, records.size()
            ));

            switch (type) {
                case "group-comparison" ->
                    sections.add(new GroupComparator().formatReport(filteredRecords));
                case "lag-regression" ->
                    sections.add(new LagRegressionAnalyzer().analyze(records));
                case "did" ->
                    sections.add(new DifferenceInDifferences().analyze(
                        records, DifferenceInDifferences.DEFAULT_BASE_YEAR,
                        DifferenceInDifferences.DEFAULT_TREAT_YEAR));
                case "panel" ->
                    sections.add(new PanelDataAnalyzer().analyze(records));
                case "explore" ->
                    sections.add(new MultiModelAnalyzer().analyze(records));
                case "all" -> {
                    sections.add(new GroupComparator().formatReport(filteredRecords));
                    sections.add(new LagRegressionAnalyzer().analyze(records));
                    sections.add(new DifferenceInDifferences().analyze(
                        records, DifferenceInDifferences.DEFAULT_BASE_YEAR,
                        DifferenceInDifferences.DEFAULT_TREAT_YEAR));
                    sections.add(new PanelDataAnalyzer().analyze(records));
                    sections.add(new MultiModelAnalyzer().analyze(records));
                }
                default -> {
                    System.err.println("不明な --type: " + type +
                        "（group-comparison / lag-regression / did / panel / explore / all のいずれかを指定してください）");
                    return;
                }
            }

            File outputDir = new File(outputPath);
            File reportFile = new TextReporter(outputDir)
                .writeReport(sections.toArray(String[]::new));
            System.out.println("\nレポートを保存しました: " + reportFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("分析中にエラーが発生しました: " + e.getMessage());
        }
    }

    private String parseStringOption(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) return args[i + 1];
        }
        return defaultValue;
    }

    private int parseIntOption(String[] args, String key, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                try { return Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException e) { return defaultValue; }
            }
        }
        return defaultValue;
    }
}
