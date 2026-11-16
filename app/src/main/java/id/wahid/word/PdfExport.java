package id.wahid.word;

import android.os.Build;
import android.text.Spanned;

import java.io.IOException;
import java.io.OutputStream;

final class PdfExport {
    /** Android 4.4+ memakai PdfDocument bawaan (Unicode penuh); di bawahnya memakai penulis PDF sendiri (Latin). */
    static void write(Spanned t, OutputStream os) throws IOException {
        if (Build.VERSION.SDK_INT >= 19) PdfModern.write(t, os);
        else PdfLegacy.write(t, os);
    }
}
