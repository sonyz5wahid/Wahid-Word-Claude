package id.wahid.word;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;

import java.io.IOException;
import java.io.OutputStream;

@TargetApi(19)
final class PdfModern {
    static void write(Spanned src, OutputStream os) throws IOException {
        final int W = 595, H = 842, M = 72, cw = W - 2 * M, ch = H - 2 * M;
        SpannableStringBuilder t = new SpannableStringBuilder(src);
        for (AbsoluteSizeSpan a : t.getSpans(0, t.length(), AbsoluteSizeSpan.class)) {
            int st = t.getSpanStart(a), en = t.getSpanEnd(a);
            t.removeSpan(a);
            t.setSpan(new AbsoluteSizeSpan(Fmt.ptOf(a), false), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(Fmt.DEFAULT_PT);
        tp.setColor(Color.BLACK);
        StaticLayout lay = new StaticLayout(t, tp, cw, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);

        PdfDocument doc = new PdfDocument();
        int line = 0, n = lay.getLineCount(), pageNo = 1;
        do {
            int first = line, top = lay.getLineTop(first);
            while (line < n && lay.getLineBottom(line) - top <= ch) line++;
            if (line == first) line++;
            int bottom = lay.getLineBottom(line - 1);
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo++).create());
            Canvas c = page.getCanvas();
            c.save();
            c.translate(M, M - top);
            c.clipRect(0, top, cw, bottom);
            lay.draw(c);
            c.restore();
            doc.finishPage(page);
        } while (line < n);
        doc.writeTo(os);
        doc.close();
    }
}
