package id.wahid.word;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int REQ_OPEN = 1, REQ_SAVE = 2, REQ_PDF = 3;
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final int BLUE = 0xFF185ABD, RIBBON = 0xFFF3F3F3;

    static final int A_BOLD = 1, A_ITALIC = 2, A_UNDER = 3, A_STRIKE = 4, A_SMALLER = 5, A_BIGGER = 6,
            A_COLOR = 7, A_HILITE = 8, A_BULLET = 9, A_LEFT = 10, A_CENTER = 11, A_RIGHT = 12,
            A_DATE = 13, A_TIME = 14, A_LINE = 15, A_TAB = 16, A_SAVE = 17;

    Paper et;
    TextView titleTv, sizeTv, statusTv;
    Icon colorIcon;
    final View[] panels = new View[2];
    final TextView[] tabs = new TextView[3];
    Uri docUri;
    String docName = "Dokumen1.docx";
    boolean dirty, loading;
    int fontColor = Color.BLACK;

    final View.OnClickListener L = new View.OnClickListener() {
        public void onClick(View v) { act((Integer) v.getTag()); }
    };

    // ------------------------------------------------------------ UI
    class Paper extends EditText {
        Paper(Context c) { super(c); }
        @Override protected void onSelectionChanged(int a, int b) {
            super.onSelectionChanged(a, b);
            if (sizeTv != null) sizeTv.setText(String.valueOf(Fmt.sizeAt(getText(), Math.min(a, b))));
        }
    }

    class Icon extends View {
        final int a;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int a) { super(MainActivity.this); this.a = a; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), l = w * 0.26f, r = w * 0.74f;
            p.setColor(0xFF444444);
            p.setStrokeWidth(dp(1.6f));
            p.setTextAlign(Paint.Align.CENTER);
            if (a == A_BULLET) {
                for (int i = 0; i < 3; i++) {
                    float y = h * (0.3f + 0.2f * i);
                    c.drawCircle(l, y, dp(1.8f), p);
                    c.drawLine(l + dp(6), y, r, y, p);
                }
            } else if (a == A_COLOR || a == A_HILITE) {
                p.setTextSize(h * 0.5f);
                p.setFakeBoldText(true);
                c.drawText(a == A_COLOR ? "A" : "ab", w / 2, h * 0.62f, p);
                p.setColor(a == A_COLOR ? fontColor : 0xFFFFFF00);
                c.drawRect(w * 0.26f, h * 0.72f, w * 0.74f, h * 0.82f, p);
            } else {
                for (int i = 0; i < 4; i++) {
                    float y = h * (0.28f + 0.15f * i);
                    float len = (i % 2 == 0) ? r - l : (r - l) * 0.6f;
                    float x0 = a == A_LEFT ? l : a == A_CENTER ? (w - len) / 2 : r - len;
                    c.drawLine(x0, y, x0 + len, y, p);
                }
            }
        }
    }

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    Drawable pressBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0xFFD0D0D0));
        s.addState(new int[]{}, new ColorDrawable(0x00000000));
        return s;
    }

    View wire(View v, int act, int wDp) {
        v.setTag(act);
        v.setOnClickListener(L);
        v.setBackground(pressBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(38));
        lp.setMargins(dp(1), 0, dp(1), 0);
        v.setLayoutParams(lp);
        return v;
    }

    View tb(String s, int style, int act, int flags, int wDp) {
        TextView t = tv(s, 16, 0xFF222222);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setGravity(Gravity.CENTER);
        if (flags == 1) t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        if (flags == 2) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return wire(t, act, wDp);
    }

    View icon(int act) {
        Icon i = new Icon(act);
        if (act == A_COLOR) colorIcon = i;
        return wire(i, act, 38);
    }

    LinearLayout group(String label, View... items) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(dp(4), dp(4), dp(4), dp(2));
        LinearLayout r = new LinearLayout(this);
        for (View v : items) r.addView(v);
        g.addView(r);
        TextView l = tv(label, 10, 0xFF666666);
        l.setGravity(Gravity.CENTER);
        g.addView(l);
        return g;
    }

    View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFD0D0D0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(50));
        lp.setMargins(dp(4), dp(6), dp(4), 0);
        v.setLayoutParams(lp);
        return v;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFE1E1E1);

        // Bilah judul
        LinearLayout title = new LinearLayout(this);
        title.setBackgroundColor(BLUE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, dp(4), 0);
        TextView logo = tv("W", 15, Color.WHITE);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable();
        lg.setColor(0xFF103F91); lg.setCornerRadius(dp(4));
        logo.setBackground(lg);
        title.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(26)));
        titleTv = tv("", 14, Color.WHITE);
        titleTv.setSingleLine(true);
        titleTv.setPadding(dp(10), 0, dp(6), 0);
        title.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView save = tv("Simpan", 14, Color.WHITE);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(12), 0, dp(12), 0);
        save.setTag(A_SAVE); save.setOnClickListener(L); save.setBackground(pressBg());
        title.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // Tab
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setBackgroundColor(BLUE);
        tabRow.setPadding(dp(6), 0, dp(6), 0);
        String[] names = {"Berkas", "Beranda", "Sisipkan"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = tv(names[i], 14, Color.WHITE);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (idx == 0) fileMenu(); else selectTab(idx - 1); }
            });
            tabs[i] = t;
            tabRow.addView(t);
        }
        root.addView(tabRow);

        // Ribbon
        sizeTv = tv("11", 14, 0xFF222222);
        sizeTv.setGravity(Gravity.CENTER);
        sizeTv.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(38)));
        LinearLayout home = new LinearLayout(this);
        home.setPadding(dp(4), 0, dp(4), 0);
        home.addView(group("Font",
                tb("B", Typeface.BOLD, A_BOLD, 0, 38), tb("I", Typeface.ITALIC, A_ITALIC, 0, 38),
                tb("U", Typeface.NORMAL, A_UNDER, 1, 38), tb("S", Typeface.NORMAL, A_STRIKE, 2, 38),
                tb("A-", Typeface.NORMAL, A_SMALLER, 0, 38), sizeTv, tb("A+", Typeface.NORMAL, A_BIGGER, 0, 38),
                icon(A_COLOR), icon(A_HILITE)));
        home.addView(divider());
        home.addView(group("Paragraf", icon(A_BULLET), icon(A_LEFT), icon(A_CENTER), icon(A_RIGHT)));
        LinearLayout ins = new LinearLayout(this);
        ins.setPadding(dp(4), 0, dp(4), 0);
        ins.addView(group("Teks", tb("Tanggal", 0, A_DATE, 0, 70), tb("Waktu", 0, A_TIME, 0, 62),
                tb("Garis", 0, A_LINE, 0, 56), tb("Tab", 0, A_TAB, 0, 48)));
        panels[0] = home; panels[1] = ins;
        FrameLayout rf = new FrameLayout(this);
        rf.addView(home); rf.addView(ins);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(RIBBON);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(rf);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View line = new View(this);
        line.setBackgroundColor(0xFFC8C8C8);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // Kertas
        et = new Paper(this);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setTextColor(Color.BLACK);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Fmt.DEFAULT_PT * Fmt.PT_TO_DP);
        et.setLineSpacing(0, 1.15f);
        et.setPadding(dp(26), dp(30), dp(26), dp(30));
        GradientDrawable paper = new GradientDrawable();
        paper.setColor(Color.WHITE); paper.setStroke(dp(1), 0xFFBDBDBD);
        et.setBackground(paper);
        et.setMinHeight((int) (getResources().getDisplayMetrics().widthPixels * 1.35f));
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable e) {
                if (!loading) { dirty = true; updateTitle(); }
                updateStatus();
            }
        });
        LinearLayout pageBox = new LinearLayout(this);
        pageBox.setPadding(dp(8), dp(8), dp(8), dp(8));
        pageBox.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(pageBox);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Bilah status
        statusTv = tv("", 11, 0xFF555555);
        statusTv.setBackgroundColor(RIBBON);
        statusTv.setPadding(dp(10), dp(5), dp(10), dp(5));
        root.addView(statusTv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(0);
        updateTitle();
        updateStatus();
        Uri u = getIntent() == null ? null : getIntent().getData();
        if (u != null) loadUri(u);
    }

    void selectTab(int k) {
        for (int i = 0; i < 2; i++) {
            panels[i].setVisibility(i == k ? View.VISIBLE : View.GONE);
            tabs[i + 1].setBackgroundColor(i == k ? RIBBON : 0x00000000);
            tabs[i + 1].setTextColor(i == k ? BLUE : Color.WHITE);
        }
    }

    void updateTitle() { titleTv.setText(docName + (dirty ? " *" : "") + " - Wahid Word"); }

    void updateStatus() {
        String s = et.getText().toString().trim();
        int words = s.length() == 0 ? 0 : s.split("\\s+").length;
        statusTv.setText("Kata: " + words + "     Karakter: " + et.getText().length());
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ------------------------------------------------------------ Aksi pemformatan
    int[] sel() {
        int a = Math.max(0, et.getSelectionStart()), b = Math.max(0, et.getSelectionEnd());
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    void toggle(Class<?> c, Fmt.Filter f, Object span) {
        int[] r = sel();
        Editable t = et.getText();
        boolean on = Fmt.covered(t, c, f, r[0], r[1]);
        Fmt.set(t, c, f, on ? null : span, r[0], r[1]);
    }

    void changeSize(int dir) {
        int[] r = sel();
        Editable t = et.getText();
        int cur = Fmt.sizeAt(t, r[0]), idx = 0;
        for (int k = 0; k < Fmt.SIZES.length; k++) if (Fmt.SIZES[k] <= cur) idx = k;
        idx = Math.max(0, Math.min(Fmt.SIZES.length - 1, idx + dir));
        Fmt.set(t, AbsoluteSizeSpan.class, Fmt.ANY, Fmt.sizeSpan(Fmt.SIZES[idx]), r[0], r[1]);
        sizeTv.setText(String.valueOf(Fmt.SIZES[idx]));
    }

    void insert(String s) {
        int[] r = sel();
        et.getText().replace(r[0], r[1], s);
    }

    void act(int a) {
        int[] r = sel();
        Editable t = et.getText();
        switch (a) {
            case A_BOLD: toggle(StyleSpan.class, Fmt.style(Typeface.BOLD), new StyleSpan(Typeface.BOLD)); break;
            case A_ITALIC: toggle(StyleSpan.class, Fmt.style(Typeface.ITALIC), new StyleSpan(Typeface.ITALIC)); break;
            case A_UNDER: toggle(UnderlineSpan.class, Fmt.ANY, new UnderlineSpan()); break;
            case A_STRIKE: toggle(StrikethroughSpan.class, Fmt.ANY, new StrikethroughSpan()); break;
            case A_SMALLER: changeSize(-1); break;
            case A_BIGGER: changeSize(1); break;
            case A_COLOR: pickColor(); break;
            case A_HILITE: toggle(BackgroundColorSpan.class, Fmt.ANY, new BackgroundColorSpan(0xFFFFFF00)); break;
            case A_BULLET: Fmt.bullets(t, r[0], r[1]); break;
            case A_LEFT: Fmt.align(t, Layout.Alignment.ALIGN_NORMAL, r[0], r[1]); break;
            case A_CENTER: Fmt.align(t, Layout.Alignment.ALIGN_CENTER, r[0], r[1]); break;
            case A_RIGHT: Fmt.align(t, Layout.Alignment.ALIGN_OPPOSITE, r[0], r[1]); break;
            case A_DATE: insert(new SimpleDateFormat("d MMMM yyyy", new Locale("id", "ID")).format(new Date())); break;
            case A_TIME: insert(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())); break;
            case A_LINE: insert("\n\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\n"); break;
            case A_TAB: insert("\t"); break;
            case A_SAVE: save(); break;
        }
    }

    void pickColor() {
        final int[] cols = {0xFF000000, 0xFFC00000, 0xFFED7D31, 0xFF00B050, 0xFF0070C0, 0xFF7030A0, 0xFF7F7F7F, 0xFF00B0F0};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        final AlertDialog[] dlg = new AlertDialog[1];
        for (int row = 0; row < 2; row++) {
            LinearLayout rw = new LinearLayout(this);
            for (int k = 0; k < 4; k++) {
                final int c = cols[row * 4 + k];
                View v = new View(this);
                v.setBackgroundColor(c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                v.setLayoutParams(lp);
                v.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View x) {
                        fontColor = c;
                        int[] r = sel();
                        Fmt.set(et.getText(), ForegroundColorSpan.class, Fmt.ANY,
                                c == Color.BLACK ? null : new ForegroundColorSpan(c), r[0], r[1]);
                        if (colorIcon != null) colorIcon.invalidate();
                        dlg[0].dismiss();
                    }
                });
                rw.addView(v);
            }
            box.addView(rw);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle("Warna font").setView(box).create();
        dlg[0].show();
    }

    // ------------------------------------------------------------ Berkas
    void fileMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{"Dokumen baru", "Buka...", "Simpan",
                "Simpan sebagai...", "Ekspor ke PDF...", "Tentang"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0: confirmDiscard(new Runnable() { public void run() { newDoc(); } }); break;
                    case 1: confirmDiscard(new Runnable() { public void run() { openDoc(); } }); break;
                    case 2: save(); break;
                    case 3: saveAs(); break;
                    case 4: exportPdf(); break;
                    case 5: new AlertDialog.Builder(MainActivity.this).setTitle("Wahid Word 1.0")
                            .setMessage("Editor dokumen .docx native untuk Android 4.2+.\n\n"
                                    + "Catatan: tabel, gambar, dan fitur lanjutan DOCX belum didukung. "
                                    + "Gunakan 'Simpan sebagai' agar berkas asli tidak tertimpa.")
                            .setPositiveButton("OK", null).show(); break;
                }
            }
        }).show();
    }

    void confirmDiscard(final Runnable r) {
        if (!dirty) { r.run(); return; }
        new AlertDialog.Builder(this).setTitle("Perubahan belum disimpan")
                .setMessage("Buang perubahan dan lanjutkan?")
                .setPositiveButton("Buang", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { r.run(); }
                }).setNegativeButton("Batal", null).show();
    }

    @Override public void onBackPressed() {
        confirmDiscard(new Runnable() { public void run() { finish(); } });
    }

    void newDoc() {
        loading = true; et.setText(""); loading = false;
        docUri = null; docName = "Dokumen1.docx"; dirty = false;
        updateTitle();
    }

    void openDoc() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 19) {
            i = new Intent("android.intent.action.OPEN_DOCUMENT");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
        }
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, REQ_OPEN); }
        catch (Exception e) { toast("Pasang aplikasi pengelola berkas terlebih dahulu."); }
    }

    String nameOf(Uri u) {
        String n = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) n = c.getString(0);
        } catch (Exception ignore) {
        } finally { if (c != null) c.close(); }
        if (n == null) n = u.getLastPathSegment();
        return n == null ? "Dokumen.docx" : n;
    }

    void loadUri(Uri u) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(u);
            SpannableStringBuilder sb = DocxIO.read(in);
            loading = true; et.setText(sb); loading = false;
            et.setSelection(0);
            docUri = u; docName = nameOf(u); dirty = false;
            updateTitle(); updateStatus();
        } catch (Exception e) {
            loading = false;
            toast("Gagal membuka berkas: " + e.getMessage());
        } finally { try { if (in != null) in.close(); } catch (Exception ignore) { } }
    }

    String base() {
        int k = docName.lastIndexOf('.');
        return k > 0 ? docName.substring(0, k) : docName;
    }

    void save() {
        if (docUri == null || !docName.toLowerCase(Locale.US).endsWith(".docx")) { saveAs(); return; }
        writeTo(docUri, false);
    }

    void saveAs() { target(false); }

    void exportPdf() { target(true); }

    void target(boolean pdf) {
        if (Build.VERSION.SDK_INT >= 19) {
            Intent i = new Intent("android.intent.action.CREATE_DOCUMENT");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(pdf ? "application/pdf" : DOCX);
            i.putExtra(Intent.EXTRA_TITLE, base() + (pdf ? ".pdf" : ".docx"));
            startActivityForResult(i, pdf ? REQ_PDF : REQ_SAVE);
        } else {
            askName(base(), pdf);
        }
    }

    /** Android < 4.4: tidak ada penyimpan berkas sistem, simpan ke folder WahidWord. */
    void askName(String def, final boolean pdf) {
        final EditText in = new EditText(this);
        in.setText(def);
        in.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle(pdf ? "Ekspor PDF" : "Simpan sebagai")
                .setMessage("Disimpan di folder WahidWord pada penyimpanan.").setView(in)
                .setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = in.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                        if (n.length() == 0) return;
                        String ext = pdf ? ".pdf" : ".docx";
                        if (!n.toLowerCase(Locale.US).endsWith(ext)) n += ext;
                        File dir = new File(Environment.getExternalStorageDirectory(), "WahidWord");
                        dir.mkdirs();
                        writeTo(Uri.fromFile(new File(dir, n)), pdf);
                    }
                }).setNegativeButton("Batal", null).show();
    }

    void writeTo(Uri uri, boolean pdf) {
        OutputStream os = null;
        try {
            SpannableStringBuilder t = Fmt.clean(et.getText());
            os = getContentResolver().openOutputStream(uri, "wt");
            if (pdf) PdfExport.write(t, os); else DocxIO.write(t, os);
            os.flush();
            os.close();
            os = null;
            if (!pdf) { docUri = uri; docName = nameOf(uri); dirty = false; updateTitle(); }
            toast(pdf ? "PDF berhasil dibuat." : "Tersimpan.");
        } catch (Exception e) {
            toast("Gagal menyimpan: " + e.getMessage());
        } finally { try { if (os != null) os.close(); } catch (Exception ignore) { } }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_OPEN) loadUri(u);
        else if (req == REQ_SAVE) writeTo(u, false);
        else if (req == REQ_PDF) writeTo(u, true);
    }
}
