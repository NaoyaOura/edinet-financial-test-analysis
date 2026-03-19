package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.analysis.AnalysisDataLoader;
import jp.ac.example.xbrl.analysis.DifferenceInDifferences;
import jp.ac.example.xbrl.analysis.GroupComparator;
import jp.ac.example.xbrl.analysis.LagRegressionAnalyzer;
import jp.ac.example.xbrl.analysis.MergedRecord;
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

        System.out.println("=== analyze 開始 ===");

        List<MergedRecord> records;
        try (Connection conn = dbManager.getConnection()) {
            AnalysisDataLoader loader = new AnalysisDataLoader(conn);
            // lag-regression と panel は全年度のデータが必要
            boolean needAllYears = type.equals("lag-regression")
                || type.equals("panel")
                || type.equals("all");
            records = loader.load(needAllYears ? 0 : fiscalYear);
        } catch (Exception e) {
            System.err.println("データの読み込みに失敗しました: " + e.getMessage());
            return;
        }

        System.out.printf("読み込みレコード数: %d件%n", records.size());

        if (records.isEmpty()) {
            System.out.println("分析対象データがありません（parse-xbrl と score-keywords が完了しているか確認してください）。");
            return;
        }

        // group-comparison と did で年度フィルタが必要な場合は別途フィルタ
        List<MergedRecord> filteredRecords = (fiscalYear > 0)
            ? records.stream().filter(r -> r.fiscalYear() == fiscalYear).toList()
            : records;

        try {
            List<String> sections = new ArrayList<>();

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
                case "all" -> {
                    sections.add(new GroupComparator().formatReport(filteredRecords));
                    sections.add(new LagRegressionAnalyzer().analyze(records));
                    sections.add(new DifferenceInDifferences().analyze(
                        records, DifferenceInDifferences.DEFAULT_BASE_YEAR,
                        DifferenceInDifferences.DEFAULT_TREAT_YEAR));
                    sections.add(new PanelDataAnalyzer().analyze(records));
                }
                default -> {
                    System.err.println("不明な --type: " + type +
                        "（group-comparison / lag-regression / did / panel / all のいずれかを指定してください）");
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
