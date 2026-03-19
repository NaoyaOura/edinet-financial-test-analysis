package jp.ac.example.xbrl.edinet;

import jp.ac.example.xbrl.db.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentDownloader の ZIP 展開ロジックの単体テスト。
 * 実際の EDINET API は呼び出さない。
 */
class DocumentDownloaderTest {

    @TempDir
    Path tempDir;

    private DocumentDownloader downloader;

    @BeforeEach
    void setUp() {
        // EdinetApiClient と DatabaseManager はこのテストでは使わない
        downloader = new DocumentDownloader(null, null, tempDir.toString());
    }

    @Test
    void ZIPが正常に展開されること() throws IOException {
        byte[] zipBytes = createZip(
            new ZipEntry("XBRL/report.xbrl"), "XBRLコンテンツ".getBytes(),
            new ZipEntry("HTML/report.html"), "<html>報告書</html>".getBytes()
        );

        File destDir = new File(tempDir.toFile(), "S100TEST");
        downloader.extractZip(zipBytes, destDir);

        assertTrue(new File(destDir, "XBRL/report.xbrl").exists());
        assertTrue(new File(destDir, "HTML/report.html").exists());
    }

    @Test
    void ディレクトリエントリが正しく作成されること() throws IOException {
        byte[] zipBytes = createZipWithDir("subdir/", "subdir/file.txt", "内容".getBytes());

        File destDir = new File(tempDir.toFile(), "S100DIR");
        downloader.extractZip(zipBytes, destDir);

        assertTrue(new File(destDir, "subdir").isDirectory());
        assertTrue(new File(destDir, "subdir/file.txt").exists());
    }

    @Test
    void ZIPスリップ攻撃のエントリは拒否されること() {
        assertThrows(IOException.class, () -> {
            byte[] zipBytes = createZip(
                new ZipEntry("../../etc/passwd"), "攻撃コンテンツ".getBytes()
            );
            File destDir = new File(tempDir.toFile(), "S100ATTACK");
            downloader.extractZip(zipBytes, destDir);
        });
    }

    @Test
    void 展開先ディレクトリが存在しない場合は自動作成されること() throws IOException {
        byte[] zipBytes = createZip(new ZipEntry("file.txt"), "内容".getBytes());

        File destDir = new File(tempDir.toFile(), "newdir/S100NEW");
        downloader.extractZip(zipBytes, destDir);

        assertTrue(destDir.exists());
        assertTrue(new File(destDir, "file.txt").exists());
    }

    // --- テスト用ヘルパー ---

    private byte[] createZip(ZipEntry entry, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZip(ZipEntry entry1, byte[] content1,
                              ZipEntry entry2, byte[] content2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(entry1);
            zos.write(content1);
            zos.closeEntry();
            zos.putNextEntry(entry2);
            zos.write(content2);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithDir(String dirName, String fileName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(dirName));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
