package id.wahid.word;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.CharacterStyle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Penulis PDF sederhana untuk Android < 4.4 (tidak ada PdfDocument). Font Helvetica, aksara Latin saja. */
final class PdfLegacy {
    static final int W = 595, H = 842, M = 72, CW = W - 2 * M, CH = H - 2 * M;
    static final Charset CP = Charset.forName("windows-1252");

    static final class Wd { String txt; int st; Fmt.Run r; float w; }

    private final List<byte[]> pages = new ArrayList<byte[]>();
    private ByteArrayOutputStream cur = new ByteArrayOutputStream();
    private float y = 0;
    private final Paint[] paints = new Paint[4];

    static void write(Spanned t, OutputStream os) throws IOException { new PdfLegacy().run(t, os); }

    private Paint paint(int st, float size) {
        if (paints[st] == null) {
            paints[st] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paints[st].setTypeface(Typeface.create(Typeface.SANS_SERIF, st));
        }
        paints[st].setTextSize(size);
        return paints[st];
    }

    private static String trimEnd(String s) { return s.replaceAll(" +$", ""); }

    private void run(Spanned t, OutputStream os) throws IOException {
        String s = t.toString();
        int pos = 0;
        while (true) {
            int nl = s.indexOf('\n', pos);
            int end = nl < 0 ? s.length() : nl;
            paragraph(t, s, pos, end);
            if (nl < 0) break;
            pos = nl + 1;
        }
        pages.add(cur.toByteArray());
        build(os);
    }

    private void paragraph(Spanned t, String s, int ps, int pe) throws IOException {
        Layout.Alignment al = Fmt.alignAt(t, ps);
        List<Wd> line = new ArrayList<Wd>();
        float lw = 0;
        int i = ps;
        while (i < pe) {
            int j = Math.min(t.nextSpanTransition(i, pe, CharacterStyle.class), pe);
            if (j <= i) j = pe;
            Fmt.Run r = Fmt.run(t, i, j);
            int st = (r.b ? 1 : 0) | (r.i ? 2 : 0);
            String x = s.substring(i, j).replace("\t", "    ");
            int a = 0;
            while (a < x.length()) {
                int b = x.indexOf(' ', a);
                b = b < 0 ? x.length() : b + 1;
                Wd w = new Wd();
                w.txt = x.substring(a, b); w.st = st; w.r = r;
                Paint p = paint(st, r.pt);
                w.w = p.measureText(w.txt);
                float trimmed = p.measureText(trimEnd(w.txt));
                if (lw + trimmed > CW && !line.isEmpty()) {
                    flush(line, lw, al);
                    line = new ArrayList<Wd>();
                    lw = 0;
                }
                line.add(w);
                lw += w.w;
                a = b;
            }
            i = j;
        }
        flush(line, lw, al);
        y += 4;
    }

    private void flush(List<Wd> line, float lw, Layout.Alignment al) throws IOException {
        float mx = line.isEmpty() ? Fmt.DEFAULT_PT : 0;
        for (Wd w : line) mx = Math.max(mx, w.r.pt);
        float lh = mx * 1.25f;
        if (y + lh > CH) { pages.add(cur.toByteArray()); cur = new ByteArrayOutputStream(); y = 0; }
        float base = H - M - y - mx;
        if (!line.isEmpty()) {
            Wd last = line.get(line.size() - 1);
            float trail = last.w - paint(last.st, last.r.pt).measureText(trimEnd(last.txt));
            float vis = lw - trail;
            float x = M + (al == Layout.Alignment.ALIGN_CENTER ? (CW - vis) / 2
                    : al == Layout.Alignment.ALIGN_OPPOSITE ? CW - vis : 0);
            for (Wd w : line) {
                Fmt.Run r = w.r;
                if (r.hasBg) {
                    out(col(r.bg) + " rg " + f(x) + " " + f(base - r.pt * 0.25f) + " " + f(w.w) + " " + f(r.pt * 1.2f) + " re f\n");
                }
                out("BT /F" + (w.st + 1) + " " + f(r.pt) + " Tf " + (r.hasFg ? col(r.fg) : "0 0 0")
                        + " rg 1 0 0 1 " + f(x) + " " + f(base) + " Tm ");
                str(w.txt);
                out(" Tj ET\n");
                if (r.u || r.s) {
                    float tw = paint(w.st, r.pt).measureText(trimEnd(w.txt));
                    out((r.hasFg ? col(r.fg) : "0 0 0") + " RG 0.6 w ");
                    if (r.u) out(f(x) + " " + f(base - 1.5f) + " m " + f(x + tw) + " " + f(base - 1.5f) + " l S\n");
                    if (r.s) out(f(x) + " " + f(base + r.pt * 0.3f) + " m " + f(x + tw) + " " + f(base + r.pt * 0.3f) + " l S\n");
                }
                x += w.w;
            }
        }
        y += lh;
    }

    // ---- util keluaran ----
    private static String f(float v) { return String.format(Locale.US, "%.2f", v); }

    private static String col(int c) {
        return String.format(Locale.US, "%.3f %.3f %.3f", ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }

    private void out(String s) throws IOException { byte[] b = s.getBytes(CP); cur.write(b, 0, b.length); }

    private void str(String s) throws IOException {
        cur.write('(');
        for (byte c : s.getBytes(CP)) {
            if (c == '(' || c == ')' || c == '\\') cur.write('\\');
            cur.write(c);
        }
        cur.write(')');
    }

    private static void a(ByteArrayOutputStream o, String s) { byte[] b = s.getBytes(CP); o.write(b, 0, b.length); }

    private static void begin(ByteArrayOutputStream o, List<Integer> off) {
        off.add(o.size());
        a(o, off.size() + " 0 obj\n");
    }

    private static void obj(ByteArrayOutputStream o, List<Integer> off, String body) {
        begin(o, off);
        a(o, body + "\nendobj\n");
    }

    private void build(OutputStream os) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        List<Integer> off = new ArrayList<Integer>();
        a(o, "%PDF-1.4\n");
        int n = pages.size();
        obj(o, off, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int k = 0; k < n; k++) kids.append(7 + 2 * k).append(" 0 R ");
        obj(o, off, "<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
        String[] fonts = {"Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique"};
        for (String fn : fonts)
            obj(o, off, "<< /Type /Font /Subtype /Type1 /BaseFont /" + fn + " /Encoding /WinAnsiEncoding >>");
        for (int k = 0; k < n; k++) {
            obj(o, off, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + W + " " + H + "] /Resources << /Font << "
                    + "/F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> /Contents " + (8 + 2 * k) + " 0 R >>");
            byte[] c = pages.get(k);
            begin(o, off);
            a(o, "<< /Length " + c.length + " >>\nstream\n");
            o.write(c, 0, c.length);
            a(o, "\nendstream\nendobj\n");
        }
        int xr = o.size();
        a(o, "xref\n0 " + (off.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : off) a(o, String.format(Locale.US, "%010d 00000 n \n", p));
        a(o, "trailer\n<< /Size " + (off.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xr + "\n%%EOF\n");
        byte[] all = o.toByteArray();
        os.write(all, 0, all.length);
    }
}
