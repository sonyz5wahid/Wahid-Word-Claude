package id.wahid.word;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Baca/tulis .docx minimal (teks, gaya karakter, perataan, daftar bullet). Tanpa library. */
final class DocxIO {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String HDR = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG = "http://schemas.openxmlformats.org/package/2006/relationships";

    // ======================= TULIS =======================
    static void write(Spanned t, OutputStream os) throws IOException {
        ZipOutputStream z = new ZipOutputStream(os);
        put(z, "[Content_Types].xml", HDR
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "</Types>");
        put(z, "_rels/.rels", HDR + "<Relationships xmlns=\"" + PKG + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        put(z, "word/_rels/document.xml.rels", HDR + "<Relationships xmlns=\"" + PKG + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL + "/styles\" Target=\"styles.xml\"/></Relationships>");
        put(z, "word/styles.xml", HDR + "<w:styles xmlns:w=\"" + W + "\"><w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>"
                + "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/>"
                + "</w:pPr></w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style></w:styles>");
        put(z, "word/document.xml", body(t));
        z.close();
    }

    private static void put(ZipOutputStream z, String name, String data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data.getBytes("UTF-8"));
        z.closeEntry();
    }

    private static String body(Spanned t) {
        StringBuilder sb = new StringBuilder(HDR);
        sb.append("<w:document xmlns:w=\"").append(W).append("\"><w:body>");
        String s = t.toString();
        int pos = 0;
        while (true) {
            int nl = s.indexOf('\n', pos);
            int end = nl < 0 ? s.length() : nl;
            para(sb, t, s, pos, end);
            if (nl < 0) break;
            pos = nl + 1;
        }
        sb.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>"
                + "</w:sectPr></w:body></w:document>");
        return sb.toString();
    }

    private static void para(StringBuilder sb, Spanned t, String s, int pos, int end) {
        sb.append("<w:p>");
        Layout.Alignment a = Fmt.alignAt(t, pos);
        if (a == Layout.Alignment.ALIGN_CENTER) sb.append("<w:pPr><w:jc w:val=\"center\"/></w:pPr>");
        else if (a == Layout.Alignment.ALIGN_OPPOSITE) sb.append("<w:pPr><w:jc w:val=\"right\"/></w:pPr>");
        int i = pos;
        while (i < end) {
            int j = Math.min(t.nextSpanTransition(i, end, CharacterStyle.class), end);
            if (j <= i) j = end;
            run(sb, t, s.substring(i, j), i, j);
            i = j;
        }
        sb.append("</w:p>");
    }

    private static String hex(int c) { return String.format(Locale.US, "%06X", c & 0xFFFFFF); }

    private static void run(StringBuilder sb, Spanned t, String x, int i, int j) {
        Fmt.Run r = Fmt.run(t, i, j);
        sb.append("<w:r><w:rPr>");
        if (r.b) sb.append("<w:b/>");
        if (r.i) sb.append("<w:i/>");
        if (r.s) sb.append("<w:strike/>");
        if (r.hasFg) sb.append("<w:color w:val=\"").append(hex(r.fg)).append("\"/>");
        if (r.pt != Fmt.DEFAULT_PT)
            sb.append("<w:sz w:val=\"").append(r.pt * 2).append("\"/><w:szCs w:val=\"").append(r.pt * 2).append("\"/>");
        if (r.u) sb.append("<w:u w:val=\"single\"/>");
        if (r.hasBg) sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(hex(r.bg)).append("\"/>");
        sb.append("</w:rPr>");
        StringBuilder cur = new StringBuilder();
        for (int k = 0; k < x.length(); k++) {
            char c = x.charAt(k);
            if (c == '\t') { flush(sb, cur); sb.append("<w:tab/>"); }
            else if (c == '&') cur.append("&amp;");
            else if (c == '<') cur.append("&lt;");
            else if (c == '>') cur.append("&gt;");
            else if (c >= 0x20 && c != 0xFFFE && c != 0xFFFF) cur.append(c);
        }
        flush(sb, cur);
        sb.append("</w:r>");
    }

    private static void flush(StringBuilder sb, StringBuilder cur) {
        if (cur.length() == 0) return;
        sb.append("<w:t xml:space=\"preserve\">").append(cur).append("</w:t>");
        cur.setLength(0);
    }

    // ======================= BACA =======================
    static SpannableStringBuilder read(InputStream in) throws Exception {
        ZipInputStream z = new ZipInputStream(in);
        ZipEntry en;
        byte[] xml = null;
        while ((en = z.getNextEntry()) != null) {
            if (en.getName().equals("word/document.xml")) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) bo.write(buf, 0, n);
                xml = bo.toByteArray();
                break;
            }
        }
        if (xml == null) throw new IOException("Bukan berkas .docx yang valid");

        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int tbl = 0, paraStart = 0, defPt = Fmt.DEFAULT_PT, pt = Fmt.DEFAULT_PT, fg = 0, bg = 0;
        boolean list = false, inRun = false, defB = false, b = false, i = false, u = false, s = false,
                hasFg = false, hasBg = false;
        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                String v = p.getAttributeValue(null, "w:val");
                if (n.equals("w:tbl")) tbl++;
                else if (n.equals("w:p")) {
                    paraStart = sb.length(); list = false; defB = false; defPt = Fmt.DEFAULT_PT;
                    align = Layout.Alignment.ALIGN_NORMAL;
                } else if (n.equals("w:pStyle") && v != null) {
                    String lv = v.toLowerCase(Locale.US);
                    if (lv.equals("title")) { defPt = 28; defB = true; }
                    else if (lv.startsWith("heading")) {
                        defB = true;
                        defPt = lv.endsWith("1") ? 16 : lv.endsWith("2") ? 14 : 12;
                    }
                } else if (n.equals("w:numPr")) list = true;
                else if (n.equals("w:jc") && v != null) {
                    align = v.equals("center") ? Layout.Alignment.ALIGN_CENTER
                            : (v.equals("right") || v.equals("end")) ? Layout.Alignment.ALIGN_OPPOSITE
                            : Layout.Alignment.ALIGN_NORMAL;
                } else if (n.equals("w:r")) {
                    inRun = true; b = defB; i = false; u = false; s = false; hasFg = false; hasBg = false; pt = defPt;
                } else if (inRun && n.equals("w:b")) b = on(v);
                else if (inRun && n.equals("w:i")) i = on(v);
                else if (inRun && n.equals("w:strike")) s = on(v);
                else if (inRun && n.equals("w:u")) u = v != null && !v.equals("none");
                else if (inRun && n.equals("w:sz") && v != null) {
                    try { pt = Math.round(Integer.parseInt(v) / 2f); } catch (NumberFormatException ignore) { }
                } else if (inRun && n.equals("w:color") && v != null && !v.equals("auto")) {
                    try { fg = 0xFF000000 | Integer.parseInt(v, 16); hasFg = true; } catch (NumberFormatException ignore) { }
                } else if (inRun && n.equals("w:highlight") && v != null) {
                    int c = hl(v);
                    if (c != 0) { bg = c; hasBg = true; }
                } else if (inRun && n.equals("w:shd")) {
                    String f = p.getAttributeValue(null, "w:fill");
                    if (f != null && !f.equals("auto")) {
                        try { bg = 0xFF000000 | Integer.parseInt(f, 16); hasBg = true; } catch (NumberFormatException ignore) { }
                    }
                } else if (inRun && n.equals("w:tab")) sb.append('\t');
                else if (inRun && (n.equals("w:br") || n.equals("w:cr"))) sb.append('\n');
                else if (n.equals("w:t")) {
                    String x = p.nextText();
                    int st = sb.length();
                    sb.append(x);
                    int en2 = sb.length();
                    if (en2 > st) {
                        if (b) sb.setSpan(new StyleSpan(Typeface.BOLD), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (i) sb.setSpan(new StyleSpan(Typeface.ITALIC), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (u) sb.setSpan(new UnderlineSpan(), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (s) sb.setSpan(new StrikethroughSpan(), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (pt != Fmt.DEFAULT_PT && pt > 0)
                            sb.setSpan(Fmt.sizeSpan(pt), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (hasFg && (fg & 0xFFFFFF) != 0)
                            sb.setSpan(new ForegroundColorSpan(fg), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (hasBg) sb.setSpan(new BackgroundColorSpan(bg), st, en2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("w:r")) inRun = false;
                else if (n.equals("w:p")) {
                    sb.append(tbl > 0 ? '\t' : '\n');
                    if (list) sb.insert(paraStart, Fmt.BUL);
                    if (tbl == 0 && align != Layout.Alignment.ALIGN_NORMAL)
                        sb.setSpan(new AlignmentSpan.Standard(align), paraStart, sb.length(), Spanned.SPAN_PARAGRAPH);
                } else if (n.equals("w:tr")) {
                    int L = sb.length();
                    if (L > 0 && sb.charAt(L - 1) == '\t') sb.replace(L - 1, L, "\n"); else sb.append('\n');
                } else if (n.equals("w:tbl")) tbl--;
            }
            ev = p.next();
        }
        int L = sb.length();
        if (L > 0 && sb.charAt(L - 1) == '\n') sb.delete(L - 1, L);
        // Supaya mengetik di akhir teks berformat melanjutkan formatnya:
        for (CharacterStyle o : sb.getSpans(0, sb.length(), CharacterStyle.class))
            sb.setSpan(o, sb.getSpanStart(o), sb.getSpanEnd(o), Fmt.F);
        return sb;
    }

    private static boolean on(String v) { return v == null || !(v.equals("0") || v.equals("false") || v.equals("off")); }

    private static int hl(String v) {
        if (v.equals("yellow")) return 0xFFFFFF00;
        if (v.equals("green")) return 0xFF00FF00;
        if (v.equals("cyan")) return 0xFF00FFFF;
        if (v.equals("magenta")) return 0xFFFF00FF;
        if (v.equals("red")) return 0xFFFF0000;
        if (v.equals("blue")) return 0xFF0000FF;
        if (v.equals("lightGray")) return 0xFFC0C0C0;
        if (v.equals("darkGray")) return 0xFF808080;
        return 0;
    }
}
