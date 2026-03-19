package jp.ac.example.xbrl.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 分析レポートを stdout およびファイルに出力するクラス。
 */
public class TextReporter {

    private final File outputDir;

    public TextReporter(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * 複数の分析結果を結合して stdout に表示し、ファイルに保存する。
     *
     * @param sections 各分析のレポート文字列（セクション）
     * @return 出力したファイル
     */
    public File writeReport(String... sections) throws IOException {
        outputDir.mkdirs();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File file = new File(outputDir, "analysis_report_" + timestamp + ".txt");

        StringBuilder full = new StringBuilder();
        full.append("EDINET キーワードスコア分析レポート\n");
        full.append("生成日時: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        full.append("=".repeat(70)).append("\n\n");

        for (String section : sections) {
            full.append(section).append("\n");
            full.append("-".repeat(70)).append("\n\n");
        }

        String content = full.toString();
        System.out.print(content);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(content);
        }

        return file;
    }
}
