package jp.ac.example.xbrl.xbrl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * XBRLファイルをDOMパースして財務指標値を抽出するクラス。
 * 連結財務諸表（contextRefに"Consolidated"を含む）を優先し、
 * 存在しない場合は個別財務諸表を使用する。
 */
public class XbrlParser {

    /**
     * XBRLファイルをパースし、要素名→値のマップを返す。
     * キーはローカル要素名（例: "NetSales"）、値は円換算済みの数値。
     *
     * @param xbrlFile パース対象のXBRLファイル
     * @return 要素名→値のマップ（取得できなかった項目は含まれない）
     */
    public Map<String, Double> parse(File xbrlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // XXE攻撃対策
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document doc = factory.newDocumentBuilder().parse(xbrlFile);
        doc.getDocumentElement().normalize();

        // 連結・個別それぞれの値を収集する
        Map<String, Double> consolidated = new HashMap<>();
        Map<String, Double> nonConsolidated = new HashMap<>();

        NodeList allElements = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < allElements.getLength(); i++) {
            if (!(allElements.item(i) instanceof Element el)) continue;

            String localName = el.getLocalName();
            if (localName == null) continue;

            String contextRef = el.getAttribute("contextRef");
            String unitRef = el.getAttribute("unitRef");
            String textContent = el.getTextContent().trim();

            if (textContent.isEmpty()) continue;

            // 通貨単位（JPY系）の要素のみ対象
            if (!unitRef.isEmpty() && !unitRef.contains("JPY") && !unitRef.contains("jpy")) continue;

            Double value = parseValue(textContent, el.getAttribute("decimals"));
            if (value == null) continue;

            boolean isConsolidated = (contextRef.contains("Consolidated") || contextRef.contains("consolidated"))
                && !contextRef.contains("NonConsolidated") && !contextRef.contains("nonConsolidated");

            if (isConsolidated) {
                consolidated.put(localName, value);
            } else if (!contextRef.contains("Prior") && !contextRef.contains("prior")) {
                // 前期比較数値は除外し、当期のみ対象
                nonConsolidated.putIfAbsent(localName, value);
            }
        }

        // 連結を優先し、なければ個別の値を使用する
        Map<String, Double> result = new HashMap<>(nonConsolidated);
        result.putAll(consolidated);
        return result;
    }

    /**
     * テキスト値と decimals 属性から円換算済みのDouble値を返す。
     * decimals="-3" は千円単位、decimals="-6" は百万円単位を意味する。
     */
    Double parseValue(String text, String decimals) {
        try {
            double value = Double.parseDouble(text);

            if (decimals != null && !decimals.isEmpty() && !decimals.equals("INF")) {
                int dec = Integer.parseInt(decimals);
                if (dec < 0) {
                    // 例: decimals="-3" → 値×1000（千円→円）
                    value = value * Math.pow(10, -dec);
                }
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * XBRLファイルから業種コードを抽出する。
     * DEI（書類の書誌情報）セクションの IndustryCategoryCode 要素を探す。
     *
     * @param xbrlFile パース対象のXBRLファイル
     * @return 業種コード（例: "6100"）。見つからない場合は空文字
     */
    public String extractIndustryCode(File xbrlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document doc = factory.newDocumentBuilder().parse(xbrlFile);
        doc.getDocumentElement().normalize();

        NodeList allElements = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < allElements.getLength(); i++) {
            if (!(allElements.item(i) instanceof Element el)) continue;
            String localName = el.getLocalName();
            if (localName == null) continue;
            // EDINET XBRL では IndustryCategoryCode または IndustryCategoryCodeDEI に業種コードが格納される
            if (localName.equals("IndustryCategoryCode") || localName.equals("IndustryCategoryCodeDEI")) {
                String value = el.getTextContent().trim();
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    /**
     * 指定ディレクトリ配下から財務XBRL（PublicDoc 内の jpcrp/jppfs 系）を探して返す。
     * AuditDoc 内の監査XBRL（jpaud- 等）は財務データを持たないため除外する。
     * 見つからない場合は null を返す。
     */
    public File findXbrlFile(File dir) {
        if (!dir.isDirectory()) return null;

        // PublicDoc ディレクトリを優先して探す
        File publicDoc = new File(dir, "PublicDoc");
        if (!publicDoc.isDirectory()) {
            // XBRL/PublicDoc 形式の場合
            File xbrlPublicDoc = new File(new File(dir, "XBRL"), "PublicDoc");
            if (xbrlPublicDoc.isDirectory()) publicDoc = xbrlPublicDoc;
        }

        if (publicDoc.isDirectory()) {
            for (File f : publicDoc.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".xbrl") && isFinancialXbrl(f.getName())) {
                    return f;
                }
            }
        }

        // PublicDoc が見つからない場合は全体を再帰検索（jpaud- 等は除外）
        return findXbrlFileRecursive(dir);
    }

    /**
     * 財務XBRL かどうかを判定する。監査系ファイル（jpaud-）は除外する。
     */
    private boolean isFinancialXbrl(String fileName) {
        return !fileName.startsWith("jpaud-");
    }

    private File findXbrlFileRecursive(File dir) {
        if (!dir.isDirectory()) return null;
        for (File f : dir.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".xbrl") && isFinancialXbrl(f.getName())) return f;
            if (f.isDirectory()) {
                File found = findXbrlFileRecursive(f);
                if (found != null) return found;
            }
        }
        return null;
    }
}
