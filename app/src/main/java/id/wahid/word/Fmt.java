package id.wahid.word;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.util.ArrayList;
import java.util.List;

/** Helper pemformatan teks (span) yang dipakai editor, penulis DOCX dan PDF. */
final class Fmt {
    static final float PT_TO_DP = 1.3f;       // 1 pt dokumen = 1,3 dp di layar
    static final int DEFAULT_PT = 11;
    static final int[] SIZES = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72};
    static final int F = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    static final String BUL = "\u2022 ";

    interface Filter { boolean ok(Object o); }

    static final Filter ANY = new Filter() { public boolean ok(Object o) { return true; } };

    static Filter style(final int st) {
        return new Filter() {
            public boolean ok(Object o) { return (((StyleSpan) o).getStyle() & st) != 0; }
        };
    }

    static int ptOf(AbsoluteSizeSpan s) { return Math.round(s.getSize() / PT_TO_DP); }

    static AbsoluteSizeSpan sizeSpan(int pt) {
        return new AbsoluteSizeSpan(Math.round(pt * PT_TO_DP), true);
    }

    static Object copy(Object o) {
        if (o instanceof StyleSpan) return new StyleSpan(((StyleSpan) o).getStyle());
        if (o instanceof UnderlineSpan) return new UnderlineSpan();
        if (o instanceof StrikethroughSpan) return new StrikethroughSpan();
        if (o instanceof AbsoluteSizeSpan) {
            AbsoluteSizeSpan a = (AbsoluteSizeSpan) o;
            return new AbsoluteSizeSpan(a.getSize(), a.getDip());
        }
        if (o instanceof ForegroundColorSpan)
            return new ForegroundColorSpan(((ForegroundColorSpan) o).getForegroundColor());
        if (o instanceof BackgroundColorSpan)
            return new BackgroundColorSpan(((BackgroundColorSpan) o).getBackgroundColor());
        if (o instanceof AlignmentSpan.Standard)
            return new AlignmentSpan.Standard(((AlignmentSpan.Standard) o).getAlignment());
        return null;
    }

    /** Salinan bersih: hanya span milik kita (tanpa span IME/seleksi). */
    static SpannableStringBuilder clean(Spanned src) {
        SpannableStringBuilder b = new SpannableStringBuilder(src.toString());
        for (Object o : src.getSpans(0, src.length(), Object.class)) {
            Object c = copy(o);
            if (c == null) continue;
            int st = src.getSpanStart(o), en = src.getSpanEnd(o);
            if (st >= en) continue;
            try {
                b.setSpan(c, st, en, c instanceof AlignmentSpan ? Spanned.SPAN_PARAGRAPH
                        : Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (RuntimeException ignore) { }
        }
        return b;
    }

    // ---------- gaya karakter ----------

    static boolean covered(Spannable t, Class<?> c, Filter f, int s, int e) {
        if (s == e) {
            for (Object o : t.getSpans(s, s, c))
                if (f.ok(o) && t.getSpanStart(o) == s && t.getSpanEnd(o) == s) return true;
            if (s == 0) return false;
            s--;
        }
        boolean[] cov = new boolean[e - s];
        for (Object o : t.getSpans(s, e, c)) {
            if (!f.ok(o)) continue;
            int a = Math.max(s, t.getSpanStart(o)), b = Math.min(e, t.getSpanEnd(o));
            for (int i = a; i < b; i++) cov[i - s] = true;
        }
        for (boolean b : cov) if (!b) return false;
        return true;
    }

    static void remove(Editable t, Class<?> c, Filter f, int s, int e) {
        for (Object o : t.getSpans(s, e, c)) {
            if (!f.ok(o)) continue;
            int a = t.getSpanStart(o), b = t.getSpanEnd(o);
            t.removeSpan(o);
            if (a < s) t.setSpan(copy(o), a, s, s == e ? Spanned.SPAN_EXCLUSIVE_EXCLUSIVE : F);
            if (b > e) t.setSpan(copy(o), e, b, F);
        }
    }

    /** Ganti gaya pada [s,e); newSpan == null berarti hapus gaya. */
    static void set(Editable t, Class<?> c, Filter f, Object newSpan, int s, int e) {
        remove(t, c, f, s, e);
        if (newSpan != null)
            t.setSpan(newSpan, s, e, s == e ? Spanned.SPAN_INCLUSIVE_INCLUSIVE : F);
    }

    static int sizeAt(Spanned t, int pos) {
        int n = t.length();
        if (pos < 0) pos = 0;
        if (pos > n) pos = n;
        for (AbsoluteSizeSpan o : t.getSpans(pos, pos, AbsoluteSizeSpan.class))
            if (t.getSpanStart(o) == pos && t.getSpanEnd(o) == pos) return ptOf(o);
        int p = pos > 0 ? pos - 1 : 0, q = Math.min(p + 1, n), r = DEFAULT_PT;
        if (q > p)
            for (AbsoluteSizeSpan o : t.getSpans(p, q, AbsoluteSizeSpan.class))
                if (t.getSpanStart(o) <= p && t.getSpanEnd(o) >= q) r = ptOf(o);
        return r;
    }

    // ---------- paragraf ----------

    static int pStart(CharSequence t, int pos) {
        int i = pos;
        while (i > 0 && t.charAt(i - 1) != '\n') i--;
        return i;
    }

    static int pEnd(CharSequence t, int pos) {
        int i = pos, n = t.length();
        while (i < n && t.charAt(i) != '\n') i++;
        return i < n ? i + 1 : i;
    }

    static Layout.Alignment alignAt(Spanned t, int ps) {
        Layout.Alignment a = Layout.Alignment.ALIGN_NORMAL;
        for (AlignmentSpan.Standard o : t.getSpans(ps, Math.min(ps + 1, t.length()), AlignmentSpan.Standard.class))
            if (t.getSpanStart(o) <= ps && t.getSpanEnd(o) > ps) a = o.getAlignment();
        return a;
    }

    static void align(Editable t, Layout.Alignment a, int s, int e) {
        int e2 = (e > s && t.charAt(e - 1) == '\n') ? e - 1 : e;
        int ps = pStart(t, s), pe = pEnd(t, e2);
        for (AlignmentSpan.Standard o : t.getSpans(ps, pe, AlignmentSpan.Standard.class)) t.removeSpan(o);
        if (a == Layout.Alignment.ALIGN_NORMAL) return;
        int i = ps;
        while (i < pe) {
            int j = pEnd(t, i);
            t.setSpan(new AlignmentSpan.Standard(a), i, j, Spanned.SPAN_PARAGRAPH);
            i = j;
        }
    }

    private static boolean hasBullet(CharSequence t, int st) {
        return t.length() >= st + 2 && t.charAt(st) == '\u2022' && t.charAt(st + 1) == ' ';
    }

    static void bullets(Editable t, int s, int e) {
        int e2 = (e > s && t.charAt(e - 1) == '\n') ? e - 1 : e;
        int ps = pStart(t, s), pe = pEnd(t, e2);
        List<Integer> starts = new ArrayList<Integer>();
        int i = ps;
        do { starts.add(i); i = pEnd(t, i); } while (i < pe);
        boolean all = true;
        for (int st : starts) if (!hasBullet(t, st)) all = false;
        for (int k = starts.size() - 1; k >= 0; k--) {
            int st = starts.get(k);
            if (all) t.delete(st, st + 2);
            else if (!hasBullet(t, st)) t.insert(st, BUL);
        }
    }

    // ---------- properti satu "run" ----------

    static final class Run {
        boolean b, i, u, s, hasFg, hasBg;
        int pt = DEFAULT_PT, fg = 0xFF000000, bg;
    }

    static Run run(Spanned t, int i, int j) {
        Run r = new Run();
        for (CharacterStyle o : t.getSpans(i, j, CharacterStyle.class)) {
            if (t.getSpanStart(o) > i || t.getSpanEnd(o) < j) continue;
            if (o instanceof StyleSpan) {
                int st = ((StyleSpan) o).getStyle();
                if ((st & Typeface.BOLD) != 0) r.b = true;
                if ((st & Typeface.ITALIC) != 0) r.i = true;
            } else if (o instanceof UnderlineSpan) r.u = true;
            else if (o instanceof StrikethroughSpan) r.s = true;
            else if (o instanceof AbsoluteSizeSpan) r.pt = ptOf((AbsoluteSizeSpan) o);
            else if (o instanceof ForegroundColorSpan) {
                r.hasFg = true; r.fg = ((ForegroundColorSpan) o).getForegroundColor();
            } else if (o instanceof BackgroundColorSpan) {
                r.hasBg = true; r.bg = ((BackgroundColorSpan) o).getBackgroundColor();
            }
        }
        return r;
    }
}
